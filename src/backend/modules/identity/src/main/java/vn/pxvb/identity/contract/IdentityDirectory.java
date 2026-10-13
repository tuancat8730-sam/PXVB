package vn.pxvb.identity.contract;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Hợp đồng công khai của module identity cho các module khác (không dùng entity/repository của identity). */
public interface IdentityDirectory {

    enum SecretKind { PASSWORD, PIN }

    /** Mã các vai trò còn hiệu lực của người dùng. */
    Set<String> roleCodesOf(UUID userId);

    /** Người dùng đang hoạt động có vai trò (còn hiệu lực) này. */
    List<UUID> userIdsWithRole(String roleCode);

    Optional<String> fullNameOf(UUID userId);

    /** Email của các người dùng đang hoạt động (bỏ qua người chưa có email). */
    java.util.Map<UUID, String> emailsOf(java.util.Collection<UUID> userIds);

    /** Xác thực lại mật khẩu/PIN (xác nhận điện tử, FR-CC-031). Không làm thay đổi bộ đếm khóa. */
    boolean verifySecret(UUID userId, SecretKind kind, String secret);
}
