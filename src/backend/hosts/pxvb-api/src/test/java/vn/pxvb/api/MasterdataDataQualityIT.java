package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-MD-021 (Pha 4 E08): báo cáo chất lượng dữ liệu nhận diện đúng nhóm thiếu/sai.
 * Các test so sánh số đếm trước và sau thao tác, nên không phụ thuộc dữ liệu do test khác tạo.
 * Dữ liệu sai không tạo được qua API (luật chồng lấn, công việc trỏ phụ tùng không có) được chèn bằng JDBC
 * để mô phỏng dữ liệu cũ đã lọt vào trước khi có validator.
 */
@AutoConfigureMockMvc
class MasterdataDataQualityIT extends AbstractIntegrationTest {

    private static final String QC_FORM_NO_ACTIVE = "QC_FORM_NO_ACTIVE";
    private static final String DEFECT_RULE_OVERLAP = "DEFECT_RULE_OVERLAP";
    private static final String MAINT_PART_MISSING = "MAINT_PART_MISSING";
    private static final String SPARE_PART_NO_SUPPLIER = "SPARE_PART_NO_SUPPLIER";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    private String admin;
    private TestUser qaHead;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        qaHead = createUser(mvc, mapper, admin, "dq", "QA_HEAD");
    }

    @Test
    void qcFormWithoutActiveVersionIsReportedUntilActivated() throws Exception {
        long before = countOf(QC_FORM_NO_ACTIVE);
        JsonNode form = createdOk("qc-form-templates", ("{\"code\":\"%s\",\"formGroup\":\"B06\","
                + "\"name\":\"Biểu mẫu DQ\",\"category\":\"INLINE\",\"formSchema\":{\"schemaVersion\":1,"
                + "\"sections\":[{\"code\":\"S1\",\"fields\":[{\"code\":\"w\",\"label\":\"Khối lượng\","
                + "\"type\":\"NUMBER\"}]}]}}").formatted(unique("DQF")));

        assertThat(countOf(QC_FORM_NO_ACTIVE)).isEqualTo(before + 1);

        MvcResult activated = mvc.perform(post("/api/v1/md/qc-form-templates/" + form.get("id").asText()
                        + "/activate-version").header("Authorization", "Bearer " + qaHead.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(activated.getResponse().getStatus()).isEqualTo(200);
        assertThat(countOf(QC_FORM_NO_ACTIVE)).isEqualTo(before);
    }

    @Test
    void overlappingDefectGradeRulesAreReported() throws Exception {
        long before = countOf(DEFECT_RULE_OVERLAP);
        String defectCode = unique("DQD");
        insertGradeRule(defectCode, "2026-01-01", null);
        insertGradeRule(defectCode, "2026-06-01", "2026-12-31");

        assertThat(countOf(DEFECT_RULE_OVERLAP)).isEqualTo(before + 1);
        assertThat(issue(report(), DEFECT_RULE_OVERLAP).get("severity").asText()).isEqualTo("ERROR");
    }

    @Test
    void maintTemplateReferencingMissingPartIsReportedOnlyForTheMissingPart() throws Exception {
        long before = countOf(MAINT_PART_MISSING);
        String existingPart = unique("PT");
        createdOk("spare-parts", "{\"partNo\":\"%s\",\"name\":\"Phụ tùng có thật\",\"uom\":\"CAI\"}"
                .formatted(existingPart));
        String missingPart = unique("PT");
        String parts = "[{\"partNo\":\"%s\",\"qty\":1},{\"partNo\":\"%s\",\"qty\":2}]"
                .formatted(existingPart, missingPart);
        jdbc.update("INSERT INTO md_maint_task_template (id, code, name, target_type, target_value, maint_type, "
                        + "cycle_value, cycle_unit, parts) VALUES (UUID_TO_BIN(?), ?, ?, 'MACHINE', 'ALL', "
                        + "'PREVENTIVE', 30, 'DAY', CAST(? AS JSON))",
                UUID.randomUUID().toString(), unique("MT"), "Bảo trì thử", parts);

        assertThat(countOf(MAINT_PART_MISSING)).isEqualTo(before + 1);
        assertThat(issue(report(), MAINT_PART_MISSING).get("severity").asText()).isEqualTo("ERROR");
    }

    @Test
    void sparePartWithoutDefaultSupplierIsWarnedButStillCreated() throws Exception {
        long before = countOf(SPARE_PART_NO_SUPPLIER);
        createdOk("spare-parts", "{\"partNo\":\"%s\",\"name\":\"Phụ tùng chưa có NCC\",\"uom\":\"CAI\"}"
                .formatted(unique("PT")));

        assertThat(countOf(SPARE_PART_NO_SUPPLIER)).isEqualTo(before + 1);
        assertThat(issue(report(), SPARE_PART_NO_SUPPLIER).get("severity").asText()).isEqualTo("WARN");
    }

    private void insertGradeRule(String defectCode, String effectiveFrom, String effectiveTo) {
        jdbc.update("INSERT INTO md_defect_grade_rule (id, defect_code, applies_to, grade, metric, "
                        + "threshold_value, effective_from, effective_to) VALUES (UUID_TO_BIN(?), ?, 'ROD', 'B', "
                        + "'COUNT_IN_SAMPLE', 1, ?, ?)",
                UUID.randomUUID().toString(), defectCode, effectiveFrom, effectiveTo);
    }

    private JsonNode createdOk(String resource, String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/md/" + resource).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode report() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/md/data-quality/report").header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    /** Nhóm trong báo cáo, hoặc null nếu nhóm không có dòng sai. */
    private static JsonNode issue(JsonNode report, String code) {
        for (JsonNode issue : report.get("issues")) {
            if (code.equals(issue.get("code").asText())) {
                return issue;
            }
        }
        return null;
    }

    private long countOf(String code) throws Exception {
        JsonNode issue = issue(report(), code);
        return issue == null ? 0 : issue.get("count").asLong();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
