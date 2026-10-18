package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
import vn.pxvb.material.application.ConsumptionService;

/** MT (E13): phiếu cấp phát tự sinh, cấp/nhận, tiêu hao, kiểm đếm, chốt ca (AC-MT-001..005). */
@AutoConfigureMockMvc
class MaterialIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ConsumptionService consumption;

    private String admin;
    private TestUser planner;
    private TestUser clerk;
    private TestUser deputy;
    private TestUser leader;
    private TestUser stat;
    private TestUser issuer;
    private TestUser operator;

    private String maker;
    private String packer;
    private String product;
    private String packingMat;
    private String fiberMat;
    private LocalDate date;
    private String orderId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        planner = createUser(mvc, mapper, admin, "plan", "PLANNER");
        clerk = createUser(mvc, mapper, admin, "clerk", "NORM_CLERK");
        deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");
        stat = createUser(mvc, mapper, admin, "statm", "STAT_MATERIAL");
        issuer = createUser(mvc, mapper, admin, "iss", "MATERIAL_ISSUER");
        operator = createUser(mvc, mapper, admin, "op", "OPERATOR");

        date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1 + ThreadLocalRandom.current().nextInt(4000));
        maker = machine("MAKER");
        packer = machine("PACKER");
        product = createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP\",\"productType\":"
                + "\"DOMESTIC\",\"rodsPerPack\":20}").formatted(unique("P"))).get("id").asText();
        call(post("/api/v1/md/product-allowed-machines"), planner.token(),
                "{\"productId\":\"%s\",\"machineId\":\"%s\"}".formatted(product, packer));
        packingMat = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"Giay bao\",\"uom\":\"kg\","
                + "\"categoryCode\":\"PACKING\"}").formatted(unique("V"))).get("id").asText();
        fiberMat = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"Soi\",\"uom\":\"kg\","
                + "\"categoryCode\":\"FIBER\"}").formatted(unique("S"))).get("id").asText();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String norm = json(call(post("/api/v1/md/bom-norms"), clerk.token(), ("{\"productId\":\"%s\","
                + "\"effectiveFrom\":\"%s\",\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":2.5},"
                + "{\"materialId\":\"%s\",\"qtyPer1000Packs\":10}]}").formatted(product, today, packingMat,
                fiberMat))).get("id").asText();
        call(post("/api/v1/md/bom-norms/" + norm + "/submit"), clerk.token(), null);
        call(post("/api/v1/md/bom-norms/" + norm + "/approve"), deputy.token(), null);

        StringBuilder lines = new StringBuilder();
        for (String shift : List.of("A", "B", "C")) {
            lines.append(lines.length() == 0 ? "" : ",").append(("{\"shiftCode\":\"%s\",\"makerMachineId\":\"%s\","
                    + "\"packerMachineId\":\"%s\"}").formatted(shift, maker, packer));
        }
        String pairing = json(call(post("/api/v1/md/machine-pairings"), planner.token(),
                ("{\"effectiveFrom\":\"%s\",\"lines\":[%s]}").formatted(today, lines))).get("id").asText();
        assertThat(call(post("/api/v1/md/machine-pairings/" + pairing + "/activate"), deputy.token(), null)
                .getResponse().getStatus()).isEqualTo(200);

        orderId = json(call(post("/api/v1/pl/orders"), planner.token(), ("{\"businessDate\":\"%s\",\"shiftCode\":"
                + "\"A\",\"makerMachineId\":\"%s\",\"packerMachineId\":\"%s\",\"productId\":\"%s\","
                + "\"plannedPacks\":20000}").formatted(date, maker, packer, product))).get("id").asText();
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

    private JsonNode slips(String machineId) throws Exception {
        return json(call(get("/api/v1/mt/slips?date=%s&shift=A&machineId=%s".formatted(date, machineId)),
                stat.token(), null));
    }

    private JsonNode slipDetail(String machineId) throws Exception {
        JsonNode list = slips(machineId);
        assertThat(list).hasSize(1);
        return json(call(get("/api/v1/mt/slips/" + list.get(0).get("id").asText()), stat.token(), null));
    }

    private void confirmSlip(String slipId) throws Exception {
        MvcResult r = call(post("/api/v1/mt/slips/" + slipId + "/confirm"), stat.token(), null);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    }

    private MvcResult issue(String lineId, String qty, String reason, String token) throws Exception {
        return call(post("/api/v1/mt/issue-records"), token, "{\"slipLineId\":\"%s\",\"issuedQty\":%s%s}"
                .formatted(lineId, qty, reason == null ? "" : ",\"reasonCode\":\"" + reason + "\""));
    }

    private String openRewoundSession() throws Exception {
        MvcResult r = call(post("/api/v1/pr/sessions"), leader.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"A\"}".formatted(date));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        String id = json(r).get("id").asText();
        jdbc.update("UPDATE pr_shift_session SET actual_start = ? WHERE id = UUID_TO_BIN(?)",
                java.sql.Timestamp.from(Instant.now().minusSeconds(6 * 3600)), id);
        return id;
    }

    // ---------- Phiếu cấp phát ----------

    @Test
    void acMt001_slipsAreGeneratedWhenOrderIsConfirmed() throws Exception {
        JsonNode packing = slipDetail(packer);
        assertThat(packing.get("slipType").asText()).isEqualTo("PACKING");
        assertThat(packing.get("status").asText()).isEqualTo("AUTO");
        assertThat(packing.get("code").asText()).startsWith("PCP-" + date.toString().replace("-", "") + "-A-PACKING");
        assertThat(packing.get("lines")).hasSize(4);
        for (JsonNode line : packing.get("lines")) {
            assertThat(line.get("plannedQty").asDouble()).isEqualTo(12.5); // 2,5 kg/1.000 bao × 20.000 / 4 khung
            assertThat(line.get("materialId").asText()).isEqualTo(packingMat);
        }
        // Sợi (nhóm FIBER) → phiếu của máy vấn
        JsonNode fiber = slipDetail(maker);
        assertThat(fiber.get("slipType").asText()).isEqualTo("FIBER");
        assertThat(fiber.get("lines").get(0).get("plannedQty").asDouble()).isEqualTo(50.0);
    }

    @Test
    void slipReviewConfirmAndLock() throws Exception {
        JsonNode slip = slipDetail(packer);
        String slipId = slip.get("id").asText();
        String lineId = slip.get("lines").get(0).get("id").asText();

        // Chưa xác nhận → chưa cấp phát được
        assertThat(issue(lineId, "12.5", null, issuer.token()).getResponse().getStatus()).isEqualTo(422);
        // Chỉnh dòng cần lý do + quyền STAT_MATERIAL
        String body = "{\"plannedQty\":13,\"reason\":\"Bổ sung dự phòng\"}";
        assertThat(call(put("/api/v1/mt/slips/" + slipId + "/lines/" + lineId), leader.token(), body)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(put("/api/v1/mt/slips/" + slipId + "/lines/" + lineId), stat.token(),
                "{\"plannedQty\":13}").getResponse().getStatus()).isEqualTo(400);
        assertThat(call(put("/api/v1/mt/slips/" + slipId + "/lines/" + lineId), stat.token(), body)
                .getResponse().getStatus()).isEqualTo(200);

        confirmSlip(slipId);
        assertThat(call(put("/api/v1/mt/slips/" + slipId + "/lines/" + lineId), stat.token(), body)
                .getResponse().getStatus()).isEqualTo(423); // BR-MT-009
        assertThat(call(post("/api/v1/mt/slips/" + slipId + "/confirm"), stat.token(), null).getResponse()
                .getStatus()).isEqualTo(422);
        assertThat(call(post("/api/v1/mt/orders/" + orderId + "/regenerate-slips"), stat.token(), null)
                .getResponse().getStatus()).isEqualTo(423);
    }

    // ---------- Cấp phát & nhận (AC-MT-002, AC-MT-003) ----------

    @Test
    void acMt002AndAcMt003_overSlipNeedsReasonAndReceiptMismatchIsDisputed() throws Exception {
        JsonNode slip = slipDetail(packer);
        confirmSlip(slip.get("id").asText());
        String lineId = slip.get("lines").get(0).get("id").asText();

        // 14 kg so với 12,5 kg (+12% > 10%) → bắt buộc lý do từ danh mục
        assertThat(issue(lineId, "14", null, issuer.token()).getResponse().getStatus()).isEqualTo(400);
        assertThat(issue(lineId, "14", "KHONG_CO", issuer.token()).getResponse().getStatus()).isEqualTo(400);
        assertThat(issue(lineId, "14", "WASTE", operator.token()).getResponse().getStatus()).isEqualTo(403);
        MvcResult issued = issue(lineId, "14", "EXTRA_REQUEST", issuer.token());
        assertThat(issued.getResponse().getStatus()).as(issued.getResponse().getContentAsString()).isEqualTo(201);
        String issueId = json(issued).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_alert WHERE rule_code = 'MT_ISSUE_OVER_SLIP' "
                + "AND subject_id = ?", Integer.class, lineId)).isEqualTo(1);
        assertThat(slipDetail(packer).get("status").asText()).isEqualTo("ISSUING");

        // Cấp ngoài phiếu phải có lý do
        assertThat(call(post("/api/v1/mt/issue-records"), issuer.token(), ("{\"businessDate\":\"%s\",\"shiftCode\":"
                + "\"A\",\"slotNo\":1,\"machineId\":\"%s\",\"materialId\":\"%s\",\"issuedQty\":3}").formatted(date,
                packer, packingMat)).getResponse().getStatus()).isEqualTo(400);

        // Nhận 13 khi giao 14 → DISPUTED (cần ghi chú), STAT_MATERIAL xử lý
        String receiptUrl = "/api/v1/mt/receipts";
        assertThat(call(post(receiptUrl), operator.token(), "{\"issueRecordId\":\"%s\",\"receivedQty\":13}"
                .formatted(issueId)).getResponse().getStatus()).isEqualTo(400);
        JsonNode receipt = json(call(post(receiptUrl), operator.token(), ("{\"issueRecordId\":\"%s\","
                + "\"receivedQty\":13,\"note\":\"Thiếu 1 kg khi nhận\"}").formatted(issueId)));
        assertThat(receipt.get("status").asText()).isEqualTo("DISPUTED");
        assertThat(call(post(receiptUrl), operator.token(), "{\"issueRecordId\":\"%s\",\"receivedQty\":14}"
                .formatted(issueId)).getResponse().getStatus()).isEqualTo(409);
        String receiptId = receipt.get("id").asText();
        assertThat(call(post(receiptUrl + "/" + receiptId + "/resolve"), leader.token(), "{\"note\":\"ok\"}")
                .getResponse().getStatus()).isEqualTo(403);
        JsonNode resolved = json(call(post(receiptUrl + "/" + receiptId + "/resolve"), stat.token(),
                "{\"note\":\"Đã cân lại, chốt 13 kg\"}"));
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
    }

    // ---------- Tiêu hao & kiểm đếm (AC-MT-004, AC-MT-005) ----------

    @Test
    void acMt004_wasteAboveThresholdRaisesAlertAndNeedsExplanation() throws Exception {
        JsonNode slip = slipDetail(packer);
        confirmSlip(slip.get("id").asText());
        String session = openRewoundSession();
        assertThat(call(post("/api/v1/pr/machines/" + packer + "/output"), operator.token(), ("{\"sessionId\":\"%s\","
                + "\"qty\":20000,\"periodTo\":\"%s\"}").formatted(session, Instant.now().minusSeconds(4 * 3600)))
                .getResponse().getStatus()).isEqualTo(201);
        // 4 khung × 13,125 kg = 52,5 kg (cấp +5% so với 50 kg định mức, mỗi khung lệch 5% < 10% nên không cần lý do)
        for (JsonNode line : slip.get("lines")) {
            MvcResult r = issue(line.get("id").asText(), "13.125", null, issuer.token());
            assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        }
        // Kiểm đếm cuối ca = 0 → thực tế 52,5 kg so với lý thuyết 50 kg
        assertThat(call(post("/api/v1/mt/stock-counts"), operator.token(), ("{\"businessDate\":\"%s\",\"shiftCode\":"
                + "\"A\",\"machineId\":\"%s\",\"materialId\":\"%s\",\"kind\":\"CLOSING\",\"qty\":0}").formatted(date,
                packer, packingMat)).getResponse().getStatus()).isEqualTo(201);

        JsonNode rows = json(call(get("/api/v1/mt/sessions/" + session + "/consumption"), leader.token(), null));
        JsonNode row = null;
        for (JsonNode r : rows) {
            if (packer.equals(r.get("machineId").asText()) && packingMat.equals(r.get("materialId").asText())) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("theoreticalConsumed").asDouble()).isEqualTo(50.0);
        assertThat(row.get("actualConsumed").asDouble()).isEqualTo(52.5);
        assertThat(row.get("wastePct").asDouble()).isEqualTo(5.0);
        assertThat(row.get("flag").asText()).isEqualTo("WARN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_alert WHERE rule_code = 'MT_WASTE_HIGH' AND "
                + "subject_id = ?", Integer.class, packer + ":" + packingMat)).isEqualTo(1);

        // Kết ca → chặn chốt tiêu hao cho tới khi giải trình; CHK_MATERIAL yêu cầu giải trình
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);
        JsonNode checks = json(call(get("/api/v1/pr/sessions/" + session + "/close-checks"), leader.token(), null));
        boolean hasMaterialExplain = false;
        for (JsonNode i : checks.get("items")) {
            hasMaterialExplain |= "CHK_MATERIAL".equals(i.get("checkCode").asText())
                    && "EXPLAIN".equals(i.get("level").asText());
        }
        assertThat(hasMaterialExplain).isTrue();

        MvcResult early = call(post("/api/v1/mt/sessions/" + session + "/consumption/confirm"), stat.token(), null);
        assertThat(early.getResponse().getStatus()).isEqualTo(422);
        String summaryId = row.get("id").asText();
        assertThat(call(post("/api/v1/mt/consumption/" + summaryId + "/explain"), stat.token(),
                "{\"explanation\":\"Hủy 2,5 kg giấy rách khi đổi cuộn\"}").getResponse().getStatus()).isEqualTo(200);
        // Dòng hao hụt FIBER (không cấp) cũng có thể xuất hiện: chỉ cần mọi dòng cờ ≠ OK đều đã giải trình
        for (JsonNode r : json(call(get("/api/v1/mt/sessions/" + session + "/consumption"), stat.token(), null))) {
            if (!"OK".equals(r.get("flag").asText()) && r.get("explanation").isNull()) {
                call(post("/api/v1/mt/consumption/" + r.get("id").asText() + "/explain"), stat.token(),
                        "{\"explanation\":\"Ngoài định mức\"}");
            }
        }
        MvcResult confirmed = call(post("/api/v1/mt/sessions/" + session + "/consumption/confirm"), stat.token(), null);
        assertThat(confirmed.getResponse().getStatus()).as(confirmed.getResponse().getContentAsString())
                .isEqualTo(200);
        assertThat(json(call(get("/api/v1/mt/reports/shift/" + session), leader.token(), null)).get("lines"))
                .isNotEmpty();
    }

    @Test
    void acMt005_openingStockFollowsPreviousClosingAndOverrideNeedsReason() throws Exception {
        // Tồn cuối ca A = 8 kg
        assertThat(call(post("/api/v1/mt/stock-counts"), operator.token(), ("{\"businessDate\":\"%s\",\"shiftCode\":"
                + "\"A\",\"machineId\":\"%s\",\"materialId\":\"%s\",\"kind\":\"CLOSING\",\"qty\":8}").formatted(date,
                packer, packingMat)).getResponse().getStatus()).isEqualTo(201);
        assertThat(consumption.autoOpening(UUID.fromString(packer), UUID.fromString(packingMat), date, "B"))
                .isEqualByComparingTo("8");

        String base = "{\"businessDate\":\"%s\",\"shiftCode\":\"B\",\"machineId\":\"%s\",\"materialId\":\"%s\","
                + "\"kind\":\"OPENING\",\"qty\":%s%s}";
        // Sửa tồn đầu khác 8 phải kèm lý do
        assertThat(call(post("/api/v1/mt/stock-counts"), operator.token(), base.formatted(date, packer, packingMat,
                "5", "")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(post("/api/v1/mt/stock-counts"), operator.token(), base.formatted(date, packer, packingMat,
                "5", ",\"note\":\"Đếm lại đầu ca\"")).getResponse().getStatus()).isEqualTo(201);
        assertThat(call(post("/api/v1/mt/stock-counts"), operator.token(), base.formatted(date, packer, packingMat,
                "8", "")).getResponse().getStatus()).isEqualTo(201);
    }

    // ---------- Chốt ca: tranh chấp NVL giải trình được (L49) ----------

    @Test
    void disputedReceiptCanBeExplainedAndApprovedAtShiftClose() throws Exception {
        JsonNode slip = slipDetail(packer);
        confirmSlip(slip.get("id").asText());
        String issueId = json(issue(slip.get("lines").get(0).get("id").asText(), "12.5", null, issuer.token()))
                .get("id").asText();
        String receiptId = json(call(post("/api/v1/mt/receipts"), operator.token(), ("{\"issueRecordId\":\"%s\","
                + "\"receivedQty\":12,\"note\":\"Thiếu 0,5 kg\"}").formatted(issueId))).get("id").asText();
        String session = openRewoundSession();
        assertThat(call(post("/api/v1/pr/sessions/" + session + "/end"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);

        String subject = "dispute:" + receiptId;
        JsonNode item = null;
        for (JsonNode i : json(call(get("/api/v1/pr/sessions/" + session + "/close-checks"), leader.token(), null))
                .get("items")) {
            if ("CHK_MATERIAL".equals(i.get("checkCode").asText()) && subject.equals(i.get("subjectKey").asText())) {
                item = i;
            }
        }
        assertThat(item).isNotNull();
        assertThat(item.get("level").asText()).isEqualTo("BLOCK");
        assertThat(item.get("approvable").asBoolean()).isTrue();

        String explainUrl = "/api/v1/pr/sessions/" + session + "/explanations";
        JsonNode explained = json(call(post(explainUrl), leader.token(), ("{\"checkCode\":\"CHK_MATERIAL\","
                + "\"subjectKey\":\"%s\",\"note\":\"Đã cân lại, chấp nhận chênh 0,5 kg\"}").formatted(subject)));
        String explanationId = null;
        for (JsonNode i : explained.get("items")) {
            if (subject.equals(i.get("subjectKey").asText())) {
                assertThat(i.get("resolved").asBoolean()).isFalse(); // chờ Phó QĐ duyệt
                explanationId = i.get("explanationId").asText();
            }
        }
        JsonNode approved = json(call(post(explainUrl + "/" + explanationId + "/decision"), deputy.token(),
                "{\"approve\":true}"));
        for (JsonNode i : approved.get("items")) {
            if (subject.equals(i.get("subjectKey").asText())) {
                assertThat(i.get("resolved").asBoolean()).isTrue();
            }
        }
    }

    // ---------- Khung giờ trễ (BR-MT-005) ----------

    @Test
    void pastSlotsWithoutIssueOrReceiptAreLate() throws Exception {
        JsonNode slip = slipDetail(packer);
        String slipId = slip.get("id").asText();
        confirmSlip(slipId);
        LocalDate past = LocalDate.of(2020, 1, 6);
        jdbc.update("UPDATE mt_issue_slip SET business_date = ? WHERE id = UUID_TO_BIN(?)", past, slipId);

        var late = consumption.lateLines(past, "A", Instant.now());
        assertThat(late).hasSize(4);
        assertThat(late).allSatisfy(l -> assertThat(l.get("reason")).isEqualTo("chưa cấp phát"));

        // Cấp + nhận đủ khung 1 → khung 1 không còn trễ; cấp mà chưa nhận → trễ "chưa xác nhận nhận"
        String line1 = slip.get("lines").get(0).get("id").asText();
        String issueId = json(issue(line1, "12.5", null, issuer.token())).get("id").asText();
        assertThat(consumption.lateLines(past, "A", Instant.now())).hasSize(4);
        assertThat(call(post("/api/v1/mt/receipts"), operator.token(), ("{\"issueRecordId\":\"%s\","
                + "\"receivedQty\":12.5}").formatted(issueId)).getResponse().getStatus()).isEqualTo(201);
        assertThat(consumption.lateLines(past, "A", Instant.now())).hasSize(3);
    }
}
