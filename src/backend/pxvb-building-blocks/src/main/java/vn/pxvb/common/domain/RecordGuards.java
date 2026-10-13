package vn.pxvb.common.domain;

import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Bảo vệ dùng chung: từ CONFIRMED trở đi không sửa trực tiếp (FR-CC-010, trả 423 PXVB-CC-0423). */
public final class RecordGuards {

    private RecordGuards() {
    }

    public static void requireEditable(RecordState state) {
        if (state != null && state.isLocked()) {
            throw new BusinessException(ErrorCode.DATA_LOCKED,
                    "Bản ghi đã ở trạng thái " + state + ", vui lòng tạo yêu cầu điều chỉnh");
        }
    }
}
