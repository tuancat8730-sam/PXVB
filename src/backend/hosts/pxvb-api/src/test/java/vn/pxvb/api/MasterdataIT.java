package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Masterdata (E06–E08): danh mục, ghép cặp, định mức có phiên bản, ngưỡng, khóa tham chiếu, phiên bản biểu mẫu. */
@AutoConfigureMockMvc
class MasterdataIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private String admin;
    private TestUser clerk;
    private TestUser deputy;
    private TestUser planner;
    private TestUser qaHead;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        clerk = createUser(mvc, mapper, admin, "clerk", "NORM_CLERK");
        deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        planner = createUser(mvc, mapper, admin, "plan", "PLANNER");
        qaHead = createUser(mvc, mapper, admin, "qa", "QA_HEAD");
    }

    // ---------- tiện ích ----------

    private MvcResult call(MockHttpServletRequestBuilder builder, String token, String body) throws Exception {
        builder.header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder).andReturn();
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

    private String machine(String type, String rate) throws Exception {
        String code = unique("M");
        return createOk("machines", admin, ("{\"code\":\"%s\",\"name\":\"May %s\",\"machineType\":\"%s\","
                + "\"model\":\"Protos\"%s}").formatted(code, code, type,
                rate == null ? "" : ",\"theoreticalRate\":" + rate)).get("id").asText();
    }

    // ---------- Danh mục chung ----------

    @Test
    void seededReferenceDataIsAvailable() throws Exception {
        assertThat(json(call(get("/api/v1/md/stop-reasons?pageSize=50"), admin, null)).get("total").asInt())
                .isGreaterThanOrEqualTo(9);
        assertThat(json(call(get("/api/v1/md/shift-defs"), admin, null)).get("total").asInt())
                .isGreaterThanOrEqualTo(3);
        assertThat(json(call(get("/api/v1/md/issue-slots?shiftCode=A"), admin, null)).get("total").asInt())
                .isEqualTo(4);
        JsonNode slotShare = json(call(get("/api/v1/md/issue-slots?shiftCode=A&pageSize=10"), admin, null));
        double sum = 0;
        for (JsonNode s : slotShare.get("content")) {
            sum += s.get("sharePct").asDouble();
        }
        assertThat(sum).isEqualTo(100.0);
    }

    @Test
    void machineCrudValidationDuplicateAndOptimisticLock() throws Exception {
        String code = unique("M");
        String body = "{\"code\":\"%s\",\"name\":\"May vấn\",\"machineType\":\"MAKER\",\"theoreticalRate\":10}"
                .formatted(code);
        JsonNode created = createOk("machines", admin, body);
        String id = created.get("id").asText();
        long version = created.get("rowVersion").asLong();
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");

        MvcResult dup = call(post("/api/v1/md/machines"), admin, body);
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(dup).get("code").asText()).isEqualTo("PXVB-MD-1005");

        MvcResult invalid = call(post("/api/v1/md/machines"), admin,
                "{\"code\":\"%s\",\"name\":\"X\",\"machineType\":\"ROBOT\",\"theoreticalRate\":-1}"
                        .formatted(unique("M")));
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(invalid).get("errors").has("machineType")).isTrue();
        assertThat(json(invalid).get("errors").has("theoreticalRate")).isTrue();

        MvcResult updated = call(put("/api/v1/md/machines/" + id).header("If-Match", "\"" + version + "\""), admin,
                "{\"name\":\"May vấn đổi tên\",\"status\":\"UNDER_MAINTENANCE\"}");
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(updated).get("rowVersion").asLong()).isEqualTo(version + 1);

        // Phiên bản cũ → xung đột
        MvcResult stale = call(put("/api/v1/md/machines/" + id).header("If-Match", String.valueOf(version)), admin,
                "{\"name\":\"Ghi đè\"}");
        assertThat(stale.getResponse().getStatus()).isEqualTo(409);
        // Mã không được đổi
        MvcResult immutable = call(put("/api/v1/md/machines/" + id), admin, "{\"code\":\"KHAC\"}");
        assertThat(immutable.getResponse().getStatus()).isEqualTo(400);

        JsonNode history = json(call(get("/api/v1/md/machines/" + id + "/status-history"), admin, null));
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("toStatus").asText()).isEqualTo("UNDER_MAINTENANCE");
    }

    @Test
    void writeIsRestrictedByRole() throws Exception {
        MvcResult r = call(post("/api/v1/md/machines"), clerk.token(),
                "{\"code\":\"%s\",\"name\":\"X\",\"machineType\":\"OTHER\"}".formatted(unique("M")));
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/md/machines"), clerk.token(), null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void productValidationAndDeactivationBlockedWhenInUse() throws Exception {
        MvcResult bad = call(post("/api/v1/md/products"), planner.token(),
                "{\"code\":\"%s\",\"name\":\"SP\",\"productType\":\"DOMESTIC\",\"rodsPerPack\":-5}"
                        .formatted(unique("P")));
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(bad).get("errors").has("rodsPerPack")).isTrue();

        String productCode = unique("P");
        String productId = createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP dùng\","
                + "\"productType\":\"DOMESTIC\",\"rodsPerPack\":20}").formatted(productCode)).get("id").asText();
        String materialId = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"Giay\",\"uom\":\"kg\","
                + "\"categoryCode\":\"PACKING\",\"groupCode\":\"GIAY\"}").formatted(unique("V"))).get("id").asText();
        String normId = json(call(post("/api/v1/md/bom-norms"), clerk.token(), ("{\"productId\":\"%s\","
                + "\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":2.5}]}").formatted(productId, materialId)))
                .get("id").asText();
        call(post("/api/v1/md/bom-norms/" + normId + "/submit"), clerk.token(), null);

        MvcResult blocked = call(post("/api/v1/md/products/" + productId + "/deactivate"), planner.token(), null);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(blocked).get("code").asText()).isEqualTo("PXVB-MD-1003");

        String freeId = createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP rảnh\","
                + "\"productType\":\"EXPORT\"}").formatted(unique("P"))).get("id").asText();
        MvcResult ok = call(post("/api/v1/md/products/" + freeId + "/deactivate"), planner.token(), null);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ok).get("status").asText()).isEqualTo("DISCONTINUED");
        assertThat(json(call(get("/api/v1/md/products?activeOnly=true&pageSize=200"), planner.token(), null))
                .toString()).doesNotContain(freeId);
        assertThat(call(get("/api/v1/md/products/" + freeId), planner.token(), null).getResponse().getStatus())
                .isEqualTo(200);
    }

    // ---------- Ghép cặp vấn–bao ----------

    @Test
    void pairingRejectsSecondMakerForSamePackerAndShift() throws Exception {
        String maker1 = machine("MAKER", "12");
        String maker2 = machine("MAKER", "12");
        String packer = machine("PACKER", "5");

        JsonNode pairing = json(call(post("/api/v1/md/machine-pairings"), planner.token(),
                ("{\"effectiveFrom\":\"%s\",\"lines\":[{\"shiftCode\":\"A\",\"makerMachineId\":\"%s\","
                        + "\"packerMachineId\":\"%s\"}]}").formatted(LocalDate.now(ZONE), maker1, packer)));
        String id = pairing.get("id").asText();
        assertThat(pairing.get("status").asText()).isEqualTo("DRAFT");

        MvcResult conflict = call(post("/api/v1/md/machine-pairings/" + id + "/lines"), planner.token(),
                "{\"shiftCode\":\"A\",\"makerMachineId\":\"%s\",\"packerMachineId\":\"%s\"}".formatted(maker2, packer));
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(conflict).get("code").asText()).isEqualTo("PXVB-MD-1002");
        assertThat(json(conflict).get("errors").get("existingPair")).isNotEmpty();

        // Ca khác thì được; và máy sai loại bị từ chối
        MvcResult otherShift = call(post("/api/v1/md/machine-pairings/" + id + "/lines"), planner.token(),
                "{\"shiftCode\":\"B\",\"makerMachineId\":\"%s\",\"packerMachineId\":\"%s\"}".formatted(maker2, packer));
        assertThat(otherShift.getResponse().getStatus()).isEqualTo(201);
        MvcResult wrongType = call(post("/api/v1/md/machine-pairings/" + id + "/lines"), planner.token(),
                "{\"shiftCode\":\"C\",\"makerMachineId\":\"%s\",\"packerMachineId\":\"%s\"}".formatted(packer, maker1));
        assertThat(wrongType.getResponse().getStatus()).isEqualTo(400);

        // Người lập không có quyền kích hoạt; Phó QĐ kích hoạt và tra cứu hiệu lực
        assertThat(call(post("/api/v1/md/machine-pairings/" + id + "/activate"), planner.token(), null)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(json(call(post("/api/v1/md/machine-pairings/" + id + "/activate"), deputy.token(), null))
                .get("status").asText()).isEqualTo("ACTIVE");
        JsonNode resolved = json(call(get("/api/v1/md/machine-pairings/resolve?shift=A&date="
                + LocalDate.now(ZONE)), planner.token(), null));
        assertThat(resolved.toString()).contains(maker1).contains(packer);
        assertThat(resolved.toString()).doesNotContain(maker2);
    }

    // ---------- Định mức có phiên bản (AC-MD-003) ----------

    @Test
    void bomNormVersionsKeepOldVersionUntilEffectiveDate() throws Exception {
        String productId = createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP định mức\","
                + "\"productType\":\"DOMESTIC\",\"rodsPerPack\":20}").formatted(unique("P"))).get("id").asText();
        String paper = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"Giay\",\"uom\":\"kg\","
                + "\"categoryCode\":\"PACKING\"}").formatted(unique("V"))).get("id").asText();
        String filter = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"DauLoc\",\"uom\":\"cai\","
                + "\"categoryCode\":\"ROLLING\"}").formatted(unique("V"))).get("id").asText();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate tomorrow = today.plusDays(1);

        // Định mức phải > 0
        MvcResult zero = call(post("/api/v1/md/bom-norms"), clerk.token(), ("{\"productId\":\"%s\",\"lines\":["
                + "{\"materialId\":\"%s\",\"qtyPer1000Packs\":0}]}").formatted(productId, paper));
        assertThat(zero.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(zero).get("code").asText()).isEqualTo("PXVB-MD-1004");

        String v1 = json(call(post("/api/v1/md/bom-norms"), clerk.token(), ("{\"productId\":\"%s\","
                + "\"effectiveFrom\":\"%s\",\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":2.5},"
                + "{\"materialId\":\"%s\",\"qtyPer1000Packs\":1000}]}").formatted(productId, today, paper, filter)))
                .get("id").asText();
        assertThat(call(post("/api/v1/md/bom-norms/" + v1 + "/approve"), deputy.token(), null).getResponse()
                .getStatus()).isEqualTo(422); // chưa gửi duyệt
        call(post("/api/v1/md/bom-norms/" + v1 + "/submit"), clerk.token(), null);
        assertThat(call(post("/api/v1/md/bom-norms/" + v1 + "/approve"), clerk.token(), null).getResponse()
                .getStatus()).isEqualTo(403); // thủ lập không được duyệt
        assertThat(json(call(post("/api/v1/md/bom-norms/" + v1 + "/approve"), deputy.token(), null))
                .get("status").asText()).isEqualTo("ACTIVE");

        // Phiên bản 2 hiệu lực từ ngày mai, tăng định mức giấy
        String v2 = json(call(post("/api/v1/md/bom-norms/" + v1 + "/clone"), clerk.token(), null)).get("id").asText();
        call(put("/api/v1/md/bom-norms/" + v2 + "/lines"), clerk.token(), ("{\"effectiveFrom\":\"%s\","
                + "\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":2.8},{\"materialId\":\"%s\","
                + "\"qtyPer1000Packs\":1000}]}").formatted(tomorrow, paper, filter));
        call(post("/api/v1/md/bom-norms/" + v2 + "/submit"), clerk.token(), null);
        JsonNode approved = json(call(post("/api/v1/md/bom-norms/" + v2 + "/approve"), deputy.token(), null));
        assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode oldVersion = json(call(get("/api/v1/md/bom-norms/" + v1), deputy.token(), null));
        assertThat(oldVersion.get("status").asText()).isEqualTo("RETIRED");
        assertThat(oldVersion.get("effectiveTo").asText()).isEqualTo(tomorrow.toString());

        JsonNode asOfToday = json(call(get("/api/v1/md/bom-norms/active?productId=" + productId + "&asOf=" + today),
                deputy.token(), null));
        assertThat(asOfToday.get("versionNo").asInt()).isEqualTo(1);
        JsonNode asOfTomorrow = json(call(get("/api/v1/md/bom-norms/active?productId=" + productId + "&asOf="
                + tomorrow), deputy.token(), null));
        assertThat(asOfTomorrow.get("versionNo").asInt()).isEqualTo(2);

        JsonNode diff = json(call(get("/api/v1/md/bom-norms/compare?a=" + v1 + "&b=" + v2), deputy.token(), null));
        assertThat(diff).hasSize(2);
        assertThat(diff.toString()).contains("CHANGED").contains("SAME");

        // Hiệu lực trong quá khứ cần quyền riêng
        String v3 = json(call(post("/api/v1/md/bom-norms/" + v2 + "/clone"), clerk.token(), null)).get("id").asText();
        call(put("/api/v1/md/bom-norms/" + v3 + "/lines"), clerk.token(), ("{\"effectiveFrom\":\"%s\","
                + "\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":3}]}").formatted(today.minusDays(5), paper));
        call(post("/api/v1/md/bom-norms/" + v3 + "/submit"), clerk.token(), null);
        assertThat(call(post("/api/v1/md/bom-norms/" + v3 + "/approve"), deputy.token(), null).getResponse()
                .getStatus()).isEqualTo(422);
    }

    // ---------- Ngưỡng (AC-MD-005) ----------

    @Test
    void thresholdResolvePrefersNarrowestScopeAndSeesChangesImmediately() throws Exception {
        String machineId = machine("PACKER", "5");
        String otherMachine = machine("PACKER", "5");
        String metric = "MT_WASTE_PCT";

        JsonNode global = json(call(get("/api/v1/md/thresholds/resolve?metric=" + metric + "&machineId="
                + machineId), deputy.token(), null));
        assertThat(global.get("scopeType").asText()).isEqualTo("GLOBAL");
        assertThat(global.get("value").asDouble()).isEqualTo(3.0);

        createOk("thresholds", deputy.token(), ("{\"metricCode\":\"%s\",\"scopeType\":\"MACHINE\","
                + "\"scopeId\":\"%s\",\"thresholdValue\":5,\"effectiveFrom\":\"2026-01-01\"}")
                .formatted(metric, machineId));
        JsonNode specific = json(call(get("/api/v1/md/thresholds/resolve?metric=" + metric + "&machineId="
                + machineId), deputy.token(), null));
        assertThat(specific.get("scopeType").asText()).isEqualTo("MACHINE");
        assertThat(specific.get("value").asDouble()).isEqualTo(5.0);

        JsonNode fallback = json(call(get("/api/v1/md/thresholds/resolve?metric=" + metric + "&machineId="
                + otherMachine), deputy.token(), null));
        assertThat(fallback.get("scopeType").asText()).isEqualTo("GLOBAL");

        MvcResult missing = call(get("/api/v1/md/thresholds/resolve?metric=KHONG_CO"), deputy.token(), null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
    }

    // ---------- Biểu mẫu có phiên bản ----------

    @Test
    void qcFormVersionsAreImmutableOnceActive() throws Exception {
        String code = unique("B06.");
        JsonNode v1 = createOk("qc-form-templates", qaHead.token(), ("{\"code\":\"%s\",\"formGroup\":\"B06\","
                + "\"name\":\"Kiểm tra điếu\",\"category\":\"INLINE\",\"formSchema\":{\"schemaVersion\":1,"
                + "\"sections\":[{\"code\":\"S1\",\"fields\":[{\"code\":\"weight\",\"label\":\"Khối lượng\","
                + "\"type\":\"NUMBER\"}]}]}}").formatted(code));
        String v1Id = v1.get("id").asText();
        assertThat(v1.get("status").asText()).isEqualTo("DRAFT");
        assertThat(v1.get("versionNo").asInt()).isEqualTo(1);

        MvcResult activated = call(post("/api/v1/md/qc-form-templates/" + v1Id + "/activate-version"),
                qaHead.token(), "{}");
        assertThat(activated.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(activated).get("status").asText()).isEqualTo("ACTIVE");

        assertThat(call(put("/api/v1/md/qc-form-templates/" + v1Id), qaHead.token(), "{\"name\":\"Đổi tên\"}")
                .getResponse().getStatus()).isEqualTo(423);

        JsonNode v2 = json(call(post("/api/v1/md/qc-form-templates/" + v1Id + "/clone"), qaHead.token(), null));
        assertThat(v2.get("versionNo").asInt()).isEqualTo(2);
        assertThat(v2.get("status").asText()).isEqualTo("DRAFT");
        call(post("/api/v1/md/qc-form-templates/" + v2.get("id").asText() + "/activate-version"), qaHead.token(),
                "{}");
        assertThat(json(call(get("/api/v1/md/qc-form-templates/" + v1Id), qaHead.token(), null)).get("status")
                .asText()).isEqualTo("RETIRED");
    }

    // ---------- Chất lượng dữ liệu ----------

    @Test
    void dataQualityReportFlagsMissingMasterData() throws Exception {
        String noRate = machine("MAKER", null);
        String productCode = unique("P");
        createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP chưa có định mức\","
                + "\"productType\":\"DOMESTIC\"}").formatted(productCode));

        JsonNode report = json(call(get("/api/v1/md/data-quality/report"), deputy.token(), null));
        assertThat(report.get("hasErrors").asBoolean()).isTrue();
        String all = report.toString();
        assertThat(all).contains("MACHINE_NO_RATE").contains("PRODUCT_NO_NORM");
        assertThat(noRate).isNotBlank();
        assertThat(all).doesNotContain("SLOT_SHARE_NOT_100");
    }
}
