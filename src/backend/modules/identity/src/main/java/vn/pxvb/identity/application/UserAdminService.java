package vn.pxvb.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.identity.domain.Role;
import vn.pxvb.identity.domain.User;
import vn.pxvb.identity.domain.UserRole;
import vn.pxvb.identity.infrastructure.PermissionRepository;
import vn.pxvb.identity.infrastructure.RefreshTokenRepository;
import vn.pxvb.identity.infrastructure.RoleRepository;
import vn.pxvb.identity.infrastructure.UserRepository;
import vn.pxvb.identity.infrastructure.UserRoleRepository;

/** Quản trị người dùng, gán vai trò/quyền (FR-CC-003). Mọi thay đổi quyền ghi audit tường minh. */
@Service
public class UserAdminService {

    public record CreateUser(String username, String fullName, String employeeCode, String badgeNo,
                             UUID orgUnitId, String email, String phone, String initialPassword) {
    }

    public record RoleAssignment(String roleCode, DataScope.Type scopeType, String scopeValue, Instant validFrom,
                                 Instant validTo) {
    }

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PermissionRepository permissions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public UserAdminService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
                            PermissionRepository permissions, RefreshTokenRepository refreshTokens,
                            PasswordEncoder encoder, PasswordPolicy policy, AuditLogWriter audit,
                            CurrentUser currentUser, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.permissions = permissions;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<User> search(String q, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        return users.findByUsernameContainingOrFullNameContaining(term, term, pageable);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public User create(CreateUser cmd) {
        if (users.existsByUsername(cmd.username())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Tên đăng nhập đã tồn tại",
                    Map.of("username", List.of("Tên đăng nhập đã tồn tại")));
        }
        policy.validatePassword(cmd.initialPassword(), cmd.username(), "initialPassword");
        User user = User.create(cmd.username(), cmd.fullName(), cmd.employeeCode(),
                encoder.encode(cmd.initialPassword()));
        user.updateProfile(cmd.fullName(), cmd.employeeCode(), cmd.badgeNo(), cmd.orgUnitId(), cmd.email(),
                cmd.phone());
        return users.save(user);
    }

    @Transactional
    public User updateProfile(UUID id, String fullName, String employeeCode, String badgeNo, UUID orgUnitId,
                              String email, String phone) {
        User user = get(id);
        user.updateProfile(fullName, employeeCode, badgeNo, orgUnitId, email, phone);
        return user;
    }

    @Transactional
    public List<UserRole> assignRoles(UUID userId, List<RoleAssignment> assignments) {
        get(userId);
        List<String> before = userRoles.findByUserId(userId).stream()
                .map(ur -> roles.findById(ur.getRoleId()).map(Role::getCode).orElse("?") + describe(ur))
                .sorted().toList();
        userRoles.deleteByUserId(userId);
        userRoles.flush();
        List<UserRole> created = new ArrayList<>();
        for (RoleAssignment a : assignments) {
            Role role = roles.findByCode(a.roleCode()).orElseThrow(() -> new BusinessException(
                    ErrorCode.VALIDATION, "Vai trò không tồn tại: " + a.roleCode()));
            DataScope.Type type = a.scopeType() == null ? DataScope.Type.ALL : a.scopeType();
            if (type != DataScope.Type.ALL && (a.scopeValue() == null || a.scopeValue().isBlank())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Phạm vi " + type + " cần có giá trị");
            }
            created.add(userRoles.save(new UserRole(userId, role.getId(), type, a.scopeValue(), a.validFrom(),
                    a.validTo())));
        }
        List<String> after = created.stream()
                .map(ur -> roles.findById(ur.getRoleId()).map(Role::getCode).orElse("?") + describe(ur))
                .sorted().toList();
        audit.write(new AuditLogWriter.Entry("identity", "User", userId.toString(), "ROLES_CHANGED",
                List.of(new FieldChange("roles", before, after)), null, currentUser.userId().orElse(null), null,
                currentUser.deviceId().orElse(null), null));
        refreshTokens.revokeAllForUser(userId, clock.instant());
        return created;
    }

    @Transactional
    public void lock(UUID id) {
        get(id).deactivate();
        refreshTokens.revokeAllForUser(id, clock.instant());
    }

    @Transactional
    public void unlock(UUID id) {
        get(id).unlock();
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User user = get(id);
        policy.validatePassword(newPassword, user.getUsername(), "newPassword");
        user.changePassword(encoder.encode(newPassword), true);
        refreshTokens.revokeAllForUser(id, clock.instant());
    }

    @Transactional
    public void setPin(UUID id, String pin) {
        policy.validatePin(pin);
        get(id).changePin(encoder.encode(pin));
    }

    @Transactional
    public Role updateRolePermissions(UUID roleId, Set<String> codes) {
        Role role = roles.findById(roleId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Set<String> known = permissions.findAllById(codes).stream().map(p -> p.getCode())
                .collect(Collectors.toSet());
        if (!known.containsAll(codes)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Có mã quyền không tồn tại");
        }
        List<String> before = role.getPermissionCodes().stream().sorted().toList();
        role.replacePermissions(codes);
        audit.write(new AuditLogWriter.Entry("identity", "Role", roleId.toString(), "PERMISSIONS_CHANGED",
                List.of(new FieldChange("permissions", before, codes.stream().sorted().toList())), null,
                currentUser.userId().orElse(null), null, currentUser.deviceId().orElse(null), null));
        return role;
    }

    private static String describe(UserRole ur) {
        return ur.getScopeType() == DataScope.Type.ALL ? "" : "[" + ur.getScopeType() + ":" + ur.getScopeValue() + "]";
    }
}
