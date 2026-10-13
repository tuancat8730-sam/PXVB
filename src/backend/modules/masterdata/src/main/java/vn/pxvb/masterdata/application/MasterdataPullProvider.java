package vn.pxvb.masterdata.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.sync.SyncPullProvider;

/**
 * Cung cấp masterdata cho tablet (FR-CC-062): máy, sản phẩm, NVL, ca, mã dừng, lỗi/luật, biểu mẫu KSCL, ngưỡng…
 * Tài nguyên không có cờ ngừng (xóa vật lý) luôn trả đầy đủ để tablet thay thế toàn bộ bộ nhớ đệm.
 */
@Component
public class MasterdataPullProvider implements SyncPullProvider {

    private static final List<String> RESOURCES = List.of("machines", "machine-components", "products",
            "product-allowed-machines", "material-categories", "materials", "shift-defs", "issue-slots",
            "employees", "stop-reasons", "reason-codes", "defects", "defect-grade-rules", "qc-form-templates",
            "stamp-types", "thresholds");
    private static final Set<String> ALWAYS_FULL = Set.of("product-allowed-machines", "thresholds");

    private final CatalogService catalogs;

    public MasterdataPullProvider(CatalogService catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    public List<String> resources() {
        return RESOURCES;
    }

    @Override
    public Set<String> alwaysFull() {
        return ALWAYS_FULL;
    }

    @Override
    public Page pull(String resource, Instant since, String cursor, int limit) {
        Instant effectiveSince = ALWAYS_FULL.contains(resource) ? null : since;
        CatalogService.Slice slice = catalogs.changedSince(resource, effectiveSince, cursor, limit);
        return new Page(slice.rows(), slice.nextCursor());
    }
}
