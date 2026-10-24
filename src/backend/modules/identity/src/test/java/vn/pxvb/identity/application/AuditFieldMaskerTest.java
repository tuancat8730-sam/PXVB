package vn.pxvb.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;

class AuditFieldMaskerTest {

    @Test
    void masksValuesOfSensitiveFieldsOnly() {
        // Arrange
        List<FieldChange> changes = List.of(
                new FieldChange("pinHash", "abc", "def"),
                new FieldChange("passwordHash", "x", "y"),
                new FieldChange("username", "admin", "ops"));

        // Act
        List<FieldChange> masked = AuditFieldMasker.mask(changes);

        // Assert
        assertThat(masked.get(0)).isEqualTo(new FieldChange("pinHash", "***", "***"));
        assertThat(masked.get(1)).isEqualTo(new FieldChange("passwordHash", "***", "***"));
        assertThat(masked.get(2)).isEqualTo(changes.get(2));
    }

    @Test
    void returnsEmptyListUnchanged() {
        assertThat(AuditFieldMasker.mask(List.of())).isEmpty();
    }
}
