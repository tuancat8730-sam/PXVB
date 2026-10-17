package vn.pxvb.production.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn phiên ca/sản lượng của PR cho module khác (MT tính tiêu hao, QC, TX…). */
public interface ProductionQueries {

    record SessionRef(UUID id, LocalDate businessDate, String shiftCode, String status, Instant plannedStart,
                      Instant plannedEnd) {
    }

    Optional<SessionRef> session(UUID sessionId);

    Optional<SessionRef> session(LocalDate businessDate, String shiftCode);

    List<SessionRef> openSessions();

    /** Sản lượng (bao) theo mác ghi nhận trên máy bao trong phiên ca. */
    Map<UUID, Long> packsByProduct(UUID sessionId, UUID packerMachineId);
}
