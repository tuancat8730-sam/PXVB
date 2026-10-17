package vn.pxvb.production.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sự kiện của phân hệ PR. {@code ShiftEnded} kích hoạt chốt ca (CC-11), OEE chính thức, báo cáo ca; outbox:
 * {@code pr.session.opened}, {@code pr.session.ended}, {@code pr.stop.started}, {@code pr.stop.ended}.
 */
public final class ProductionEvents {

    private ProductionEvents() {
    }

    public record ShiftOpened(UUID sessionId, LocalDate businessDate, String shiftCode, Instant plannedStart,
                              Instant plannedEnd) {
    }

    public record ShiftEnded(UUID sessionId, LocalDate businessDate, String shiftCode, Instant endedAt) {
    }

    /** Phiên ca đã chốt (CLOSED/SHIFT_LOCKED): RP, MT, TX, PR tổng hợp lắng nghe (outbox {@code pr.shift.closed}). */
    public record ShiftClosed(UUID sessionId, LocalDate businessDate, String shiftCode, Instant closedAt) {
    }

    public record StopStarted(UUID stopId, UUID sessionId, UUID machineId, String reasonCode, Instant startAt) {
    }

    public record StopEnded(UUID stopId, UUID sessionId, UUID machineId, String reasonCode, Instant startAt,
                            Instant endAt) {
    }
}
