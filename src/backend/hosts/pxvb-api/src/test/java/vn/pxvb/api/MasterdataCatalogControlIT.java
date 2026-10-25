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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-MD-022 (Pha 5 E08): mọi danh mục trả về người phụ trách, ngày cập nhật, phiên bản và trạng thái.
 */
@AutoConfigureMockMvc
class MasterdataCatalogControlIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
    }

    @Test
    void catalogRecordCarriesOwnerUpdatedAtVersionAndStatus() throws Exception {
        JsonNode product = createdOk("products", "{\"code\":\"%s\",\"name\":\"SP kiểm soát\",\"productType\":\"DOMESTIC\"}"
                .formatted(unique("KS")));

        JsonNode read = json(get("/api/v1/md/products/" + product.get("id").asText()));

        assertThat(read.get("owner").asText()).isEqualTo("DEPUTY_DIRECTOR");
        assertThat(read.get("updatedAt").isNull()).isFalse();
        assertThat(read.get("version").asLong()).isEqualTo(read.get("rowVersion").asLong());
        assertThat(read.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void versionedCatalogExposesVersionNumberAndStatusOfTheVersion() throws Exception {
        JsonNode form = createdOk("qc-form-templates", ("{\"code\":\"%s\",\"formGroup\":\"B06\","
                + "\"name\":\"Biểu mẫu kiểm soát\",\"category\":\"INLINE\",\"formSchema\":{\"schemaVersion\":1,"
                + "\"sections\":[{\"code\":\"S1\",\"fields\":[{\"code\":\"w\",\"label\":\"Khối lượng\","
                + "\"type\":\"NUMBER\"}]}]}}").formatted(unique("KSF")));

        assertThat(form.get("owner").asText()).isEqualTo("QA_HEAD");
        assertThat(form.get("version").asInt()).isEqualTo(1);
        assertThat(form.get("status").asText()).isEqualTo("DRAFT");
        assertThat(form.get("updatedAt").isNull()).isFalse();
    }

    @Test
    void listedRecordsCarryTheSameStewardshipFields() throws Exception {
        createdOk("products", "{\"code\":\"%s\",\"name\":\"SP liệt kê\",\"productType\":\"DOMESTIC\"}"
                .formatted(unique("KS")));

        JsonNode page = json(get("/api/v1/md/products?page=0&pageSize=5"));

        JsonNode first = page.get("content").get(0);
        assertThat(first.get("owner").asText()).isEqualTo("DEPUTY_DIRECTOR");
        assertThat(first.has("version")).isTrue();
        assertThat(first.has("updatedAt")).isTrue();
        assertThat(first.has("status")).isTrue();
    }

    private JsonNode createdOk(String resource, String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/md/" + resource).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult r = mvc.perform(request.header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
