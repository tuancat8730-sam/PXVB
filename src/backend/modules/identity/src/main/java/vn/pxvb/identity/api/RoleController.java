package vn.pxvb.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.identity.application.UserAdminService;
import vn.pxvb.identity.domain.Permission;
import vn.pxvb.identity.domain.Role;
import vn.pxvb.identity.infrastructure.PermissionRepository;
import vn.pxvb.identity.infrastructure.RoleRepository;

@RestController
@RequestMapping("/api/v1/identity")
public class RoleController {

    public record RoleDto(UUID id, String code, String name, boolean system, Set<String> permissions) {
        static RoleDto from(Role r) {
            return new RoleDto(r.getId(), r.getCode(), r.getName(), r.isSystem(), r.getPermissionCodes());
        }
    }

    public record PermissionDto(String code, String module, String description) {
        static PermissionDto from(Permission p) {
            return new PermissionDto(p.getCode(), p.getModule(), p.getDescription());
        }
    }

    public record UpdatePermissionsRequest(@NotNull(message = "Thiếu danh sách quyền") Set<String> permissions) {
    }

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserAdminService service;

    public RoleController(RoleRepository roles, PermissionRepository permissions, UserAdminService service) {
        this.roles = roles;
        this.permissions = permissions;
        this.service = service;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('identity.role.view')")
    public List<RoleDto> roles() {
        return roles.findAll().stream().map(RoleDto::from).toList();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('identity.role.view')")
    public List<PermissionDto> permissions() {
        return permissions.findAll().stream().map(PermissionDto::from).toList();
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('identity.role.update')")
    public RoleDto updatePermissions(@PathVariable UUID id, @Valid @RequestBody UpdatePermissionsRequest req) {
        return RoleDto.from(service.updateRolePermissions(id, req.permissions()));
    }
}
