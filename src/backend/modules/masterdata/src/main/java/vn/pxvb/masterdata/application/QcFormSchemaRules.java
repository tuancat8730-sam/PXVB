package vn.pxvb.masterdata.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kiểm tra cấu trúc lược đồ biểu mẫu KSCL/TDT v1 (FR-MD-013, FR-MD-016). Thuần, không truy vấn DB. */
final class QcFormSchemaRules {

    static final int SUPPORTED_VERSION = 1;
    private static final Set<String> FIELD_TYPES = Set.of("NUMBER", "TEXT", "CHECK", "SELECT");

    private QcFormSchemaRules() {
    }

    static List<String> violations(JsonNode schema) {
        List<String> out = new ArrayList<>();
        if (schema == null || !schema.isObject()) {
            out.add("Lược đồ biểu mẫu phải là đối tượng JSON");
            return out;
        }
        if (schema.path("schemaVersion").asInt() != SUPPORTED_VERSION) {
            out.add("Phiên bản lược đồ không được hỗ trợ: " + schema.path("schemaVersion").asText());
        }
        JsonNode sections = schema.path("sections");
        if (!sections.isArray() || sections.isEmpty()) {
            out.add("Biểu mẫu cần ít nhất một mục");
            return out;
        }
        Set<String> fieldCodes = new HashSet<>();
        for (JsonNode section : sections) {
            fieldViolations(section.path("fields"), fieldCodes, out);
        }
        return out;
    }

    private static void fieldViolations(JsonNode fields, Set<String> fieldCodes, List<String> out) {
        if (!fields.isArray() || fields.isEmpty()) {
            out.add("Mục không có trường nào");
            return;
        }
        for (JsonNode field : fields) {
            String code = field.path("code").asText("").trim();
            if (code.isEmpty()) {
                out.add("Trường thiếu mã");
            } else if (!fieldCodes.add(code)) {
                out.add("Trùng mã trường " + code);
            }
            String type = field.path("type").asText("");
            if (!FIELD_TYPES.contains(type)) {
                out.add("Trường " + code + " có kiểu không hỗ trợ: " + type);
            }
            if (field.path("label").asText("").isBlank()) {
                out.add("Trường " + code + " thiếu nhãn");
            }
            if ("SELECT".equals(type) && !hasOptions(field.path("options"))) {
                out.add("Trường " + code + " kiểu SELECT cần danh sách lựa chọn");
            }
        }
    }

    private static boolean hasOptions(JsonNode options) {
        return options.isArray() && !options.isEmpty();
    }
}
