package vn.pxvb.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Lược đồ biểu mẫu KSCL v1 (FR-MD-013): cấu trúc mục, trường, kiểu và mã trường duy nhất. */
class QcFormSchemaRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsWellFormedSchemaVersionOne() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"schemaVersion":1,"sections":[
                  {"code":"S1","title":"Trọng lượng","fields":[
                    {"code":"w1","label":"Khối lượng","type":"NUMBER"},
                    {"code":"c1","label":"Đạt","type":"CHECK"}]},
                  {"code":"S2","title":"Khác","fields":[
                    {"code":"s1","label":"Loại","type":"SELECT","options":["X","Y"]}]}]}
                """);

        assertThat(QcFormSchemaRules.violations(schema)).isEmpty();
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws Exception {
        JsonNode schema = mapper.readTree("{\"schemaVersion\":2,\"sections\":[]}");

        assertThat(QcFormSchemaRules.violations(schema))
                .anyMatch(message -> message.contains("Phiên bản lược đồ"));
    }

    @Test
    void rejectsSchemaWithoutSections() throws Exception {
        JsonNode schema = mapper.readTree("{\"schemaVersion\":1,\"sections\":[]}");

        assertThat(QcFormSchemaRules.violations(schema)).anyMatch(message -> message.contains("ít nhất một mục"));
    }

    @Test
    void rejectsFieldCodeRepeatedAcrossSections() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"schemaVersion":1,"sections":[
                  {"code":"S1","fields":[{"code":"f","label":"A","type":"TEXT"}]},
                  {"code":"S2","fields":[{"code":"f","label":"B","type":"TEXT"}]}]}
                """);

        assertThat(QcFormSchemaRules.violations(schema)).anyMatch(message -> message.contains("Trùng mã trường f"));
    }

    @Test
    void rejectsUnknownFieldType() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"schemaVersion":1,"sections":[
                  {"code":"S1","fields":[{"code":"f","label":"A","type":"DRAWING"}]}]}
                """);

        assertThat(QcFormSchemaRules.violations(schema)).anyMatch(message -> message.contains("kiểu không hỗ trợ"));
    }

    @Test
    void rejectsSelectFieldWithoutOptions() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"schemaVersion":1,"sections":[
                  {"code":"S1","fields":[{"code":"f","label":"A","type":"SELECT"}]}]}
                """);

        assertThat(QcFormSchemaRules.violations(schema)).anyMatch(message -> message.contains("cần danh sách lựa chọn"));
    }

    @Test
    void rejectsFieldWithoutLabel() throws Exception {
        JsonNode schema = mapper.readTree("""
                {"schemaVersion":1,"sections":[
                  {"code":"S1","fields":[{"code":"f","label":"  ","type":"TEXT"}]}]}
                """);

        assertThat(QcFormSchemaRules.violations(schema)).anyMatch(message -> message.contains("thiếu nhãn"));
    }
}
