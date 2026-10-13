package vn.pxvb.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Truy cập người dùng hiện tại từ SecurityContext (SDD §2.4 CurrentUser). */
@Component
public class CurrentUser {

    public Optional<PxvbPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PxvbPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public Optional<UUID> userId() {
        return principal().map(PxvbPrincipal::userId);
    }

    public Optional<UUID> deviceId() {
        return principal().map(PxvbPrincipal::deviceId);
    }

    public PxvbPrincipal require() {
        return principal().orElseThrow(() ->
                new vn.pxvb.common.error.BusinessException(vn.pxvb.common.error.ErrorCode.UNAUTHENTICATED));
    }
}
