package vn.pxvb.material.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiêu hao NVL theo ca – máy – NVL (BR-MT-003/004). Có kiểm đếm: {@code Actual = Opening + Issued − Returned −
 * Closing}. Không kiểm đếm: coi tồn cuối = tồn đầu + cấp − hoàn − lý thuyết (hao hụt 0 theo thiết kế); hao hụt
 * chỉ phát hiện qua cấp vượt: {@code Issued − Returned − Opening − Theoretical} (chỉ đánh giá khi ca đã kết thúc).
 */
public final class ConsumptionCalculator {

    public enum Flag { OK, WARN, CRIT }

    public record Input(BigDecimal opening, BigDecimal issued, BigDecimal returned, BigDecimal countedClosing,
                        BigDecimal theoretical, BigDecimal thresholdPct, boolean shiftEnded) {
    }

    public record Result(BigDecimal closing, boolean counted, BigDecimal actualConsumed, BigDecimal wasteQty,
                         BigDecimal wastePct, Flag flag) {
    }

    private ConsumptionCalculator() {
    }

    public static Result compute(Input in) {
        boolean counted = in.countedClosing() != null;
        BigDecimal closing;
        BigDecimal actual;
        BigDecimal waste;
        BigDecimal available = in.opening().add(in.issued()).subtract(in.returned());
        if (counted) {
            closing = in.countedClosing();
            actual = available.subtract(closing);
            waste = actual.subtract(in.theoretical());
        } else {
            closing = available.subtract(in.theoretical());
            actual = in.theoretical();
            waste = in.shiftEnded() ? in.issued().subtract(in.returned()).subtract(in.opening())
                    .subtract(in.theoretical()).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        }
        BigDecimal pct = in.theoretical().signum() > 0
                ? waste.multiply(BigDecimal.valueOf(100)).divide(in.theoretical(), 3, RoundingMode.HALF_UP) : null;
        return new Result(closing, counted, actual, waste, pct, flag(waste, pct, in.thresholdPct()));
    }

    private static Flag flag(BigDecimal waste, BigDecimal pct, BigDecimal threshold) {
        if (waste.signum() <= 0) {
            return Flag.OK;
        }
        if (pct == null) {
            return Flag.WARN; // tiêu hao ngoài định mức
        }
        if (pct.compareTo(threshold) <= 0) {
            return Flag.OK;
        }
        return pct.compareTo(threshold.multiply(BigDecimal.valueOf(2))) > 0 ? Flag.CRIT : Flag.WARN;
    }
}
