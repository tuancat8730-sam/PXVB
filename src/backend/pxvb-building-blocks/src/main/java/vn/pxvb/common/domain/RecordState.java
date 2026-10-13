package vn.pxvb.common.domain;

/** Trạng thái dữ liệu giao dịch dùng chung (SDD §4.2, BR-CC-001: chỉ tiến tới). */
public enum RecordState {
    DRAFT,
    SUBMITTED,
    CONFIRMED,
    SHIFT_LOCKED,
    FINALIZED;

    /** Từ CONFIRMED trở đi không sửa trực tiếp (FR-CC-010). */
    public boolean isLocked() {
        return this.compareTo(CONFIRMED) >= 0;
    }
}
