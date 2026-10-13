package vn.pxvb.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/** Thay SMTP bằng bộ gom thư trong bộ nhớ để kiểm thử kênh email. */
@TestConfiguration
class MailTestConfig {

    static final List<SimpleMailMessage> SENT = new CopyOnWriteArrayList<>();

    @Bean
    MailSender capturingMailSender() {
        return new MailSender() {
            @Override
            public void send(SimpleMailMessage simpleMessage) throws MailException {
                SENT.add(simpleMessage);
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) throws MailException {
                SENT.addAll(List.of(simpleMessages));
            }
        };
    }
}
