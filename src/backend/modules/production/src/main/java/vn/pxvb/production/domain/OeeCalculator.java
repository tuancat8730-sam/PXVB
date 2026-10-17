package vn.pxvb.production.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Công thức OEE (SDD §8.3), thuần miền — không phụ thuộc Spring/DB để kiểm thử bằng bộ số liệu chuẩn (golden).
 * Tỷ lệ là phân số 0–1 (6 chữ số thập phân); {@code null} nghĩa là N/A.
 */
public final class OeeCalculator {

    public enum PlannedTimeMode { EXCLUDE_PLANNED_STOPS, FULL_SHIFT }

    public enum QualityMode { FROM_NC_RECORDS, FROM_INLINE_REJECT_RATE, ASSUME_100 }

    public record Formula(PlannedTimeMode plannedTimeMode, QualityMode qualityMode, boolean capPerformanceAt100) {
    }

    /** {@code ratePerMinute} cùng đơn vị với {@code totalQty} (điếu hoặc bao); {@code null} = chưa khai báo. */
    public record Input(int shiftMinutes, int plannedStopMinutes, int unplannedStopMinutes, long totalQty,
                        long goodQty, BigDecimal ratePerMinute) {
    }

    public record Result(int plannedMinutes, int runMinutes, long totalQty, long goodQty, BigDecimal theoreticalQty,
                         BigDecimal availability, BigDecimal performance, BigDecimal quality, BigDecimal oee) {
    }

    private static final int SCALE = 6;
    private static final MathContext MC = MathContext.DECIMAL64;

    private OeeCalculator() {
    }

    public static Result compute(Formula formula, Input in) {
        int planned = formula.plannedTimeMode() == PlannedTimeMode.FULL_SHIFT
                ? in.shiftMinutes() : in.shiftMinutes() - in.plannedStopMinutes();
        int stops = formula.plannedTimeMode() == PlannedTimeMode.FULL_SHIFT
                ? in.plannedStopMinutes() + in.unplannedStopMinutes() : in.unplannedStopMinutes();
        int run = Math.max(0, planned - stops);
        BigDecimal theoretical = in.ratePerMinute() == null ? null : in.ratePerMinute().multiply(BigDecimal.valueOf(run));
        if (planned <= 0) {
            return new Result(Math.max(planned, 0), run, in.totalQty(), in.goodQty(), theoretical, null, null, null,
                    null);
        }
        BigDecimal availability = ratio(run, planned);
        BigDecimal performance = performance(formula, in.totalQty(), theoretical, run);
        BigDecimal quality = quality(in.totalQty(), effectiveGood(formula, in));
        BigDecimal oee = performance == null ? null : availability.multiply(performance, MC)
                .multiply(quality, MC).setScale(SCALE, RoundingMode.HALF_UP);
        return new Result(planned, run, in.totalQty(), in.goodQty(), theoretical, availability, performance, quality,
                oee);
    }

    /** Gộp theo ngày/tuần/tháng/dòng máy: cộng dồn tử – mẫu, không trung bình cộng phần trăm (SDD §8.3). */
    public static Result aggregate(Formula formula, Collection<Result> parts) {
        long planned = 0;
        long run = 0;
        long total = 0;
        long good = 0;
        BigDecimal theoretical = BigDecimal.ZERO;
        boolean theoreticalKnown = true;
        for (Result p : parts) {
            planned += p.plannedMinutes();
            run += p.runMinutes();
            total += p.totalQty();
            good += p.goodQty();
            if (p.theoreticalQty() == null) {
                theoreticalKnown = false;
            } else {
                theoretical = theoretical.add(p.theoreticalQty());
            }
        }
        if (planned <= 0) {
            return new Result(0, (int) run, total, good, theoreticalKnown ? theoretical : null, null, null, null,
                    null);
        }
        BigDecimal t = theoreticalKnown ? theoretical : null;
        BigDecimal availability = ratio(run, planned);
        BigDecimal performance = performance(formula, total, t, run);
        BigDecimal quality = quality(total, formula.qualityMode() == QualityMode.ASSUME_100 ? total : good);
        BigDecimal oee = performance == null ? null : availability.multiply(performance, MC).multiply(quality, MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return new Result((int) planned, (int) run, total, good, t, availability, performance, quality, oee);
    }

    private static long effectiveGood(Formula formula, Input in) {
        return formula.qualityMode() == QualityMode.ASSUME_100 ? in.totalQty() : in.goodQty();
    }

    private static BigDecimal performance(Formula formula, long total, BigDecimal theoretical, long run) {
        if (theoretical == null) {
            return null; // chưa có công suất lý thuyết → N/A
        }
        if (theoretical.signum() == 0) {
            return run == 0 ? BigDecimal.ZERO.setScale(SCALE) : null;
        }
        BigDecimal p = BigDecimal.valueOf(total).divide(theoretical, SCALE, RoundingMode.HALF_UP);
        return formula.capPerformanceAt100() && p.compareTo(BigDecimal.ONE) > 0
                ? BigDecimal.ONE.setScale(SCALE) : p;
    }

    /** Không có sản lượng → coi chất lượng 100% (không có phế phẩm để trừ). */
    private static BigDecimal quality(long total, long good) {
        if (total <= 0) {
            return BigDecimal.ONE.setScale(SCALE);
        }
        return BigDecimal.valueOf(Math.min(good, total)).divide(BigDecimal.valueOf(total), SCALE,
                RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }
}
