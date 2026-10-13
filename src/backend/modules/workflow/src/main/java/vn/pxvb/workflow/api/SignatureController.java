package vn.pxvb.workflow.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.workflow.application.SignatureService;
import vn.pxvb.workflow.domain.Signature;

/**
 * Tra cứu xác nhận điện tử. Việc ký do endpoint của từng module nghiệp vụ gọi {@link SignatureService#sign}
 * (hash do máy chủ tự tính), nên không có API ký tổng quát ở đây.
 */
@RestController
@RequestMapping("/api/v1/workflow/signatures")
public class SignatureController {

    public record SignatureDto(UUID id, String entityType, UUID entityId, String purpose, String roleCode,
                               UUID userId, Instant signedAt, String method, String hash) {
        static SignatureDto from(Signature s) {
            return new SignatureDto(s.getId(), s.getEntityType(), s.getEntityId(), s.getPurpose().name(),
                    s.getRoleCode(), s.getUserId(), s.getSignedAt(), s.getMethod().name(), s.getHash());
        }
    }

    private final SignatureService service;

    public SignatureController(SignatureService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('workflow.signature.view')")
    public List<SignatureDto> list(@RequestParam String entityType, @RequestParam UUID entityId) {
        return service.list(entityType, entityId).stream().map(SignatureDto::from).toList();
    }
}
