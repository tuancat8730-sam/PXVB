package vn.pxvb.workflow.application;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.pxvb.workflow.application.Repositories.FlowDefRepository;
import vn.pxvb.workflow.domain.ApprovalFlowDef;

/**
 * Seed các luồng duyệt chuẩn (SDD §4.4). Vai trò/SLA là giả định ⚠ (Q15) — chỉ tạo khi chưa có, quản trị chỉnh
 * được qua API cấu hình luồng.
 */
@Component
public class FlowSeeder implements ApplicationRunner {

    private record Seed(String code, String module, String entityType, String steps) {
    }

    private static String step(int no, String role, String mode, Integer sla, String onTimeout) {
        return "{\"no\":" + no + ",\"roleCode\":\"" + role + "\",\"mode\":\"" + mode + "\""
                + (sla == null ? "" : ",\"slaMinutes\":" + sla) + ",\"onTimeout\":\"" + onTimeout + "\"}";
    }

    private static String steps(String... items) {
        return "[" + String.join(",", items) + "]";
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("PL_WEEKLY_APPROVAL", "PL", "WeeklyPlan",
                    steps(step(1, "DEPUTY_DIRECTOR", "ANY", 480, "NOTIFY"))),
            new Seed("PL_ORDER_CONFIRM", "PL", "ProductionOrder",
                    steps(step(1, "SHIFT_LEADER", "ANY", 60, "ESCALATE"),
                            step(1, "DEPUTY_DIRECTOR", "ANY", 60, "ESCALATE"))),
            new Seed("MD_NORM_CHANGE", "MD", "BomNorm",
                    steps(step(1, "DEPUTY_DIRECTOR", "ANY", 1440, "NOTIFY"))),
            new Seed("PM_MONTHLY_PLAN", "PM", "MaintenanceMonthlyPlan",
                    steps(step(1, "MNT_LEAD", "ANY", 1440, "NOTIFY"))),
            new Seed("PM_REPAIR_REQUEST", "PM", "RepairRequest",
                    steps(step(1, "MNT_LEAD", "ANY", 15, "ESCALATE"),
                            step(2, "WAREHOUSE_KEEPER", "ANY", 60, "NOTIFY"))),
            new Seed("SP_ISSUE_UNPLANNED", "SP", "SpareIssueRequest",
                    steps(step(1, "KTCD", "ANY", 240, "NOTIFY"))),
            new Seed("OH_YEAR_PLAN", "OH", "OverhaulYearPlan",
                    steps(step(1, "BOARD", "ANY", 4320, "NOTIFY"))),
            new Seed("OH_PARTS_PLAN", "OH", "OverhaulPartsPlan",
                    steps(step(1, "KTCD", "ANY", 1440, "NOTIFY"))),
            new Seed("OH_EXTRA_PART", "OH", "OverhaulExtraPart",
                    steps(step(1, "KTCD", "ANY", 240, "ESCALATE"))),
            new Seed("OH_ACCEPTANCE", "OH", "OverhaulDossier",
                    steps(step(1, "KTCD", "ALL", null, "NOTIFY"), step(1, "PXCD", "ALL", null, "NOTIFY"),
                            step(1, "QA_DEPT", "ALL", null, "NOTIFY"), step(1, "MNT_LEAD", "ALL", null, "NOTIFY"))),
            new Seed("QC_NC_DISPOSITION", "QC", "NonConformance",
                    steps(step(1, "QA_HEAD", "ANY", 480, "NOTIFY"))),
            // Chốt ca: 4 bên bắt buộc (PO, 2026-10-13); mỗi bên là một nhóm vai trò "bất kỳ vai trò nào".
            // Bên "không phát sinh" được module PR tự bỏ qua khi khởi tạo luồng.
            new Seed("SHIFT_CLOSE", "CC", "ShiftSession",
                    steps(step(1, "SHIFT_LEADER", "ALL", 30, "NOTIFY"),
                            step(1, "STAT_OUTPUT,STAT_MATERIAL,STAT_STAMP", "ALL", 30, "NOTIFY"),
                            step(1, "QC_INLINE,QA_INSPECTOR,QA_SUPERVISOR", "ALL", 30, "NOTIFY"),
                            step(1, "MATERIAL_ISSUER", "ALL", 30, "NOTIFY"))),
            new Seed("CC_ADJUSTMENT", "CC", "AdjustmentRequest",
                    steps(step(1, "DEPUTY_DIRECTOR", "ANY", 480, "NOTIFY"))));

    private final FlowDefRepository flows;
    private final TransactionTemplate tx;

    public FlowSeeder(FlowDefRepository flows, TransactionTemplate tx) {
        this.flows = flows;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> {
            for (Seed seed : SEEDS) {
                var existing = flows.findByCode(seed.code());
                if (existing.isEmpty()) {
                    flows.save(new ApprovalFlowDef(seed.code(), seed.module(), seed.entityType(), seed.steps()));
                } else if ("SHIFT_CLOSE".equals(seed.code())
                        && existing.get().getSteps().contains("\"roleCode\":\"STAT_OUTPUT\",\"mode\"")) {
                    // Cấu hình seed cũ (trước quyết định PO): nâng cấp, không đụng vào cấu hình đã chỉnh tay
                    existing.get().update(seed.module(), seed.entityType(), seed.steps(), existing.get().isActive());
                }
            }
        });
    }
}
