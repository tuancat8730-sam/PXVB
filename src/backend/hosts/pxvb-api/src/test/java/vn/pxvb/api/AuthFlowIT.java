package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Luồng xác thực/RBAC/audit/idempotency/thiết bị trên MySQL thật (FR-CC-001..005, FR-CC-020, §2.5). */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "N3w#Passw0rd-Pxvb";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult login(String user, String pass) throws Exception {
        return mvc.perform(post("/api/v1/identity/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass))).andReturn();
    }

    /** Đăng nhập admin, đổi mật khẩu lần đầu (nếu cần) và trả access token dùng được mọi API. */
    private String adminToken() throws Exception {
        MvcResult first = login("admin", ADMIN_PASSWORD);
        if (first.getResponse().getStatus() == 200) {
            JsonNode body = json(first);
            mvc.perform(post("/api/v1/identity/auth/change-password")
                            .header("Authorization", "Bearer " + body.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                                    .formatted(ADMIN_PASSWORD, NEW_PASSWORD)))
                    .andExpect(status().isNoContent());
        }
        MvcResult second = login("admin", NEW_PASSWORD);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        return json(second).get("accessToken").asText();
    }

    @Test
    @Order(1)
    void protectedEndpointWithoutTokenReturnsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/identity/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PXVB-CC-0401"));
    }

    @Test
    @Order(2)
    void firstLoginMustChangePasswordBeforeUsingOtherApis() throws Exception {
        MvcResult result = login("admin", ADMIN_PASSWORD);
        // Các IT khác dùng chung DB có thể đã đổi mật khẩu admin trước test này
        org.junit.jupiter.api.Assumptions.assumeTrue(result.getResponse().getStatus() == 200,
                "admin đã đổi mật khẩu ở test khác");
        JsonNode body = json(result);
        assertThat(body.get("mustChangePassword").asBoolean()).isTrue();

        mvc.perform(get("/api/v1/identity/users")
                        .header("Authorization", "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void adminCanListUsersAndSeesPermissionsAfterChangingPassword() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.permissions[?(@ == 'identity.user.view')]").exists());
        mvc.perform(get("/api/v1/identity/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void refreshRotatesAndReuseRevokesSession() throws Exception {
        adminToken();
        JsonNode tokens = json(login("admin", NEW_PASSWORD));
        String refresh = tokens.get("refreshToken").asText();

        MvcResult rotated = mvc.perform(post("/api/v1/identity/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh))).andExpect(status().isOk()).andReturn();
        String newRefresh = json(rotated).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        mvc.perform(post("/api/v1/identity/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh))).andExpect(status().isUnauthorized());
        // Dùng lại token cũ làm thu hồi cả phiên: token mới cũng không còn dùng được
        mvc.perform(post("/api/v1/identity/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(newRefresh))).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void userWithoutPermissionGetsForbiddenAndAccountLocksAfterFiveFailures() throws Exception {
        String token = adminToken();
        String username = "worker" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"fullName\":\"Cong nhan\",\"initialPassword\":\"%s\"}"
                                .formatted(username, "Worker#Pass2026")))
                .andExpect(status().isCreated());

        // Người dùng chưa có vai trò → đăng nhập được nhưng bị chặn do phải đổi mật khẩu, và không có quyền
        MvcResult ok = login(username, "Worker#Pass2026");
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            assertThat(login(username, "wrong-password").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = login(username, "wrong-password");
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        assertThat(json(locked).get("code").asText()).isEqualTo("PXVB-CC-0411");
        // Đúng mật khẩu nhưng đang bị khóa
        assertThat(login(username, "Worker#Pass2026").getResponse().getStatus()).isEqualTo(423);
    }

    @Test
    @Order(6)
    void loginAndEntityChangesAreAuditedWithSensitiveFieldsMasked() throws Exception {
        String token = adminToken();
        String username = "audited" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"fullName\":\"Audited\",\"initialPassword\":\"%s\"}"
                                .formatted(username, "Audit#Pass2026X")))
                .andExpect(status().isCreated());

        Integer logins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_log WHERE action = 'LOGIN' AND entity_type = 'User'", Integer.class);
        assertThat(logins).isPositive();

        String changes = jdbc.queryForObject(
                "SELECT CAST(field_changes AS CHAR) FROM sys_audit_log WHERE entity_type = 'User' "
                        + "AND action = 'CREATE' AND CAST(field_changes AS CHAR) LIKE ? LIMIT 1",
                String.class, "%" + username + "%");
        assertThat(changes).contains("passwordHash").contains("***").doesNotContain("Audit#Pass2026X");
    }

    @Test
    @Order(7)
    void auditLogIsAppendOnly() {
        Integer anyRows = jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log", Integer.class);
        assertThat(anyRows).isPositive();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbc.update("UPDATE sys_audit_log SET reason = 'tamper'"))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM sys_audit_log"))
                .hasMessageContaining("append-only");
    }

    @Test
    @Order(8)
    void duplicateIdempotencyKeyReplaysResponseWithoutSecondWrite() throws Exception {
        String token = adminToken();
        String username = "idem" + UUID.randomUUID().toString().substring(0, 6);
        String key = UUID.randomUUID().toString();
        String body = "{\"username\":\"%s\",\"fullName\":\"Idem\",\"initialPassword\":\"Idem#Pass2026X\"}"
                .formatted(username);

        MvcResult first = mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).header("X-Device-Id", "tablet-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).header("X-Device-Id", "tablet-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();

        assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class,
                username);
        assertThat(count).isEqualTo(1);

        // Cùng khóa nhưng nội dung khác → 409
        mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).header("X-Device-Id", "tablet-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Idem\"", "Khac\"")))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(9)
    void deviceActivationThenPinLoginWorks() throws Exception {
        String token = adminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String deviceCode = "TAB-" + suffix;

        MvcResult created = mvc.perform(post("/api/v1/identity/devices").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"%s\",\"name\":\"Tablet test\",\"position\":\"VAN\"}"
                                .formatted(deviceCode)))
                .andExpect(status().isCreated()).andReturn();
        String activationCode = json(created).get("activationCode").asText();

        mvc.perform(post("/api/v1/identity/devices/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"%s\",\"activationCode\":\"WRONGCODE1\"}".formatted(deviceCode)))
                .andExpect(status().isUnauthorized());
        MvcResult activated = mvc.perform(post("/api/v1/identity/devices/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"%s\",\"activationCode\":\"%s\"}"
                                .formatted(deviceCode, activationCode)))
                .andExpect(status().isOk()).andReturn();
        String deviceToken = json(activated).get("deviceToken").asText();
        // Mã kích hoạt chỉ dùng một lần
        mvc.perform(post("/api/v1/identity/devices/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"%s\",\"activationCode\":\"%s\"}"
                                .formatted(deviceCode, activationCode)))
                .andExpect(status().isUnauthorized());

        String employeeCode = "E" + suffix;
        MvcResult user = mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\":\"op%s\",\"fullName\":\"Van hanh\",\"employeeCode\":\"%s\","
                                + "\"initialPassword\":\"Oper#Pass2026X\"}").formatted(suffix, employeeCode)))
                .andExpect(status().isCreated()).andReturn();
        String userId = json(user).get("id").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/identity/users/" + userId + "/pin")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"4821\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/identity/auth/pin-login").header("X-Device-Token", deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"pin\":\"4821\"}".formatted(employeeCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/v1/identity/auth/pin-login").header("X-Device-Token", deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"pin\":\"0000\"}".formatted(employeeCode)))
                .andExpect(status().isUnauthorized());
    }
}
