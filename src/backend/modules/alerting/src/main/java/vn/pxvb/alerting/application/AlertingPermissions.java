package vn.pxvb.alerting.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

/** Quyền của phân hệ cảnh báo (seed). Thông báo cá nhân chỉ cần đăng nhập, không cần quyền riêng. */
@Component
public class AlertingPermissions implements PermissionContributor {

    private static final Set<String> VIEWERS = Set.of("SYS_ADMIN", "DIRECTOR", "DEPUTY_DIRECTOR", "SHIFT_LEADER",
            "SUPERVISOR", "OPERATOR", "STAT_OUTPUT", "STAT_MATERIAL", "STAT_STAMP", "MATERIAL_ISSUER", "QC_INLINE",
            "QA_INSPECTOR", "QA_SUPERVISOR", "QA_HEAD", "MNT_TECH", "MNT_LEAD", "MECHANIC", "WAREHOUSE_KEEPER",
            "KTCD", "PXCD", "BOARD");
    private static final Set<String> HANDLERS = Set.of("SYS_ADMIN", "DEPUTY_DIRECTOR", "SHIFT_LEADER", "SUPERVISOR",
            "OPERATOR", "STAT_MATERIAL", "STAT_STAMP", "QC_INLINE", "QA_SUPERVISOR", "QA_HEAD", "MNT_TECH",
            "MNT_LEAD", "WAREHOUSE_KEEPER", "KTCD");

    @Override
    public List<PermissionDef> permissions() {
        return List.of(
                new PermissionDef("alerting.alert.view", "alerting", "Xem trung tâm cảnh báo", VIEWERS),
                new PermissionDef("alerting.alert.handle", "alerting",
                        "Tiếp nhận/xử lý/đóng cảnh báo", HANDLERS),
                new PermissionDef("alerting.rule.config", "alerting", "Cấu hình rule cảnh báo",
                        Set.of("SYS_ADMIN", "DEPUTY_DIRECTOR")));
    }
}
