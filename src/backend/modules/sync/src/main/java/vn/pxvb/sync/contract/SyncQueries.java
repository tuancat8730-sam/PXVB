package vn.pxvb.sync.contract;

/** Truy vấn trạng thái đồng bộ cho module khác (vd chốt ca chặn khi còn xung đột mở của lệnh PR). */
public interface SyncQueries {

    /** Số xung đột còn mở có loại lệnh bắt đầu bằng {@code commandTypePrefix} (vd {@code "PR."}). */
    long openConflicts(String commandTypePrefix);
}
