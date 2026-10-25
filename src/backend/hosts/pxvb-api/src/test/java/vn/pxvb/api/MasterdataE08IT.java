package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** E08: luật biểu mẫu KSCL, bảo trì chuẩn, luật lỗi A/B/C và liên kết thiết bị–phụ tùng (FR-MD-012/013/015/019). */
@AutoConfigureMockMvc
class MasterdataE08IT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
    }

    private MvcResult send(String resource, String body) throws Exception {
        return mvc.perform(post("/api/v1/md/" + resource)
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private JsonNode created(String resource, String body) throws Exception {
        MvcResult r = send(resource, body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void qcFormWithUnsupportedSchemaVersionIsRejected() throws Exception {
        String body = """
                {"code":"%s","formGroup":"B06","name":"Biểu mẫu thử","category":"INLINE",
                 "formSchema":"{\\"schemaVersion\\":2,\\"sections\\":[]}"}
                """.formatted(unique("F"));

        MvcResult r = send("qc-form-templates", body);

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("formSchema");
    }

    @Test
    void maintTemplateRejectsSparePartThatDoesNotExist() throws Exception {
        String body = """
                {"code":"%s","name":"Vệ sinh","targetType":"MODEL","targetValue":"Protos",
                 "maintType":"CLEANING","cycleValue":7,"cycleUnit":"DAY",
                 "parts":"[{\\"partNo\\":\\"%s\\",\\"qty\\":1}]"}
                """.formatted(unique("T"), unique("KHONG-CO-"));

        MvcResult r = send("maint-task-templates", body);

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("Phụ tùng không tồn tại");
    }

    @Test
    void defectGradeRulesWithOverlappingValidityAreRejected() throws Exception {
        String defectCode = unique("D");
        created("defects", "{\"code\":\"%s\",\"name\":\"Lỗi thử\",\"appliesTo\":\"ROD\"}".formatted(defectCode));
        created("defect-grade-rules", """
                {"defectCode":"%s","appliesTo":"ROD","grade":"A","metric":"COUNT_IN_SAMPLE",
                 "thresholdValue":3,"sampleSize":50,"effectiveFrom":"2026-01-01"}
                """.formatted(defectCode));

        MvcResult r = send("defect-grade-rules", """
                {"defectCode":"%s","appliesTo":"ROD","grade":"A","metric":"COUNT_IN_SAMPLE",
                 "thresholdValue":4,"sampleSize":50,"effectiveFrom":"2026-06-01"}
                """.formatted(defectCode));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("Trùng khoảng hiệu lực");
    }

    @Test
    void partLinkComponentMustBelongToSelectedMachine() throws Exception {
        String machineA = created("machines", machineBody()).get("id").asText();
        String machineB = created("machines", machineBody()).get("id").asText();
        String component = created("machine-components",
                "{\"machineId\":\"%s\",\"code\":\"%s\",\"name\":\"Cụm\",\"discipline\":\"MECHANICAL\"}"
                        .formatted(machineA, unique("C"))).get("id").asText();
        String part = created("spare-parts",
                "{\"partNo\":\"%s\",\"name\":\"Phụ tùng\",\"uom\":\"cái\"}".formatted(unique("P"))).get("id").asText();

        MvcResult r = send("machine-part-links", """
                {"machineId":"%s","componentId":"%s","sparePartId":"%s"}
                """.formatted(machineB, component, part));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("Cụm không thuộc máy");
    }

    private String machineBody() {
        return "{\"code\":\"%s\",\"name\":\"May\",\"machineType\":\"MAKER\",\"model\":\"Protos\"}"
                .formatted(unique("M"));
    }
}
