package vn.pxvb.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.identity.application.AccessProfileService.AccessProfile;
import vn.pxvb.identity.application.TokenService.IssuedToken;
import vn.pxvb.identity.domain.AuthSource;
import vn.pxvb.identity.domain.Device;
import vn.pxvb.identity.domain.RefreshToken;
import vn.pxvb.identity.domain.User;
import vn.pxvb.identity.infrastructure.DeviceRepository;
import vn.pxvb.identity.infrastructure.RefreshTokenRepository;
import vn.pxvb.identity.infrastructure.UserRepository;

/**
 * Đăng nhập/làm mới/đăng xuất/đổi mật khẩu/đăng nhập PIN trên tablet (FR-CC-001, FR-CC-002, FR-CC-004).
 * Các luồng đăng nhập dùng {@code noRollbackFor} để bộ đếm sai + audit thất bại vẫn được lưu khi ném lỗi.
 */
@Service
public class AuthService {

    public record TokenPair(String accessToken, String refreshToken, Instant accessExpiresAt,
                            boolean mustChangePassword) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final DeviceRepository devices;
    private final AccessProfileService profiles;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditLogWriter audit;
    private final IdentityProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, DeviceRepository devices,
                       AccessProfileService profiles, TokenService tokens, PasswordEncoder encoder,
                       PasswordPolicy policy, AuditLogWriter audit, IdentityProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.devices = devices;
        this.profiles = profiles;
        this.tokens = tokens;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("pxvb-timing-equalizer");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair login(String username, String password) {
        User user = users.findByUsername(username).orElse(null);
        if (user == null || !user.isActive() || user.getAuthSource() != AuthSource.LOCAL) {
            encoder.matches(password, dummyHash);
            auditLogin("LOGIN_FAILED", username, null, null, "Sai thông tin đăng nhập hoặc tài khoản không hoạt động");
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        verifyCredential(user, encoder.matches(password, user.getPasswordHash()), null, "LOGIN_FAILED");
        user.registerSuccess(clock.instant());
        auditLogin("LOGIN", user.getUsername(), user.getId(), null, null);
        return issue(user, null);
    }

    /** Đăng nhập nhanh trên tablet: mã nhân viên hoặc số thẻ + PIN, trên thiết bị đã kích hoạt. */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair pinLogin(String deviceToken, String identifier, String pin) {
        UUID deviceId = tokens.verifyDeviceToken(deviceToken);
        Device device = devices.findById(deviceId).filter(Device::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        User user = users.findByEmployeeCode(identifier).or(() -> users.findByBadgeNo(identifier)).orElse(null);
        if (user == null || !user.isActive() || user.getPinHash() == null) {
            encoder.matches(pin, dummyHash);
            auditLogin("LOGIN_FAILED", identifier, null, deviceId, "PIN: không tìm thấy người dùng");
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        verifyCredential(user, encoder.matches(pin, user.getPinHash()), deviceId, "LOGIN_FAILED");
        Instant now = clock.instant();
        user.registerSuccess(now);
        device.touch(now);
        auditLogin("LOGIN", user.getUsername(), user.getId(), deviceId, "PIN");
        return issue(user, deviceId);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (current.isRevoked()) {
            // Dùng lại token đã xoay: nghi ngờ bị đánh cắp → thu hồi toàn bộ phiên của người dùng.
            refreshTokens.revokeAllForUser(current.getUserId(), now);
            auditLogin("REFRESH_REUSE", current.getUserId().toString(), current.getUserId(), current.getDeviceId(),
                    "Refresh token đã bị thu hồi được dùng lại");
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (current.isExpired(now)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        User user = users.findById(current.getUserId()).filter(u -> u.isActive() && !u.isLocked(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        TokenPair pair = issue(user, current.getDeviceId());
        RefreshToken replacement = refreshTokens.findByTokenHash(hash(pair.refreshToken())).orElseThrow();
        current.revoke(now, replacement.getId());
        return pair;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.revoke(clock.instant(), null);
            }
        });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "Mật khẩu hiện tại không đúng");
        }
        policy.validatePassword(newPassword, user.getUsername(), "newPassword");
        user.changePassword(encoder.encode(newPassword), false);
        refreshTokens.revokeAllForUser(userId, clock.instant());
        auditLogin("PASSWORD_CHANGED", user.getUsername(), userId, null, null);
    }

    private void verifyCredential(User user, boolean matches, UUID deviceId, String failAction) {
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            auditLogin(failAction, user.getUsername(), user.getId(), deviceId, "Tài khoản đang bị khóa");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!matches) {
            boolean lockedNow = user.registerFailure(now, props.maxFailedLogins(), props.lockDuration());
            auditLogin(failAction, user.getUsername(), user.getId(), deviceId,
                    lockedNow ? "Sai thông tin, tài khoản bị khóa tạm thời" : "Sai thông tin đăng nhập");
            throw new BusinessException(lockedNow ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.AUTH_FAILED);
        }
    }

    private TokenPair issue(User user, UUID deviceId) {
        AccessProfile profile = profiles.profileOf(user.getId());
        IssuedToken access = tokens.issueAccessToken(user.getUsername(), user.getId(), deviceId,
                List.copyOf(profile.permissions()), profile.scopes(), user.isMustChangePassword());
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(user.getId(), hash(raw), deviceId, now, now.plus(props.refreshTtl())));
        return new TokenPair(access.value(), raw, access.expiresAt(), user.isMustChangePassword());
    }

    private void auditLogin(String action, String subject, UUID userId, UUID deviceId, String reason) {
        audit.write(new AuditLogWriter.Entry("identity", "User", userId != null ? userId.toString() : subject, action,
                subject == null ? List.of() : List.of(new AuditLogWriter.FieldChange("username", null, subject)),
                reason, userId, null, deviceId, null));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
