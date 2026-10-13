package vn.pxvb.common.audit;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;
import vn.pxvb.common.audit.AuditLogWriter.Entry;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.domain.Auditable;
import vn.pxvb.common.security.CurrentUser;

/**
 * Tự động ghi audit diff theo trường cho mọi entity {@link Auditable} (FR-CC-020). Trường chứa "hash"/"pin"
 * bị che. Chạy trong giai đoạn flush nên cùng giao dịch với thay đổi nghiệp vụ.
 */
@Component
public class HibernateAuditListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final String MASK = "***";
    private static final Set<String> IGNORED = Set.of("rowVersion", "updatedAt", "updatedBy", "createdAt",
            "createdBy");

    private final EntityManagerFactory emf;
    private final AuditLogWriter writer;
    private final CurrentUser currentUser;

    public HibernateAuditListener(EntityManagerFactory emf, AuditLogWriter writer, CurrentUser currentUser) {
        this.emf = emf;
        this.writer = writer;
        this.currentUser = currentUser;
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = emf.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (event.getEntity() instanceof Auditable) {
            record(event.getEntity(), event.getId(), "CREATE",
                    diff(event.getPersister().getPropertyNames(), null, event.getState()));
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (event.getEntity() instanceof Auditable) {
            List<FieldChange> changes = diff(event.getPersister().getPropertyNames(), event.getOldState(),
                    event.getState());
            if (!changes.isEmpty()) {
                record(event.getEntity(), event.getId(), "UPDATE", changes);
            }
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (event.getEntity() instanceof Auditable) {
            record(event.getEntity(), event.getId(), "DELETE",
                    diff(event.getPersister().getPropertyNames(), event.getDeletedState(), null));
        }
    }

    private void record(Object entity, Object id, String action, List<FieldChange> changes) {
        String[] pkg = entity.getClass().getPackageName().split("\\.");
        String module = pkg.length > 2 ? pkg[2] : "common";
        var principal = currentUser.principal();
        writer.write(new Entry(module, entity.getClass().getSimpleName(), String.valueOf(id), action, changes,
                null, principal.map(p -> p.userId()).orElse(null), null,
                principal.map(p -> p.deviceId()).orElse(null), null));
    }

    private static List<FieldChange> diff(String[] names, Object[] oldState, Object[] newState) {
        List<FieldChange> out = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (IGNORED.contains(name)) {
                continue;
            }
            Object before = oldState == null ? null : oldState[i];
            Object after = newState == null ? null : newState[i];
            if (before instanceof Collection<?> || after instanceof Collection<?>) {
                continue;
            }
            if (Objects.equals(before, after)) {
                continue;
            }
            boolean sensitive = name.toLowerCase().contains("hash") || name.toLowerCase().contains("pin");
            out.add(new FieldChange(name, mask(before, sensitive), mask(after, sensitive)));
        }
        return out;
    }

    private static Object mask(Object value, boolean sensitive) {
        if (value == null) {
            return null;
        }
        return sensitive ? MASK : value.toString();
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}
