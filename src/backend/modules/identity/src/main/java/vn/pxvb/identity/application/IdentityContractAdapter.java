package vn.pxvb.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.identity.contract.AccessTokenVerifier;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.identity.domain.User;
import vn.pxvb.identity.domain.UserRole;
import vn.pxvb.identity.infrastructure.RoleRepository;
import vn.pxvb.identity.infrastructure.UserRepository;
import vn.pxvb.identity.infrastructure.UserRoleRepository;

/** Hiện thực các contract công khai của identity. */
@Service
public class IdentityContractAdapter implements IdentityDirectory, AccessTokenVerifier {

    private final AccessProfileService profiles;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final Clock clock;

    public IdentityContractAdapter(AccessProfileService profiles, UserRepository users, RoleRepository roles,
                                   UserRoleRepository userRoles, PasswordEncoder encoder, TokenService tokens,
                                   Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.encoder = encoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> roleCodesOf(UUID userId) {
        return Set.copyOf(profiles.profileOf(userId).roleCodes());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> userIdsWithRole(String roleCode) {
        Instant now = clock.instant();
        return roles.findByCode(roleCode).map(role -> {
            List<UUID> candidates = userRoles.findByRoleId(role.getId()).stream()
                    .filter(ur -> ur.isEffective(now)).map(UserRole::getUserId).distinct().toList();
            return users.findAllById(candidates).stream().filter(User::isActive).map(User::getId).toList();
        }).orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> fullNameOf(UUID userId) {
        return users.findById(userId).map(User::getFullName);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifySecret(UUID userId, SecretKind kind, String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        return users.findById(userId).filter(User::isActive).map(u -> {
            String hash = kind == SecretKind.PIN ? u.getPinHash() : u.getPasswordHash();
            return hash != null && encoder.matches(secret, hash);
        }).orElse(false);
    }

    @Override
    public Optional<PxvbPrincipal> verify(String bearerToken) {
        try {
            var jwt = tokens.accessDecoder().decode(bearerToken);
            if (Boolean.TRUE.equals(jwt.getClaim(TokenService.CLAIM_MUST_CHANGE_PASSWORD))) {
                return Optional.empty();
            }
            return Optional.of(TokenService.toPrincipal(jwt));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
