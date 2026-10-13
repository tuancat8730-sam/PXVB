package vn.pxvb.identity.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import vn.pxvb.common.security.PermissionContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.identity.domain.OrgUnit;
import vn.pxvb.identity.domain.Permission;
import vn.pxvb.identity.domain.Role;
import vn.pxvb.identity.domain.User;
import vn.pxvb.identity.domain.UserRole;
import vn.pxvb.identity.infrastructure.OrgUnitRepository;
import vn.pxvb.identity.infrastructure.PermissionRepository;
import vn.pxvb.identity.infrastructure.RoleRepository;
import vn.pxvb.identity.infrastructure.UserRepository;
import vn.pxvb.identity.infrastructure.UserRoleRepository;

/**
 * Seed idempotent: quyền, vai trò, đơn vị và (tùy chọn) tài khoản quản trị đầu tiên khi cấu hình
 * {@code pxvb.security.bootstrap-admin-password}. Vai trò SYS_ADMIN luôn được đồng bộ đủ quyền catalog.
 */
@Component
public class IdentitySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentitySeeder.class);
    private static final String ADMIN_USERNAME = "admin";

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final OrgUnitRepository orgUnits;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final IdentityProperties props;
    private final TransactionTemplate tx;
    private final List<PermissionContributor> contributors;

    public IdentitySeeder(PermissionRepository permissions, RoleRepository roles, OrgUnitRepository orgUnits,
                          UserRepository users, UserRoleRepository userRoles, PasswordEncoder encoder,
                          PasswordPolicy policy, IdentityProperties props, TransactionTemplate tx,
                          List<PermissionContributor> contributors) {
        this.contributors = contributors;
        this.permissions = permissions;
        this.roles = roles;
        this.orgUnits = orgUnits;
        this.users = users;
        this.userRoles = userRoles;
        this.encoder = encoder;
        this.policy = policy;
        this.props = props;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> seed());
    }

    void seed() {
        for (PermissionCatalog.PermissionDef def : PermissionCatalog.PERMISSIONS) {
            if (!permissions.existsById(def.code())) {
                permissions.save(new Permission(def.code(), def.module(), def.description()));
            }
        }
        PermissionCatalog.ORG_UNITS.forEach((code, name) -> {
            if (orgUnits.findByCode(code).isEmpty()) {
                orgUnits.save(new OrgUnit(code, name));
            }
        });
        PermissionCatalog.ROLES.forEach((code, name) -> {
            if (roles.findByCode(code).isEmpty()) {
                roles.save(new Role(code, name, true));
            }
        });
        Set<String> all = PermissionCatalog.PERMISSIONS.stream().map(PermissionCatalog.PermissionDef::code)
                .collect(Collectors.toSet());
        Role admin = roles.findByCode("SYS_ADMIN").orElseThrow();
        // Chỉ bổ sung quyền identity còn thiếu; không được thu hồi quyền do module khác/quản trị đã cấp.
        if (!admin.getPermissionCodes().containsAll(all)) {
            Set<String> merged = new HashSet<>(admin.getPermissionCodes());
            merged.addAll(all);
            admin.replacePermissions(merged);
        }
        seedContributedPermissions();
        bootstrapAdmin(admin);
    }

    /** Quyền do các module khác khai báo; gán vai trò mặc định đúng một lần khi quyền mới xuất hiện. */
    private void seedContributedPermissions() {
        for (PermissionContributor contributor : contributors) {
            for (PermissionContributor.PermissionDef def : contributor.permissions()) {
                if (permissions.existsById(def.code())) {
                    continue;
                }
                permissions.save(new Permission(def.code(), def.module(), def.description()));
                for (String roleCode : def.defaultRoles()) {
                    roles.findByCode(roleCode).ifPresent(role -> {
                        Set<String> granted = new HashSet<>(role.getPermissionCodes());
                        granted.add(def.code());
                        role.replacePermissions(granted);
                    });
                }
            }
        }
        permissions.flush();
    }

    private void bootstrapAdmin(Role adminRole) {
        String password = props.bootstrapAdminPassword();
        if (password == null || password.isBlank() || users.existsByUsername(ADMIN_USERNAME)) {
            return;
        }
        policy.validatePassword(password, ADMIN_USERNAME, "bootstrapAdminPassword");
        User admin = users.save(User.create(ADMIN_USERNAME, "Quản trị hệ thống", null, encoder.encode(password)));
        userRoles.save(new UserRole(admin.getId(), adminRole.getId(), DataScope.Type.ALL, null, null, null));
        log.warn("Đã tạo tài khoản quản trị ban đầu '{}' (bắt buộc đổi mật khẩu ở lần đăng nhập đầu).",
                ADMIN_USERNAME);
    }
}
