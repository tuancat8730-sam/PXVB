package vn.pxvb.masterdata.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kiểm tra checklist, vật tư dự kiến và chu kỳ của công việc bảo trì chuẩn (FR-MD-015). Thuần, không truy vấn DB. */
final class MaintTemplateRules {

    private static final Set<String> RESULT_TYPES = Set.of("OK_NG", "NUMBER", "TEXT");

    private MaintTemplateRules() {
    }

    static List<String> checklistViolations(JsonNode checklist) {
        List<String> out = new ArrayList<>();
        if (isAbsent(checklist)) {
            return out;
        }
        if (!checklist.isArray()) {
            out.add("Checklist phải là danh sách");
            return out;
        }
        Set<Integer> seqs = new HashSet<>();
        for (JsonNode item : checklist) {
            int seq = item.path("seq").asInt();
            if (!seqs.add(seq)) {
                out.add("Trùng thứ tự " + seq + " trong checklist");
            }
            String resultType = item.path("resultType").asText("");
            if (!RESULT_TYPES.contains(resultType)) {
                out.add("Mục checklist " + seq + " có kiểu kết quả không hỗ trợ: " + resultType);
            }
            if (hasInvertedRange(item)) {
                out.add("Mục checklist " + seq + ": giá trị nhỏ nhất không được lớn hơn giá trị lớn nhất");
            }
        }
        return out;
    }

    static List<String> partsViolations(JsonNode parts) {
        List<String> out = new ArrayList<>();
        if (isAbsent(parts)) {
            return out;
        }
        if (!parts.isArray()) {
            out.add("Vật tư dự kiến phải là danh sách");
            return out;
        }
        Set<String> partNos = new HashSet<>();
        for (JsonNode part : parts) {
            String partNo = part.path("partNo").asText("").trim();
            if (partNo.isEmpty()) {
                out.add("Vật tư dự kiến thiếu mã phụ tùng");
                continue;
            }
            if (!partNos.add(partNo)) {
                out.add("Trùng phụ tùng " + partNo + " trong vật tư dự kiến");
            }
            if (part.path("qty").asDouble() <= 0) {
                out.add("Số lượng của phụ tùng " + partNo + " phải lớn hơn 0");
            }
        }
        return out;
    }

    static List<String> partNumbers(JsonNode parts) {
        List<String> numbers = new ArrayList<>();
        if (isAbsent(parts) || !parts.isArray()) {
            return numbers;
        }
        for (JsonNode part : parts) {
            String partNo = part.path("partNo").asText("").trim();
            if (!partNo.isEmpty()) {
                numbers.add(partNo);
            }
        }
        return numbers;
    }

    static List<String> cycleViolations(Integer cycleValue) {
        List<String> out = new ArrayList<>();
        if (cycleValue != null && cycleValue <= 0) {
            out.add("Chu kỳ phải lớn hơn 0");
        }
        return out;
    }

    private static boolean hasInvertedRange(JsonNode item) {
        return item.hasNonNull("min") && item.hasNonNull("max")
                && item.get("min").decimalValue().compareTo(item.get("max").decimalValue()) > 0;
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }
}
