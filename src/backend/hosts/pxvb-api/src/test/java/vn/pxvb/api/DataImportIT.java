package vn.pxvb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Import/Export Excel (E05) trên masterdata: AC-MD-002 và các ràng buộc quyền/định dạng. */
@AutoConfigureMockMvc
class DataImportIT extends AbstractIntegrationTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    private String admin;
    private TestUser clerk;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken(mvc, mapper);
        clerk = createUser(mvc, mapper, admin, "clerk", "NORM_CLERK");
    }

    private static byte[] xlsx(List<String> header, List<List<String>> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Dữ liệu");
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                h.createCell(i).setCellValue(header.get(i));
            }
            int r = 1;
            for (List<String> values : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.size(); i++) {
                    row.createCell(i).setCellValue(values.get(i));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MvcResult upload(String resource, String token, String name, byte[] bytes, String mode)
            throws Exception {
        return mvc.perform(multipart("/api/v1/md/" + resource + "/import")
                .file(new MockMultipartFile("file", name, XLSX, bytes)).param("mode", mode)
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private long countMaterials(String prefix) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM md_material WHERE code LIKE ?", Long.class, prefix + "%");
        return n == null ? 0 : n;
    }

    @Test
    void templateFileHasGuideDataAndReferenceSheets() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/md/import-templates/materials")
                .header("Authorization", "Bearer " + clerk.token())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheet("Hướng dẫn")).isNotNull();
            assertThat(wb.getSheet("Danh mục tham chiếu")).isNotNull();
            Sheet data = wb.getSheet("Dữ liệu");
            assertThat(data.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã NVL");
        }
    }

    @Test
    void invalidRowsAreReportedAndNothingIsWrittenUntilCommit() throws Exception {
        String prefix = "IMP" + UUID.randomUUID().toString().substring(0, 5).toUpperCase() + "-";
        List<String> header = List.of("Mã NVL", "Tên NVL", "Đơn vị tính", "Nhóm (FIBER/ROLLING/PACKING)",
                "Nhóm ngưỡng hao hụt");
        List<List<String>> rows = new ArrayList<>();
        int valid = 2470;
        for (int i = 0; i < valid; i++) {
            rows.add(List.of(prefix + i, "Vật tư " + i, "kg", "PACKING", "GIAY"));
        }
        // 30 dòng lỗi: thiếu tên (10), nhóm không tồn tại (10), trùng mã trong file (10)
        for (int i = 0; i < 10; i++) {
            rows.add(List.of(prefix + "E" + i, "", "kg", "PACKING", "G"));
        }
        for (int i = 0; i < 10; i++) {
            rows.add(List.of(prefix + "N" + i, "Sai nhóm", "kg", "KHONGCO", "G"));
        }
        for (int i = 0; i < 10; i++) {
            rows.add(List.of(prefix + i, "Trùng mã", "kg", "PACKING", "G"));
        }
        byte[] file = xlsx(header, rows);

        MvcResult validated = upload("materials", clerk.token(), "nvl.xlsx", file, "validate");
        assertThat(validated.getResponse().getStatus()).isEqualTo(200);
        JsonNode job = json(validated);
        assertThat(job.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(job.get("totalRows").asInt()).isEqualTo(2500);
        assertThat(job.get("okRows").asInt()).isEqualTo(valid);
        assertThat(job.get("errorRows").asInt()).isEqualTo(30);
        assertThat(job.get("sampleErrors").get(0).has("row")).isTrue();
        assertThat(countMaterials(prefix)).isZero();

        String jobId = job.get("jobId").asText();
        MvcResult report = mvc.perform(get("/api/v1/dataio/imports/" + jobId + "/error-report")
                .header("Authorization", "Bearer " + clerk.token())).andReturn();
        assertThat(report.getResponse().getStatus()).isEqualTo(200);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(report.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(30);
        }

        MvcResult committed = mvc.perform(post("/api/v1/dataio/imports/" + jobId + "/commit")
                .header("Authorization", "Bearer " + clerk.token())).andReturn();
        assertThat(committed.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(committed).get("status").asText()).isEqualTo("COMMITTED");
        assertThat(json(committed).get("committedRows").asInt()).isEqualTo(valid);
        assertThat(countMaterials(prefix)).isEqualTo(valid);

        // Không commit hai lần
        assertThat(mvc.perform(post("/api/v1/dataio/imports/" + jobId + "/commit")
                .header("Authorization", "Bearer " + clerk.token())).andReturn().getResponse().getStatus())
                .isEqualTo(422);
    }

    @Test
    void reImportUpdatesExistingRowsInsteadOfDuplicating() throws Exception {
        String code = "UPS" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        List<String> header = List.of("Mã NVL", "Tên NVL", "Đơn vị tính", "Nhóm (FIBER/ROLLING/PACKING)");
        upload("materials", clerk.token(), "a.xlsx",
                xlsx(header, List.of(List.of(code, "Tên cũ", "kg", "FIBER"))), "commit");
        MvcResult second = upload("materials", clerk.token(), "b.xlsx",
                xlsx(header, List.of(List.of(code, "Tên mới", "kg", "FIBER"))), "commit");
        assertThat(json(second).get("committedRows").asInt()).isEqualTo(1);

        assertThat(countMaterials(code)).isEqualTo(1);
        String name = jdbc.queryForObject("SELECT name FROM md_material WHERE code = ?", String.class, code);
        assertThat(name).isEqualTo("Tên mới");
    }

    @Test
    void importRequiresPermissionAndXlsxFile() throws Exception {
        TestUser operator = createUser(mvc, mapper, admin, "op", "OPERATOR");
        byte[] file = xlsx(List.of("Mã NVL", "Tên NVL", "Đơn vị tính", "Nhóm (FIBER/ROLLING/PACKING)"),
                List.of(List.of("X1", "Y", "kg", "FIBER")));
        assertThat(upload("materials", operator.token(), "a.xlsx", file, "validate").getResponse().getStatus())
                .isEqualTo(403);

        MvcResult notExcel = mvc.perform(multipart("/api/v1/md/materials/import")
                .file(new MockMultipartFile("file", "a.xlsx", XLSX, "khong phai excel".getBytes()))
                .header("Authorization", "Bearer " + clerk.token())).andReturn();
        assertThat(notExcel.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(notExcel).get("code").asText()).isEqualTo("PXVB-CC-0510");

        // Thiếu cột bắt buộc trong tiêu đề
        MvcResult missingColumn = upload("materials", clerk.token(), "a.xlsx",
                xlsx(List.of("Mã NVL"), List.of(List.of("X1"))), "validate");
        assertThat(missingColumn.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void bomNormImportCreatesDraftVersionPerProduct() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TestUser planner = createUser(mvc, mapper, admin, "plan", "PLANNER");
        String productCode = "PB" + suffix;
        mvc.perform(post("/api/v1/md/products").header("Authorization", "Bearer " + planner.token())
                .contentType("application/json").content("{\"code\":\"%s\",\"name\":\"SP\",\"productType\":\"DOMESTIC\"}"
                        .formatted(productCode)));
        String m1 = "MB1" + suffix;
        String m2 = "MB2" + suffix;
        List<String> mh = List.of("Mã NVL", "Tên NVL", "Đơn vị tính", "Nhóm (FIBER/ROLLING/PACKING)");
        upload("materials", clerk.token(), "m.xlsx", xlsx(mh, List.of(List.of(m1, "A", "kg", "FIBER"),
                List.of(m2, "B", "cai", "ROLLING"))), "commit");

        byte[] norms = xlsx(List.of("Mã sản phẩm", "Mã NVL", "Định mức/1.000 bao", "Đơn vị tính", "Hiệu lực từ"),
                List.of(List.of(productCode, m1, "2,5", "", "2030-01-01"),
                        List.of(productCode, m2, "1000", "", "2030-01-01")));
        MvcResult r = mvc.perform(multipart("/api/v1/dataio/imports").file(new MockMultipartFile("file", "bom.xlsx",
                        XLSX, norms)).param("templateCode", "bom-norms").param("mode", "commit")
                .header("Authorization", "Bearer " + clerk.token())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(r).get("committedRows").asInt()).isEqualTo(2);

        Long lines = jdbc.queryForObject("SELECT COUNT(*) FROM md_bom_norm_line l JOIN md_bom_norm n ON n.id = "
                + "l.norm_id JOIN md_product p ON p.id = n.product_id WHERE p.code = ? AND n.status = 'DRAFT'",
                Long.class, productCode);
        assertThat(lines).isEqualTo(2);
    }

    @Test
    void exportContainsHeaderAndRows() throws Exception {
        String code = "EXP" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        upload("materials", clerk.token(), "a.xlsx", xlsx(List.of("Mã NVL", "Tên NVL", "Đơn vị tính",
                "Nhóm (FIBER/ROLLING/PACKING)"), List.of(List.of(code, "Xuất thử", "kg", "FIBER"))), "commit");

        MvcResult r = mvc.perform(get("/api/v1/md/materials/export?q=" + code)
                .header("Authorization", "Bearer " + clerk.token())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã NVL");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(code);
        }
    }
}
