package vn.pxvb.planning.contract;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sự kiện của phân hệ kế hoạch. {@code OrderConfirmed} kích hoạt sinh phiếu cấp phát NVL (MT), lịch KSCL (QC) và
 * khởi tạo phiên ghi nhận (PR) — các phân hệ đó lắng nghe (Spring event hoặc outbox {@code pl.order.confirmed}).
 */
public final class PlanningEvents {

    private PlanningEvents() {
    }

    public record OrderConfirmed(UUID orderId, String code, LocalDate businessDate, String shiftCode,
                                 UUID makerMachineId, UUID packerMachineId, UUID productId, long plannedPacks,
                                 UUID normVersionId, int revision) {
    }
}
