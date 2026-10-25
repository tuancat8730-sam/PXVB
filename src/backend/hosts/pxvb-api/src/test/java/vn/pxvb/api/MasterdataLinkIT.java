package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** E08 Pha 3: liên kết thiết bị–phụ tùng đọc được theo hai chiều (FR-MD-019). */
@AutoConfigureMockMvc
class MasterdataLinkIT extends AbstractIntegrationTest {

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

    private JsonNode getJson(String path) throws Exception {
        MvcResult r = mvc.perform(get(path).header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(r.getResponse().getStatus()).as(path).isEqualTo(200);
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String machine(String model) throws Exception {
        return created("machines", "{\"code\":\"%s\",\"name\":\"May\",\"machineType\":\"MAKER\",\"model\":\"%s\"}"
                .formatted(unique("M"), model)).get("id").asText();
    }

    private String sparePart(String name) throws Exception {
        return created("spare-parts", "{\"partNo\":\"%s\",\"name\":\"%s\",\"uom\":\"cái\"}"
                .formatted(unique("P"), name)).get("id").asText();
    }

    private static List<String> values(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path(field).asText()));
        return out;
    }

    private static JsonNode find(JsonNode array, String sparePartId) {
        for (JsonNode n : array) {
            if (sparePartId.equals(n.path("sparePartId").asText())) {
                return n;
            }
        }
        throw new AssertionError("Không thấy phụ tùng " + sparePartId + " trong " + array);
    }

    @Test
    void machinePartsListsDirectAndModelLinksWithComponent() throws Exception {
        String model = unique("MDL");
        String machineA = machine(model);
        String component = created("machine-components",
                "{\"machineId\":\"%s\",\"code\":\"%s\",\"name\":\"Cụm truyền động\",\"discipline\":\"MECHANICAL\"}"
                        .formatted(machineA, unique("C"))).get("id").asText();
        String direct = sparePart("Bạc đạn");
        String modelWide = sparePart("Dầu bôi trơn");
        created("machine-part-links", "{\"machineId\":\"%s\",\"componentId\":\"%s\",\"sparePartId\":\"%s\"}"
                .formatted(machineA, component, direct));
        created("machine-part-links", "{\"modelCode\":\"%s\",\"sparePartId\":\"%s\"}".formatted(model, modelWide));

        JsonNode partsOfA = getJson("/api/v1/md/machines/" + machineA + "/parts");

        assertThat(values(partsOfA, "sparePartId")).containsExactlyInAnyOrder(direct, modelWide);
        JsonNode withComponent = find(partsOfA, direct);
        assertThat(withComponent.get("componentId").asText()).isEqualTo(component);
        assertThat(withComponent.get("componentName").asText()).isEqualTo("Cụm truyền động");
        assertThat(find(partsOfA, modelWide).get("scope").asText()).isEqualTo("MODEL");
    }

    @Test
    void machinePartsDoNotIncludeLinksOfOtherMachines() throws Exception {
        String machineA = machine(unique("MDL"));
        String machineB = machine(unique("MDL"));
        String part = sparePart("Phớt chắn");
        created("machine-part-links", "{\"machineId\":\"%s\",\"sparePartId\":\"%s\"}".formatted(machineA, part));

        JsonNode partsOfB = getJson("/api/v1/md/machines/" + machineB + "/parts");

        assertThat(values(partsOfB, "sparePartId")).doesNotContain(part);
    }

    @Test
    void sparePartShowsEveryMachineItIsLinkedTo() throws Exception {
        String model = unique("MDL");
        String machineA = machine(model);
        String machineB = machine(model);
        String shared = sparePart("Bộ lọc khí");
        created("machine-part-links", "{\"machineId\":\"%s\",\"sparePartId\":\"%s\"}".formatted(machineA, shared));
        created("machine-part-links", "{\"machineId\":\"%s\",\"sparePartId\":\"%s\"}".formatted(machineB, shared));

        JsonNode machinesOfPart = getJson("/api/v1/md/spare-parts/" + shared + "/machines");

        assertThat(values(machinesOfPart, "machineId")).containsExactlyInAnyOrder(machineA, machineB);
    }

    @Test
    void sparePartShowsMachinesOfLinkedModel() throws Exception {
        String model = unique("MDL");
        String machineA = machine(model);
        String part = sparePart("Vòng đệm");
        created("machine-part-links", "{\"modelCode\":\"%s\",\"sparePartId\":\"%s\"}".formatted(model, part));

        JsonNode machinesOfPart = getJson("/api/v1/md/spare-parts/" + part + "/machines");

        assertThat(values(machinesOfPart, "machineId")).containsExactly(machineA);
        assertThat(machinesOfPart.get(0).get("scope").asText()).isEqualTo("MODEL");
    }

    @Test
    void partsOfUnknownMachineReturnsNotFound() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/md/machines/" + UUID.randomUUID() + "/parts")
                .header("Authorization", "Bearer " + admin)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }
}
