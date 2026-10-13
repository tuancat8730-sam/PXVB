package vn.pxvb.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Nền cho test tích hợp trên MySQL thật (Testcontainers). Phiên bản chọn bằng {@code -Dpxvb.test.mysql=8.4}
 * (mặc định 8.0) để kiểm thử cả 8.0 và 8.4 LTS theo SDD Q01.
 */
@SpringBootTest
@Testcontainers
abstract class AbstractIntegrationTest {

    static final String ADMIN_PASSWORD = "Adm1n#Pxvb2026!";

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            "mysql:" + System.getProperty("pxvb.test.mysql", "8.0"))
            .withDatabaseName("pxvb")
            // Migration tạo trigger append-only cho sys_audit_log cần quyền này khi bật binlog
            .withCommand("--log-bin-trust-function-creators=1", "--character-set-server=utf8mb4");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("pxvb.security.jwt-secret", () -> "test-secret-test-secret-test-secret-0123456789");
        registry.add("pxvb.security.bootstrap-admin-password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    }
}
