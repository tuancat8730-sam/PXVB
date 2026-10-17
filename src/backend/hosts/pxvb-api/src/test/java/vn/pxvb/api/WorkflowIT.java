package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.pxvb.common.domain.RecordState;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxDispatcher;
import vn.pxvb.workflow.application.ApprovalSlaScanner;
import vn.pxvb.workflow.application.SignatureService;
import vn.pxvb.workflow.domain.Signature;

/** Phê duyệt đa bước, ủy quyền, xác nhận điện tử và điều chỉnh sau khóa (E03). */
@AutoConfigureMockMvc
class WorkflowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestAdjustmentApplier applier;
    @Autowired
    SignatureService signatures;
    @Autowired
    ApprovalSlaScanner slaScanner;
    @Autowired
    OutboxDispatcher dispatcher;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String bearer(TestUser u) {
        return "Bearer " + u.token();
    }

    private MvcResult start(TestUser by, String flow, UUID entityId) throws Exception {
        return mvc.perform(post("/api/v1/workflow/approvals").header("Authorization", bearer(by))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flowCode\":\"%s\",\"entityId\":\"%s\"}".formatted(flow, entityId))).andReturn();
    }

    private MvcResult decide(TestUser by, String instanceId, String action, String note) throws Exception {
        return mvc.perform(post("/api/v1/workflow/approvals/" + instanceId + "/" + action)
                .header("Authorization", bearer(by)).contentType(MediaType.APPLICATION_JSON)
                .content(note == null ? "{}" : "{\"note\":\"%s\"}".formatted(note))).andReturn();
    }

    private void defineFlow(String code, String steps) throws Exception {
        mvc.perform(put("/api/v1/workflow/flows/" + code).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"CC\",\"entityType\":\"TestRecord\",\"steps\":%s,\"active\":true}"
                                .formatted(mapper.writeValueAsString(steps))))
                .andExpect(status().isOk());
    }

    @Test
    void approvalByAnotherUserCompletesFlowAndNotifiesStarter() throws Exception {
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        TestUser deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        UUID entity = UUID.randomUUID();

        MvcResult started = start(leader, "PL_WEEKLY_APPROVAL", entity);
        assertThat(started.getResponse().getStatus()).isEqualTo(201);
        JsonNode instance = body(started);
        String id = instance.get("id").asText();
        assertThat(instance.get("status").asText()).isEqualTo("PENDING");

        // BR-CC-003: người đề nghị không được tự duyệt
        assertThat(decide(leader, id, "approve", null).getResponse().getStatus()).isEqualTo(403);
        // Đối tượng đang có yêu cầu chưa xong → không tạo trùng
        assertThat(start(leader, "PL_WEEKLY_APPROVAL", entity).getResponse().getStatus()).isEqualTo(409);

        mvc.perform(get("/api/v1/workflow/approvals/pending").header("Authorization", bearer(deputy)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).exists());

        MvcResult approved = decide(deputy, id, "approve", "Đồng ý");
        assertThat(approved.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(approved).get("status").asText()).isEqualTo("APPROVED");
        assertThat(decide(deputy, id, "approve", null).getResponse().getStatus()).isEqualTo(422);

        Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox_message WHERE type = "
                + "'approval.completed' AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.entityId')) = ?",
                Integer.class, entity.toString());
        assertThat(events).isEqualTo(1);

        dispatcher.dispatchBatch();
        mvc.perform(get("/api/v1/alerting/notifications").header("Authorization", bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'APPROVAL_COMPLETED')]").exists());
    }

    @Test
    void shiftCloseNeedsStatQualityAndIssuerEachByAnyOfItsRoles() throws Exception {
        // Bên Vận hành (Trưởng ca) xác nhận bằng chữ ký khi gửi chốt; luồng còn 3 bên duyệt
        TestUser deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        TestUser stat = createUser(mvc, mapper, admin, "stat", "STAT_STAMP");
        TestUser qc = createUser(mvc, mapper, admin, "qc", "QA_SUPERVISOR");
        TestUser issuer = createUser(mvc, mapper, admin, "iss", "MATERIAL_ISSUER");
        String id = body(start(deputy, "SHIFT_CLOSE", UUID.randomUUID())).get("id").asText();

        assertThat(body(decide(stat, id, "approve", null)).get("status").asText()).isEqualTo("PENDING");
        assertThat(body(decide(qc, id, "approve", null)).get("status").asText()).isEqualTo("PENDING");
        assertThat(body(decide(issuer, id, "approve", null)).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void rejectRequiresReason() throws Exception {
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        TestUser deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        String id = body(start(leader, "PL_WEEKLY_APPROVAL", UUID.randomUUID())).get("id").asText();

        MvcResult noNote = decide(deputy, id, "reject", null);
        assertThat(noNote.getResponse().getStatus()).isEqualTo(400);
        MvcResult rejected = decide(deputy, id, "reject", "Thiếu số liệu");
        assertThat(body(rejected).get("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void parallelAllStageThenSequentialStage() throws Exception {
        String flow = "TEST_ALL_" + UUID.randomUUID().toString().substring(0, 6);
        defineFlow(flow, "[{\"no\":1,\"roleCode\":\"DEPUTY_DIRECTOR\",\"mode\":\"ALL\"},"
                + "{\"no\":1,\"roleCode\":\"MNT_LEAD\",\"mode\":\"ALL\"},{\"no\":2,\"roleCode\":\"KTCD\"}]");
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        TestUser deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        TestUser mnt = createUser(mvc, mapper, admin, "mnt", "MNT_LEAD");
        TestUser ktcd = createUser(mvc, mapper, admin, "ktcd", "KTCD");
        String id = body(start(leader, flow, UUID.randomUUID())).get("id").asText();

        // KTCĐ chưa tới lượt
        assertThat(decide(ktcd, id, "approve", null).getResponse().getStatus()).isEqualTo(403);
        JsonNode afterFirst = body(decide(deputy, id, "approve", null));
        assertThat(afterFirst.get("status").asText()).isEqualTo("PENDING");
        assertThat(afterFirst.get("currentStep").asInt()).isEqualTo(1);
        // Cùng một người không duyệt hai lần trong một giai đoạn
        assertThat(decide(deputy, id, "approve", null).getResponse().getStatus()).isEqualTo(409);

        JsonNode afterSecond = body(decide(mnt, id, "approve", null));
        assertThat(afterSecond.get("currentStep").asInt()).isEqualTo(2);
        assertThat(afterSecond.get("status").asText()).isEqualTo("PENDING");

        JsonNode done = body(decide(ktcd, id, "approve", null));
        assertThat(done.get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void delegateCanApproveOnBehalfWithinValidity() throws Exception {
        String flow = "TEST_ONE_" + UUID.randomUUID().toString().substring(0, 6);
        defineFlow(flow, "[{\"no\":1,\"roleCode\":\"QA_HEAD\",\"mode\":\"ANY\"}]");
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        TestUser qaHead = createUser(mvc, mapper, admin, "qah", "QA_HEAD");
        TestUser stand = createUser(mvc, mapper, admin, "stand", "OPERATOR");
        String id = body(start(leader, flow, UUID.randomUUID())).get("id").asText();

        assertThat(decide(stand, id, "approve", null).getResponse().getStatus()).isEqualTo(403);

        Instant now = Instant.now();
        mvc.perform(post("/api/v1/workflow/delegations").header("Authorization", bearer(qaHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"%s\",\"validFrom\":\"%s\",\"validTo\":\"%s\"}".formatted(
                                stand.id(), now.minus(1, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());

        MvcResult approved = decide(stand, id, "approve", null);
        assertThat(approved.getResponse().getStatus()).isEqualTo(200);
        JsonNode step = body(approved).get("steps").get(0);
        assertThat(step.get("actingFor").asText()).isEqualTo(qaHead.id());
        assertThat(step.get("decidedBy").asText()).isEqualTo(stand.id());
    }

    @Test
    void overdueStepEmitsSingleOverdueEvent() throws Exception {
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        UUID entity = UUID.randomUUID();
        assertThat(start(leader, "PL_ORDER_CONFIRM", entity).getResponse().getStatus()).isEqualTo(201);

        int first = slaScanner.scan(Instant.now().plus(3, ChronoUnit.HOURS));
        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(slaScanner.scan(Instant.now().plus(4, ChronoUnit.HOURS))).isZero();
        Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox_message WHERE type = "
                + "'approval.overdue'", Integer.class);
        assertThat(events).isGreaterThanOrEqualTo(1);
    }

    @Test
    void signatureRequiresReauthAndBecomesInvalidWhenRecordChanges() throws Exception {
        TestUser deputy = createUser(mvc, mapper, admin, "sign", "DEPUTY_DIRECTOR");
        UUID userId = UUID.fromString(deputy.id());
        UUID entity = UUID.randomUUID();
        String hash = SignatureService.hashOf("qty=10");

        assertThatThrownBy(() -> signatures.sign("TestRecord", entity, Signature.Purpose.APPROVE,
                "DEPUTY_DIRECTOR", userId, null, Signature.Method.PASSWORD, "sai-mat-khau", hash))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AUTH_FAILED));
        assertThatThrownBy(() -> signatures.sign("TestRecord", entity, Signature.Purpose.APPROVE,
                "DIRECTOR", userId, null, Signature.Method.PASSWORD, USER_PASSWORD, hash))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        signatures.sign("TestRecord", entity, Signature.Purpose.APPROVE, "DEPUTY_DIRECTOR", userId, null,
                Signature.Method.PASSWORD, USER_PASSWORD, hash);
        assertThat(signatures.hasValidSignature("TestRecord", entity, Signature.Purpose.APPROVE,
                "DEPUTY_DIRECTOR", hash)).isTrue();
        String changed = SignatureService.hashOf("qty=12");
        assertThat(signatures.hasValidSignature("TestRecord", entity, Signature.Purpose.APPROVE,
                "DEPUTY_DIRECTOR", changed)).isFalse();
        assertThatThrownBy(() -> signatures.requireValid("TestRecord", entity, Signature.Purpose.APPROVE,
                "DEPUTY_DIRECTOR", changed)).isInstanceOf(BusinessException.class);
    }

    @Test
    void adjustmentOfLockedRecordIsAppliedAfterApprovalWithFullAudit() throws Exception {
        TestUser leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        TestUser deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        UUID locked = applier.seed("10", RecordState.CONFIRMED);
        UUID draft = applier.seed("10", RecordState.DRAFT);

        // Chưa khóa → phải sửa trực tiếp
        assertThat(adjust(leader, draft, "12", "Nhập nhầm").getResponse().getStatus()).isEqualTo(400);
        // Thiếu lý do
        assertThat(adjust(leader, locked, "12", "").getResponse().getStatus()).isEqualTo(400);

        MvcResult created = adjust(leader, locked, "12", "Nhập nhầm số lượng");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode request = body(created);
        String id = request.get("id").asText();
        assertThat(request.get("status").asText()).isEqualTo("PENDING");
        assertThat(request.get("oldValue").asText()).isEqualTo("10");
        assertThat(request.get("approverRoleCode").asText()).isEqualTo("DEPUTY_DIRECTOR");
        assertThat(applier.quantityOf(locked)).isEqualTo("10");

        // Người đề nghị không có quyền duyệt
        mvc.perform(post("/api/v1/workflow/adjustments/" + id + "/approve").header("Authorization", bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/workflow/adjustments/" + id + "/approve").header("Authorization", bearer(deputy))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"OK\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
        assertThat(applier.quantityOf(locked)).isEqualTo("12");

        Integer audited = jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE action = 'ADJUST' "
                        + "AND entity_id = ? AND adjustment_request_id IS NOT NULL AND approved_by_user_id IS NOT NULL "
                        + "AND reason = 'Nhập nhầm số lượng'", Integer.class, locked.toString());
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void approverCannotApproveOwnAdjustmentAndRejectNeedsReason() throws Exception {
        TestUser deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        TestUser director = createUser(mvc, mapper, admin, "dir", "DIRECTOR");
        UUID locked = applier.seed("5", RecordState.SHIFT_LOCKED);
        String id = body(adjust(deputy, locked, "6", "Sửa theo biên bản")).get("id").asText();

        mvc.perform(post("/api/v1/workflow/adjustments/" + id + "/approve").header("Authorization", bearer(deputy))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // DIRECTOR có quyền duyệt nhưng không giữ vai trò duyệt cấu hình (DEPUTY_DIRECTOR)
        mvc.perform(post("/api/v1/workflow/adjustments/" + id + "/approve").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        TestUser otherDeputy = createUser(mvc, mapper, admin, "dep2", "DEPUTY_DIRECTOR");
        mvc.perform(post("/api/v1/workflow/adjustments/" + id + "/reject").header("Authorization", bearer(otherDeputy))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/workflow/adjustments/" + id + "/reject").header("Authorization", bearer(otherDeputy))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Không hợp lệ\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(applier.quantityOf(locked)).isEqualTo("5");
    }

    private MvcResult adjust(TestUser by, UUID entityId, String newValue, String reason) throws Exception {
        return mvc.perform(post("/api/v1/workflow/adjustments").header("Authorization", bearer(by))
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"module\":\"PR\",\"entityType\":\"TestRecord\",\"entityId\":\"%s\","
                        + "\"fieldPath\":\"quantity\",\"newValue\":\"%s\",\"reason\":\"%s\"}")
                        .formatted(entityId, newValue, reason))).andReturn();
    }
}
