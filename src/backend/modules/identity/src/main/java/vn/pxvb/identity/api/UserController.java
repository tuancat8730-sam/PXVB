package vn.pxvb.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.common.web.PageResponse;
import vn.pxvb.identity.application.UserAdminService;
import vn.pxvb.identity.application.UserAdminService.CreateUser;
import vn.pxvb.identity.application.UserAdminService.RoleAssignment;
import vn.pxvb.identity.domain.User;

@RestController
@RequestMapping("/api/v1/identity/users")
public class UserController {

    private static final int MAX_PAGE_SIZE = 200;

    public record UserDto(UUID id, String username, String fullName, String employeeCode, String badgeNo,
                          UUID orgUnitId, String email, String phone, boolean active, boolean locked,
                          boolean mustChangePassword, Instant lastLoginAt, Long rowVersion) {
        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmployeeCode(), u.getBadgeNo(),
                    u.getOrgUnitId(), u.getEmail(), u.getPhone(), u.isActive(),
                    u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now()),
                    u.isMustChangePassword(), u.getLastLoginAt(), u.getRowVersion());
        }
    }

    public record CreateUserRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập") @Size(max = 100) String username,
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 200) String fullName,
            @Size(max = 50) String employeeCode, @Size(max = 50) String badgeNo, UUID orgUnitId, String email,
            String phone,
            @NotBlank(message = "Vui lòng nhập mật khẩu khởi tạo") String initialPassword) {
    }

    public record UpdateUserRequest(@NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 200) String fullName,
                                    @Size(max = 50) String employeeCode, @Size(max = 50) String badgeNo,
                                    UUID orgUnitId, String email, String phone) {
    }

    public record RoleAssignmentRequest(@NotBlank String roleCode, DataScope.Type scopeType, String scopeValue,
                                        Instant validFrom, Instant validTo) {
    }

    public record AssignRolesRequest(@Valid List<RoleAssignmentRequest> roles) {
    }

    public record PasswordRequest(@NotBlank(message = "Vui lòng nhập mật khẩu mới") String newPassword) {
    }

    public record PinRequest(@NotBlank(message = "Vui lòng nhập PIN") String pin) {
    }

    private final UserAdminService service;

    public UserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.user.view')")
    public PageResponse<UserDto> search(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return PageResponse.of(service.search(q, PageRequest.of(Math.max(page, 0), size, Sort.by("username"))),
                UserDto::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('identity.user.view')")
    public UserDto get(@PathVariable UUID id) {
        return UserDto.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('identity.user.create')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return UserDto.from(service.create(new CreateUser(req.username(), req.fullName(), req.employeeCode(),
                req.badgeNo(), req.orgUnitId(), req.email(), req.phone(), req.initialPassword())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('identity.user.update')")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return UserDto.from(service.updateProfile(id, req.fullName(), req.employeeCode(), req.badgeNo(),
                req.orgUnitId(), req.email(), req.phone()));
    }

    @PutMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity.user.update')")
    public void assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest req) {
        List<RoleAssignment> assignments = req.roles() == null ? List.of() : req.roles().stream()
                .map(r -> new RoleAssignment(r.roleCode(), r.scopeType(), r.scopeValue(), r.validFrom(),
                        r.validTo())).toList();
        service.assignRoles(id, assignments);
    }

    @PostMapping("/{id}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity.user.lock')")
    public void lock(@PathVariable UUID id) {
        service.lock(id);
    }

    @PostMapping("/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity.user.lock')")
    public void unlock(@PathVariable UUID id) {
        service.unlock(id);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity.user.update')")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordRequest req) {
        service.resetPassword(id, req.newPassword());
    }

    @PutMapping("/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('identity.user.update')")
    public void setPin(@PathVariable UUID id, @Valid @RequestBody PinRequest req) {
        service.setPin(id, req.pin());
    }
}
