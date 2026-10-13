package vn.pxvb.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.pxvb.common.error.BusinessException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validatePassword("Str0ng#Passw0rd", "tuan", "password"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> policy.validatePassword("Ab1#xyz", "tuan", "password"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsPasswordWithTooFewCharacterClasses() {
        assertThatThrownBy(() -> policy.validatePassword("alllowercaseletters", "tuan", "password"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> policy.validatePassword("Tuan-Is-Here-123", "tuan", "password"))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "123456", "000000"})
    void acceptsValidPins(String pin) {
        assertThatCode(() -> policy.validatePin(pin)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1234567", "12a4", ""})
    void rejectsInvalidPins(String pin) {
        assertThatThrownBy(() -> policy.validatePin(pin)).isInstanceOf(BusinessException.class);
    }
}
