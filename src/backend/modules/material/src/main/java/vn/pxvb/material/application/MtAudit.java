package vn.pxvb.material.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.security.CurrentUser;

/** Ghi audit tường minh cho các thao tác JDBC của phân hệ MT. */
@Component
class MtAudit {

    private final AuditLogWriter audit;
    private final CurrentUser currentUser;

    MtAudit(AuditLogWriter audit, CurrentUser currentUser) {
        this.audit = audit;
        this.currentUser = currentUser;
    }

    void write(String entityType, UUID id, String action, List<FieldChange> changes, String reason, UUID userId,
               UUID deviceId) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("mt", entityType, id.toString(), action, changes, reason,
                userId != null ? userId : principal.map(p -> p.userId()).orElse(null), null,
                deviceId != null ? deviceId : principal.map(p -> p.deviceId()).orElse(null), null));
    }

    void write(String entityType, UUID id, String action, List<FieldChange> changes, String reason) {
        write(entityType, id, action, changes, reason, null, null);
    }
}
