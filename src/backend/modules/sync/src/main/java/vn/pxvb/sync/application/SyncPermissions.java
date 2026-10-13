package vn.pxvb.sync.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

@Component
public class SyncPermissions implements PermissionContributor {

    private static final Set<String> FIELD_ROLES = Set.of("OPERATOR", "SUPERVISOR", "SHIFT_LEADER",
            "MATERIAL_ISSUER", "QC_INLINE", "QA_INSPECTOR", "QA_SUPERVISOR", "MECHANIC", "MNT_TECH", "MNT_LEAD",
            "STAT_OUTPUT", "STAT_MATERIAL", "STAT_STAMP", "WAREHOUSE_KEEPER");
    private static final Set<String> SUPERVISORS = Set.of("SYS_ADMIN", "SHIFT_LEADER", "DEPUTY_DIRECTOR",
            "DIRECTOR");

    @Override
    public List<PermissionDef> permissions() {
        return List.of(
                new PermissionDef("sync.push", "sync", "Đẩy lệnh ngoại tuyến từ thiết bị", FIELD_ROLES),
                new PermissionDef("sync.pull", "sync", "Kéo dữ liệu về thiết bị", FIELD_ROLES),
                new PermissionDef("sync.conflict.view", "sync", "Xem xung đột đồng bộ", SUPERVISORS),
                new PermissionDef("sync.conflict.resolve", "sync", "Xử lý xung đột đồng bộ", SUPERVISORS),
                new PermissionDef("sync.device.view", "sync", "Giám sát thiết bị & đồng bộ", SUPERVISORS));
    }
}
