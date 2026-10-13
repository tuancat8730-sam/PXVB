package vn.pxvb.identity.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.common.security.PxvbPrincipal;

/** Phát hành/kiểm tra JWT HS256: access token (15') và device token (đăng nhập thiết bị tablet). */
@Service
public class TokenService {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_DEVICE = "device";
    public static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";
    private static final String ISSUER = "pxvb";
    private static final int MIN_SECRET_BYTES = 32;

    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final IdentityProperties props;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder accessDecoder;
    private final JwtDecoder deviceDecoder;

    public TokenService(IdentityProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        if (props.jwtSecret() == null || props.jwtSecret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "pxvb.security.jwt-secret phải được cấu hình (biến PXVB_SECURITY_JWT_SECRET), tối thiểu 32 byte");
        }
        SecretKey key = new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.accessDecoder = decoder(key, TYPE_ACCESS);
        this.deviceDecoder = decoder(key, TYPE_DEVICE);
    }

    private static JwtDecoder decoder(SecretKey key, String type) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<String>(CLAIM_TYPE, type::equals)));
        return d;
    }

    public JwtDecoder accessDecoder() {
        return accessDecoder;
    }

    public IssuedToken issueAccessToken(String username, UUID userId, UUID deviceId, List<String> permissions,
                                        List<DataScope> scopes, boolean mustChangePassword) {
        Instant now = clock.instant();
        Instant exp = now.plus(props.accessTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(ISSUER).issuedAt(now).expiresAt(exp)
                .subject(userId.toString()).claim(CLAIM_TYPE, TYPE_ACCESS).claim("usr", username)
                .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
                .claim("perms", permissions)
                .claim("scopes", scopes.stream().map(s -> s.type() + ":" + (s.value() == null ? "" : s.value()))
                        .toList());
        if (deviceId != null) {
            claims.claim("dev", deviceId.toString());
        }
        return new IssuedToken(encode(claims.build()), exp);
    }

    public IssuedToken issueDeviceToken(UUID deviceId) {
        Instant now = clock.instant();
        Instant exp = now.plus(props.deviceTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).issuedAt(now).expiresAt(exp)
                .subject(deviceId.toString()).claim(CLAIM_TYPE, TYPE_DEVICE).build();
        return new IssuedToken(encode(claims), exp);
    }

    /** @return deviceId trong device token hợp lệ; ném UNAUTHENTICATED nếu sai/hết hạn. */
    public UUID verifyDeviceToken(String token) {
        try {
            return UUID.fromString(deviceDecoder.decode(token).getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /** Dựng principal từ access token đã được Spring Security xác thực. */
    public static PxvbPrincipal toPrincipal(Jwt jwt) {
        List<String> perms = jwt.getClaimAsStringList("perms");
        List<String> rawScopes = jwt.getClaimAsStringList("scopes");
        List<DataScope> scopes = rawScopes == null ? List.of() : rawScopes.stream().map(s -> {
            int i = s.indexOf(':');
            String type = i < 0 ? s : s.substring(0, i);
            String value = i < 0 ? "" : s.substring(i + 1);
            return new DataScope(DataScope.Type.valueOf(type), value.isEmpty() ? null : value);
        }).toList();
        String dev = jwt.getClaimAsString("dev");
        return new PxvbPrincipal(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("usr"),
                dev == null ? null : UUID.fromString(dev), perms == null ? Set.of() : Set.copyOf(perms), scopes);
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
