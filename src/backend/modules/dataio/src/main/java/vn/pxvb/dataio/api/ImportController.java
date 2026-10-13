package vn.pxvb.dataio.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.dataio.application.ImportService;
import vn.pxvb.dataio.application.ImportService.ErrorSample;
import vn.pxvb.dataio.application.ImportService.UploadResult;
import vn.pxvb.dataio.contract.ImportTemplate;
import vn.pxvb.dataio.domain.ImportJob;

/** Nhập liệu Excel: danh sách mẫu, tải mẫu, tải lên/kiểm tra, commit, tải báo cáo lỗi. */
@RestController
@RequestMapping("/api/v1/dataio")
@PreAuthorize("isAuthenticated()")
public class ImportController {

    static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public record TemplateDto(String code, String title, String permission, List<ImportTemplate.Column> columns) {
    }

    public record JobDto(UUID id, String templateCode, String fileName, String status, String mode, int totalRows,
                         int okRows, int errorRows, int committedRows, Instant createdAt, Instant committedAt,
                         String message, boolean hasErrorReport, List<ErrorSample> sampleErrors) {
        static JobDto from(ImportJob j, List<ErrorSample> sample) {
            return new JobDto(j.getId(), j.getTemplateCode(), j.getFileName(), j.getStatus().name(),
                    j.getMode().name(), j.getTotalRows(), j.getOkRows(), j.getErrorRows(), j.getCommittedRows(),
                    j.getCreatedAt(), j.getCommittedAt(), j.getMessage(), j.getErrorRows() > 0, sample);
        }
    }

    private final ImportService service;
    private final CurrentUser currentUser;

    public ImportController(ImportService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/templates")
    public List<TemplateDto> templates() {
        var permissions = currentUser.require().permissions();
        return service.templates().stream().filter(t -> permissions.contains(t.permission()))
                .map(t -> new TemplateDto(t.code(), t.title(), t.permission(), t.columns())).toList();
    }

    @GetMapping("/templates/{code}/file")
    public ResponseEntity<byte[]> templateFile(@PathVariable String code) {
        byte[] file = service.templateFile(code, currentUser.require().permissions());
        return download(file, code + "-mau-nhap-lieu.xlsx");
    }

    /** {@code mode=validate} (mặc định) chỉ kiểm tra; {@code mode=commit} kiểm tra rồi ghi luôn các dòng hợp lệ. */
    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobDto upload(@RequestParam String templateCode, @RequestParam(defaultValue = "validate") String mode,
                         @RequestParam("file") MultipartFile file) throws IOException {
        PxvbPrincipal p = currentUser.require();
        UploadResult result = service.upload(templateCode, file.getOriginalFilename(), file.getBytes(),
                "commit".equalsIgnoreCase(mode), p.userId(), p.permissions());
        return JobDto.from(result.job(), result.sampleErrors());
    }

    @GetMapping("/imports/{id}")
    public JobDto get(@PathVariable UUID id) {
        PxvbPrincipal p = currentUser.require();
        return JobDto.from(service.get(id, p.userId(), p.permissions()), List.of());
    }

    @PostMapping("/imports/{id}/commit")
    public JobDto commit(@PathVariable UUID id) {
        PxvbPrincipal p = currentUser.require();
        return JobDto.from(service.commit(id, p.userId(), p.permissions()), List.of());
    }

    @GetMapping("/imports/{id}/error-report")
    public ResponseEntity<byte[]> errorReport(@PathVariable UUID id) {
        PxvbPrincipal p = currentUser.require();
        return download(service.errorReport(id, p.userId(), p.permissions()), "loi-nhap-lieu-" + id + ".xlsx");
    }

    static ResponseEntity<byte[]> download(byte[] content, String fileName) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(content);
    }
}
