package vn.pxvb.workflow.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

/** Quyền của phân hệ workflow (seed, SDD §4.1). Vai trò mặc định là giả định, quản trị chỉnh được. */
@Component
public class WorkflowPermissions implements PermissionContributor {

    private static final Set<String> SUPERVISORS = Set.of("DIRECTOR", "DEPUTY_DIRECTOR", "SHIFT_LEADER",
            "MNT_LEAD", "KTCD", "BOARD", "QA_HEAD", "QA_SUPERVISOR", "WAREHOUSE_KEEPER", "PLANNER", "NORM_CLERK",
            "STAT_MATERIAL", "STAT_OUTPUT", "STAT_STAMP", "SUPERVISOR", "QC_INLINE", "PXCD", "QA_DEPT", "KHVT",
            "TCKT", "MNT_TECH", "MATERIAL_ISSUER");
    private static final Set<String> APPROVERS = Set.of("DIRECTOR", "DEPUTY_DIRECTOR");

    @Override
    public List<PermissionDef> permissions() {
        return List.of(
                new PermissionDef("workflow.approval.view", "workflow", "Xem yêu cầu duyệt và luồng duyệt",
                        SUPERVISORS),
                new PermissionDef("workflow.approval.start", "workflow", "Gửi yêu cầu duyệt", SUPERVISORS),
                new PermissionDef("workflow.approval.manage", "workflow", "Hủy/xử lý yêu cầu duyệt của người khác",
                        Set.of("SYS_ADMIN", "DIRECTOR", "DEPUTY_DIRECTOR")),
                new PermissionDef("workflow.flow.config", "workflow", "Cấu hình luồng duyệt", Set.of("SYS_ADMIN")),
                new PermissionDef("workflow.delegation.manage", "workflow", "Ủy quyền duyệt tạm thời", SUPERVISORS),
                new PermissionDef("workflow.adjustment.view", "workflow", "Xem yêu cầu điều chỉnh", SUPERVISORS),
                new PermissionDef("workflow.adjustment.request", "workflow", "Tạo yêu cầu điều chỉnh dữ liệu",
                        SUPERVISORS),
                new PermissionDef("workflow.adjustment.approve", "workflow", "Duyệt/từ chối yêu cầu điều chỉnh",
                        APPROVERS),
                new PermissionDef("workflow.signature.view", "workflow", "Xem xác nhận điện tử", SUPERVISORS));
    }
}
