package vn.pxvb.alerting.application;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.application.Repositories.NotificationRepository;
import vn.pxvb.alerting.domain.Notification;
import vn.pxvb.alerting.infrastructure.RealtimePublisher;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Thông báo trong ứng dụng (sys_notification) + đẩy realtime (FR-CC-053). */
@Service
public class NotificationService {

    public record NotificationPush(UUID id, String type, String title, String body, String deepLink, UUID alertId) {
    }

    private final NotificationRepository notifications;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, RealtimePublisher realtime, Clock clock) {
        this.notifications = notifications;
        this.realtime = realtime;
        this.clock = clock;
    }

    @Transactional
    public List<Notification> notifyUsers(Collection<UUID> userIds, String type, String title, String body,
                                          String deepLink, UUID alertId) {
        Set<UUID> distinct = new LinkedHashSet<>(userIds);
        List<Notification> created = distinct.stream()
                .map(u -> new Notification(u, type, title, body, deepLink, alertId, clock.instant())).toList();
        notifications.saveAll(created);
        created.forEach(n -> realtime.pushToUsers(List.of(n.getUserId()), "/queue/notifications",
                new NotificationPush(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getDeepLink(),
                        n.getAlertId())));
        return created;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        Notification n = notifications.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        n.markRead(clock.instant());
    }

    @Transactional
    public int markAllRead(UUID userId) {
        List<Notification> unread = notifications.findByUserIdAndReadAtIsNull(userId);
        unread.forEach(n -> n.markRead(clock.instant()));
        return unread.size();
    }
}
