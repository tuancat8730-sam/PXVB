package vn.pxvb.alerting.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.alerting.application.NotificationService;
import vn.pxvb.alerting.domain.Notification;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.web.PageResponse;

/** Thông báo của chính người dùng đăng nhập. */
@RestController
@RequestMapping("/api/v1/alerting/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    public record NotificationDto(UUID id, String type, String title, String body, String deepLink, UUID alertId,
                                  Instant createdAt, Instant readAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getDeepLink(),
                    n.getAlertId(), n.getCreatedAt(), n.getReadAt());
        }
    }

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100));
        return PageResponse.of(service.list(currentUser.require().userId(), pageable), NotificationDto::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", service.unreadCount(currentUser.require().userId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable UUID id) {
        service.markRead(id, currentUser.require().userId());
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", service.markAllRead(currentUser.require().userId()));
    }
}
