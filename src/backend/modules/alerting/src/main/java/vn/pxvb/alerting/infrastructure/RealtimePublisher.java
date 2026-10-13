package vn.pxvb.alerting.infrastructure;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Đẩy realtime tới người dùng qua STOMP (FR-CC-053): {@code /user/queue/alerts}, {@code /user/queue/notifications}.
 * Chỉ hoạt động trong tiến trình có WebSocket (API host); ở nơi khác là no-op — client luôn có polling 60s dự phòng.
 * Việc đẩy chạy sau khi giao dịch commit để không báo về dữ liệu có thể bị rollback.
 */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final ObjectProvider<SimpMessagingTemplate> template;

    public RealtimePublisher(ObjectProvider<SimpMessagingTemplate> template) {
        this.template = template;
    }

    public void pushToUsers(Collection<java.util.UUID> userIds, String destination, Object payload) {
        runAfterCommit(() -> {
            SimpMessagingTemplate t = template.getIfAvailable();
            if (t == null) {
                return;
            }
            for (java.util.UUID userId : userIds) {
                try {
                    t.convertAndSendToUser(userId.toString(), destination, payload);
                } catch (RuntimeException e) {
                    log.warn("Không đẩy được realtime tới {}: {}", userId, e.toString());
                }
            }
        });
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
