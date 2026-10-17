package vn.pxvb.material.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

/** Quyền phân hệ MT (SDD §7.6). Vai trò mặc định là giả định, quản trị chỉnh được. */
@Component
public class MaterialPermissions implements PermissionContributor {

    @Override
    public List<PermissionDef> permissions() {
        Set<String> viewers = Set.of("DIRECTOR", "DEPUTY_DIRECTOR", "PLANNER", "SHIFT_LEADER", "SUPERVISOR",
                "OPERATOR", "STAT_MATERIAL", "MATERIAL_ISSUER", "QA_SUPERVISOR", "BOARD", "KHVT", "SYS_ADMIN");
        Set<String> statistician = Set.of("STAT_MATERIAL");
        return List.of(
                new PermissionDef("mt.slip.view", "mt", "Xem phiếu cấp phát", viewers),
                new PermissionDef("mt.slip.update", "mt", "Chỉnh phiếu cấp phát (AUTO)", statistician),
                new PermissionDef("mt.slip.confirm", "mt", "Xác nhận phiếu cấp phát", statistician),
                new PermissionDef("mt.issue.record", "mt", "Ghi nhận cấp phát thực tế",
                        Set.of("MATERIAL_ISSUER", "STAT_MATERIAL")),
                new PermissionDef("mt.receipt.confirm", "mt", "Xác nhận nhận NVL",
                        Set.of("OPERATOR", "SHIFT_LEADER", "SUPERVISOR")),
                new PermissionDef("mt.receipt.resolve", "mt", "Xử lý tranh chấp nhận NVL", statistician),
                new PermissionDef("mt.return.record", "mt", "Ghi nhận hoàn trả NVL",
                        Set.of("MATERIAL_ISSUER", "STAT_MATERIAL", "OPERATOR")),
                new PermissionDef("mt.count.record", "mt", "Kiểm đếm tồn tại máy",
                        Set.of("OPERATOR", "SHIFT_LEADER", "STAT_MATERIAL", "MATERIAL_ISSUER")),
                new PermissionDef("mt.consumption.view", "mt", "Xem tiêu hao NVL", viewers),
                new PermissionDef("mt.consumption.confirm", "mt", "Chốt tiêu hao ca", statistician),
                new PermissionDef("mt.consumption.explain", "mt", "Ghi giải trình hao hụt",
                        Set.of("STAT_MATERIAL", "SHIFT_LEADER")));
    }
}
