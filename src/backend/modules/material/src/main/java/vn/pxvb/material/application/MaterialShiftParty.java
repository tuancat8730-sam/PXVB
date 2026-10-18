package vn.pxvb.material.application;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.pxvb.production.contract.ShiftCloseParty;

/** Bên Cấp phát NVL: không phát sinh khi ca không có phiếu đã xác nhận và không có bản ghi cấp phát. */
@Component
class MaterialShiftParty implements ShiftCloseParty {

    private final JdbcTemplate jdbc;

    MaterialShiftParty(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String roleCode() {
        return "MATERIAL_ISSUER";
    }

    @Override
    public String label() {
        return "Cấp phát NVL";
    }

    @Override
    public boolean hasActivity(UUID sessionId, LocalDate businessDate, String shiftCode) {
        Integer issues = jdbc.queryForObject("SELECT COUNT(*) FROM mt_issue_record WHERE business_date = ? AND "
                + "shift_code = ?", Integer.class, businessDate, shiftCode);
        Integer slips = jdbc.queryForObject("SELECT COUNT(*) FROM mt_issue_slip WHERE business_date = ? AND "
                + "shift_code = ? AND status <> 'AUTO' AND status <> 'CANCELLED'", Integer.class, businessDate,
                shiftCode);
        return (issues != null && issues > 0) || (slips != null && slips > 0);
    }
}
