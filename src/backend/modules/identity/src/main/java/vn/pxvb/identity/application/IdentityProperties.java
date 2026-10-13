package vn.pxvb.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình bảo mật nhận diện (FR-CC-001/004). {@code jwtSecret} bắt buộc, ≥ 32 byte, lấy từ biến môi trường
 * ({@code PXVB_SECURITY_JWT_SECRET}) — không commit vào repo.
 */
@ConfigurationProperties("pxvb.security")
public record IdentityProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTtl,
        @DefaultValue("8h") Duration refreshTtl,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("365d") Duration deviceTokenTtl,
        @DefaultValue("24h") Duration activationTtl,
        String bootstrapAdminPassword) {
}
