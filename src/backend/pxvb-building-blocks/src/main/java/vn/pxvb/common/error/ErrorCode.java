package vn.pxvb.common.error;

/**
 * Mã lỗi nghiệp vụ dạng {@code PXVB-<MOD>-<nnnn>} (SDD Phụ lục C). Thông điệp mặc định bằng tiếng Việt;
 * không lộ chi tiết nội bộ cho người dùng cuối (chỉ traceId).
 */
public enum ErrorCode {
    VALIDATION("PXVB-CC-0400", 400, "Dữ liệu không hợp lệ"),
    UNAUTHENTICATED("PXVB-CC-0401", 401, "Chưa đăng nhập hoặc phiên đã hết hạn"),
    AUTH_FAILED("PXVB-CC-0402", 401, "Tên đăng nhập hoặc mật khẩu không đúng"),
    FORBIDDEN("PXVB-CC-0403", 403, "Bạn không có quyền thực hiện thao tác này"),
    NOT_FOUND("PXVB-CC-0404", 404, "Không tìm thấy dữ liệu"),
    CONFLICT("PXVB-CC-0409", 409, "Dữ liệu đã bị thay đổi bởi người khác, vui lòng tải lại"),
    ACCOUNT_LOCKED("PXVB-CC-0411", 423, "Tài khoản đang bị khóa tạm thời"),
    INVALID_STATE_TRANSITION("PXVB-CC-0422", 422, "Chuyển trạng thái không hợp lệ"),
    DATA_LOCKED("PXVB-CC-0423", 423, "Dữ liệu đã được xác nhận/khóa, không thể sửa trực tiếp"),
    RATE_LIMITED("PXVB-CC-0429", 429, "Thao tác quá nhanh, vui lòng thử lại sau"),
    INTERNAL("PXVB-CC-0500", 500, "Lỗi hệ thống, vui lòng liên hệ quản trị kèm mã truy vết");

    private final String code;
    private final int status;
    private final String title;

    ErrorCode(String code, int status, String title) {
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }
}
