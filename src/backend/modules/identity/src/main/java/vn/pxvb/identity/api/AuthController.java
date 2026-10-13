package vn.pxvb.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.identity.application.AuthService;
import vn.pxvb.identity.application.AuthService.TokenPair;

@RestController
@RequestMapping("/api/v1/identity/auth")
public class AuthController {

    public record LoginRequest(@NotBlank(message = "Vui lòng nhập tên đăng nhập") String username,
                               @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {
    }

    public record PinLoginRequest(@NotBlank(message = "Vui lòng nhập mã nhân viên hoặc quét thẻ") String identifier,
                                  @NotBlank(message = "Vui lòng nhập PIN") String pin) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
                                        @NotBlank(message = "Vui lòng nhập mật khẩu mới") String newPassword) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, Instant accessExpiresAt,
                                boolean mustChangePassword) {
        static TokenResponse of(TokenPair p) {
            return new TokenResponse(p.accessToken(), p.refreshToken(), "Bearer", p.accessExpiresAt(),
                    p.mustChangePassword());
        }
    }

    private final AuthService auth;
    private final CurrentUser currentUser;

    public AuthController(AuthService auth, CurrentUser currentUser) {
        this.auth = auth;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return TokenResponse.of(auth.login(req.username(), req.password()));
    }

    @PostMapping("/pin-login")
    public TokenResponse pinLogin(@RequestHeader("X-Device-Token") String deviceToken,
                                  @Valid @RequestBody PinLoginRequest req) {
        return TokenResponse.of(auth.pinLogin(deviceToken, req.identifier(), req.pin()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return TokenResponse.of(auth.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(currentUser.require().userId(), req.currentPassword(), req.newPassword());
    }
}
