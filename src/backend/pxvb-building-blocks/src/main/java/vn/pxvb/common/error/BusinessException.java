package vn.pxvb.common.error;

import java.util.List;
import java.util.Map;

/** Lỗi nghiệp vụ dự kiến trước; được {@code GlobalExceptionHandler} map sang RFC 7807 ProblemDetail. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, List<String>> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.title());
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    public BusinessException(ErrorCode errorCode, String detail, Map<String, List<String>> fieldErrors) {
        super(detail);
        this.errorCode = errorCode;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, List<String>> fieldErrors() {
        return fieldErrors;
    }
}
