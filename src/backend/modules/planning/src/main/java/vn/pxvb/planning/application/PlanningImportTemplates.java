package vn.pxvb.planning.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.pxvb.dataio.contract.ImportTemplate;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.planning.application.MonthlyPlanService.ItemInput;

/** Mẫu nhập Excel KHSX tháng (FR-PL-001): mỗi cặp (năm, tháng) tạo một revision mới. */
@Component
public class PlanningImportTemplates implements ImportTemplate.Provider {

    private final MonthlyPlanService monthly;
    private final MasterdataQueries masterdata;

    public PlanningImportTemplates(MonthlyPlanService monthly, MasterdataQueries masterdata) {
        this.monthly = monthly;
        this.masterdata = masterdata;
    }

    @Override
    public List<ImportTemplate> templates() {
        return List.of(new MonthlyPlanTemplate());
    }

    private final class MonthlyPlanTemplate implements ImportTemplate {

        @Override
        public String code() {
            return "monthly-plan";
        }

        @Override
        public String title() {
            return "Nhập liệu: KHSX tháng";
        }

        @Override
        public String permission() {
            return "pl.monthlyPlan.update";
        }

        @Override
        public List<Column> columns() {
            return List.of(
                    new Column("year", "Năm", true, "Ví dụ 2026"),
                    new Column("month", "Tháng", true, "1–12"),
                    new Column("productCode", "Mã sản phẩm", true, "Sản phẩm phải đã tồn tại và đang hoạt động"),
                    new Column("plannedPacks", "Sản lượng (bao)", true, "Số nguyên lớn hơn 0"),
                    new Column("note", "Ghi chú", false, ""));
        }

        @Override
        public Map<String, String> validate(Map<String, String> row) {
            Map<String, String> errors = new LinkedHashMap<>();
            Integer year = parseInt(row.get("year"));
            Integer month = parseInt(row.get("month"));
            if (year == null || year < 2000 || year > 2100) {
                errors.put("year", "Năm không hợp lệ");
            }
            if (month == null || month < 1 || month > 12) {
                errors.put("month", "Tháng phải từ 1 đến 12");
            }
            Long packs = parseLong(row.get("plannedPacks"));
            if (packs == null || packs <= 0) {
                errors.put("plannedPacks", "Sản lượng phải là số nguyên lớn hơn 0");
            }
            if (masterdata.productIdByCode(row.get("productCode")).isEmpty()) {
                errors.put("productCode", "Sản phẩm '" + row.get("productCode") + "' không tồn tại");
            }
            return errors;
        }

        @Override
        public String naturalKey(Map<String, String> row) {
            return row.get("year") + "|" + row.get("month") + "|" + row.get("productCode");
        }

        @Override
        public void upsert(Map<String, String> row) {
            commitBatch(List.of(row));
        }

        @Override
        public void commitBatch(List<Map<String, String>> rows) {
            Map<String, List<Map<String, String>>> byMonth = new LinkedHashMap<>();
            rows.forEach(r -> byMonth.computeIfAbsent(r.get("year") + "-" + r.get("month"),
                    k -> new ArrayList<>()).add(r));
            for (List<Map<String, String>> group : byMonth.values()) {
                int year = parseInt(group.get(0).get("year"));
                int month = parseInt(group.get(0).get("month"));
                List<ItemInput> items = group.stream().map(r -> new ItemInput(null, r.get("productCode"),
                        parseLong(r.get("plannedPacks")), r.get("note"))).toList();
                monthly.create(year, month, items, "Nhập từ Excel");
            }
        }

        private Integer parseInt(String v) {
            try {
                return v == null ? null : Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Long parseLong(String v) {
            try {
                return v == null ? null : Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
