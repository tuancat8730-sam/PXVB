package vn.pxvb.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.identity.domain.Role;
import vn.pxvb.identity.domain.UserRole;
import vn.pxvb.identity.infrastructure.RoleRepository;
import vn.pxvb.identity.infrastructure.UserRoleRepository;

/** Tổng hợp quyền + phạm vi dữ liệu hiệu lực của một người dùng từ các UserRole còn hiệu lực. */
@Service
public class AccessProfileService {

    public record AccessProfile(Set<String> permissions, List<DataScope> scopes, List<String> roleCodes) {
    }

    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final Clock clock;

    public AccessProfileService(UserRoleRepository userRoles, RoleRepository roles, Clock clock) {
        this.userRoles = userRoles;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccessProfile profileOf(UUID userId) {
        Instant now = clock.instant();
        List<UserRole> effective = userRoles.findByUserId(userId).stream().filter(ur -> ur.isEffective(now)).toList();
        Map<UUID, Role> byId = roles.findAllById(effective.stream().map(UserRole::getRoleId).toList()).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Set<String> permissions = new LinkedHashSet<>();
        List<DataScope> scopes = new ArrayList<>();
        List<String> roleCodes = new ArrayList<>();
        for (UserRole ur : effective) {
            Role role = byId.get(ur.getRoleId());
            if (role == null) {
                continue;
            }
            permissions.addAll(role.getPermissionCodes());
            roleCodes.add(role.getCode());
            DataScope scope = new DataScope(ur.getScopeType(), ur.getScopeValue());
            if (!scopes.contains(scope)) {
                scopes.add(scope);
            }
        }
        return new AccessProfile(permissions, scopes, roleCodes);
    }
}
