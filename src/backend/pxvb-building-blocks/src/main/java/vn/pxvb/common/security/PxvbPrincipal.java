package vn.pxvb.common.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Principal dùng chung trong SecurityContext (do module identity dựng từ JWT).
 * {@code deviceId} khác null khi thao tác từ tablet đã đăng ký; {@code userId} là người đang đăng nhập
 * (không phải tài khoản thiết bị — FR-CC-002).
 */
public record PxvbPrincipal(UUID userId, String username, UUID deviceId, Set<String> permissions,
                            List<DataScope> scopes) {

    public PxvbPrincipal {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
