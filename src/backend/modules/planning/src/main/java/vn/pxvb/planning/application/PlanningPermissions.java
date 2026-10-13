package vn.pxvb.planning.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

/** Quyền phân hệ PL (SDD §6.7). Vai trò mặc định là giả định, quản trị chỉnh được. */
@Component
public class PlanningPermissions implements PermissionContributor {

    private static final Set<String> VIEWERS = Set.of("DIRECTOR", "DEPUTY_DIRECTOR", "PLANNER", "NORM_CLERK",
            "SHIFT_LEADER", "SUPERVISOR", "OPERATOR", "STAT_OUTPUT", "STAT_MATERIAL", "MATERIAL_ISSUER",
            "STAT_STAMP", "QC_INLINE", "QA_SUPERVISOR", "QA_HEAD", "MNT_LEAD", "BOARD", "KHVT", "SYS_ADMIN");

    @Override
    public List<PermissionDef> permissions() {
        Set<String> planners = Set.of("PLANNER", "DEPUTY_DIRECTOR");
        return List.of(
                new PermissionDef("pl.monthlyPlan.view", "pl", "Xem KHSX tháng", VIEWERS),
                new PermissionDef("pl.monthlyPlan.update", "pl", "Lập/điều chỉnh KHSX tháng", planners),
                new PermissionDef("pl.weeklyPlan.view", "pl", "Xem KHSX tuần", VIEWERS),
                new PermissionDef("pl.weeklyPlan.update", "pl", "Lập/điều chỉnh KHSX tuần", planners),
                new PermissionDef("pl.weeklyPlan.submit", "pl", "Gửi duyệt KHSX tuần", planners),
                new PermissionDef("pl.order.view", "pl", "Xem lệnh sản xuất", VIEWERS),
                new PermissionDef("pl.order.suggest", "pl", "Sinh gợi ý lệnh sản xuất", planners),
                new PermissionDef("pl.order.update", "pl", "Sửa/điều chỉnh lệnh sản xuất",
                        Set.of("PLANNER", "DEPUTY_DIRECTOR", "SHIFT_LEADER")),
                new PermissionDef("pl.order.confirm", "pl", "Xác nhận lệnh sản xuất",
                        Set.of("SHIFT_LEADER", "DEPUTY_DIRECTOR")),
                new PermissionDef("pl.roster.view", "pl", "Xem bố trí nhân sự ca", VIEWERS),
                new PermissionDef("pl.roster.update", "pl", "Bố trí nhân sự ca",
                        Set.of("SHIFT_LEADER", "DEPUTY_DIRECTOR", "SUPERVISOR")));
    }
}
