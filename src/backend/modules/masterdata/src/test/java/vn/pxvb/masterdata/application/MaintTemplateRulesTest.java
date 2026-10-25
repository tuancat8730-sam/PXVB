package vn.pxvb.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Công việc bảo trì chuẩn (FR-MD-015): checklist, vật tư dự kiến và chu kỳ. */
class MaintTemplateRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsValidChecklistAndParts() throws Exception {
        JsonNode checklist = mapper.readTree("""
                [{"seq":1,"text":"Vệ sinh","resultType":"OK_NG"},
                 {"seq":2,"text":"Áp suất","resultType":"NUMBER","min":2,"max":5}]
                """);
        JsonNode parts = mapper.readTree("[{\"partNo\":\"P-001\",\"qty\":2}]");

        assertThat(MaintTemplateRules.checklistViolations(checklist)).isEmpty();
        assertThat(MaintTemplateRules.partsViolations(parts)).isEmpty();
    }

    @Test
    void treatsMissingChecklistAndPartsAsEmpty() {
        assertThat(MaintTemplateRules.checklistViolations(null)).isEmpty();
        assertThat(MaintTemplateRules.partsViolations(null)).isEmpty();
    }

    @Test
    void rejectsDuplicateChecklistSequence() throws Exception {
        JsonNode checklist = mapper.readTree("""
                [{"seq":1,"text":"A","resultType":"TEXT"},{"seq":1,"text":"B","resultType":"TEXT"}]
                """);

        assertThat(MaintTemplateRules.checklistViolations(checklist)).anyMatch(m -> m.contains("Trùng thứ tự 1"));
    }

    @Test
    void rejectsUnknownChecklistResultType() throws Exception {
        JsonNode checklist = mapper.readTree("[{\"seq\":1,\"text\":\"A\",\"resultType\":\"PHOTO\"}]");

        assertThat(MaintTemplateRules.checklistViolations(checklist)).anyMatch(m -> m.contains("kiểu kết quả"));
    }

    @Test
    void rejectsChecklistMinGreaterThanMax() throws Exception {
        JsonNode checklist = mapper.readTree("""
                [{"seq":1,"text":"A","resultType":"NUMBER","min":5,"max":2}]
                """);

        assertThat(MaintTemplateRules.checklistViolations(checklist)).anyMatch(m -> m.contains("nhỏ nhất"));
    }

    @Test
    void rejectsPartWithNonPositiveQuantity() throws Exception {
        JsonNode parts = mapper.readTree("[{\"partNo\":\"P-001\",\"qty\":0}]");

        assertThat(MaintTemplateRules.partsViolations(parts)).anyMatch(m -> m.contains("Số lượng"));
    }

    @Test
    void rejectsPartNumberRepeatedInParts() throws Exception {
        JsonNode parts = mapper.readTree("[{\"partNo\":\"P-001\",\"qty\":1},{\"partNo\":\"P-001\",\"qty\":3}]");

        assertThat(MaintTemplateRules.partsViolations(parts)).anyMatch(m -> m.contains("Trùng phụ tùng P-001"));
    }

    @Test
    void rejectsNonPositiveCycle() {
        assertThat(MaintTemplateRules.cycleViolations(0)).isNotEmpty();
        assertThat(MaintTemplateRules.cycleViolations(7)).isEmpty();
    }
}
