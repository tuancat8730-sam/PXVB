package vn.pxvb.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Host HTTP + WebSocket (SDD §2.3). Quét toàn bộ {@code vn.pxvb} để nạp các module (identity, workflow, alerting).
 * Tạm thời chạy cả các job quét/outbox vì worker chưa nạp module nghiệp vụ (xem docs/OPEN-QUESTIONS.md L11).
 */
@SpringBootApplication(scanBasePackages = "vn.pxvb")
@EnableScheduling
public class PxvbApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PxvbApiApplication.class, args);
    }
}
