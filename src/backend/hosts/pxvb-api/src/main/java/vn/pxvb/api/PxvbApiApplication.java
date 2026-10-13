package vn.pxvb.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Host HTTP + WebSocket (SDD §2.3). Quét toàn bộ {@code vn.pxvb} để nạp các module (identity, …). */
@SpringBootApplication(scanBasePackages = "vn.pxvb")
public class PxvbApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PxvbApiApplication.class, args);
    }
}
