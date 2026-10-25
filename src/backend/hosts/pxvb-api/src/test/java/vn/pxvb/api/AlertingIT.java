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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.pxvb.alerting.application.AlertService;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.alerting.contract.AlertRaiser.AlertRef;
import vn.pxvb.common.error.BusinessException;

/** Động cơ cảnh báo: khử trùng, vòng đời, người nhận, leo thang, cấu hình rule (E04). */
@AutoConfigureMockMvc
class AlertingIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AlertRaiser raiser;
    @Autowired
    AlertService alertService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MeterRegistry meters;

    private static final String ESCALATING_RULE = "MT_WASTE_HIGH";
    private static final String NO_LEVEL_POLICY = "{\"levels\":[]}";
    private static final String ALERTS_PATH = "/api/v1/alerting/alerts/";
    private static final String TWO_LEVEL_POLICY = "{\"levels\":["
            + "{\"afterMinutes\":30,\"roles\":[\"DEPUTY_DIRECTOR\"]},"
            + "{\"afterMinutes\":60,\"roles\":[\"DEPUTY_DIRECTOR\"]}]}";

    private String admin;
    private TestUser leader;
    private TestUser deputy;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
    }

    private AlertCommand command(String rule, String subject) {
        return new AlertCommand(rule, "Machine", subject, UUID.randomUUID(), null, "Cảnh báo " + rule,
                "Chi tiết " + subject, Map.of("value", 4.2), null, null);
    }

    private AlertCommand severityCommand(String rule, String subject, String severity) {
        return new AlertCommand(rule, "Machine", subject, UUID.randomUUID(), null, "Cảnh báo " + rule,
                "Chi tiết " + subject, Map.of("value", 4.2), null, severity);
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult call(TestUser user, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    @Test
    void raisedAlertNotifiesRuleRecipientsAndIsDeduplicated() throws Exception {
        String subject = "M-" + UUID.randomUUID();
        AlertRef first = raiser.raise(command("MT_WASTE_HIGH", subject));
        assertThat(first.created()).isTrue();

        mvc.perform(get("/api/v1/alerting/notifications").header("Authorization", "Bearer " + leader.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.alertId == '%s')]".formatted(first.alertId())).exists());
        mvc.perform(get("/api/v1/alerting/notifications").header("Authorization", "Bearer " + deputy.token()))
                .andExpect(jsonPath("$.content[?(@.alertId == '%s')]".formatted(first.alertId())).exists());

        AlertRef again = raiser.raise(command("MT_WASTE_HIGH", subject));
        assertThat(again.created()).isFalse();
        assertThat(again.alertId()).isEqualTo(first.alertId());
        mvc.perform(get("/api/v1/alerting/alerts/" + first.alertId())
                        .header("Authorization", "Bearer " + leader.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alert.occurrenceCount").value(2))
                .andExpect(jsonPath("$.alert.status").value("OPEN"));
    }

    @Test
    void lifecycleRequiresCauseAndRejectsInvalidTransitions() throws Exception {
        AlertRef ref = raiser.raise(command("PR_BUFFER_LOW", "M-" + UUID.randomUUID()));
        String base = "/api/v1/alerting/alerts/" + ref.alertId();

        // Chưa ACK thì không thể đóng
        assertThat(call(leader,base + "/close", "{}").getResponse().getStatus()).isEqualTo(422);
        assertThat(json(call(leader,base + "/ack", "{\"note\":\"Đã nhận\"}")).get("status").asText())
                .isEqualTo("ACK");
        assertThat(json(call(leader,base + "/start", "{}")).get("status").asText()).isEqualTo("IN_PROGRESS");

        // Bắt buộc nguyên nhân khi xử lý xong
        assertThat(call(leader,base + "/resolve", "{\"note\":\"Xong\"}").getResponse().getStatus()).isEqualTo(400);
        // Nguyên nhân phải thuộc danh mục ALERT_CAUSE (quyết định PO, L16)
        assertThat(call(leader, base + "/resolve", "{\"cause\":\"Tự đặt\",\"note\":\"Xong\"}").getResponse()
                .getStatus()).isEqualTo(400);
        JsonNode resolved = json(call(leader, base + "/resolve",
                "{\"cause\":\"MATERIAL_SHORTAGE\",\"note\":\"Đã bổ sung\"}"));
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("cause").asText()).isEqualTo("MATERIAL_SHORTAGE");
        assertThat(json(call(leader,base + "/close", "{}")).get("status").asText()).isEqualTo("CLOSED");

        JsonNode detail = json(mvc.perform(get(base).header("Authorization", "Bearer " + leader.token()))
                .andReturn());
        assertThat(detail.get("history")).hasSize(5);
    }

    @Test
    void resolveWithoutNoteIsRejectedAndKeepsStatus() throws Exception {
        AlertRef ref = raiser.raise(command("PR_BUFFER_LOW", "M-" + UUID.randomUUID()));
        String base = "/api/v1/alerting/alerts/" + ref.alertId();
        call(leader, base + "/ack", "{\"note\":\"Đã nhận\"}");
        call(leader, base + "/start", "{}");

        assertThat(call(leader, base + "/resolve", "{\"cause\":\"MATERIAL_SHORTAGE\"}").getResponse().getStatus())
                .isEqualTo(400);
        assertThat(call(leader, base + "/resolve", "{\"cause\":\"MATERIAL_SHORTAGE\",\"note\":\"   \"}")
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(alertService.detail(ref.alertId()).alert().getStatus()).isEqualTo(Alert.Status.IN_PROGRESS);
    }

    @Test
    void higherSeverityRecurrenceEscalatesAndEmailsOnceWhileSameLevelOnlyCounts() throws Exception {
        MailTestConfig.SENT.clear();
        String subject = "M-" + UUID.randomUUID();
        AlertRef first = raiser.raise(command("PR_STOP_LONG", subject));
        assertThat(alertService.detail(first.alertId()).alert().getSeverity()).isEqualTo(Alert.Severity.WARN);
        assertThat(MailTestConfig.SENT).isEmpty();

        AlertRef escalated = raiser.raise(severityCommand("PR_STOP_LONG", subject, "CRITICAL"));
        assertThat(escalated.created()).isFalse();
        assertThat(escalated.alertId()).isEqualTo(first.alertId());
        Alert afterEscalation = alertService.detail(first.alertId()).alert();
        assertThat(afterEscalation.getSeverity()).isEqualTo(Alert.Severity.CRITICAL);
        assertThat(afterEscalation.getOccurrenceCount()).isEqualTo(2);
        // Escalation re-notifies every current policy target: one email per distinct recipient.
        List<String> notifiedRecipients = MailTestConfig.SENT.stream().map(message -> message.getTo()[0]).toList();
        assertThat(notifiedRecipients).isNotEmpty().doesNotHaveDuplicates();
        assertThat(MailTestConfig.SENT).allSatisfy(message -> assertThat(message.getSubject()).contains("CRITICAL"));
        int notifiedAfterEscalation = MailTestConfig.SENT.size();

        raiser.raise(severityCommand("PR_STOP_LONG", subject, "CRITICAL"));
        raiser.raise(command("PR_STOP_LONG", subject));

        Alert afterRepeats = alertService.detail(first.alertId()).alert();
        assertThat(afterRepeats.getSeverity()).isEqualTo(Alert.Severity.CRITICAL);
        assertThat(afterRepeats.getOccurrenceCount()).isEqualTo(4);
        assertThat(MailTestConfig.SENT).hasSize(notifiedAfterEscalation);
    }

    @Test
    void recurrenceAfterDedupWindowCreatesNewAlert() throws Exception {
        String subject = "M-" + UUID.randomUUID();
        AlertRef first = raiser.raise(command("MT_WASTE_HIGH", subject));
        assertThat(first.created()).isTrue();
        backdateLastOccurrence("MT_WASTE_HIGH:Machine:" + subject, Instant.now().minus(2, ChronoUnit.DAYS));

        AlertRef later = raiser.raise(command("MT_WASTE_HIGH", subject));

        assertThat(later.created()).isTrue();
        assertThat(later.alertId()).isNotEqualTo(first.alertId());
        assertThat(alertService.detail(first.alertId()).alert().getOccurrenceCount()).isEqualTo(1);
    }

    private void backdateLastOccurrence(String dedupKey, Instant lastOccurredAt) {
        jdbc.update("UPDATE sys_alert SET last_occurred_at = ? WHERE dedup_key = ?",
                Timestamp.from(lastOccurredAt), dedupKey);
    }

    @Test
    void escalationAdvancesOneLevelAtATimeAndNeverRepeatsALevel() throws Exception {
        updateRule(ESCALATING_RULE, TWO_LEVEL_POLICY, true);
        try {
            AlertRef ref = raiser.raise(command(ESCALATING_RULE, "M-" + UUID.randomUUID()));
            Instant raisedAt = Instant.now();

            alertService.escalateDue(raisedAt.plus(31, ChronoUnit.MINUTES));
            assertThat(escalationLevelOf(ref)).isEqualTo(1);
            alertService.escalateDue(raisedAt.plus(31, ChronoUnit.MINUTES));
            assertThat(escalationLevelOf(ref)).isEqualTo(1);

            alertService.escalateDue(raisedAt.plus(61, ChronoUnit.MINUTES));
            assertThat(escalationLevelOf(ref)).isEqualTo(2);
            alertService.escalateDue(raisedAt.plus(200, ChronoUnit.MINUTES));
            assertThat(escalationLevelOf(ref)).isEqualTo(2);
        } finally {
            updateRule(ESCALATING_RULE, NO_LEVEL_POLICY, true);
        }
    }

    @Test
    void acknowledgedAlertStopsEscalation() throws Exception {
        updateRule(ESCALATING_RULE, TWO_LEVEL_POLICY, true);
        try {
            AlertRef ref = raiser.raise(command(ESCALATING_RULE, "M-" + UUID.randomUUID()));
            call(leader, "/api/v1/alerting/alerts/" + ref.alertId() + "/ack", "{\"note\":\"Đã nhận\"}");

            alertService.escalateDue(Instant.now().plus(200, ChronoUnit.MINUTES));

            assertThat(escalationLevelOf(ref)).isZero();
            assertThat(alertService.detail(ref.alertId()).alert().getStatus()).isEqualTo(Alert.Status.ACK);
        } finally {
            updateRule(ESCALATING_RULE, NO_LEVEL_POLICY, true);
        }
    }

    @Test
    void inProgressAlertStopsEscalation() throws Exception {
        updateRule(ESCALATING_RULE, TWO_LEVEL_POLICY, true);
        try {
            AlertRef ref = raiser.raise(command(ESCALATING_RULE, "M-" + UUID.randomUUID()));
            call(leader, "/api/v1/alerting/alerts/" + ref.alertId() + "/ack", "{\"note\":\"Đã nhận\"}");
            call(leader, "/api/v1/alerting/alerts/" + ref.alertId() + "/start", "{\"note\":\"Đang xử lý\"}");

            alertService.escalateDue(Instant.now().plus(200, ChronoUnit.MINUTES));

            assertThat(escalationLevelOf(ref)).isZero();
            assertThat(alertService.detail(ref.alertId()).alert().getStatus()).isEqualTo(Alert.Status.IN_PROGRESS);
        } finally {
            updateRule(ESCALATING_RULE, NO_LEVEL_POLICY, true);
        }
    }

    @Test
    void disabledRuleDoesNotEscalateOpenAlerts() throws Exception {
        updateRule(ESCALATING_RULE, TWO_LEVEL_POLICY, true);
        try {
            AlertRef ref = raiser.raise(command(ESCALATING_RULE, "M-" + UUID.randomUUID()));
            updateRule(ESCALATING_RULE, null, false);

            alertService.escalateDue(Instant.now().plus(200, ChronoUnit.MINUTES));

            assertThat(escalationLevelOf(ref)).isZero();
        } finally {
            updateRule(ESCALATING_RULE, NO_LEVEL_POLICY, true);
        }
    }

    private int escalationLevelOf(AlertRef ref) {
        return alertService.detail(ref.alertId()).alert().getEscalationLevel();
    }

    private void updateRule(String code, String escalationPolicy, boolean active) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("active", active);
        if (escalationPolicy != null) {
            body.put("escalationPolicy", escalationPolicy);
        }
        mvc.perform(put("/api/v1/alerting/rules/" + code).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutHandlePermissionCannotAcknowledge() throws Exception {
        TestUser planner = createUser(mvc, mapper, admin, "plan", "PLANNER");
        AlertRef ref = raiser.raise(command("PR_BUFFER_HIGH", "M-" + UUID.randomUUID()));
        assertThat(call(planner,"/api/v1/alerting/alerts/" + ref.alertId() + "/ack", "{}")
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void unackedAlertEscalatesToSuperiorOnce() throws Exception {
        TestUser freshDeputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        AlertRef ref = raiser.raise(command("PR_OUTPUT_BEHIND", "M-" + UUID.randomUUID()));

        // Chưa quá hạn: không leo thang
        assertThat(alertService.escalateDue(Instant.now().plus(5, ChronoUnit.MINUTES))).isZero();
        assertThat(alertService.escalateDue(Instant.now().plus(31, ChronoUnit.MINUTES))).isGreaterThanOrEqualTo(1);
        assertThat(alertService.escalateDue(Instant.now().plus(32, ChronoUnit.MINUTES))).isZero();

        mvc.perform(get("/api/v1/alerting/alerts/" + ref.alertId())
                        .header("Authorization", "Bearer " + freshDeputy.token()))
                .andExpect(jsonPath("$.alert.escalationLevel").value(1));
        mvc.perform(get("/api/v1/alerting/notifications").header("Authorization", "Bearer " + freshDeputy.token()))
                .andExpect(jsonPath("$.content[?(@.alertId == '%s')]".formatted(ref.alertId())).exists())
                .andExpect(jsonPath("$.content[0].title").value(org.hamcrest.Matchers.startsWith("[Leo thang]")));
    }

    @Test
    void disabledRuleDoesNotRaiseAndUnknownRuleIsRejected() throws Exception {
        MvcResult off = mvc.perform(put("/api/v1/alerting/rules/TX_BLOCK_LOW")
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andReturn();
        assertThat(off.getResponse().getStatus()).isEqualTo(200);
        try {
            AlertRef ref = raiser.raise(command("TX_BLOCK_LOW", "S-1"));
            assertThat(ref.alertId()).isNull();
        } finally {
            mvc.perform(put("/api/v1/alerting/rules/TX_BLOCK_LOW").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"));
        }
        assertThatThrownBy(() -> raiser.raise(command("NO_SUCH_RULE", "x"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void criticalAlertIsEmailedButWarningIsNot() throws Exception {
        MailTestConfig.SENT.clear();
        raiser.raise(command("MT_WASTE_HIGH", "M-" + UUID.randomUUID()));
        assertThat(MailTestConfig.SENT).isEmpty();

        raiser.raise(command("QC_DEFECT_A", "M-" + UUID.randomUUID()));
        assertThat(MailTestConfig.SENT).isNotEmpty();
        var mail = MailTestConfig.SENT.get(0);
        assertThat(mail.getSubject()).contains("CRITICAL");
        assertThat(mail.getTo()).isNotEmpty();
        assertThat(java.util.Arrays.stream(mail.getTo()).anyMatch(t -> t.startsWith("dep"))).isTrue();
    }

    @Test
    void alertsCanBeExportedToExcel() throws Exception {
        raiser.raise(command("PR_BUFFER_HIGH", "M-" + UUID.randomUUID()));
        MvcResult r = mvc.perform(get("/api/v1/alerting/alerts/export?module=PR")
                .header("Authorization", "Bearer " + leader.token())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Mã rule");
            assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void notificationsCanBeMarkedRead() throws Exception {
        AlertRef ref = raiser.raise(command("PR_OEE_LOW", "M-" + UUID.randomUUID()));
        assertThat(notificationFor(leader, ref.alertId()).get("readAt").isNull()).isTrue();

        mvc.perform(post("/api/v1/alerting/notifications/read-all").header("Authorization",
                "Bearer " + leader.token())).andExpect(status().isOk());

        // Chỉ kiểm thông báo vừa tạo: scanner nền có thể tạo thêm thông báo khác giữa hai bước (F1).
        assertThat(notificationFor(leader, ref.alertId()).get("readAt").isNull()).isFalse();
    }

    @Test
    void alertListAndExportCanBeFilteredByShiftSession() throws Exception {
        UUID shiftA = UUID.randomUUID();
        AlertRef inShiftA = raiser.raise(shiftCommand("MT_WASTE_HIGH", "M-" + UUID.randomUUID(), shiftA));
        AlertRef inOtherShift = raiser.raise(shiftCommand("MT_WASTE_HIGH", "M-" + UUID.randomUUID(), UUID.randomUUID()));

        JsonNode list = json(mvc.perform(get("/api/v1/alerting/alerts").param("shiftSessionId", shiftA.toString())
                .header("Authorization", "Bearer " + admin)).andReturn());
        List<String> listedIds = new ArrayList<>();
        list.get("content").forEach(alert -> listedIds.add(alert.get("id").asText()));
        assertThat(listedIds).contains(inShiftA.alertId().toString())
                .doesNotContain(inOtherShift.alertId().toString());

        MvcResult export = mvc.perform(get("/api/v1/alerting/alerts/export").param("shiftSessionId", shiftA.toString())
                .header("Authorization", "Bearer " + admin)).andReturn();
        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            // 1 dòng tiêu đề + đúng 1 cảnh báo của ca A
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(2);
        }
    }

    @Test
    void scopedUserCannotActOnAlertOfAnotherLine() throws Exception {
        String lineA = uniqueLine();
        String lineB = uniqueLine();
        UUID machineB = createMachineOnLine(lineB);
        TestUser supervisorA = createUser(mvc, mapper, admin, "supA", "SHIFT_LEADER", "LINE", lineA);
        TestUser supervisorB = createUser(mvc, mapper, admin, "supB", "SHIFT_LEADER", "LINE", lineB);
        AlertRef ref = raiser.raise(machineCommand("MT_WASTE_HIGH", "M-" + UUID.randomUUID(), machineB));
        String path = ALERTS_PATH + ref.alertId();

        assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + supervisorA.token())).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(supervisorA, path + "/ack", "{}").getResponse().getStatus()).isEqualTo(403);
        assertThat(call(supervisorB, path + "/ack", "{\"note\":\"Đã nhận\"}").getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void listAndExportShowOnlyAlertsOfTheScopedLine() throws Exception {
        String lineA = uniqueLine();
        String lineB = uniqueLine();
        TestUser supervisorA = createUser(mvc, mapper, admin, "supA", "SHIFT_LEADER", "LINE", lineA);
        AlertRef inLineA = raiser.raise(machineCommand("MT_WASTE_HIGH", "M-" + UUID.randomUUID(), createMachineOnLine(lineA)));
        AlertRef inLineB = raiser.raise(machineCommand("MT_WASTE_HIGH", "M-" + UUID.randomUUID(), createMachineOnLine(lineB)));

        JsonNode list = json(mvc.perform(get("/api/v1/alerting/alerts").header("Authorization", "Bearer " + supervisorA.token()))
                .andReturn());
        List<String> listedIds = new ArrayList<>();
        list.get("content").forEach(alert -> listedIds.add(alert.get("id").asText()));
        assertThat(listedIds).contains(inLineA.alertId().toString())
                .doesNotContain(inLineB.alertId().toString());

        MvcResult export = mvc.perform(get(ALERTS_PATH + "export").header("Authorization", "Bearer " + supervisorA.token()))
                .andReturn();
        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            // 1 dòng tiêu đề + đúng 1 cảnh báo của dây chuyền A
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(2);
        }
    }

    @Test
    void alertWithoutLineIsVisibleOnlyToAllScope() throws Exception {
        TestUser supervisorA = createUser(mvc, mapper, admin, "supA", "SHIFT_LEADER", "LINE", uniqueLine());
        AlertRef ref = raiser.raise(command("MT_WASTE_HIGH", "M-" + UUID.randomUUID()));
        String path = ALERTS_PATH + ref.alertId();

        assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + supervisorA.token())).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + admin)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unitScopeSeesNothingUntilUnitsAreMappedToLines() throws Exception {
        UUID machine = createMachineOnLine(uniqueLine());
        TestUser unitUser = createUser(mvc, mapper, admin, "uni", "SHIFT_LEADER", "UNIT", "U-" + UUID.randomUUID());
        AlertRef ref = raiser.raise(machineCommand("MT_WASTE_HIGH", "M-" + UUID.randomUUID(), machine));

        assertThat(mvc.perform(get(ALERTS_PATH + ref.alertId()).header("Authorization", "Bearer " + unitUser.token()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    private static String uniqueLine() {
        return "LN-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID createMachineOnLine(String lineCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO md_machine (id, code, name, machine_type, line_code) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?)",
                id.toString(), "MC-" + id.toString().substring(0, 8), "Máy kiểm thử", "PACKER", lineCode);
        return id;
    }

    private AlertCommand machineCommand(String rule, String subject, UUID machineId) {
        return new AlertCommand(rule, "Machine", subject, machineId, null, "Cảnh báo " + rule,
                "Chi tiết " + subject, Map.of("value", 4.2), null, null);
    }

    @Test
    void statsCountAlertsByBusinessDateInBusinessZone() throws Exception {
        // 2020-01-10 17:30Z = 00:30 ngày 11/01 theo giờ Việt Nam; 16:30Z = 23:30 ngày 10/01.
        String lateSubject = "M-" + UUID.randomUUID();
        String earlySubject = "M-" + UUID.randomUUID();
        raiser.raise(command("MT_WASTE_HIGH", lateSubject));
        backdateAlert("MT_WASTE_HIGH:Machine:" + lateSubject, Instant.parse("2020-01-10T17:30:00Z"));
        raiser.raise(command("MT_WASTE_HIGH", earlySubject));
        backdateAlert("MT_WASTE_HIGH:Machine:" + earlySubject, Instant.parse("2020-01-10T16:30:00Z"));

        List<String> rows = statsRows(admin, "2020-01-10", "2020-01-11", "MT_WASTE_HIGH");

        assertThat(rows).containsExactly("2020-01-10=1", "2020-01-11=1");
    }

    @Test
    void statsRejectRangeOverNinetyDays() throws Exception {
        MvcResult r = mvc.perform(get(ALERTS_PATH + "stats").param("from", "2020-01-01").param("to", "2020-06-01")
                .header("Authorization", "Bearer " + admin)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void statsOnlyCountAlertsInScope() throws Exception {
        String lineA = uniqueLine();
        String lineB = uniqueLine();
        TestUser supervisorB = createUser(mvc, mapper, admin, "supB", "SHIFT_LEADER", "LINE", lineB);
        String subjectInLineA = "M-" + UUID.randomUUID();
        raiser.raise(machineCommand("MT_WASTE_HIGH", subjectInLineA, createMachineOnLine(lineA)));
        // Cửa sổ riêng (2019) để không lẫn với test thống kê khác dùng chung DB.
        backdateAlert("MT_WASTE_HIGH:Machine:" + subjectInLineA, Instant.parse("2019-07-01T12:00:00Z"));

        assertThat(statsRows(admin, "2019-07-01", "2019-07-02", "MT_WASTE_HIGH")).containsExactly("2019-07-01=1");
        assertThat(statsRows(supervisorB.token(), "2019-07-01", "2019-07-02", "MT_WASTE_HIGH")).isEmpty();
    }

    @Test
    void raiseRecordsNotifyLatencyTimer() throws Exception {
        long before = notifyLatencyCount("WARN");

        raiser.raise(command("MT_WASTE_HIGH", "M-" + UUID.randomUUID()));

        assertThat(notifyLatencyCount("WARN")).isEqualTo(before + 1);
    }

    private long notifyLatencyCount(String severity) {
        var timer = meters.find("pxvb.alert.notify.latency").tag("severity", severity).timer();
        return timer == null ? 0 : timer.count();
    }

    /** Dòng thống kê dạng {@code ngày=số} của một mã rule, theo thứ tự trả về. */
    private List<String> statsRows(String token, String from, String to, String ruleCode) throws Exception {
        JsonNode stats = json(mvc.perform(get(ALERTS_PATH + "stats").param("from", from).param("to", to)
                .header("Authorization", "Bearer " + token)).andReturn());
        List<String> rows = new ArrayList<>();
        for (JsonNode day : stats) {
            if (ruleCode.equals(day.get("ruleCode").asText())) {
                rows.add(day.get("businessDate").asText() + "=" + day.get("count").asLong());
            }
        }
        return rows;
    }

    /** Ghi theo UTC như app (DATETIME lưu UTC); Timestamp.from sẽ lệch theo múi giờ của JVM. */
    private void backdateAlert(String dedupKey, Instant at) {
        LocalDateTime utc = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
        jdbc.update("UPDATE sys_alert SET raised_at = ?, last_occurred_at = ? WHERE dedup_key = ?",
                utc, utc, dedupKey);
    }

    private AlertCommand shiftCommand(String rule, String subject, UUID shiftSessionId) {
        return new AlertCommand(rule, "Machine", subject, UUID.randomUUID(), shiftSessionId, "Cảnh báo " + rule,
                "Chi tiết " + subject, Map.of("value", 4.2), null, null);
    }

    private JsonNode notificationFor(TestUser user, UUID alertId) throws Exception {
        JsonNode page = json(mvc.perform(get("/api/v1/alerting/notifications").param("pageSize", "100")
                .header("Authorization", "Bearer " + user.token())).andReturn());
        for (JsonNode notification : page.get("content")) {
            if (alertId.toString().equals(notification.path("alertId").asText())) {
                return notification;
            }
        }
        throw new AssertionError("Không có thông báo cho cảnh báo " + alertId);
    }
}
