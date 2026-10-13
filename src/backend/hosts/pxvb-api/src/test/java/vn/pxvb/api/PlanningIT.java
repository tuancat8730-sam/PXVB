package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Kế hoạch & lệnh sản xuất (E10): KHSX tháng/tuần, duyệt, gợi ý lệnh, xác nhận, điều chỉnh, bố trí nhân sự. */
@AutoConfigureMockMvc
class PlanningIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    private String admin;
    private TestUser planner;
    private TestUser deputy;
    private TestUser clerk;
    private TestUser leader;

    private String maker1;
    private String maker2;
    private String packer1;
    private String packer2;
    private String productId;
    private String productCode;
    private LocalDate monday;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        planner = createUser(mvc, mapper, admin, "plan", "PLANNER");
        deputy = createUser(mvc, mapper, admin, "dep", "DEPUTY_DIRECTOR");
        clerk = createUser(mvc, mapper, admin, "clerk", "NORM_CLERK");
        leader = createUser(mvc, mapper, admin, "lead", "SHIFT_LEADER");

        LocalDate candidate;
        do {
            candidate = LocalDate.of(2031, 1, 6).plusWeeks(ThreadLocalRandom.current().nextInt(0, 500));
        } while (candidate.getDayOfMonth() > 18);
        monday = candidate;

        maker1 = machine("MAKER");
        maker2 = machine("MAKER");
        packer1 = machine("PACKER");
        packer2 = machine("PACKER");
        productCode = unique("P");
        productId = createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP kế hoạch\","
                + "\"productType\":\"DOMESTIC\",\"rodsPerPack\":20}").formatted(productCode)).get("id").asText();
        for (String packer : List.of(packer1, packer2)) {
            createOk("product-allowed-machines", planner.token(), ("{\"productId\":\"%s\",\"machineId\":\"%s\"}")
                    .formatted(productId, packer));
        }
        String material = createOk("materials", clerk.token(), ("{\"code\":\"%s\",\"name\":\"Giay\",\"uom\":\"kg\","
                + "\"categoryCode\":\"PACKING\"}").formatted(unique("V"))).get("id").asText();
        String norm = json(call(post("/api/v1/md/bom-norms"), clerk.token(), ("{\"productId\":\"%s\","
                + "\"effectiveFrom\":\"%s\",\"lines\":[{\"materialId\":\"%s\",\"qtyPer1000Packs\":2.5}]}")
                .formatted(productId, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), material))).get("id").asText();
        call(post("/api/v1/md/bom-norms/" + norm + "/submit"), clerk.token(), null);
        call(post("/api/v1/md/bom-norms/" + norm + "/approve"), deputy.token(), null);

        StringBuilder lines = new StringBuilder();
        for (String shift : List.of("A", "B", "C")) {
            lines.append(lines.length() == 0 ? "" : ",").append(("{\"shiftCode\":\"%s\",\"makerMachineId\":\"%s\","
                    + "\"packerMachineId\":\"%s\"},{\"shiftCode\":\"%s\",\"makerMachineId\":\"%s\","
                    + "\"packerMachineId\":\"%s\"}").formatted(shift, maker1, packer1, shift, maker2, packer2));
        }
        String pairing = json(call(post("/api/v1/md/machine-pairings"), planner.token(),
                ("{\"effectiveFrom\":\"%s\",\"lines\":[%s]}").formatted(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                        lines))).get("id").asText();
        assertThat(call(post("/api/v1/md/machine-pairings/" + pairing + "/activate"), deputy.token(), null)
                .getResponse().getStatus()).isEqualTo(200);
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

    private String machine(String type) throws Exception {
        String code = unique("M");
        return createOk("machines", admin, ("{\"code\":\"%s\",\"name\":\"May %s\",\"machineType\":\"%s\","
                + "\"theoreticalRate\":10}").formatted(code, code, type)).get("id").asText();
    }

    private String monthlyPlan(long packs) throws Exception {
        return json(call(post("/api/v1/pl/monthly-plans"), planner.token(),
                ("{\"year\":%d,\"month\":%d,\"items\":[{\"productId\":\"%s\",\"plannedPacks\":%d}]}")
                        .formatted(monday.getYear(), monday.getMonthValue(), productId, packs))).get("id").asText();
    }

    private String weeklyPlan(long mondayPacks, long tuesdayPacks) throws Exception {
        MvcResult r = call(post("/api/v1/pl/weekly-plans"), planner.token(),
                ("{\"weekStart\":\"%s\",\"items\":[{\"productId\":\"%s\",\"planDate\":\"%s\",\"plannedPacks\":%d},"
                        + "{\"productId\":\"%s\",\"planDate\":\"%s\",\"plannedPacks\":%d}]}")
                        .formatted(monday, productId, monday, mondayPacks, productId, monday.plusDays(1),
                                tuesdayPacks));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return json(r).get("id").asText();
    }

    private String approvedWeeklyPlan(long mondayPacks, long tuesdayPacks) throws Exception {
        monthlyPlan(1_000_000);
        String id = weeklyPlan(mondayPacks, tuesdayPacks);
        assertThat(call(post("/api/v1/pl/weekly-plans/" + id + "/submit"), planner.token(), "{}").getResponse()
                .getStatus()).isEqualTo(200);
        assertThat(call(post("/api/v1/pl/weekly-plans/" + id + "/approve"), deputy.token(), "{}").getResponse()
                .getStatus()).isEqualTo(200);
        return id;
    }

    private JsonNode suggest(String weeklyId) throws Exception {
        MvcResult r = call(post("/api/v1/pl/orders/suggest"), planner.token(),
                "{\"weeklyPlanId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\"}".formatted(weeklyId, monday, monday));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r);
    }

    // ---------- KHSX tháng ----------

    @Test
    void monthlyPlanKeepsRevisionsWithSingleActive() throws Exception {
        String first = monthlyPlan(100_000);
        JsonNode revised = json(call(post("/api/v1/pl/monthly-plans/" + first + "/revise"), planner.token(),
                "{\"items\":[{\"productCode\":\"%s\",\"plannedPacks\":120000}],\"note\":\"Điều chỉnh\"}"
                        .formatted(productCode)));
        assertThat(revised.get("revision").asInt()).isEqualTo(2);
        assertThat(revised.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(json(call(get("/api/v1/pl/monthly-plans/" + first), planner.token(), null)).get("status")
                .asText()).isEqualTo("SUPERSEDED");

        JsonNode progress = json(call(get("/api/v1/pl/monthly-plans/" + revised.get("id").asText() + "/progress"),
                planner.token(), null));
        assertThat(progress.get("plannedTotal").asLong()).isEqualTo(120_000);
        assertThat(progress.get("remainingTotal").asLong()).isEqualTo(120_000);

        // Vai trò vận hành không được lập KHSX tháng
        assertThat(call(post("/api/v1/pl/monthly-plans"), leader.token(),
                "{\"year\":2031,\"month\":1,\"items\":[]}").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void monthlyPlanCanBeImportedFromExcel() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Dữ liệu");
            String[] header = {"Năm", "Tháng", "Mã sản phẩm", "Sản lượng (bao)", "Ghi chú"};
            var h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(String.valueOf(monday.getYear()));
            row.createCell(1).setCellValue(String.valueOf(monday.getMonthValue()));
            row.createCell(2).setCellValue(productCode);
            row.createCell(3).setCellValue("55000");
            wb.write(out);
            MvcResult r = mvc.perform(multipart("/api/v1/dataio/imports").file(new MockMultipartFile("file", "kh.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()))
                    .param("templateCode", "monthly-plan").param("mode", "commit")
                    .header("Authorization", "Bearer " + planner.token())).andReturn();
            assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(json(r).get("committedRows").asInt()).isEqualTo(1);
        }
        JsonNode plans = json(call(get("/api/v1/pl/monthly-plans?year=" + monday.getYear() + "&month="
                + monday.getMonthValue()), planner.token(), null));
        assertThat(plans.toString()).contains("ACTIVE");
    }

    // ---------- KHSX tuần ----------

    @Test
    void weeklyPlanOverMonthlyTolerancePlusReasonThenApprovedByDeputy() throws Exception {
        monthlyPlan(10_000);
        String id = weeklyPlan(8_000, 7_000); // 15.000 = 150% phần còn lại
        JsonNode context = json(call(get("/api/v1/pl/weekly-plans/" + id + "/context"), planner.token(), null));
        assertThat(context.get("products").get(0).get("exceedsTolerance").asBoolean()).isTrue();

        MvcResult noReason = call(post("/api/v1/pl/weekly-plans/" + id + "/submit"), planner.token(), "{}");
        assertThat(noReason.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(noReason).get("errors").has("varianceReason")).isTrue();

        MvcResult submitted = call(post("/api/v1/pl/weekly-plans/" + id + "/submit"), planner.token(),
                "{\"varianceReason\":\"Bổ sung đơn hàng xuất khẩu\"}");
        assertThat(json(submitted).get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(json(submitted).get("approvalInstanceId").isNull()).isFalse();

        // Người gửi không được tự duyệt (BR-CC-003); Phó QĐ duyệt thì kế hoạch chuyển APPROVED
        assertThat(call(post("/api/v1/pl/weekly-plans/" + id + "/approve"), planner.token(), "{}").getResponse()
                .getStatus()).isEqualTo(403);
        assertThat(json(call(post("/api/v1/pl/weekly-plans/" + id + "/approve"), deputy.token(), "{}"))
                .get("status").asText()).isEqualTo("APPROVED");

        // Điều chỉnh sau duyệt sinh revision mới, bản cũ giữ nguyên đến khi bản mới được duyệt
        JsonNode revision = json(call(post("/api/v1/pl/weekly-plans/" + id + "/revise"), planner.token(), null));
        assertThat(revision.get("revision").asInt()).isEqualTo(2);
        assertThat(revision.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void rejectedWeeklyPlanReturnsToDraftForEditing() throws Exception {
        monthlyPlan(1_000_000);
        String id = weeklyPlan(10_000, 10_000);
        call(post("/api/v1/pl/weekly-plans/" + id + "/submit"), planner.token(), "{}");
        JsonNode rejected = json(call(post("/api/v1/pl/weekly-plans/" + id + "/reject"), deputy.token(),
                "{\"note\":\"Thiếu sợi tuần này\"}"));
        assertThat(rejected.get("status").asText()).isEqualTo("DRAFT");
        MvcResult edited = call(put("/api/v1/pl/weekly-plans/" + id), planner.token(),
                "{\"items\":[{\"productId\":\"%s\",\"planDate\":\"%s\",\"plannedPacks\":5000}]}"
                        .formatted(productId, monday));
        assertThat(edited.getResponse().getStatus()).isEqualTo(200);
    }

    // ---------- Lệnh sản xuất ----------

    @Test
    void suggestConfirmAdjustAndProtectConfirmedOrders() throws Exception {
        String weekly = approvedWeeklyPlan(60_000, 30_000);

        // 2 máy bao × 3 ca = 6 lệnh, chia đều 10.000 bao
        JsonNode first = suggest(weekly);
        JsonNode orders = first.get("orders");
        assertThat(orders).hasSize(6);
        long total = 0;
        for (JsonNode o : orders) {
            total += o.get("plannedPacks").asLong();
            assertThat(o.get("status").asText()).isEqualTo("SUGGESTED");
            assertThat(o.get("code").asText()).startsWith("LSX-" + monday.toString().replace("-", ""));
        }
        assertThat(total).isEqualTo(60_000);

        // Máy bao 2 đang bảo trì → bị loại khỏi gợi ý và có ghi chú
        assertThat(call(put("/api/v1/md/machines/" + packer2), admin, "{\"status\":\"UNDER_MAINTENANCE\"}")
                .getResponse().getStatus()).isEqualTo(200);
        JsonNode second = suggest(weekly);
        assertThat(second.get("orders")).hasSize(3);
        assertThat(second.get("skipped").toString()).contains("UNDER_MAINTENANCE");

        // Xác nhận một lệnh: cần quyền và xác nhận điện tử
        String orderId = second.get("orders").get(0).get("id").asText();
        assertThat(call(post("/api/v1/pl/orders/" + orderId + "/confirm"), planner.token(),
                "{\"secret\":\"" + USER_PASSWORD + "\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(call(post("/api/v1/pl/orders/" + orderId + "/confirm"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"sai-mat-khau\"}").getResponse().getStatus()).isEqualTo(401);
        JsonNode confirmed = json(call(post("/api/v1/pl/orders/" + orderId + "/confirm"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}"));
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("normVersionId").isNull()).isFalse();
        JsonNode signatures = json(call(get("/api/v1/workflow/signatures?entityType=ProductionOrder&entityId="
                + orderId), leader.token(), null));
        assertThat(signatures).hasSize(1);

        // Gợi ý lại không đụng lệnh đã xác nhận (BR-PL-004) và không phân bổ dư
        JsonNode third = suggest(weekly);
        JsonNode all = json(call(get("/api/v1/pl/orders?date=" + monday), planner.token(), null));
        long sumNonCancelled = 0;
        boolean confirmedStillThere = false;
        for (JsonNode o : all) {
            if (!"CANCELLED".equals(o.get("status").asText())) {
                sumNonCancelled += o.get("plannedPacks").asLong();
            }
            if (o.get("id").asText().equals(orderId)) {
                confirmedStillThere = "CONFIRMED".equals(o.get("status").asText());
            }
        }
        assertThat(confirmedStillThere).isTrue();
        assertThat(third.get("orders")).hasSize(2);
        assertThat(sumNonCancelled).isLessThanOrEqualTo(60_000);

        // Lệnh đã xác nhận: sửa trực tiếp → quay về SUGGESTED, tăng revision (BR-PL-002)
        JsonNode revised = json(call(put("/api/v1/pl/orders/" + orderId), planner.token(),
                "{\"plannedPacks\":18000}"));
        assertThat(revised.get("status").asText()).isEqualTo("SUGGESTED");
        assertThat(revised.get("revision").asInt()).isEqualTo(2);
        assertThat(revised.get("plannedPacks").asLong()).isEqualTo(18_000);

        // Xác nhận lại rồi điều chỉnh trong ca: đổi mác → thêm segment; lý do phải thuộc danh mục
        call(post("/api/v1/pl/orders/" + orderId + "/confirm"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}");
        String product2 = createOk("products", planner.token(), ("{\"code\":\"%s\",\"name\":\"SP đổi mác\","
                + "\"productType\":\"DOMESTIC\"}").formatted(unique("P"))).get("id").asText();
        assertThat(call(post("/api/v1/pl/orders/" + orderId + "/adjustments"), leader.token(),
                "{\"type\":\"PRODUCT_CHANGE\",\"reasonCode\":\"KHONG_CO\",\"newProductId\":\"%s\"}"
                        .formatted(product2)).getResponse().getStatus()).isEqualTo(400);
        JsonNode adjusted = json(call(post("/api/v1/pl/orders/" + orderId + "/adjustments"), leader.token(),
                ("{\"type\":\"PRODUCT_CHANGE\",\"reasonCode\":\"PRODUCT_CHANGE\",\"newProductId\":\"%s\","
                        + "\"note\":\"Đổi mác giữa ca\"}").formatted(product2)));
        assertThat(adjusted.get("segments")).hasSize(2);
        assertThat(adjusted.get("productId").asText()).isEqualTo(product2);
        Integer adjustments = jdbc.queryForObject("SELECT COUNT(*) FROM pl_order_adjustment WHERE order_id = "
                + "UUID_TO_BIN(?)", Integer.class, orderId);
        assertThat(adjustments).isEqualTo(1);
    }

    @Test
    void deactivatingProductWithOpenOrdersIsBlockedListingOrders() throws Exception {
        String weekly = approvedWeeklyPlan(30_000, 10_000);
        String orderId = suggest(weekly).get("orders").get(0).get("id").asText();
        call(post("/api/v1/pl/orders/" + orderId + "/confirm"), leader.token(),
                "{\"method\":\"PASSWORD\",\"secret\":\"" + USER_PASSWORD + "\"}");

        MvcResult blocked = call(post("/api/v1/md/products/" + productId + "/deactivate"), planner.token(), null);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(blocked).get("code").asText()).isEqualTo("PXVB-MD-1003");
        assertThat(blocked.getResponse().getContentAsString()).contains("Lệnh SX LSX-");

        // Hủy lệnh đã xác nhận (có lý do) → SP không còn lệnh mở nhưng vẫn còn định mức → vẫn bị chặn bởi định mức
        assertThat(call(post("/api/v1/pl/orders/" + orderId + "/cancel"), planner.token(), "{}").getResponse()
                .getStatus()).isEqualTo(400);
        assertThat(json(call(post("/api/v1/pl/orders/" + orderId + "/cancel"), planner.token(),
                "{\"reason\":\"Hủy theo yêu cầu\"}")).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void suggestRequiresApprovedWeeklyPlan() throws Exception {
        monthlyPlan(100_000);
        String draft = weeklyPlan(1_000, 1_000);
        MvcResult r = call(post("/api/v1/pl/orders/suggest"), planner.token(),
                "{\"weeklyPlanId\":\"%s\"}".formatted(draft));
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
    }

    // ---------- Bố trí nhân sự ----------

    @Test
    void rosterCopyShowsReplacementsAndPublishes() throws Exception {
        String e1 = employee();
        String e2 = employee();
        String e3 = employee();
        String shiftA = json(call(post("/api/v1/pl/rosters"), leader.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"A\",\"leaderEmployeeId\":\"%s\"}".formatted(monday, e1)))
                .get("id").asText();
        MvcResult dup = call(post("/api/v1/pl/rosters"), leader.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"A\"}".formatted(monday));
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);

        JsonNode assigned = json(call(put("/api/v1/pl/rosters/" + shiftA + "/assignments"), leader.token(),
                ("{\"assignments\":[{\"machineId\":\"%s\",\"position\":\"OPERATOR_1\",\"employeeId\":\"%s\"},"
                        + "{\"machineId\":\"%s\",\"position\":\"OPERATOR_1\",\"employeeId\":\"%s\"},"
                        + "{\"machineId\":\"%s\",\"position\":\"MECHANIC\",\"employeeId\":\"%s\"}]}")
                        .formatted(packer1, e2, packer2, e3, packer1, e2)));
        assertThat(assigned.get("assignments")).hasSize(3);
        // e2 giữ hai vị trí → cảnh báo trùng
        assertThat(assigned.get("warnings").toString()).contains("2 vị trí");
        assertThat(call(post("/api/v1/pl/rosters/" + shiftA + "/publish"), leader.token(), null).getResponse()
                .getStatus()).isEqualTo(200);

        // Nhân sự e3 nghỉ việc → khi sao chép sang ca B phải được báo cần thay
        assertThat(call(post("/api/v1/md/employees/" + e3 + "/deactivate"), admin, null).getResponse()
                .getStatus()).isEqualTo(200);
        String shiftB = json(call(post("/api/v1/pl/rosters"), leader.token(),
                "{\"businessDate\":\"%s\",\"shiftCode\":\"B\"}".formatted(monday))).get("id").asText();
        JsonNode copied = json(call(post("/api/v1/pl/rosters/" + shiftB + "/copy-from/" + shiftA), leader.token(),
                null));
        assertThat(copied.get("assignments")).hasSize(3);
        assertThat(copied.get("needReplacement")).hasSize(1);
        assertThat(copied.get("status").asText()).isEqualTo("DRAFT");

        // Đã công bố thì không sửa trực tiếp
        assertThat(call(put("/api/v1/pl/rosters/" + shiftA + "/assignments"), leader.token(),
                "{\"assignments\":[]}").getResponse().getStatus()).isEqualTo(423);

        MvcResult export = mvc.perform(get("/api/v1/pl/rosters/" + shiftA + "/export")
                .header("Authorization", "Bearer " + leader.token())).andReturn();
        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(3);
        }
        assertThat(delete("/x")).isNotNull();
    }

    private String employee() throws Exception {
        return createOk("employees", admin, ("{\"code\":\"%s\",\"fullName\":\"Nhan vien %s\"}")
                .formatted(unique("NV"), UUID.randomUUID().toString().substring(0, 4))).get("id").asText();
    }
}
