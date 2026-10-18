package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.pxvb.production.application.ProductionScanner;
import vn.pxvb.production.application.ShiftFinalizer;
import vn.pxvb.production.contract.ProductionQueries;
import vn.pxvb.production.contract.ProductionQueries.RunFact;
import vn.pxvb.production.contract.ProductionQueries.ShiftFacts;

/** PR (E11): phiên ca, sản lượng, dừng máy, đổi mác, đồng bộ offline, cảnh báo (AC-PR-001/003/004/005). */
@AutoConfigureMockMvc
class ProductionIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ProductionScanner scanner;
    @Autowired
    ShiftFinalizer finalizer;
    @Autowired
    ProductionQueries production;

    private String admin;
    private TestUser leader;
    private TestUser operator;
    private TestUser deputy;
    private TestUser planner;
    private TestUser clerk;
    private TestUser mnt;

    private String maker;
    private String packer;
    private String idleMachine;
    private String productA;
    private String productB;
    private LocalDate date;
    private final String shift = "A";

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        operator = createUser(mvc, mapper, admin, "op", "OPERATOR");
        deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        planner = createUser(mvc, mapper, admin, "plan", "PLANNER");
        clerk = createUser(mvc, mapper, admin, "clerk", "NORM_CLERK");
        mnt = createUser(mvc, mapper, admin, "mnt", "MNT_LEAD");

        // Ngày duy nhất cho mỗi test để (ngày, ca) không đụng nhau; sau hôm nay để thời điểm ca nằm ở tương lai
        date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1 + ThreadLocalRandom.current().nextInt(4000));
        maker = machine("MAKER");
        packer = machine("PACKER");
        idleMachine = machine("PACKER");
        productA = product();
        productB = product();
        for (String p : new String[]{productA, productB}) {
            call(post("/api/v1/md/product-allowed-machines"), planner.token(),
                    "{\"productId\":\"%s\",\"machineId\":\"%s\"}".formatted(p, packer));
        }
        String material = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"Giay\",\"uom\":\"kg\","
                + "\"categoryCode\":\"PACKING\"}").formatted(unique("V"))).get("id").asText();
        String norm = json(call(post("/api/v1/md/bom-norms"), clerk.token(), ("{\"productId\":\"%s\","
                + "\"effectiveFrom\":\"%s\",\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":2.5}]}")
                .formatted(productA, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), material))).get("id").asText();
        call(post("/api/v1/md/bom-norms/" + norm + "/submit"), clerk.token(), null);
        call(post("/api/v1/md/bom-norms/" + norm + "/approve"), deputy.token(), null);

        String orderId = json(call(post("/api/v1/pl/orders"), planner.token(), ("{\"businessDate\":\"%s\","
                + "\"shiftCode\":\"%s\",\"makerMachineId\":\"%s\",\"packerMachineId\":\"%s\",\"productId\":\"%s\","
                + "\"plannedPacks\":100000}").formatted(date, shift, maker, packer, productA))).get("id").asText();
        MvcResult confirm = call(post("/api/v1/pl/orders/" + orderId + "/confirm"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}");
        assertThat(confirm.getResponse().getStatus()).as(confirm.getResponse().getContentAsString()).isEqualTo(200);
    }

    // ---------- tiện ích ----------

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

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private JsonNode createOk(String resource, String token, String body) throws Exception {
        MvcResult r = call(post("/api/v1/md/" + resource), token, body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return json(r);
    }

    private String machine(String type) throws Exception {
        String code = unique("M");
        return createOk("machines", admin, ("{\"code\":\"%s\",\"name\":\"May %s\",\"machineType\":\"%s\","
                + "\"theoreticalRate\":10}").formatted(code, code, type)).get("id").asText();
    }

    private String product() throws Exception {
        return createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP\",\"productType\":"
                + "\"DOMESTIC\",\"rodsPerPack\":20}").formatted(unique("P"))).get("id").asText();
    }

    private String openSession() throws Exception {
        MvcResult r = call(post("/api/v1/pr/sessions"), leader.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"%s\"}".formatted(date, shift));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return json(r).get("id").asText();
    }

    /** Lùi giờ bắt đầu thực tế 6 giờ để có thể nhập các sự kiện nằm trong quá khứ (giờ hệ thống là thật). */
    private String openRewoundSession() throws Exception {
        String id = openSession();
        jdbc.update("UPDATE pr_shift_session SET actual_start = ? WHERE id = UUID_TO_BIN(?)",
                java.sql.Timestamp.from(Instant.now().minusSeconds(6 * 3600)), id);
        return id;
    }

    private MvcResult stop(String machineId, String sessionId, String reason, Instant start, Instant end,
                           String token) throws Exception {
        return call(post("/api/v1/pr/machines/" + machineId + "/stops"), token,
                ("{\"sessionId\":\"%s\",\"reasonCode\":\"%s\",\"startAt\":\"%s\"%s}").formatted(sessionId, reason,
                        start, end == null ? "" : ",\"endAt\":\"" + end + "\""));
    }

    private MvcResult output(String machineId, String sessionId, String body, String token) throws Exception {
        return call(post("/api/v1/pr/machines/" + machineId + "/output"), token,
                "{\"sessionId\":\"%s\",%s}".formatted(sessionId, body));
    }

    private JsonNode run(String sessionId, String machineId) throws Exception {
        JsonNode dash = json(call(get("/api/v1/pr/sessions/" + sessionId + "/dashboard"), leader.token(), null));
        for (JsonNode m : dash.get("machines")) {
            if (machineId.equals(m.get("machineId").asText())) {
                return m;
            }
        }
        throw new AssertionError("Không có máy " + machineId + " trong dashboard");
    }

    // ---------- Phiên ca ----------

    @Test
    void openCreatesRunForEveryActiveMachineWithOrderMapping() throws Exception {
        String session = openSession();
        JsonNode dash = json(call(get("/api/v1/pr/sessions/" + session + "/dashboard"), leader.token(), null));
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM md_machine WHERE is_active = 1", Integer.class);
        assertThat(dash.get("machines")).hasSize(active);
        assertThat(run(session, packer).get("status").asText()).isEqualTo("NOT_STARTED");
        assertThat(run(session, maker).get("status").asText()).isEqualTo("NOT_STARTED");
        assertThat(run(session, idleMachine).get("status").asText()).isEqualTo("NO_ORDER");
        assertThat(run(session, packer).get("plannedPacks").asLong()).isEqualTo(100_000);
        assertThat(jdbc.queryForObject("SELECT status FROM pl_production_order WHERE planned_packs = 100000 "
                + "AND business_date = ? AND shift_code = ?", String.class, date, shift)).isEqualTo("RUNNING");

        // Ca đã có phiên → không mở lại; vận hành viên không được mở ca
        assertThat(call(post("/api/v1/pr/sessions"), leader.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"%s\"}".formatted(date, shift)).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(call(post("/api/v1/pr/sessions"), operator.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"B\"}".formatted(date)).getResponse().getStatus())
                .isEqualTo(403);
    }

    // ---------- Sản lượng ----------

    @Test
    void outputRulesAndCounterReading() throws Exception {
        String session = openRewoundSession();
        Instant t0 = Instant.now().minusSeconds(5 * 3600);

        MvcResult first = output(packer, session, "\"qty\":1000,\"counterReading\":5000,\"periodTo\":\"%s\""
                .formatted(t0.plusSeconds(3600)), operator.token());
        assertThat(first.getResponse().getStatus()).as(first.getResponse().getContentAsString()).isEqualTo(201);

        // Số đồng hồ lũy kế: hệ thống trừ ra (6200 − 5000 = 1200)
        MvcResult second = output(packer, session, "\"counterReading\":6200,\"periodTo\":\"%s\""
                .formatted(t0.plusSeconds(7200)), operator.token());
        assertThat(json(second).get("qty").asLong()).isEqualTo(1200);
        assertThat(run(session, packer).get("outputQty").asLong()).isEqualTo(2200);
        assertThat(run(session, packer).get("status").asText()).isEqualTo("RUNNING");

        // Số đồng hồ nhỏ hơn lần trước → sản lượng âm bị từ chối
        assertThat(output(packer, session, "\"counterReading\":100,\"periodTo\":\"%s\""
                .formatted(t0.plusSeconds(9000)), operator.token()).getResponse().getStatus()).isEqualTo(400);
        // Khoảng trùng lần trước (BR-PR-003)
        assertThat(output(packer, session, "\"qty\":10,\"periodFrom\":\"%s\",\"periodTo\":\"%s\""
                .formatted(t0.plusSeconds(5000), t0.plusSeconds(9000)), operator.token()).getResponse().getStatus())
                .isEqualTo(409);
        // Khoảng > 2 × chu kỳ (BR-PR-004): 5 giờ
        assertThat(output(packer, session, "\"qty\":10,\"periodTo\":\"%s\"".formatted(t0.plusSeconds(7200 + 18_000)),
                operator.token()).getResponse().getStatus()).isEqualTo(400);
        // Máy không lệnh không ghi được
        assertThat(output(idleMachine, session, "\"qty\":10", operator.token()).getResponse().getStatus())
                .isEqualTo(422);
    }

    // ---------- Dừng máy ----------

    @Test
    void overlappingStopIsRejectedAndRunMinutesAreComputed() throws Exception {
        String session = openRewoundSession();
        Instant t0 = Instant.now().minusSeconds(5 * 3600);

        MvcResult planned = stop(packer, session, "E", t0, t0.plusSeconds(30 * 60), operator.token());
        assertThat(planned.getResponse().getStatus()).as(planned.getResponse().getContentAsString())
                .isEqualTo(201);
        MvcResult fault = stop(packer, session, "C", t0.plusSeconds(40 * 60), t0.plusSeconds(80 * 60),
                operator.token());
        assertThat(fault.getResponse().getStatus()).as(fault.getResponse().getContentAsString()).isEqualTo(201);

        // AC-PR-003: chồng lấn bị từ chối, thông báo chỉ ra sự kiện xung đột
        MvcResult overlap = stop(packer, session, "D", t0.plusSeconds(20 * 60), t0.plusSeconds(50 * 60),
                operator.token());
        assertThat(overlap.getResponse().getStatus()).isEqualTo(409);
        assertThat(overlap.getResponse().getContentAsString()).contains("chồng lấn").contains("E");

        JsonNode r = run(session, packer);
        assertThat(r.get("plannedStopMinutes").asInt()).isEqualTo(30);
        assertThat(r.get("unplannedStopMinutes").asInt()).isEqualTo(40);
        // Ca A = 480 phút → chạy = 480 − 30 − 40 = 410 (AC-PR-002: dữ liệu đầu vào của OEE)
        assertThat(r.get("runMinutes").asInt()).isEqualTo(410);

        // Mã dừng không tồn tại
        assertThat(stop(packer, session, "Z", t0.plusSeconds(90 * 60), null, operator.token()).getResponse()
                .getStatus()).isEqualTo(400);
    }

    @Test
    void openStopThenEndSwitchesStatus() throws Exception {
        String session = openRewoundSession();
        Instant t0 = Instant.now().minusSeconds(5 * 3600);
        MvcResult started = stop(maker, session, "C", t0, null, operator.token());
        assertThat(started.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(started).get("canCreateRepairRequest").asBoolean()).isTrue();
        assertThat(run(session, maker).get("status").asText()).isEqualTo("STOPPED");
        // Đang dừng: không mở thêm sự kiện dừng
        assertThat(stop(maker, session, "D", t0.plusSeconds(60), null, operator.token()).getResponse().getStatus())
                .isEqualTo(409);

        MvcResult ended = call(post("/api/v1/pr/machines/" + maker + "/stops/end"), operator.token(),
                "{\"sessionId\":\"%s\",\"endAt\":\"%s\"}".formatted(session, t0.plusSeconds(1200)));
        assertThat(ended.getResponse().getStatus()).as(ended.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(run(session, maker).get("status").asText()).isEqualTo("RUNNING");

        // Xác nhận kỹ thuật chỉ MNT_LEAD, vận hành viên bị chặn
        String stopId = json(started).get("id").asText();
        assertThat(call(post("/api/v1/pr/stops/" + stopId + "/confirm-tech"), operator.token(),
                "{\"techCauseCode\":\"BEARING\"}").getResponse().getStatus()).isEqualTo(403);
        JsonNode confirmed = json(call(post("/api/v1/pr/stops/" + stopId + "/confirm-tech"), mnt.token(),
                "{\"techCauseCode\":\"BEARING\"}"));
        assertThat(confirmed.get("techCauseCode").asText()).isEqualTo("BEARING");
    }

    // ---------- Đổi mác ----------

    @Test
    void productChangeSplitsSegmentAndStartsStopA() throws Exception {
        String session = openSession();
        Instant t0 = Instant.now().plusSeconds(5);
        MvcResult change = call(post("/api/v1/pr/machines/" + packer + "/product-change"), operator.token(),
                "{\"sessionId\":\"%s\",\"toProductId\":\"%s\",\"changedAt\":\"%s\",\"startStopA\":true}"
                        .formatted(session, productB, t0));
        assertThat(change.getResponse().getStatus()).as(change.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json(change).get("segmentNo").asInt()).isEqualTo(2);
        assertThat(json(change).get("stopEventId").isNull()).isFalse();
        JsonNode r = run(session, packer);
        assertThat(r.get("segmentNo").asInt()).isEqualTo(2);
        assertThat(r.get("productId").asText()).isEqualTo(productB);
        assertThat(r.get("openStopReason").asText()).isEqualTo("A");

        // Đổi sang chính mác đang chạy bị từ chối
        assertThat(call(post("/api/v1/pr/machines/" + packer + "/product-change"), operator.token(),
                "{\"sessionId\":\"%s\",\"toProductId\":\"%s\"}".formatted(session, productB)).getResponse()
                .getStatus()).isEqualTo(400);
    }

    // ---------- Kết ca & khóa ghi nhận ----------

    @Test
    void endShiftClosesOpenStopsAndBlocksFurtherRecording() throws Exception {
        String session = openSession();
        assertThat(stop(packer, session, "C", Instant.now(), null, operator.token()).getResponse().getStatus())
                .isEqualTo(201);

        MvcResult ended = call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null);
        assertThat(ended.getResponse().getStatus()).as(ended.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode body = json(ended);
        assertThat(body.get("status").asText()).isEqualTo("CLOSING");
        assertThat(body.get("autoClosedStops").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM pl_production_order WHERE business_date = ? "
                + "AND shift_code = ? AND planned_packs = 100000", String.class, date, shift))
                .isEqualTo("COMPLETED");

        // BR-PR-001: ca không còn OPEN → từ chối ghi nhận (423 DATA_LOCKED)
        assertThat(output(packer, session, "\"qty\":10", operator.token()).getResponse().getStatus())
                .isEqualTo(423);
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(422);
    }

    // ---------- Offline sync (AC-PR-004) ----------

    @Test
    void offlineBatchAppliesInOrderAndIsIdempotent() throws Exception {
        String session = openRewoundSession();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String deviceCode = "TAB-" + suffix;
        JsonNode created = json(call(post("/api/v1/identity/devices"), admin,
                "{\"deviceCode\":\"%s\",\"name\":\"Tablet %s\",\"position\":\"VAN\"}".formatted(deviceCode, suffix)));
        String deviceToken = json(mvc.perform(post("/api/v1/identity/devices/activate")
                .contentType(MediaType.APPLICATION_JSON).content(("{\"deviceCode\":\"%s\",\"activationCode\":\"%s\"}")
                        .formatted(deviceCode, created.get("activationCode").asText()))).andReturn())
                .get("deviceToken").asText();
        String employeeCode = "E" + suffix;
        JsonNode user = json(call(post("/api/v1/identity/users"), admin, ("{\"username\":\"op%s\","
                + "\"fullName\":\"Van hanh\",\"employeeCode\":\"%s\",\"initialPassword\":\"Oper#Pass2026X\"}")
                .formatted(suffix, employeeCode)));
        call(put("/api/v1/identity/users/" + user.get("id").asText() + "/roles"), admin,
                "{\"roles\":[{\"roleCode\":\"OPERATOR\"}]}");
        call(put("/api/v1/identity/users/" + user.get("id").asText() + "/pin"), admin, "{\"pin\":\"4821\"}");
        String token = json(mvc.perform(post("/api/v1/identity/auth/pin-login").header("X-Device-Token", deviceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"identifier\":\"%s\",\"pin\":\"4821\"}"
                        .formatted(employeeCode))).andReturn()).get("accessToken").asText();

        Instant t0 = Instant.now().minusSeconds(5 * 3600);
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        String[] commands = {
                command(c1, "PR.RecordOutput", o1, session, packer, t0.plusSeconds(1800),
                        "{\"qty\":4000,\"periodTo\":\"%s\"}".formatted(t0.plusSeconds(1800))),
                command(c2, "PR.StartStop", s1, session, packer, t0.plusSeconds(2000),
                        "{\"reasonCode\":\"C\",\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(t0.plusSeconds(2000),
                                t0.plusSeconds(2600))),
                command(c3, "PR.RecordOutput", o2, session, packer, t0.plusSeconds(3600),
                        "{\"qty\":5000,\"periodTo\":\"%s\"}".formatted(t0.plusSeconds(3600)))};
        JsonNode first = push(token, commands);
        for (JsonNode r : first.get("results")) {
            assertThat(r.get("status").asText()).as(r.toString()).isEqualTo("APPLIED");
        }
        JsonNode r = run(session, packer);
        assertThat(r.get("outputQty").asLong()).isEqualTo(9000);
        assertThat(r.get("outputCount").asInt()).isEqualTo(2);
        assertThat(r.get("unplannedStopMinutes").asInt()).isEqualTo(10);

        // Gửi lại → DUPLICATE, không nhân đôi sản lượng
        JsonNode again = push(token, commands);
        for (JsonNode d : again.get("results")) {
            assertThat(d.get("status").asText()).isEqualTo("DUPLICATE");
        }
        assertThat(run(session, packer).get("outputQty").asLong()).isEqualTo(9000);
    }

    private String command(UUID id, String type, UUID entity, String sessionId, String machineId, Instant occurredAt,
                           String payload) {
        return ("{\"clientCommandId\":\"%s\",\"type\":\"%s\",\"entityId\":\"%s\",\"shiftSessionId\":\"%s\","
                + "\"machineId\":\"%s\",\"payload\":%s,\"occurredAt\":\"%s\",\"enteredAt\":\"%s\"}")
                .formatted(id, type, entity, sessionId, machineId, payload, occurredAt, Instant.now());
    }

    private JsonNode push(String token, String... commands) throws Exception {
        String body = "{\"deviceTime\":\"%s\",\"appVersion\":\"1.0.3\",\"pendingCount\":%d,\"commands\":[%s]}"
                .formatted(Instant.now(), commands.length, String.join(",", commands));
        MvcResult r = call(post("/api/v1/sync/push"), token, body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r);
    }

    // ---------- Cảnh báo (AC-PR-005) ----------

    @Test
    void scannerRaisesOutputBehindAndLongStop() throws Exception {
        String session = openSession();
        Instant now = Instant.now();
        // Dừng đang mở 45 phút (> ngưỡng 30)
        assertThat(stop(maker, session, "C", now.plusSeconds(1), null, operator.token()).getResponse().getStatus())
                .isEqualTo(201);
        // Sản lượng 1 bao trong khi kế hoạch-đến-thời-điểm lớn
        assertThat(output(packer, session, "\"qty\":1,\"periodTo\":\"%s\"".formatted(now.plusSeconds(60)),
                operator.token()).getResponse().getStatus()).isEqualTo(201);

        int raised = scanner.scan(now.plusSeconds(3 * 3600));
        assertThat(raised).isGreaterThanOrEqualTo(2);
        Integer behind = jdbc.queryForObject("SELECT COUNT(*) FROM sys_alert WHERE rule_code = 'PR_OUTPUT_BEHIND' "
                + "AND subject_id = ?", Integer.class, packer);
        Integer longStop = jdbc.queryForObject("SELECT COUNT(*) FROM sys_alert WHERE rule_code = 'PR_STOP_LONG' "
                + "AND machine_id = UUID_TO_BIN(?)", Integer.class, maker);
        assertThat(behind).isEqualTo(1);
        assertThat(longStop).isGreaterThanOrEqualTo(1);
        Integer low = jdbc.queryForObject("SELECT COUNT(*) FROM sys_alert WHERE rule_code = 'PR_OEE_LOW' "
                + "AND subject_id = ?", Integer.class, packer);
        assertThat(low).isGreaterThanOrEqualTo(1);
    }

    // ---------- OEE (AC-PR-002) ----------

    @Test
    void oeeMatchesAcPr002AndIsAggregatedByDayAndFormulaIsVersioned() throws Exception {
        assertThat(call(put("/api/v1/md/machines/" + packer), admin,
                "{\"theoreticalRate\":400,\"rateUnit\":\"PACKS_PER_MIN\"}").getResponse().getStatus())
                .isEqualTo(200);
        String session = openRewoundSession();
        Instant t0 = Instant.now().minusSeconds(5 * 3600);
        assertThat(stop(packer, session, "E", t0, t0.plusSeconds(30 * 60), operator.token()).getResponse()
                .getStatus()).isEqualTo(201);
        assertThat(stop(packer, session, "C", t0.plusSeconds(40 * 60), t0.plusSeconds(80 * 60), operator.token())
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(output(packer, session, "\"qty\":150000,\"periodTo\":\"%s\"".formatted(Instant.now()
                .minusSeconds(4 * 3600)), operator.token()).getResponse().getStatus()).isEqualTo(201);

        JsonNode row = oeeRow(session, packer);
        assertThat(row.get("plannedMinutes").asInt()).isEqualTo(450);
        assertThat(row.get("runMinutes").asInt()).isEqualTo(410);
        assertThat(row.get("availability").asDouble()).isCloseTo(0.911111, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(row.get("performance").asDouble()).isCloseTo(0.914634, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(row.get("oeePct").asDouble()).isCloseTo(83.33, org.assertj.core.data.Offset.offset(0.01));
        assertThat(row.get("isProvisional").asBoolean()).isTrue();

        // Gộp theo ngày: dòng của máy và dòng tổng; một ca → trùng giá trị ca
        JsonNode day = json(call(get("/api/v1/pr/oee?grain=DAY&from=%s&to=%s&machineId=%s".formatted(date, date,
                packer)), leader.token(), null));
        assertThat(day).hasSize(2);
        assertThat(day.get(0).get("oeePct").asDouble()).isCloseTo(83.33, org.assertj.core.data.Offset.offset(0.01));

        // Công thức có phiên bản: chỉ Phó QĐ/quản trị được đổi; FULL_SHIFT tính dừng kế hoạch là thời gian mất
        assertThat(call(put("/api/v1/pr/oee-formula"), leader.token(), "{\"plannedTimeMode\":\"FULL_SHIFT\","
                + "\"qualityMode\":\"ASSUME_100\",\"capPerformanceAt100\":false}").getResponse().getStatus())
                .isEqualTo(403);
        int before = json(call(get("/api/v1/pr/oee-formula"), leader.token(), null)).get("versionNo").asInt();
        try {
            JsonNode changed = json(call(put("/api/v1/pr/oee-formula"), deputy.token(), ("{\"plannedTimeMode\":"
                    + "\"FULL_SHIFT\",\"qualityMode\":\"ASSUME_100\",\"capPerformanceAt100\":false}")));
            assertThat(changed.get("versionNo").asInt()).isEqualTo(before + 1);
            assertThat(oeeRow(session, packer).get("plannedMinutes").asInt()).isEqualTo(480);
        } finally {
            call(put("/api/v1/pr/oee-formula"), deputy.token(), "{\"plannedTimeMode\":\"EXCLUDE_PLANNED_STOPS\","
                    + "\"qualityMode\":\"ASSUME_100\",\"capPerformanceAt100\":true}");
        }

        // Kết ca vẫn tạm tính (chưa chốt ca) và lưu kết quả
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);
        Boolean provisional = jdbc.queryForObject("SELECT is_provisional FROM pr_oee_result r JOIN pr_machine_run m "
                + "ON m.id = r.run_id WHERE m.session_id = UUID_TO_BIN(?) AND r.machine_id = UUID_TO_BIN(?)",
                Boolean.class, session, packer);
        assertThat(provisional).isTrue();
    }

    // ---------- Chốt ca (CC-11, AC-PR-006) ----------

    private JsonNode checkItem(JsonNode checks, String code, String subject) {
        for (JsonNode i : checks.get("items")) {
            if (code.equals(i.get("checkCode").asText()) && subject.equals(i.get("subjectKey").asText())) {
                return i;
            }
        }
        throw new AssertionError("Không có mục " + code + " / " + subject + " trong " + checks);
    }

    private void approve(String instanceId, TestUser user) throws Exception {
        MvcResult r = call(post("/api/v1/workflow/approvals/" + instanceId + "/approve"), user.token(), "{}");
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    }

    @Test
    void shiftCloseRequiresExplanationsThenMultiPartyApprovalAndProducesReport() throws Exception {
        jdbc.update("UPDATE sys_device SET pending_count = 0"); // thiết bị của các test khác không được chặn chốt ca
        TestUser stat = createUser(mvc, mapper, admin, "stat", "STAT_OUTPUT");
        TestUser qc = createUser(mvc, mapper, admin, "qc", "QC_INLINE");
        TestUser issuer = createUser(mvc, mapper, admin, "iss", "MATERIAL_ISSUER");
        String session = openRewoundSession();
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);

        // AC-PR-006: máy có lệnh chưa đủ lượt ghi sản lượng → chặn, có danh sách máy thiếu
        JsonNode checks = json(call(get("/api/v1/pr/sessions/" + session + "/close-checks"), leader.token(), null));
        assertThat(checks.get("closable").asBoolean()).isFalse();
        assertThat(checks.get("blocking").asInt()).isEqualTo(2);
        assertThat(checkItem(checks, "CHK_OUTPUT_COMPLETE", packer).get("level").asText()).isEqualTo("BLOCK");
        assertThat(checkItem(checks, "CHK_OUTPUT_PLAN", packer).get("level").asText()).isEqualTo("EXPLAIN");
        MvcResult early = call(post("/api/v1/pr/sessions/" + session + "/close-request"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}");
        assertThat(early.getResponse().getStatus()).isEqualTo(422);
        assertThat(early.getResponse().getContentAsString()).contains("CHK_OUTPUT_COMPLETE");

        // Giải trình: vận hành viên không có quyền; mục BLOCK cần Phó QĐ duyệt giải trình
        String explainUrl = "/api/v1/pr/sessions/" + session + "/explanations";
        assertThat(call(post(explainUrl), operator.token(), ("{\"checkCode\":\"CHK_OUTPUT_COMPLETE\","
                + "\"subjectKey\":\"%s\",\"note\":\"Ghi bù\"}").formatted(packer)).getResponse().getStatus())
                .isEqualTo(403);
        for (String machine : new String[]{packer, maker}) {
            JsonNode after = json(call(post(explainUrl), leader.token(), ("{\"checkCode\":\"CHK_OUTPUT_COMPLETE\","
                    + "\"subjectKey\":\"%s\",\"note\":\"Máy chạy thử, ghi bù cuối ca\"}").formatted(machine)));
            JsonNode item = checkItem(after, "CHK_OUTPUT_COMPLETE", machine);
            assertThat(item.get("resolved").asBoolean()).isFalse(); // chưa duyệt → vẫn chặn
            assertThat(call(post(explainUrl + "/" + item.get("explanationId").asText() + "/decision"),
                    leader.token(), "{\"approve\":true}").getResponse().getStatus()).isEqualTo(403);
            JsonNode approved = json(call(post(explainUrl + "/" + item.get("explanationId").asText()
                    + "/decision"), deputy.token(), "{\"approve\":true}"));
            assertThat(checkItem(approved, "CHK_OUTPUT_COMPLETE", machine).get("resolved").asBoolean()).isTrue();
        }
        // Lệch kế hoạch phải chọn nguyên nhân từ danh mục
        assertThat(call(post(explainUrl), leader.token(), ("{\"checkCode\":\"CHK_OUTPUT_PLAN\",\"subjectKey\":"
                + "\"%s\",\"reasonCode\":\"KHONG_CO\"}").formatted(packer)).getResponse().getStatus())
                .isEqualTo(400);
        JsonNode explained = json(call(post(explainUrl), leader.token(), ("{\"checkCode\":\"CHK_OUTPUT_PLAN\","
                + "\"subjectKey\":\"%s\",\"reasonCode\":\"MACHINE_FAULT\"}").formatted(packer)));
        assertThat(explained.get("closable").asBoolean()).isTrue();

        // Gửi chốt: cần quyền + xác nhận điện tử
        String closeUrl = "/api/v1/pr/sessions/" + session + "/close-request";
        assertThat(call(post(closeUrl), operator.token(), "{\"method\":\"PASSWORD\",\"secret\":\"x\"}")
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(post(closeUrl), leader.token(), "{\"method\":\"PASSWORD\",\"secret\":\"sai\"}")
                .getResponse().getStatus()).isEqualTo(401);
        JsonNode requested = json(call(post(closeUrl), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}"));
        String instance = requested.get("approvalInstanceId").asText();
        assertThat(json(call(get("/api/v1/pr/sessions/" + session), leader.token(), null)).get("status").asText())
                .isEqualTo("CLOSING");
        assertThat(call(post(closeUrl), leader.token(), "{\"method\":\"PASSWORD\",\"secret\":\""
                + USER_PASSWORD + "\"}").getResponse().getStatus()).isEqualTo(409);

        // Bốn bên bắt buộc xác nhận → CLOSED
        // Ca không phát sinh cấp phát NVL → bên Cấp phát NVL tự bỏ qua, chỉ còn Thống kê + KSCL
        assertThat(requested.get("skippedParties").toString()).contains("Cấp phát NVL");
        approve(instance, stat);
        assertThat(json(call(get("/api/v1/pr/sessions/" + session), leader.token(), null)).get("status").asText())
                .isEqualTo("CLOSING");
        approve(instance, qc);
        JsonNode closed = json(call(get("/api/v1/pr/sessions/" + session), leader.token(), null));
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");

        // Báo cáo ca + OEE chính thức + khóa ghi nhận
        JsonNode report = json(call(get("/api/v1/pr/sessions/" + session + "/report"), leader.token(), null));
        assertThat(report.get("sessionId").asText()).isEqualTo(session);
        assertThat(report.get("oee")).isNotEmpty();
        assertThat(report.get("closeChecks")).isNotEmpty();
        Boolean provisional = jdbc.queryForObject("SELECT is_provisional FROM pr_oee_result WHERE run_id = ("
                + "SELECT id FROM pr_machine_run WHERE session_id = UUID_TO_BIN(?) AND machine_id = UUID_TO_BIN(?))",
                Boolean.class, session, packer);
        assertThat(provisional).isFalse();
        assertThat(output(packer, session, "\"qty\":1", operator.token()).getResponse().getStatus())
                .isEqualTo(423);

        // Mở lại ca chỉ qua điều chỉnh được Phó QĐ/quản trị duyệt
        MvcResult adj = call(post("/api/v1/workflow/adjustments"), deputy.token(), ("{\"module\":\"pr\","
                + "\"entityType\":\"ShiftSession\",\"entityId\":\"%s\",\"fieldPath\":\"status\","
                + "\"newValue\":\"CLOSING\",\"reason\":\"Sửa sản lượng ghi nhầm\"}").formatted(session));
        assertThat(adj.getResponse().getStatus()).as(adj.getResponse().getContentAsString()).isEqualTo(201);
        TestUser deputy2 = createUser(mvc, mapper, admin, "dep2", "DEPUTY_DIRECTOR");
        MvcResult approvedAdj = call(post("/api/v1/workflow/adjustments/" + json(adj).get("id").asText()
                + "/approve"), deputy2.token(), "{}");
        assertThat(approvedAdj.getResponse().getStatus()).as(approvedAdj.getResponse().getContentAsString())
                .isEqualTo(200);
        assertThat(json(call(get("/api/v1/pr/sessions/" + session), leader.token(), null)).get("status").asText())
                .isEqualTo("CLOSING");
    }

    @Test
    void closedShiftIsFinalizedAfterConfiguredDays() throws Exception {
        String session = openRewoundSession();
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);
        jdbc.update("UPDATE pr_shift_session SET status = 'CLOSED', closed_at = ? WHERE id = UUID_TO_BIN(?)",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), session);

        finalizer.finalizeDue(Instant.now());
        assertThat(jdbc.queryForObject("SELECT status FROM pr_shift_session WHERE id = UUID_TO_BIN(?)",
                String.class, session)).isEqualTo("CLOSED"); // mới 1 giờ, chưa tới 7 ngày

        assertThat(finalizer.finalizeDue(Instant.now().plusSeconds(8 * 86400L))).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM pr_shift_session WHERE id = UUID_TO_BIN(?)",
                String.class, session)).isEqualTo("FINALIZED");
        // Đã FINALIZED: không còn mở lại được bằng điều chỉnh cấp ca
        MvcResult adj = call(post("/api/v1/workflow/adjustments"), deputy.token(), ("{\"module\":\"pr\","
                + "\"entityType\":\"ShiftSession\",\"entityId\":\"%s\",\"fieldPath\":\"status\","
                + "\"newValue\":\"CLOSING\",\"reason\":\"Thử mở lại\"}").formatted(session));
        TestUser deputy2 = createUser(mvc, mapper, admin, "dep2", "DEPUTY_DIRECTOR");
        assertThat(call(post("/api/v1/workflow/adjustments/" + json(adj).get("id").asText() + "/approve"),
                deputy2.token(), "{}").getResponse().getStatus()).isEqualTo(422);
    }

    // ---------- Contract cho RP (E15, ADR-0001 D3) ----------

    private long outboxCount(String type, String sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox_message WHERE type = ? AND "
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sessionId')) = ?", Long.class, type, sessionId);
    }

    /** Ca có đổi mác, dừng kế hoạch/ngoài kế hoạch, đã kết ca: dựng dữ liệu cho các test contract RP. */
    private String endedSessionWithTwoSegments() throws Exception {
        assertThat(call(put("/api/v1/md/machines/" + packer), admin,
                "{\"theoreticalRate\":400,\"rateUnit\":\"PACKS_PER_MIN\"}").getResponse().getStatus())
                .isEqualTo(200);
        String session = openRewoundSession();
        Instant t0 = Instant.now().minusSeconds(5 * 3600);
        assertThat(stop(packer, session, "E", t0, t0.plusSeconds(30 * 60), operator.token()).getResponse()
                .getStatus()).isEqualTo(201);
        assertThat(stop(packer, session, "C", t0.plusSeconds(40 * 60), t0.plusSeconds(80 * 60), operator.token())
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(output(packer, session, "\"qty\":150000,\"periodTo\":\"%s\"".formatted(Instant.now()
                .minusSeconds(4 * 3600)), operator.token()).getResponse().getStatus()).isEqualTo(201);
        MvcResult change = call(post("/api/v1/pr/machines/" + packer + "/product-change"), operator.token(),
                "{\"sessionId\":\"%s\",\"toProductId\":\"%s\",\"changedAt\":\"%s\"}".formatted(session,
                        productB, Instant.now().minusSeconds(3 * 3600 + 1800)));
        assertThat(change.getResponse().getStatus()).as(change.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(output(packer, session, "\"qty\":1000,\"periodTo\":\"%s\"".formatted(Instant.now()
                .minusSeconds(3 * 3600)), operator.token()).getResponse().getStatus()).isEqualTo(201);
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);
        return session;
    }

    @Test
    void shiftFactsExposeRunSegmentsAndStopsConsistentWithOee() throws Exception {
        String session = endedSessionWithTwoSegments();

        ShiftFacts facts = production.shiftFacts(UUID.fromString(session)).orElseThrow();
        assertThat(facts.sessionId()).isEqualTo(UUID.fromString(session));
        assertThat(facts.businessDate()).isEqualTo(date);
        assertThat(facts.shiftCode()).isEqualTo(shift);
        assertThat(facts.status()).isEqualTo("CLOSING");

        // Chỉ các máy có kết quả OEE (loại NO_ORDER), khớp pr_oee_result
        java.util.Set<UUID> oeeMachines = new java.util.HashSet<>(jdbc.query("SELECT BIN_TO_UUID(machine_id) FROM "
                + "pr_oee_result WHERE session_id = UUID_TO_BIN(?)", (rs, i) -> UUID.fromString(rs.getString(1)),
                session));
        assertThat(facts.runs()).extracting(RunFact::machineId).containsExactlyInAnyOrderElementsOf(oeeMachines);
        assertThat(facts.runs()).extracting(RunFact::machineId).doesNotContain(UUID.fromString(idleMachine));

        RunFact r = facts.runs().stream().filter(x -> x.machineId().equals(UUID.fromString(packer))).findFirst()
                .orElseThrow();
        assertThat(r.unit()).isEqualTo("PACKS");
        assertThat(r.plannedPacks()).isEqualTo(100_000L);
        assertThat(r.totalQty()).isEqualTo(151_000L);

        // Số liệu OEE lấy đúng từ pr_oee_result (không tính lại ở contract)
        var oee = jdbc.queryForMap("SELECT planned_minutes, run_minutes, good_qty, theoretical_qty, availability, "
                + "performance, quality, oee, is_provisional, BIN_TO_UUID(formula_id) AS formula FROM pr_oee_result "
                + "WHERE session_id = UUID_TO_BIN(?) AND machine_id = UUID_TO_BIN(?)", session, packer);
        assertThat(r.plannedMinutes()).isEqualTo(((Number) oee.get("planned_minutes")).intValue());
        assertThat(r.runMinutes()).isEqualTo(((Number) oee.get("run_minutes")).intValue());
        assertThat(r.goodQty()).isEqualTo(((Number) oee.get("good_qty")).longValue());
        assertThat(r.theoreticalQty()).isEqualByComparingTo((java.math.BigDecimal) oee.get("theoretical_qty"));
        assertThat(r.availability()).isEqualByComparingTo((java.math.BigDecimal) oee.get("availability"));
        assertThat(r.performance()).isEqualByComparingTo((java.math.BigDecimal) oee.get("performance"));
        assertThat(r.quality()).isEqualByComparingTo((java.math.BigDecimal) oee.get("quality"));
        assertThat(r.oee()).isEqualByComparingTo((java.math.BigDecimal) oee.get("oee"));
        assertThat(r.provisional()).isEqualTo(Boolean.TRUE.equals(oee.get("is_provisional")));
        assertThat(r.formulaId()).isEqualTo(UUID.fromString((String) oee.get("formula")));

        // Đổi mác: hai segment, tổng khớp sản lượng máy (không nhân đôi phút/sản lượng)
        assertThat(r.segments()).hasSize(2);
        assertThat(r.segments().get(0).segmentNo()).isEqualTo(1);
        assertThat(r.segments().get(0).productId()).isEqualTo(UUID.fromString(productA));
        assertThat(r.segments().get(0).qty()).isEqualTo(150_000L);
        assertThat(r.segments().get(1).segmentNo()).isEqualTo(2);
        assertThat(r.segments().get(1).productId()).isEqualTo(UUID.fromString(productB));
        assertThat(r.segments().get(1).qty()).isEqualTo(1_000L);
        assertThat(r.segments().stream().mapToLong(ProductionQueries.SegmentFact::qty).sum())
                .isEqualTo(r.totalQty());

        // Dừng máy theo lý do: E (kế hoạch) 30', C (ngoài kế hoạch) 40'; tổng khớp phút dừng của máy
        assertThat(r.stops()).hasSize(2);
        var e = r.stops().stream().filter(x -> x.reasonCode().equals("E")).findFirst().orElseThrow();
        var c = r.stops().stream().filter(x -> x.reasonCode().equals("C")).findFirst().orElseThrow();
        assertThat(e.planned()).isTrue();
        assertThat(e.durationSeconds()).isEqualTo(30 * 60L);
        assertThat(e.events()).isEqualTo(1);
        assertThat(c.planned()).isFalse();
        assertThat(c.durationSeconds()).isEqualTo(40 * 60L);
        assertThat(r.plannedStopMinutes()).isEqualTo(30);
        assertThat(r.unplannedStopMinutes()).isEqualTo(40);
    }

    @Test
    void shiftFactsAreEmptyForUnknownSession() {
        assertThat(production.shiftFacts(UUID.randomUUID())).isEmpty();
    }

    @Test
    void sessionsBetweenReturnsOnlySessionsInRangeOrderedByDateAndShift() throws Exception {
        String session = openSession();
        assertThat(production.sessionsBetween(date, date)).extracting(ProductionQueries.SessionRef::id)
                .containsExactly(UUID.fromString(session));
        assertThat(production.sessionsBetween(date.plusDays(1), date.plusDays(2))).isEmpty();
        assertThat(production.sessionsBetween(date.minusDays(1), date.plusDays(1)))
                .extracting(ProductionQueries.SessionRef::id).contains(UUID.fromString(session));
        assertThatThrownBy(() -> production.sessionsBetween(date.plusDays(1), date))
                .isInstanceOf(vn.pxvb.common.error.BusinessException.class);
    }

    @Test
    void outputAndOeeComputationPublishOutboxEventsKeyedBySession() throws Exception {
        String session = openRewoundSession();
        assertThat(outboxCount("pr.output.recorded", session)).isZero();
        assertThat(output(packer, session, "\"qty\":500,\"periodTo\":\"%s\"".formatted(Instant.now()
                .minusSeconds(4 * 3600)), operator.token()).getResponse().getStatus()).isEqualTo(201);
        assertThat(outboxCount("pr.output.recorded", session)).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT CAST(payload AS CHAR) FROM sys_outbox_message WHERE type = "
                + "'pr.output.recorded' AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sessionId')) = ?", String.class,
                session);
        assertThat(mapper.readTree(payload).get("businessDate").asText()).isEqualTo(date.toString());
        assertThat(mapper.readTree(payload).get("machineId").asText()).isEqualTo(packer);

        // Tính OEE (kết ca) phát pr.oee.computed để RP cập nhật fact
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);
        long computed = outboxCount("pr.oee.computed", session);
        assertThat(computed).isGreaterThanOrEqualTo(1);

        // Tính lại khi số liệu không đổi (vd UI mở lại màn OEE) không phát thêm sự kiện
        assertThat(call(get("/api/v1/pr/sessions/" + session + "/oee"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);
        assertThat(outboxCount("pr.oee.computed", session)).isEqualTo(computed);
    }

    /**
     * Tương đương với báo cáo ca đã chốt (E14): các con số RP sẽ dựng từ {@code shiftFacts} phải khớp bản chụp
     * {@code pr_shift_report} tại thời điểm chốt, nếu không RP và báo cáo ca sẽ âm thầm bất đồng.
     */
    @Test
    void shiftFactsMatchTheShiftReportSnapshotTakenAtClose() throws Exception {
        jdbc.update("UPDATE sys_device SET pending_count = 0");
        TestUser stat = createUser(mvc, mapper, admin, "stat", "STAT_OUTPUT");
        TestUser qc = createUser(mvc, mapper, admin, "qc", "QC_INLINE");
        assertThat(call(put("/api/v1/md/machines/" + packer), admin,
                "{\"theoreticalRate\":400,\"rateUnit\":\"PACKS_PER_MIN\"}").getResponse().getStatus())
                .isEqualTo(200);
        String session = openRewoundSession();
        Instant t0 = Instant.now().minusSeconds(5 * 3600);
        assertThat(stop(packer, session, "E", t0, t0.plusSeconds(30 * 60), operator.token()).getResponse()
                .getStatus()).isEqualTo(201);
        assertThat(stop(packer, session, "G", t0.plusSeconds(40 * 60), t0.plusSeconds(55 * 60), operator.token())
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(output(packer, session, "\"qty\":150000,\"periodTo\":\"%s\"".formatted(Instant.now()
                .minusSeconds(4 * 3600)), operator.token()).getResponse().getStatus()).isEqualTo(201);
        assertThat(call(post("/api/v1/pr/machines/" + packer + "/product-change"), operator.token(),
                "{\"sessionId\":\"%s\",\"toProductId\":\"%s\",\"changedAt\":\"%s\"}".formatted(session,
                        productB, Instant.now().minusSeconds(3 * 3600 + 1800))).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(output(packer, session, "\"qty\":1000,\"periodTo\":\"%s\"".formatted(Instant.now()
                .minusSeconds(3 * 3600)), operator.token()).getResponse().getStatus()).isEqualTo(201);
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);

        String explainUrl = "/api/v1/pr/sessions/" + session + "/explanations";
        for (String machine : new String[]{packer, maker}) {
            JsonNode after = json(call(post(explainUrl), leader.token(), ("{\"checkCode\":\"CHK_OUTPUT_COMPLETE\","
                    + "\"subjectKey\":\"%s\",\"note\":\"Ghi bù cuối ca\"}").formatted(machine)));
            call(post(explainUrl + "/" + checkItem(after, "CHK_OUTPUT_COMPLETE", machine).get("explanationId")
                    .asText() + "/decision"), deputy.token(), "{\"approve\":true}");
        }
        JsonNode explained = json(call(post(explainUrl), leader.token(), ("{\"checkCode\":\"CHK_OUTPUT_PLAN\","
                + "\"subjectKey\":\"%s\",\"reasonCode\":\"MACHINE_FAULT\"}").formatted(packer)));
        assertThat(explained.get("closable").asBoolean()).as(explained.toString()).isTrue();
        JsonNode requested = json(call(post("/api/v1/pr/sessions/" + session + "/close-request"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}"));
        String instance = requested.get("approvalInstanceId").asText();
        approve(instance, stat);
        approve(instance, qc);
        assertThat(json(call(get("/api/v1/pr/sessions/" + session), leader.token(), null)).get("status").asText())
                .isEqualTo("CLOSED");

        JsonNode report = json(call(get("/api/v1/pr/sessions/" + session + "/report"), leader.token(), null));
        ShiftFacts facts = production.shiftFacts(UUID.fromString(session)).orElseThrow();
        assertThat(facts.status()).isEqualTo("CLOSED");

        // OEE: cùng tập máy và cùng số liệu từng máy
        assertThat(facts.runs()).hasSize(report.get("oee").size());
        for (JsonNode o : report.get("oee")) {
            RunFact f = facts.runs().stream().filter(x -> x.machineId().toString().equals(o.get("machineId")
                    .asText())).findFirst().orElseThrow();
            assertThat(f.unit()).isEqualTo(o.get("unit").asText());
            assertThat(f.plannedMinutes()).isEqualTo(o.get("plannedMinutes").asInt());
            assertThat(f.runMinutes()).isEqualTo(o.get("runMinutes").asInt());
            assertThat(f.totalQty()).isEqualTo(o.get("totalQty").asLong());
            assertThat(f.goodQty()).isEqualTo(o.get("goodQty").asLong());
            assertThat(f.provisional()).isEqualTo(o.get("isProvisional").asBoolean());
            for (var pair : new Object[][]{{"availability", f.availability()}, {"performance", f.performance()},
                    {"quality", f.quality()}, {"oee", f.oee()}}) {
                JsonNode expected = o.get((String) pair[0]);
                if (expected == null || expected.isNull()) {
                    assertThat(pair[1]).as((String) pair[0]).isNull();
                } else {
                    assertThat((java.math.BigDecimal) pair[1]).as((String) pair[0])
                            .isEqualByComparingTo(expected.decimalValue());
                }
            }
        }

        // Sản lượng theo mác: tổng các segment theo (mác, đơn vị) khớp outputByProduct của báo cáo
        java.util.Map<String, Long> factQty = new java.util.TreeMap<>();
        for (RunFact f : facts.runs()) {
            for (ProductionQueries.SegmentFact sg : f.segments()) {
                factQty.merge(sg.productId() + "|" + f.unit(), sg.qty(), Long::sum);
            }
        }
        java.util.Map<String, Long> reportQty = new java.util.TreeMap<>();
        for (JsonNode p : report.get("outputByProduct")) {
            reportQty.merge(p.get("productId").asText() + "|" + p.get("unit").asText(), p.get("qty").asLong(),
                    Long::sum);
        }
        assertThat(factQty).isEqualTo(reportQty);
        assertThat(factQty).hasSize(2);

        // Dừng máy theo lý do: phút = tổng giây / 60 như báo cáo; số sự kiện khớp
        java.util.Map<String, long[]> factStops = new java.util.TreeMap<>();
        for (RunFact f : facts.runs()) {
            for (ProductionQueries.StopFact st : f.stops()) {
                long[] acc = factStops.computeIfAbsent(st.reasonCode(), k -> new long[2]);
                acc[0] += st.durationSeconds();
                acc[1] += st.events();
            }
        }
        assertThat(factStops.keySet()).containsExactlyInAnyOrder("E", "G");
        for (JsonNode st : report.get("stopsByReason")) {
            long[] acc = factStops.get(st.get("reasonCode").asText());
            assertThat(acc[0] / 60).as(st.get("reasonCode").asText()).isEqualTo(st.get("minutes").asLong());
            assertThat(acc[1]).isEqualTo(st.get("events").asLong());
        }
        assertThat(report.get("stopsByReason")).hasSize(factStops.size());
    }

    private JsonNode oeeRow(String session, String machineId) throws Exception {
        JsonNode rows = json(call(get("/api/v1/pr/sessions/" + session + "/oee"), leader.token(), null));
        for (JsonNode r : rows) {
            if (machineId.equals(r.get("machineId").asText())) {
                return r;
            }
        }
        throw new AssertionError("Không có OEE cho máy " + machineId);
    }
}
