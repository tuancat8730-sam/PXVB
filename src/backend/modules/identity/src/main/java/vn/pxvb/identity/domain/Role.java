package vn.pxvb.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import vn.pxvb.common.domain.AuditableEntity;

/** Vai trò (sys_role) gắn nhiều quyền; mã vai trò theo SDD §1.4. */
@Entity
@Table(name = "sys_role")
public class Role extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sys_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", length = 100)
    private Set<String> permissionCodes = new HashSet<>();

    protected Role() {
    }

    public Role(String code, String name, boolean system) {
        this.code = code;
        this.name = name;
        this.system = system;
    }

    public void replacePermissions(Set<String> codes) {
        permissionCodes.clear();
        permissionCodes.addAll(codes);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<String> getPermissionCodes() {
        return Set.copyOf(permissionCodes);
    }
}
