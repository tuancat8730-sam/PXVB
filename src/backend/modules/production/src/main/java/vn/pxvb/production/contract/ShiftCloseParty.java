package vn.pxvb.production.contract;

import java.time.LocalDate;
import java.util.UUID;

/**
 * SPI một bên xác nhận chốt ca (SDD §4.10, FR-CC-082): module sở hữu bên đó cho biết ca có phát sinh hay không.
 * Bên "không phát sinh" được tự bỏ qua khi gửi chốt (quyết định PO 2026-10-18). {@link #roleCode()} phải trùng
 * đúng {@code roleCode} của bước trong luồng {@code SHIFT_CLOSE}. Bên không đăng ký SPI luôn bắt buộc.
 */
public interface ShiftCloseParty {

    String roleCode();

    String label();

    boolean hasActivity(UUID sessionId, LocalDate businessDate, String shiftCode);
}
