package vn.pxvb.identity.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.identity.application.UserAdminService;

@RestController
@RequestMapping("/api/v1/identity/me")
public class MeController {

    public record MeResponse(UUID userId, String username, String fullName, boolean mustChangePassword,
                             Set<String> permissions, List<DataScope> scopes) {
    }

    private final CurrentUser currentUser;
    private final UserAdminService users;

    public MeController(CurrentUser currentUser, UserAdminService users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping
    public MeResponse me() {
        PxvbPrincipal p = currentUser.require();
        var user = users.get(p.userId());
        return new MeResponse(p.userId(), p.username(), user.getFullName(), user.isMustChangePassword(),
                p.permissions(), p.scopes());
    }
}
