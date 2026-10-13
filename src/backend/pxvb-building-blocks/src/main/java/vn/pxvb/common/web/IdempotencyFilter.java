package vn.pxvb.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import vn.pxvb.common.error.ErrorCode;

/**
 * Idempotency cho lệnh ghi từ tablet (SDD §2.5, FR-CC-061): header {@code Idempotency-Key} (= clientCommandId)
 * + {@code X-Device-Id}. Trùng khóa + cùng nội dung → trả lại kết quả đã lưu; khác nội dung hoặc đang xử lý → 409.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String KEY_HEADER = "Idempotency-Key";
    public static final String DEVICE_HEADER = "X-Device-Id";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_KEY_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public IdempotencyFilter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITE_METHODS.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")
                || request.getHeader(KEY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(KEY_HEADER);
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            writeProblem(response, ErrorCode.VALIDATION, "Idempotency-Key không hợp lệ");
            return;
        }
        String device = request.getHeader(DEVICE_HEADER) == null ? "" : request.getHeader(DEVICE_HEADER);
        byte[] body = request.getInputStream().readAllBytes();
        String hash = sha256(request.getMethod() + " " + request.getRequestURI() + "\n", body);

        try {
            jdbc.update("INSERT INTO sys_idempotency_record(idem_key, device_id, request_hash, created_at) "
                    + "VALUES (?, ?, ?, UTC_TIMESTAMP(6))", key, device, hash);
        } catch (DuplicateKeyException dup) {
            replayOrReject(key, device, hash, response);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapped);
        } catch (IOException | ServletException | RuntimeException ex) {
            jdbc.update("DELETE FROM sys_idempotency_record WHERE idem_key = ? AND device_id = ?", key, device);
            throw ex;
        }
        if (wrapped.getStatus() >= 500) {
            jdbc.update("DELETE FROM sys_idempotency_record WHERE idem_key = ? AND device_id = ?", key, device);
        } else {
            jdbc.update("UPDATE sys_idempotency_record SET response_status = ?, response_content_type = ?, "
                            + "response_body = ? WHERE idem_key = ? AND device_id = ?",
                    wrapped.getStatus(), wrapped.getContentType(),
                    new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8), key, device);
        }
        wrapped.copyBodyToResponse();
    }

    private void replayOrReject(String key, String device, String hash, HttpServletResponse response)
            throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT request_hash, response_status, response_content_type, response_body "
                        + "FROM sys_idempotency_record WHERE idem_key = ? AND device_id = ?", key, device);
        if (rows.isEmpty()) {
            writeProblem(response, ErrorCode.CONFLICT, "Yêu cầu trùng khóa đang được xử lý, vui lòng thử lại");
            return;
        }
        Map<String, Object> row = rows.get(0);
        if (!hash.equals(row.get("request_hash"))) {
            writeProblem(response, ErrorCode.CONFLICT, "Idempotency-Key đã được dùng cho nội dung khác");
            return;
        }
        Object status = row.get("response_status");
        if (status == null) {
            writeProblem(response, ErrorCode.CONFLICT, "Yêu cầu trùng khóa đang được xử lý, vui lòng thử lại");
            return;
        }
        response.setStatus(((Number) status).intValue());
        if (row.get("response_content_type") != null) {
            response.setContentType((String) row.get("response_content_type"));
        }
        response.setHeader("Idempotent-Replayed", "true");
        String saved = (String) row.get("response_body");
        if (saved != null) {
            response.getOutputStream().write(saved.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeProblem(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(code.status()), detail);
        pd.setTitle(code.title());
        pd.setProperty("code", code.code());
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), pd);
    }

    private static String sha256(String prefix, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(prefix.getBytes(StandardCharsets.UTF_8));
            md.update(body);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
