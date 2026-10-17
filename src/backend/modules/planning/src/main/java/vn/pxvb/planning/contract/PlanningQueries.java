package vn.pxvb.planning.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hợp đồng truy vấn/cập nhật trạng thái lệnh và bố trí ca cho PR (mở ca, ghi nhận, kết ca). */
public interface PlanningQueries {

    record OrderRef(UUID orderId, String code, String shiftCode, UUID makerMachineId, UUID packerMachineId,
                    UUID productId, long plannedPacks, String status) {
    }

    record RosterRef(UUID rosterId, String status, UUID leaderEmployeeId) {
    }

    /** Lệnh CONFIRMED/RUNNING của ca (mỗi máy bao một lệnh). */
    List<OrderRef> activeOrders(LocalDate businessDate, String shiftCode);

    Optional<RosterRef> roster(LocalDate businessDate, String shiftCode);

    /** CONFIRMED → RUNNING khi mở ca. */
    void markRunning(UUID orderId);

    /** RUNNING → COMPLETED khi kết ca. */
    void markCompleted(UUID orderId);
}
