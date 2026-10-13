package vn.pxvb.masterdata.api;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.common.web.PageResponse;
import vn.pxvb.dataio.application.ImportService;
import vn.pxvb.dataio.application.ImportService.UploadResult;
import vn.pxvb.masterdata.application.CatalogService;
import vn.pxvb.masterdata.application.CatalogSpec;
import vn.pxvb.masterdata.application.Catalogs;

/**
 * API chuẩn của mọi danh mục (SDD §5.6): danh sách/chi tiết/tạo/sửa (If-Match), khóa/mở khóa, import/export,
 * nhân bản & kích hoạt phiên bản. Quyền kiểm theo từng danh mục: {@code md.<resource>.<action>}.
 */
@RestController
@RequestMapping("/api/v1/md")
public class CatalogController {

    private static final Map<String, String> RESERVED = Map.of("q", "", "page", "", "pageSize", "", "sort", "",
            "activeOnly", "");

    public record ColumnDto(String name, String label, String type, boolean required, List<String> values,
                            String refTable, boolean immutable) {
    }

    public record CatalogDto(String resource, String title, boolean versioned, boolean hasActive, List<String> keys,
                             List<ColumnDto> columns) {
    }

    public record ActivateVersionRequest(LocalDate effectiveFrom) {
    }

    public record ImportResult(UUID jobId, String status, int totalRows, int okRows, int errorRows,
                               int committedRows, List<ImportService.ErrorSample> sampleErrors) {
    }

    private final CatalogService service;
    private final ImportService imports;
    private final CurrentUser currentUser;

    public CatalogController(CatalogService service, ImportService imports, CurrentUser currentUser) {
        this.service = service;
        this.imports = imports;
        this.currentUser = currentUser;
    }

    /** Siêu dữ liệu các danh mục người dùng được xem — để UI dựng form/bảng động. */
    @GetMapping("/catalogs")
    @PreAuthorize("isAuthenticated()")
    public List<CatalogDto> catalogs() {
        var permissions = currentUser.require().permissions();
        return Catalogs.all().stream()
                .filter(s -> permissions.contains("md." + s.permKey() + ".view"))
                .map(s -> new CatalogDto(s.resource(), s.title(), s.versioned(), s.hasActive(), s.keys(),
                        s.cols().stream().map(c -> new ColumnDto(c.name(), c.label(), c.type().name(), c.required(),
                                c.values(), c.refTable(), c.immutable())).toList()))
                .toList();
    }

    @GetMapping("/{resource}")
    @PreAuthorize("@mdAccess.can(#resource, 'view')")
    public PageResponse<Map<String, Object>> list(@PathVariable String resource,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(defaultValue = "false") boolean activeOnly,
                                                  @RequestParam Map<String, String> params) {
        return service.list(resource, q, filters(params), activeOnly, page, pageSize, sort);
    }

    @GetMapping("/{resource}/export")
    @PreAuthorize("@mdAccess.can(#resource, 'export')")
    public ResponseEntity<byte[]> export(@PathVariable String resource, @RequestParam(required = false) String q,
                                         @RequestParam Map<String, String> params) {
        byte[] file = service.export(resource, q, filters(params));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(resource + ".xlsx").build().toString())
                .body(file);
    }

    @GetMapping("/{resource}/{id}")
    @PreAuthorize("@mdAccess.can(#resource, 'view')")
    public Map<String, Object> get(@PathVariable String resource, @PathVariable UUID id) {
        return service.get(resource, id);
    }

    @PostMapping("/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@mdAccess.can(#resource, 'create')")
    public Map<String, Object> create(@PathVariable String resource, @RequestBody Map<String, Object> body) {
        return service.create(resource, body);
    }

    @PutMapping("/{resource}/{id}")
    @PreAuthorize("@mdAccess.can(#resource, 'update')")
    public Map<String, Object> update(@PathVariable String resource, @PathVariable UUID id,
                                      @RequestBody Map<String, Object> body,
                                      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return service.update(resource, id, body, parseEtag(ifMatch));
    }

    @PostMapping("/{resource}/{id}/deactivate")
    @PreAuthorize("@mdAccess.can(#resource, 'update')")
    public Map<String, Object> deactivate(@PathVariable String resource, @PathVariable UUID id) {
        return service.setActive(resource, id, false);
    }

    @PostMapping("/{resource}/{id}/activate")
    @PreAuthorize("@mdAccess.can(#resource, 'update')")
    public Map<String, Object> activate(@PathVariable String resource, @PathVariable UUID id) {
        return service.setActive(resource, id, true);
    }

    @DeleteMapping("/{resource}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@mdAccess.can(#resource, 'update')")
    public void delete(@PathVariable String resource, @PathVariable UUID id) {
        service.delete(resource, id);
    }

    @PostMapping("/{resource}/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@mdAccess.can(#resource, 'create')")
    public Map<String, Object> cloneVersion(@PathVariable String resource, @PathVariable UUID id) {
        return service.cloneVersion(resource, id);
    }

    @PostMapping("/{resource}/{id}/activate-version")
    @PreAuthorize("@mdAccess.can(#resource, 'approve')")
    public Map<String, Object> activateVersion(@PathVariable String resource, @PathVariable UUID id,
                                               @RequestBody(required = false) ActivateVersionRequest req) {
        return service.activateVersion(resource, id, req == null ? null : req.effectiveFrom());
    }

    /** {@code mode=validate} (mặc định) hoặc {@code mode=commit}; xem {@code /api/v1/dataio} để commit/tải lỗi. */
    @PostMapping(value = "/{resource}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@mdAccess.can(#resource, 'create')")
    public ImportResult importFile(@PathVariable String resource,
                                   @RequestParam(defaultValue = "validate") String mode,
                                   @RequestParam("file") @NotNull MultipartFile file) throws IOException {
        CatalogSpec spec = service.spec(resource);
        PxvbPrincipal p = currentUser.require();
        UploadResult r = imports.upload(spec.importCode(), file.getOriginalFilename(), file.getBytes(),
                "commit".equalsIgnoreCase(mode), p.userId(), p.permissions());
        var job = r.job();
        return new ImportResult(job.getId(), job.getStatus().name(), job.getTotalRows(), job.getOkRows(),
                job.getErrorRows(), job.getCommittedRows(), r.sampleErrors());
    }

    @GetMapping("/import-templates/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> importTemplate(@PathVariable String code) {
        byte[] file = imports.templateFile(code, currentUser.require().permissions());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(code + "-mau-nhap-lieu.xlsx").build().toString())
                .body(file);
    }

    private static Map<String, String> filters(Map<String, String> params) {
        Map<String, String> filters = new HashMap<>(params);
        RESERVED.keySet().forEach(filters::remove);
        return filters;
    }

    private static Long parseEtag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replace("\"", "").replace("W/", "").trim();
        return cleaned.matches("\\d+") ? Long.parseLong(cleaned) : null;
    }
}
