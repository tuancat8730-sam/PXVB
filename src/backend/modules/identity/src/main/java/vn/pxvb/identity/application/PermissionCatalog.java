package vn.pxvb.identity.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh mục quyền (seed bằng code, SDD §4.1) và vai trò chuẩn (§1.4). Mỗi phân hệ sau này bổ sung quyền của
 * mình vào {@link #PERMISSIONS}; quyền dạng {@code <mod>.<resource>.<action>}.
 */
public final class PermissionCatalog {

    public record PermissionDef(String code, String module, String description) {
    }

    public static final List<PermissionDef> PERMISSIONS = List.of(
            new PermissionDef("identity.user.view", "identity", "Xem người dùng"),
            new PermissionDef("identity.user.create", "identity", "Tạo người dùng"),
            new PermissionDef("identity.user.update", "identity", "Sửa người dùng, gán vai trò"),
            new PermissionDef("identity.user.lock", "identity", "Khóa/mở khóa tài khoản"),
            new PermissionDef("identity.role.view", "identity", "Xem vai trò và quyền"),
            new PermissionDef("identity.role.update", "identity", "Cấu hình quyền của vai trò"),
            new PermissionDef("identity.device.view", "identity", "Xem thiết bị"),
            new PermissionDef("identity.device.create", "identity", "Đăng ký thiết bị"),
            new PermissionDef("identity.device.update", "identity", "Cập nhật/khóa thiết bị"),
            new PermissionDef("identity.audit.view", "identity", "Tra cứu nhật ký kiểm toán"));

    /** Mã vai trò → tên (theo SDD §1.4). */
    public static final Map<String, String> ROLES = rolesInOrder();

    /** Đơn vị tổ chức mặc định. */
    public static final Map<String, String> ORG_UNITS = Map.of(
            "PXVB", "Phân xưởng Vấn Bao",
            "PXCD", "Phân xưởng Cơ điện",
            "KTCD", "Phòng Kỹ thuật Cơ điện",
            "KHVT", "Phòng Kế hoạch Vật tư",
            "TCKT", "Phòng Tài chính Kế toán",
            "DBCL", "Phòng Đảm bảo chất lượng",
            "KHO_CK", "Kho cơ khí",
            "BGD", "Ban Giám đốc");

    private PermissionCatalog() {
    }

    private static Map<String, String> rolesInOrder() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SYS_ADMIN", "Quản trị hệ thống/CNTT");
        m.put("DIRECTOR", "Quản đốc");
        m.put("DEPUTY_DIRECTOR", "Phó Quản đốc");
        m.put("PLANNER", "NV Kế hoạch & Điều độ SX");
        m.put("NORM_CLERK", "NV Định mức NVL");
        m.put("SHIFT_LEADER", "Trưởng ca");
        m.put("SUPERVISOR", "NV giám sát sản xuất");
        m.put("OPERATOR", "Công nhân vận hành");
        m.put("STAT_OUTPUT", "NV thống kê sản lượng");
        m.put("STAT_MATERIAL", "NV thống kê NVL");
        m.put("MATERIAL_ISSUER", "NV/CN cấp phát NVL");
        m.put("STAT_STAMP", "NV thống kê tem");
        m.put("QC_INLINE", "NV KSCL PXVB");
        m.put("QA_INSPECTOR", "NV ĐBCL");
        m.put("QA_SUPERVISOR", "Giám sát ĐBCL");
        m.put("QA_HEAD", "TP/PP ĐBCL");
        m.put("MNT_TECH", "Kỹ thuật viên bảo trì");
        m.put("MNT_LEAD", "Tổ trưởng tổ MMTB");
        m.put("MECHANIC", "Thợ máy/thợ sửa chữa");
        m.put("WAREHOUSE_KEEPER", "Thủ kho phụ tùng");
        m.put("KTCD", "Phòng KTCĐ");
        m.put("PXCD", "PXCĐ");
        m.put("KHVT", "Phòng KHVT");
        m.put("TCKT", "Phòng TCKT");
        m.put("BOARD", "Ban Giám đốc");
        m.put("QA_DEPT", "Phòng ĐBCL (nghiệm thu TDT)");
        return Map.copyOf(m);
    }
}
