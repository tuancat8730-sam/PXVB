package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.pxvb.sync.application.SyncMonitorScanner;

/** Đồng bộ ngoại tuyến (E09): idempotency, xung đột, delta pull, giám sát thiết bị. */
@AutoConfigureMockMvc
class SyncIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    TestSyncHandler handler;
    @Autowired
    SyncMonitorScanner scanner;

    private String admin;
    private String deviceToken;
    private String operatorToken;
    private TestUser leader;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String deviceCode = "TAB-" + suffix;
        JsonNode created = json(call(post("/api/v1/identity/devices"), admin,
                "{\"deviceCode\":\"%s\",\"name\":\"Tablet %s\",\"position\":\"VAN\"}".formatted(deviceCode, suffix)));
        deviceToken = json(mvc.perform(post("/api/v1/identity/devices/activate")
                        .contentType(MediaType.APPLICATION_JSON).content(("{\"deviceCode\":\"%s\","
                                + "\"activationCode\":\"%s\"}").formatted(deviceCode, created.get("activationCode")
                                .asText()))).andReturn()).get("deviceToken").asText();
        String employeeCode = "E" + suffix;
        JsonNode user = json(call(post("/api/v1/identity/users"), admin, ("{\"username\":\"op%s\","
                + "\"fullName\":\"Van hanh\",\"employeeCode\":\"%s\",\"initialPassword\":\"Oper#Pass2026X\"}")
                .formatted(suffix, employeeCode)));
        String userId = user.get("id").asText();
        call(put("/api/v1/identity/users/" + userId + "/roles"), admin,
                "{\"roles\":[{\"roleCode\":\"OPERATOR\"}]}");
        call(put("/api/v1/identity/users/" + userId + "/pin"), admin, "{\"pin\":\"4821\"}");
        operatorToken = json(mvc.perform(post("/api/v1/identity/auth/pin-login")
                .header("X-Device-Token", deviceToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"pin\":\"4821\"}".formatted(employeeCode))).andReturn())
                .get("accessToken").asText();
    }

    private MvcResult call(MockHttpServletRequestBuilder b, String token, String body) throws Exception {
        b.header("Authorization", "Bearer " + token);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(b).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String cmd(UUID id, String type, UUID entity, String value, Long baseVersion) {
        return ("{\"clientCommandId\":\"%s\",\"type\":\"%s\",\"entityId\":\"%s\",\"payload\":{\"value\":\"%s\"},"
                + "\"occurredAt\":\"%s\",\"enteredAt\":\"%s\",\"baseVersion\":%s}")
                .formatted(id, type, entity, value, Instant.now(), Instant.now(), baseVersion);
    }

    private JsonNode push(String token, String... commands) throws Exception {
        String body = "{\"deviceTime\":\"%s\",\"appVersion\":\"1.0.3\",\"pendingCount\":%d,\"commands\":[%s]}"
                .formatted(Instant.now(), commands.length, String.join(",", commands));
        MvcResult r = call(post("/api/v1/sync/push"), token, body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r);
    }

    @Test
    void pushIsOrderedIdempotentAndReportsEachCommand() throws Exception {
        UUID entity = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();

        JsonNode first = push(operatorToken, cmd(c1, "TEST.Set", entity, "A", 0L),
                cmd(c2, "TEST.Set", entity, "INVALID", null), cmd(c3, "UNKNOWN.Type", entity, "x", null));
        JsonNode results = first.get("results");
        assertThat(results.get(0).get("status").asText()).isEqualTo("APPLIED");
        assertThat(results.get(1).get("status").asText()).isEqualTo("REJECTED");
        assertThat(results.get(2).get("status").asText()).isEqualTo("REJECTED");
        assertThat(handler.valueOf(entity)).isEqualTo("A");

        // Gửi lại cùng lô: không áp dụng lần hai
        JsonNode again = push(operatorToken, cmd(c1, "TEST.Set", entity, "A2", 0L));
        assertThat(again.get("results").get(0).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(handler.valueOf(entity)).isEqualTo("A");
        assertThat(handler.versionOf(entity)).isEqualTo(1);
    }

    @Test
    void staleBaseVersionCreatesConflictWithoutOverwriteAndCanBeResolved() throws Exception {
        UUID entity = UUID.randomUUID();
        handler.seed(entity, "server");
        UUID stale = UUID.randomUUID();

        JsonNode response = push(operatorToken, cmd(stale, "TEST.Set", entity, "tablet", 0L));
        JsonNode result = response.get("results").get(0);
        assertThat(result.get("status").asText()).isEqualTo("CONFLICT");
        String conflictId = result.get("conflictId").asText();
        assertThat(handler.valueOf(entity)).isEqualTo("server");

        JsonNode open = json(call(get("/api/v1/sync/conflicts?status=OPEN"), leader.token(), null));
        assertThat(open.toString()).contains(conflictId);

        // Cảnh báo CC_SYNC_CONFLICT được phát
        JsonNode alerts = json(call(get("/api/v1/alerting/alerts?module=CC&pageSize=100"), leader.token(), null));
        assertThat(alerts.toString()).contains("CC_SYNC_CONFLICT");

        // Vận hành viên không có quyền xử lý xung đột
        assertThat(call(post("/api/v1/sync/conflicts/" + conflictId + "/resolve"), operatorToken,
                "{\"decision\":\"ACCEPT_CLIENT\"}").getResponse().getStatus()).isEqualTo(403);

        MvcResult resolved = call(post("/api/v1/sync/conflicts/" + conflictId + "/resolve"), leader.token(),
                "{\"decision\":\"ACCEPT_CLIENT\",\"note\":\"Bản tablet đúng\"}");
        assertThat(resolved.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(resolved).get("status").asText()).isEqualTo("RESOLVED_ACCEPT_CLIENT");
        assertThat(handler.valueOf(entity)).isEqualTo("tablet");

        // Không xử lý hai lần
        assertThat(call(post("/api/v1/sync/conflicts/" + conflictId + "/resolve"), leader.token(),
                "{\"decision\":\"KEEP_SERVER\"}").getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void batchLimitAndDeviceRequirementAreEnforced() throws Exception {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(cmd(UUID.randomUUID(), "TEST.Set", UUID.randomUUID(), "v", null));
        }
        String body = "{\"commands\":[%s]}".formatted(String.join(",", many));
        assertThat(call(post("/api/v1/sync/push"), operatorToken, body).getResponse().getStatus()).isEqualTo(400);

        // Token của người dùng đăng nhập web (không gắn thiết bị) không được đẩy lệnh
        assertThat(call(post("/api/v1/sync/push"), leader.token(), "{\"commands\":[]}").getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void pullDeliversMasterdataDeltaWithPaging() throws Exception {
        String code = "SYNC" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        call(post("/api/v1/md/machines"), admin,
                "{\"code\":\"%s\",\"name\":\"May dong bo\",\"machineType\":\"OTHER\"}".formatted(code));

        JsonNode manifest = json(call(get("/api/v1/sync/pull/manifest"), operatorToken, null));
        assertThat(manifest.get("resources").toString()).contains("machines").contains("stop-reasons");

        JsonNode full = json(call(get("/api/v1/sync/pull?resource=machines&limit=1000"), operatorToken, null));
        assertThat(full.get("records").toString()).contains(code);

        // Phân trang bằng cursor
        JsonNode page1 = json(call(get("/api/v1/sync/pull?resource=stop-reasons&limit=4"), operatorToken, null));
        assertThat(page1.get("records")).hasSize(4);
        assertThat(page1.get("nextCursor").asText()).isNotBlank();
        JsonNode page2 = json(call(get("/api/v1/sync/pull?resource=stop-reasons&limit=50&cursor="
                + page1.get("nextCursor").asText()), operatorToken, null));
        assertThat(page2.get("records").size()).isGreaterThanOrEqualTo(5);

        // Delta: sau thời điểm máy chủ vừa trả về thì không còn bản ghi mới
        String serverTime = full.get("serverTime").asText();
        Thread.sleep(50);
        JsonNode delta = json(call(get("/api/v1/sync/pull?resource=machines&since=" + serverTime), operatorToken,
                null));
        assertThat(delta.get("records").toString()).doesNotContain(code);
    }

    @Test
    void deviceMonitorShowsSyncStateAndStaleDeviceRaisesAlert() throws Exception {
        push(operatorToken, cmd(UUID.randomUUID(), "TEST.Set", UUID.randomUUID(), "v", null));
        JsonNode devices = json(call(get("/api/v1/sync/devices"), leader.token(), null));
        boolean found = false;
        for (JsonNode d : devices) {
            if ("1.0.3".equals(d.path("appVersion").asText())) {
                found = true;
                assertThat(d.get("lastSyncAt").isNull()).isFalse();
                assertThat(d.has("clockOffsetMs")).isTrue();
            }
        }
        assertThat(found).isTrue();

        // Thiết bị báo còn lệnh chờ nhưng đã lâu không đồng bộ → cảnh báo CC_DEVICE_UNSYNCED
        MvcResult r = call(post("/api/v1/sync/push"), operatorToken,
                "{\"pendingCount\":7,\"appVersion\":\"1.0.3\",\"commands\":[]}");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(scanner.scan(Instant.now().plusSeconds(3 * 3600))).isGreaterThanOrEqualTo(1);
    }
}
