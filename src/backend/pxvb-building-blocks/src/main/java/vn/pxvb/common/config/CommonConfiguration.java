package vn.pxvb.common.config;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import vn.pxvb.common.security.CurrentUser;

/** Cấu hình nền tảng: Clock (UTC, thay được khi test), auditor cho CreatedBy/UpdatedBy, JPA scan. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EntityScan("vn.pxvb")
@EnableJpaRepositories("vn.pxvb")
public class CommonConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuditorAware<UUID> auditorAware(CurrentUser currentUser) {
        return () -> Optional.ofNullable(currentUser.userId().orElse(null));
    }
}
