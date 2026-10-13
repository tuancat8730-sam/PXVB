package vn.pxvb.common.web;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Map mọi lỗi sang RFC 7807: {type,title(vi),status,code,detail,errors{field:[msg]},traceId} (SDD §2.5). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException ex) {
        return problem(ex.errorCode(), ex.getMessage(), ex.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.computeIfAbsent(fe.getField(), k -> new ArrayList<>()).add(fe.getDefaultMessage()));
        return problem(ErrorCode.VALIDATION, ErrorCode.VALIDATION.title(), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraint(ConstraintViolationException ex) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.computeIfAbsent(v.getPropertyPath().toString(), k -> new ArrayList<>()).add(v.getMessage()));
        return problem(ErrorCode.VALIDATION, ErrorCode.VALIDATION.title(), errors);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> optimistic(OptimisticLockingFailureException ex) {
        return problem(ErrorCode.CONFLICT, ErrorCode.CONFLICT.title(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.title(), Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated(AuthenticationException ex) {
        return problem(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.title(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> noResource(NoResourceFoundException ex) {
        return problem(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.title(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        String traceId = traceId();
        log.error("Lỗi không dự kiến traceId={}", traceId, ex);
        return problem(ErrorCode.INTERNAL, ErrorCode.INTERNAL.title(), Map.of());
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail,
                                                         Map<String, List<String>> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.status()), detail);
        pd.setTitle(code.title());
        pd.setProperty("code", code.code());
        pd.setProperty("errors", errors);
        pd.setProperty("traceId", traceId());
        return ResponseEntity.status(code.status()).body(pd);
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
