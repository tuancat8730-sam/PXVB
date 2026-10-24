package vn.pxvb.identity.api;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.identity.application.AuditQueryService;
import vn.pxvb.identity.application.AuditQueryService.AuditPage;
import vn.pxvb.identity.application.AuditQueryService.Criteria;

/** Tra cứu nhật ký kiểm toán (FR-CC-021/022). Mọi truy vấn đều cần quyền {@code identity.audit.view}. */
@RestController
@RequestMapping("/api/v1/identity/audit-logs")
public class AuditController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String EXPORT_FILE = "nhat-ky-kiem-toan.xlsx";

    private final AuditQueryService audits;
    private final CurrentUser currentUser;

    public AuditController(AuditQueryService audits, CurrentUser currentUser) {
        this.audits = audits;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.audit.view')")
    public AuditPage search(@RequestParam(required = false) String module,
                            @RequestParam(required = false) String entityType,
                            @RequestParam(required = false) String entityId,
                            @RequestParam(required = false) String action,
                            @RequestParam(required = false) UUID byUserId,
                            @RequestParam(required = false) Instant from,
                            @RequestParam(required = false) Instant to,
                            @RequestParam(required = false) String cursor,
                            @RequestParam(defaultValue = "50") int size) {
        return audits.search(new Criteria(module, entityType, entityId, action, byUserId, from, to), cursor, size);
    }

    @GetMapping("/entities/{type}/{id}")
    @PreAuthorize("hasAuthority('identity.audit.view')")
    public AuditPage history(@PathVariable String type, @PathVariable String id,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(defaultValue = "50") int size) {
        return audits.search(new Criteria(null, type, id, null, null, null, null), cursor, size);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('identity.audit.view')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String module,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) String entityId,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) UUID byUserId,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        byte[] workbook = audits.export(new Criteria(module, entityType, entityId, action, byUserId, from, to),
                currentUser.require().userId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(EXPORT_FILE).build().toString())
                .body(workbook);
    }
}
