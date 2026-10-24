package vn.pxvb.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.identity.application.RequestRateLimiter;

/**
 * Giới hạn tần suất các lệnh nhạy cảm đăng nhập/kích hoạt (L04, PXVB-CC-0429). Khóa gồm đường dẫn, địa chỉ kết nối
 * và {@code X-Device-Id} để máy tablet dùng chung IP NAT không bị chặn nhầm. Không tin {@code X-Forwarded-For}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN = "/api/v1/identity/auth/login";
    static final String PIN_LOGIN = "/api/v1/identity/auth/pin-login";
    static final String DEVICE_ACTIVATE = "/api/v1/identity/devices/activate";
    private static final Set<String> LIMITED_PATHS = Set.of(LOGIN, PIN_LOGIN, DEVICE_ACTIVATE);
    private static final String DEVICE_HEADER = "X-Device-Id";
    private static final String KEY_SEPARATOR = "|";

    private final RequestRateLimiter limiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(RequestRateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> retryAfter = limiter.tryAcquire(keyOf(request));
        if (retryAfter.isPresent()) {
            writeTooManyRequests(response, retryAfter.get());
            return;
        }
        chain.doFilter(request, response);
    }

    private static String keyOf(HttpServletRequest request) {
        String device = request.getHeader(DEVICE_HEADER) == null ? "" : request.getHeader(DEVICE_HEADER);
        return request.getRequestURI() + KEY_SEPARATOR + request.getRemoteAddr() + KEY_SEPARATOR + device;
    }

    private void writeTooManyRequests(HttpServletResponse response, Duration retryAfter) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(ErrorCode.RATE_LIMITED.status()),
                ErrorCode.RATE_LIMITED.title());
        pd.setTitle(ErrorCode.RATE_LIMITED.title());
        pd.setProperty("code", ErrorCode.RATE_LIMITED.code());
        response.setStatus(ErrorCode.RATE_LIMITED.status());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1L, retryAfter.toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
