package vn.pxvb.masterdata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.pxvb.dataio.contract.ImportTemplate;
import vn.pxvb.masterdata.application.BomNormService.LineInput;
import vn.pxvb.masterdata.application.CatalogSpec.Col;
import vn.pxvb.masterdata.application.CatalogSpec.Type;

/**
 * Đăng ký mẫu nhập Excel cho mọi danh mục (FR-MD-021, FR-CC-070): mỗi {@link CatalogSpec} một mẫu, cộng mẫu
 * {@code bom-norms} (nhóm các dòng theo sản phẩm thành một phiên bản định mức nháp).
 */
@Component
public class CatalogImportTemplates implements ImportTemplate.Provider {

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CatalogService catalogs;
    private final BomNormService norms;
    private final JdbcTemplate jdbc;

    public CatalogImportTemplates(CatalogService catalogs, BomNormService norms, JdbcTemplate jdbc) {
        this.catalogs = catalogs;
        this.norms = norms;
        this.jdbc = jdbc;
    }

    @Override
    public List<ImportTemplate> templates() {
        List<ImportTemplate> list = new ArrayList<>();
        for (CatalogSpec spec : Catalogs.all()) {
            list.add(new CatalogTemplate(spec));
        }
        list.add(new BomNormTemplate());
        return list;
    }

    private final class CatalogTemplate implements ImportTemplate {
        private final CatalogSpec spec;

        CatalogTemplate(CatalogSpec spec) {
            this.spec = spec;
        }

        @Override
        public String code() {
            return spec.importCode();
        }

        @Override
        public String title() {
            return "Nhập liệu: " + spec.title();
        }

        @Override
        public String permission() {
            return "md." + spec.permKey() + ".create";
        }

        @Override
        public List<Column> columns() {
            List<Column> cols = new ArrayList<>();
            for (Col c : spec.cols()) {
                cols.add(new Column(c.importKey(), c.label(), c.required() && c.defaultValue() == null, hint(c)));
            }
            return cols;
        }

        @Override
        public Map<String, String> validate(Map<String, String> row) {
            return catalogs.validateImportRow(spec, row);
        }

        @Override
        public String naturalKey(Map<String, String> row) {
            return catalogs.naturalKey(spec, row);
        }

        @Override
        public void upsert(Map<String, String> row) {
            catalogs.upsertByKey(spec, row);
        }

        @Override
        public Map<String, List<String>> references() {
            Map<String, List<String>> refs = new LinkedHashMap<>();
            for (Col c : spec.cols()) {
                if (c.type() == Type.ENUM) {
                    refs.put(c.label(), c.values());
                }
            }
            return refs;
        }
    }

    private static String hint(Col c) {
        return switch (c.type()) {
            case ENUM -> "Một trong: " + String.join(", ", c.values());
            case DATE -> "Dạng yyyy-MM-dd hoặc dd/MM/yyyy";
            case TIME -> "Dạng HH:mm";
            case BOOL -> "Có/Không";
            case INT -> "Số nguyên";
            case DECIMAL -> "Số (dấu . hoặc ,)";
            case REF, CODE_REF -> "Mã của danh mục liên quan phải đã tồn tại";
            case JSON -> "Chuỗi JSON hợp lệ";
            case UUID -> "Mã định danh (UUID)";
            default -> c.maxLen() > 0 ? "Tối đa " + c.maxLen() + " ký tự" : "";
        };
    }

    private final class BomNormTemplate implements ImportTemplate {

        @Override
        public String code() {
            return "bom-norms";
        }

        @Override
        public String title() {
            return "Nhập liệu: Định mức nguyên vật liệu (tạo phiên bản nháp theo từng sản phẩm)";
        }

        @Override
        public String permission() {
            return "md.bomNorm.create";
        }

        @Override
        public List<Column> columns() {
            return List.of(
                    new Column("productCode", "Mã sản phẩm", true, "Sản phẩm phải đã tồn tại và đang hoạt động"),
                    new Column("materialCode", "Mã NVL", true, "Nguyên vật liệu phải đã tồn tại"),
                    new Column("qtyPer1000Packs", "Định mức/1.000 bao", true, "Số lớn hơn 0"),
                    new Column("uom", "Đơn vị tính", false, "Để trống = đơn vị của NVL"),
                    new Column("consumerType", "Loại máy tiêu thụ", false, "MAKER hoặc PACKER; trống = suy ra từ nhóm"),
                    new Column("effectiveFrom", "Hiệu lực từ", false, "yyyy-MM-dd hoặc dd/MM/yyyy; mọi dòng của một SP phải giống nhau"),
                    new Column("changeReason", "Lý do thay đổi", false, ""));
        }

        @Override
        public Map<String, String> validate(Map<String, String> row) {
            Map<String, String> errors = new LinkedHashMap<>();
            if (count("SELECT COUNT(*) FROM md_product WHERE code = ? AND is_active = 1",
                    row.get("productCode")) == 0) {
                errors.put("productCode", "Sản phẩm '" + row.get("productCode") + "' không tồn tại hoặc đã ngừng");
            }
            if (count("SELECT COUNT(*) FROM md_material WHERE code = ? AND is_active = 1",
                    row.get("materialCode")) == 0) {
                errors.put("materialCode", "NVL '" + row.get("materialCode") + "' không tồn tại hoặc đã ngừng");
            }
            try {
                if (new BigDecimal(row.get("qtyPer1000Packs").replace(',', '.')).signum() <= 0) {
                    errors.put("qtyPer1000Packs", "Định mức phải lớn hơn 0");
                }
            } catch (NumberFormatException e) {
                errors.put("qtyPer1000Packs", "Định mức phải là số");
            }
            String consumer = row.get("consumerType");
            if (consumer != null && !consumer.isBlank()
                    && !List.of("MAKER", "PACKER").contains(consumer.trim().toUpperCase())) {
                errors.put("consumerType", "Phải là MAKER hoặc PACKER");
            }
            String from = row.get("effectiveFrom");
            if (from != null && !from.isBlank()) {
                try {
                    parseDate(from);
                } catch (DateTimeParseException e) {
                    errors.put("effectiveFrom", "Ngày không hợp lệ");
                }
            }
            return errors;
        }

        @Override
        public String naturalKey(Map<String, String> row) {
            return row.get("productCode") + "|" + row.get("materialCode");
        }

        @Override
        public void upsert(Map<String, String> row) {
            commitBatch(List.of(row));
        }

        @Override
        public void commitBatch(List<Map<String, String>> rows) {
            Map<String, List<Map<String, String>>> byProduct = new LinkedHashMap<>();
            rows.forEach(r -> byProduct.computeIfAbsent(r.get("productCode"), k -> new ArrayList<>()).add(r));
            for (Map.Entry<String, List<Map<String, String>>> e : byProduct.entrySet()) {
                UUID productId = UUID.fromString(jdbc.queryForObject(
                        "SELECT BIN_TO_UUID(id) FROM md_product WHERE code = ?", String.class, e.getKey()));
                String fromText = e.getValue().get(0).get("effectiveFrom");
                LocalDate from = fromText == null || fromText.isBlank() ? null : parseDate(fromText);
                String reason = e.getValue().get(0).get("changeReason");
                List<LineInput> lines = e.getValue().stream().map(r -> new LineInput(null, r.get("materialCode"),
                        new BigDecimal(r.get("qtyPer1000Packs").replace(',', '.')),
                        r.get("uom"), r.get("consumerType"))).toList();
                norms.create(productId, from, reason == null || reason.isBlank() ? "Nhập từ Excel" : reason, lines);
            }
        }

        private long count(String sql, String arg) {
            Long n = jdbc.queryForObject(sql, Long.class, arg);
            return n == null ? 0 : n;
        }

        private LocalDate parseDate(String text) {
            return text.contains("/") ? LocalDate.parse(text.trim(), VN_DATE) : LocalDate.parse(text.trim());
        }
    }
}
