package vn.pxvb.workflow.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.identity.contract.IdentityDirectory.SecretKind;
import vn.pxvb.workflow.application.Repositories.SignatureRepository;
import vn.pxvb.workflow.domain.Signature;

/**
 * Xác nhận điện tử đa bên (FR-CC-031). Module nghiệp vụ gọi {@link #sign} với hash do máy chủ tự tính từ bản ghi
 * (không nhận hash từ client). Bản ghi đổi sau khi ký → {@link #requireValid} ném lỗi, yêu cầu ký lại.
 */
@Service
public class SignatureService {

    private final SignatureRepository signatures;
    private final IdentityDirectory directory;
    private final AuditLogWriter audit;
    private final Clock clock;

    public SignatureService(SignatureRepository signatures, IdentityDirectory directory, AuditLogWriter audit,
                            Clock clock) {
        this.signatures = signatures;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    /** SHA-256 (hex) của chuỗi chuẩn hóa của bản ghi. */
    public static String hashOf(String canonicalRecord) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRecord.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public Signature sign(String entityType, UUID entityId, Signature.Purpose purpose, String roleCode,
                          UUID userId, UUID deviceId, Signature.Method method, String secret, String recordHash) {
        if (!directory.roleCodesOf(userId).contains(roleCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không giữ vai trò " + roleCode);
        }
        SecretKind kind = method == Signature.Method.PIN ? SecretKind.PIN : SecretKind.PASSWORD;
        if (!directory.verifySecret(userId, kind, secret)) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "Xác nhận không thành công: sai mật khẩu/PIN");
        }
        Signature signature = signatures.save(new Signature(entityType, entityId, purpose, roleCode, userId,
                clock.instant(), deviceId, method, recordHash));
        audit.write(new AuditLogWriter.Entry("workflow", entityType, entityId.toString(), "SIGN",
                List.of(new AuditLogWriter.FieldChange("purpose", null, purpose.name()),
                        new AuditLogWriter.FieldChange("roleCode", null, roleCode)),
                null, userId, null, deviceId, null));
        return signature;
    }

    @Transactional(readOnly = true)
    public List<Signature> list(String entityType, UUID entityId) {
        return signatures.findByEntityTypeAndEntityIdOrderBySignedAtAsc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public boolean hasValidSignature(String entityType, UUID entityId, Signature.Purpose purpose, String roleCode,
                                     String currentHash) {
        return list(entityType, entityId).stream().anyMatch(s -> s.getPurpose() == purpose
                && s.getRoleCode().equals(roleCode) && s.isValidFor(currentHash));
    }

    @Transactional(readOnly = true)
    public void requireValid(String entityType, UUID entityId, Signature.Purpose purpose, String roleCode,
                             String currentHash) {
        if (!hasValidSignature(entityType, entityId, purpose, roleCode, currentHash)) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Chưa có xác nhận hợp lệ của " + roleCode + " (bản ghi có thể đã thay đổi sau khi ký, cần ký lại)");
        }
    }
}
