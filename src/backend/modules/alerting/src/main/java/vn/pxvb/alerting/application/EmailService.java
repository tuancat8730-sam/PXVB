package vn.pxvb.alerting.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.pxvb.identity.contract.IdentityDirectory;

/**
 * Kênh email nội bộ (Q22, quyết định PO): gửi cho cảnh báo CRITICAL và yêu cầu duyệt/điều chỉnh chờ duyệt.
 * Tắt mặc định ({@code pxvb.mail.enabled=false}); gửi sau khi giao dịch commit và không bao giờ làm hỏng nghiệp vụ
 * khi SMTP lỗi (chỉ ghi log).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<MailSender> sender;
    private final IdentityDirectory directory;
    private final boolean enabled;
    private final String from;
    private final String baseUrl;

    public EmailService(ObjectProvider<MailSender> sender, IdentityDirectory directory,
                        @Value("${pxvb.mail.enabled:false}") boolean enabled,
                        @Value("${pxvb.mail.from:pxvb@localhost}") String from,
                        @Value("${pxvb.mail.base-url:}") String baseUrl) {
        this.sender = sender;
        this.directory = directory;
        this.enabled = enabled;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    public void sendAfterCommit(Collection<UUID> userIds, String subject, String body, String deepLink) {
        if (!enabled || userIds.isEmpty()) {
            return;
        }
        Runnable task = () -> send(userIds, subject, body, deepLink);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void send(Collection<UUID> userIds, String subject, String body, String deepLink) {
        MailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            log.warn("Email bật nhưng chưa cấu hình SMTP (spring.mail.host); bỏ qua '{}'", subject);
            return;
        }
        Map<UUID, String> emails = directory.emailsOf(userIds);
        String text = body == null ? "" : body;
        if (deepLink != null && !baseUrl.isBlank()) {
            text += "\n\n" + baseUrl + deepLink;
        }
        for (String address : emails.values()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(address);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
            } catch (MailException e) {
                log.warn("Không gửi được email tới {}: {}", address, e.toString());
            }
        }
    }
}
