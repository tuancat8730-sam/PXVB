package vn.pxvb.identity.application;

import java.util.List;
import java.util.Locale;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;

/**
 * Che giá trị của trường nhạy cảm khi trả nhật ký kiểm toán (FR-CC-020). Ghi audit thủ công có thể quên che,
 * nên API tra cứu che lại lần nữa theo tên trường.
 */
final class AuditFieldMasker {

    static final String MASK = "***";
    private static final List<String> SENSITIVE_MARKERS = List.of("hash", "pin", "password", "secret", "token");

    private AuditFieldMasker() {
    }

    static List<FieldChange> mask(List<FieldChange> changes) {
        return changes.stream().map(AuditFieldMasker::maskOne).toList();
    }

    static boolean isSensitive(String field) {
        String lower = field.toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(lower::contains);
    }

    private static FieldChange maskOne(FieldChange change) {
        if (!isSensitive(change.field())) {
            return change;
        }
        return new FieldChange(change.field(), MASK, MASK);
    }
}
