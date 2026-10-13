package vn.pxvb.alerting.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertHistory;
import vn.pxvb.alerting.domain.AlertRecipient;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.alerting.domain.Notification;

/** Các Spring Data repository của module alerting. */
public final class Repositories {

    private Repositories() {
    }

    public interface RuleRepository extends JpaRepository<AlertRule, UUID> {
        Optional<AlertRule> findByCode(String code);
    }

    public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {
        Optional<Alert> findFirstByDedupKeyAndStatusInOrderByRaisedAtDesc(String dedupKey,
                                                                         Collection<Alert.Status> statuses);

        List<Alert> findByStatus(Alert.Status status);
    }

    public interface HistoryRepository extends JpaRepository<AlertHistory, UUID> {
        List<AlertHistory> findByAlertIdOrderByAt(UUID alertId);
    }

    public interface RecipientRepository extends JpaRepository<AlertRecipient, UUID> {
        List<AlertRecipient> findByAlertId(UUID alertId);
    }

    public interface NotificationRepository extends JpaRepository<Notification, UUID> {
        Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

        long countByUserIdAndReadAtIsNull(UUID userId);

        List<Notification> findByUserIdAndReadAtIsNull(UUID userId);
    }
}
