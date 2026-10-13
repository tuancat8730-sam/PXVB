package vn.pxvb.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/**
 * Chính sách mật khẩu (FR-CC-004): ≥ 10 ký tự, có ít nhất 3 trong 4 nhóm (thường/hoa/số/ký tự đặc biệt),
 * không chứa tên đăng nhập. PIN tablet: 4–6 chữ số.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    public void validatePassword(String password, String username, String field) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("Mật khẩu phải có tối thiểu " + MIN_LENGTH + " ký tự");
        } else {
            int classes = 0;
            classes += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
            classes += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
            classes += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
            classes += password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0;
            if (classes < 3) {
                problems.add("Mật khẩu phải gồm ít nhất 3 trong 4 nhóm: chữ thường, chữ hoa, số, ký tự đặc biệt");
            }
            if (username != null && !username.isBlank()
                    && password.toLowerCase().contains(username.toLowerCase())) {
                problems.add("Mật khẩu không được chứa tên đăng nhập");
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mật khẩu không đạt yêu cầu",
                    Map.of(field, problems));
        }
    }

    public void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCode.VALIDATION, "PIN không hợp lệ",
                    Map.of("pin", List.of("PIN phải gồm 4–6 chữ số")));
        }
    }
}
