package vn.pxvb.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pxvb.identity.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, String> {
}
