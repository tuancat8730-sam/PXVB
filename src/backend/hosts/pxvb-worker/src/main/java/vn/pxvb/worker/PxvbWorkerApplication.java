package vn.pxvb.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Host nền (SDD §2.3): outbox dispatcher; Quartz job, alert evaluator, projection sẽ bổ sung ở các epic sau.
 * Không mở cổng HTTP.
 */
@SpringBootApplication(scanBasePackages = "vn.pxvb")
@EnableScheduling
public class PxvbWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PxvbWorkerApplication.class, args);
    }
}
