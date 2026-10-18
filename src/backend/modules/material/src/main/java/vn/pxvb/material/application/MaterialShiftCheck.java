package vn.pxvb.material.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.pxvb.production.contract.ShiftCloseCheck;

/**
 * {@code CHK_MATERIAL} (SDD §4.10): chặn chốt ca khi còn tranh chấp nhận NVL hoặc khung giờ đã qua chưa giao nhận
 * xong (BR-MT-005/007); hao hụt vượt ngưỡng cần giải trình.
 */
@Component
class MaterialShiftCheck implements ShiftCloseCheck {

    private final ConsumptionService consumption;
    private final Clock clock;

    MaterialShiftCheck(ConsumptionService consumption, Clock clock) {
        this.consumption = consumption;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "CHK_MATERIAL";
    }

    @Override
    public List<Item> evaluate(UUID sessionId, LocalDate businessDate, String shiftCode) {
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> d : consumption.disputed(businessDate, shiftCode)) {
            items.add(new Item(code(), "dispute:" + d.get("id"), "Tranh chấp nhận NVL", Level.BLOCK,
                    "Còn tranh chấp nhận NVL chưa xử lý (máy " + d.get("machineId") + ")", true));
        }
        for (Map<String, Object> l : consumption.lateLines(businessDate, shiftCode, clock.instant())) {
            items.add(new Item(code(), "late:" + l.get("lineId"), "Khung " + l.get("slotNo"), Level.BLOCK,
                    "Khung giờ " + l.get("slotNo") + " đã qua nhưng " + l.get("reason") + " (máy "
                    + l.get("machineId") + ")", true));
        }
        for (Map<String, Object> s : consumption.summaries(sessionId)) {
            String flag = (String) s.get("flag");
            if (!"OK".equals(flag)) {
                items.add(new Item(code(), "waste:" + s.get("machineId") + ":" + s.get("materialId"),
                        "Hao hụt NVL", Level.EXPLAIN, "Hao hụt " + flag + (s.get("wastePct") == null
                        ? " (ngoài định mức)" : " " + s.get("wastePct") + "%") + " — máy " + s.get("machineId"),
                        false));
            }
        }
        return items;
    }
}
