package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Tra cứu nhật ký kiểm toán: lọc, phân trang, lịch sử theo bản ghi, xuất Excel (FR-CC-021/022). */
@AutoConfigureMockMvc
class AuditQueryIT extends AbstractIntegrationTest {

    private static final String AUDIT = "/api/v1/identity/audit-logs";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void auditSearchRequiresPermission() throws Exception {
        TestUser operator = createUser(mvc, mapper, adminToken(mvc, mapper), "aud", "OPERATOR");

        mvc.perform(get(AUDIT).header("Authorization", "Bearer " + operator.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchByEntityReturnsRoleChangeHistory() throws Exception {
        String admin = adminToken(mvc, mapper);
        TestUser user = createUser(mvc, mapper, admin, "hist", "OPERATOR");

        mvc.perform(get(AUDIT)
                        .param("entityType", "User")
                        .param("entityId", user.id())
                        .param("action", "ROLES_CHANGED")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].entityId").value(user.id()))
                .andExpect(jsonPath("$.items[0].action").value("ROLES_CHANGED"));
    }

    @Test
    void moduleAndActorFiltersNarrowTheResult() throws Exception {
        String admin = adminToken(mvc, mapper);
        String adminId = json(mvc.perform(get("/api/v1/identity/me")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn()).get("userId").asText();

        JsonNode byActor = json(mvc.perform(get(AUDIT)
                        .param("module", "identity")
                        .param("byUserId", adminId)
                        .param("action", "LOGIN")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(byActor.get("items")).isNotEmpty();
        byActor.get("items").forEach(item -> assertThat(item.get("byUserId").asText()).isEqualTo(adminId));

        mvc.perform(get(AUDIT)
                        .param("module", "production")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void entityHistoryReturnsOnlyRecordsOfThatEntity() throws Exception {
        String admin = adminToken(mvc, mapper);
        TestUser user = createUser(mvc, mapper, admin, "ent", "OPERATOR");

        mvc.perform(get(AUDIT + "/entities/User/" + user.id())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].action").value(org.hamcrest.Matchers.hasItem("ROLES_CHANGED")))
                .andExpect(jsonPath("$.items[*].entityId").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(user.id()))));

        mvc.perform(get(AUDIT + "/entities/User/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void pagingReturnsCursorUntilLastPage() throws Exception {
        String admin = adminToken(mvc, mapper);
        createUser(mvc, mapper, admin, "pg", "OPERATOR");

        JsonNode firstPage = json(mvc.perform(get(AUDIT)
                        .param("size", "1")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(firstPage.get("items")).hasSize(1);
        String cursor = firstPage.get("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        JsonNode secondPage = json(mvc.perform(get(AUDIT)
                        .param("size", "1")
                        .param("cursor", cursor)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(secondPage.get("items").get(0).get("id").asText())
                .isNotEqualTo(firstPage.get("items").get(0).get("id").asText());
    }

    @Test
    void malformedCursorIsRejectedAsValidationError() throws Exception {
        String admin = adminToken(mvc, mapper);

        mvc.perform(get(AUDIT)
                        .param("cursor", "bm90LWEtY3Vyc29y")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PXVB-CC-0400"));
    }

    @Test
    void rangeLongerThanThirtyOneDaysIsRejected() throws Exception {
        String admin = adminToken(mvc, mapper);
        Instant now = Instant.now();

        mvc.perform(get(AUDIT)
                        .param("from", now.minus(40, ChronoUnit.DAYS).toString())
                        .param("to", now.toString())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PXVB-CC-0400"));
    }

    @Test
    void exportReturnsWorkbookAndRecordsExportAudit() throws Exception {
        String admin = adminToken(mvc, mapper);

        mvc.perform(get(AUDIT + "/export")
                        .param("entityType", "User")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("nhat-ky")));

        JsonNode exported = json(mvc.perform(get(AUDIT)
                        .param("entityType", "AuditLog")
                        .param("action", "EXPORT")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(exported.get("items")).hasSizeGreaterThan(0);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
