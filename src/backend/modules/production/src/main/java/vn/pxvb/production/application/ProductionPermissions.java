package vn.pxvb.production.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

/** Quyền phân hệ PR (SDD §8, §6.7). Vai trò mặc định là giả định, quản trị chỉnh được. */
@Component
public class ProductionPermissions implements PermissionContributor {

    @Override
    public List<PermissionDef> permissions() {
        Set<String> viewers = Set.of("DIRECTOR", "DEPUTY_DIRECTOR", "PLANNER", "SHIFT_LEADER", "SUPERVISOR",
                "OPERATOR", "STAT_OUTPUT", "STAT_MATERIAL", "QC_INLINE", "QA_SUPERVISOR", "QA_HEAD", "MNT_LEAD",
                "BOARD", "SYS_ADMIN");
        Set<String> recorders = Set.of("OPERATOR", "SHIFT_LEADER", "SUPERVISOR", "STAT_OUTPUT");
        Set<String> leaders = Set.of("SHIFT_LEADER", "DEPUTY_DIRECTOR");
        return List.of(
                new PermissionDef("pr.session.view", "pr", "Xem phiên ca", viewers),
                new PermissionDef("pr.session.open", "pr", "Bắt đầu ca", leaders),
                new PermissionDef("pr.session.end", "pr", "Kết thúc ca", leaders),
                new PermissionDef("pr.output.view", "pr", "Xem sản lượng/dừng máy", viewers),
                new PermissionDef("pr.output.record", "pr", "Ghi nhận sản lượng", recorders),
                new PermissionDef("pr.stop.record", "pr", "Ghi nhận dừng máy", recorders),
                new PermissionDef("pr.productChange.record", "pr", "Ghi nhận đổi mác", recorders),
                new PermissionDef("pr.close.view", "pr", "Xem kiểm tra chốt ca", viewers),
                new PermissionDef("pr.close.explain", "pr", "Nhập giải trình chốt ca",
                        Set.of("SHIFT_LEADER", "DEPUTY_DIRECTOR", "STAT_OUTPUT", "STAT_MATERIAL", "QC_INLINE")),
                new PermissionDef("pr.close.approveExplanation", "pr", "Duyệt giải trình chốt ca",
                        Set.of("DEPUTY_DIRECTOR", "DIRECTOR")),
                new PermissionDef("pr.close.request", "pr", "Gửi chốt ca", leaders),
                new PermissionDef("pr.oee.view", "pr", "Xem OEE", viewers),
                new PermissionDef("pr.oee.configure", "pr", "Cấu hình công thức OEE",
                        Set.of("DEPUTY_DIRECTOR", "SYS_ADMIN")),
                new PermissionDef("pr.stop.confirmTech", "pr", "Xác nhận nguyên nhân kỹ thuật",
                        Set.of("MNT_LEAD", "ENGINEER", "DEPUTY_DIRECTOR")),
                new PermissionDef("pr.output.onBehalf", "pr", "Nhập hộ máy khác",
                        Set.of("SUPERVISOR", "SHIFT_LEADER")));
    }
}
