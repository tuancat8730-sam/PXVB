package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Nền cho test tích hợp trên MySQL thật (Testcontainers). Phiên bản chọn bằng {@code -Dpxvb.test.mysql=8.4}
 * (mặc định 8.0) để kiểm thử cả 8.0 và 8.4 LTS theo SDD Q01.
 */
@SpringBootTest
@Testcontainers
@org.springframework.context.annotation.Import(MailTestConfig.class)
// Ngưỡng giới hạn tần suất cao để các IT đăng nhập nhiều lần không bị chặn; RateLimitIT ghi đè ngưỡng thấp
@org.springframework.test.context.TestPropertySource(properties = "pxvb.security.rate-limit-max-requests=100000")
abstract class AbstractIntegrationTest {

    static final String ADMIN_PASSWORD = "Adm1n#Pxvb2026!";

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            "mysql:" + System.getProperty("pxvb.test.mysql", "8.0"))
            .withDatabaseName("pxvb")
            // Migration tạo trigger append-only cho sys_audit_log cần quyền này khi bật binlog
            .withCommand("--log-bin-trust-function-creators=1", "--character-set-server=utf8mb4");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("pxvb.security.jwt-secret", () -> "test-secret-test-secret-test-secret-0123456789");
        registry.add("pxvb.security.bootstrap-admin-password", () -> ADMIN_PASSWORD);
        registry.add("pxvb.mail.enabled", () -> "true");
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    }

    static final String NEW_ADMIN_PASSWORD = "N3w#Passw0rd-Pxvb";
    static final String USER_PASSWORD = "Us3r#Passw0rd-Pxvb";

    record TestUser(String id, String username, String token) {
    }

    /** Đăng nhập admin; đổi mật khẩu lần đầu nếu cần; trả access token dùng được mọi API. */
    static String adminToken(MockMvc mvc, ObjectMapper mapper) throws Exception {
        MvcResult first = loginRaw(mvc, "admin", ADMIN_PASSWORD);
        if (first.getResponse().getStatus() == 200) {
            String token = mapper.readTree(first.getResponse().getContentAsString()).get("accessToken").asText();
            mvc.perform(post("/api/v1/identity/auth/change-password").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                                    .formatted(ADMIN_PASSWORD, NEW_ADMIN_PASSWORD)))
                    .andExpect(status().isNoContent());
        }
        MvcResult second = loginRaw(mvc, "admin", NEW_ADMIN_PASSWORD);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        return mapper.readTree(second.getResponse().getContentAsString()).get("accessToken").asText();
    }

    static MvcResult loginRaw(MockMvc mvc, String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/identity/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))).andReturn();
    }

    /** Tạo người dùng có một vai trò (phạm vi ALL), đổi mật khẩu lần đầu và trả token đã dùng được. */
    static TestUser createUser(MockMvc mvc, ObjectMapper mapper, String adminToken, String prefix, String roleCode)
            throws Exception {
        return createUser(mvc, mapper, adminToken, prefix, roleCode, null, null);
    }

    /** Như trên, nhưng vai trò được gán với phạm vi dữ liệu {@code scopeType}/{@code scopeValue} (FR-CC-006). */
    static TestUser createUser(MockMvc mvc, ObjectMapper mapper, String adminToken, String prefix, String roleCode,
                               String scopeType, String scopeValue) throws Exception {
        String scope = scopeType == null ? ""
                : ",\"scopeType\":\"%s\",\"scopeValue\":\"%s\"".formatted(scopeType, scopeValue);
        String username = prefix + UUID.randomUUID().toString().substring(0, 6);
        MvcResult created = mvc.perform(post("/api/v1/identity/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\":\"%s\",\"fullName\":\"%s\",\"initialPassword\":\"%s\","
                                + "\"email\":\"%s@pxvb.test\"}").formatted(username, "Nguoi dung " + roleCode,
                                "Init#Passw0rd-Pxvb", username)))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(put("/api/v1/identity/users/" + id + "/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"roleCode\":\"%s\"%s}]}".formatted(roleCode, scope)))
                .andExpect(status().isNoContent());
        String firstToken = mapper.readTree(loginRaw(mvc, username, "Init#Passw0rd-Pxvb").getResponse()
                .getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/v1/identity/auth/change-password").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Init#Passw0rd-Pxvb\",\"newPassword\":\"%s\"}"
                                .formatted(USER_PASSWORD)))
                .andExpect(status().isNoContent());
        String token = mapper.readTree(loginRaw(mvc, username, USER_PASSWORD).getResponse().getContentAsString())
                .get("accessToken").asText();
        return new TestUser(id, username, token);
    }
}
