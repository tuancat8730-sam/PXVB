package vn.pxvb.common.security;

import java.util.List;
import java.util.Set;

/**
 * Mỗi module khai báo quyền của mình bằng bean này; module identity seed vào {@code sys_permission}.
 * {@code defaultRoles} chỉ được gán một lần khi quyền mới được tạo (không ghi đè chỉnh sửa của quản trị).
 */
public interface PermissionContributor {

    record PermissionDef(String code, String module, String description, Set<String> defaultRoles) {
        public PermissionDef {
            defaultRoles = defaultRoles == null ? Set.of() : Set.copyOf(defaultRoles);
        }
    }

    List<PermissionDef> permissions();
}
