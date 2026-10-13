package vn.pxvb.masterdata.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

/**
 * Quyền của phân hệ MD (SDD §5.8): {@code md.<resource>.<view|create|update|export|approve>} sinh từ đặc tả
 * danh mục, cộng quyền riêng cho định mức, ghép cặp, chất lượng dữ liệu.
 */
@Component
public class MasterdataPermissions implements PermissionContributor {

    private static final Set<String> DEPUTY = Set.of("DEPUTY_DIRECTOR", "DIRECTOR");

    @Override
    public List<PermissionDef> permissions() {
        List<PermissionDef> defs = new ArrayList<>();
        for (CatalogSpec spec : Catalogs.all()) {
            String base = "md." + spec.permKey() + ".";
            defs.add(new PermissionDef(base + "view", "md", "Xem " + spec.title(), Catalogs.VIEWERS));
            defs.add(new PermissionDef(base + "create", "md", "Tạo mới " + spec.title(), spec.writers()));
            defs.add(new PermissionDef(base + "update", "md", "Sửa/khóa " + spec.title(), spec.writers()));
            defs.add(new PermissionDef(base + "export", "md", "Xuất Excel " + spec.title(), Catalogs.VIEWERS));
            if (!spec.approvers().isEmpty()) {
                defs.add(new PermissionDef(base + "approve", "md", "Duyệt/kích hoạt " + spec.title(),
                        spec.approvers()));
            }
        }
        defs.add(new PermissionDef("md.bomNorm.view", "md", "Xem định mức NVL", Catalogs.VIEWERS));
        defs.add(new PermissionDef("md.bomNorm.create", "md", "Lập/sửa định mức NVL (bản nháp)",
                Set.of("SYS_ADMIN", "NORM_CLERK")));
        defs.add(new PermissionDef("md.bomNorm.approve", "md", "Duyệt định mức NVL", DEPUTY));
        defs.add(new PermissionDef("md.bomNorm.backdate", "md", "Cho hiệu lực định mức trong quá khứ",
                Set.of("SYS_ADMIN")));
        defs.add(new PermissionDef("md.bomNorm.export", "md", "Xuất định mức NVL", Catalogs.VIEWERS));
        defs.add(new PermissionDef("md.pairing.view", "md", "Xem ghép cặp vấn–bao", Catalogs.VIEWERS));
        defs.add(new PermissionDef("md.pairing.update", "md", "Lập/sửa ghép cặp vấn–bao",
                Set.of("SYS_ADMIN", "PLANNER")));
        defs.add(new PermissionDef("md.pairing.approve", "md", "Kích hoạt bộ ghép cặp", DEPUTY));
        defs.add(new PermissionDef("md.dataQuality.view", "md", "Xem báo cáo chất lượng dữ liệu",
                Set.of("SYS_ADMIN", "DEPUTY_DIRECTOR", "PLANNER", "NORM_CLERK")));
        return defs;
    }
}
