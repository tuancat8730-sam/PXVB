package vn.pxvb.planning.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.pxvb.masterdata.contract.UsageChecker;

/**
 * Chặn khóa sản phẩm/máy đang có lệnh sản xuất mở (BR-MD-003, AC-MD-004): khóa SP có lệnh CONFIRMED/RUNNING bị
 * từ chối kèm danh sách lệnh; khóa SP không còn lệnh mở thì thành công.
 */
@Configuration
public class PlanningUsageChecker {

    private static final int MAX_LISTED = 10;

    @Bean
    UsageChecker productUsageInOrders(JdbcTemplate jdbc) {
        return checker("Product", jdbc, "SELECT code, business_date, shift_code FROM pl_production_order "
                + "WHERE status IN ('CONFIRMED','RUNNING') AND (product_id = UUID_TO_BIN(?) OR id IN ("
                + "SELECT order_id FROM pl_order_segment WHERE product_id = UUID_TO_BIN(?))) ORDER BY code");
    }

    @Bean
    UsageChecker machineUsageInOrders(JdbcTemplate jdbc) {
        return checker("Machine", jdbc, "SELECT code, business_date, shift_code FROM pl_production_order "
                + "WHERE status IN ('CONFIRMED','RUNNING') AND (maker_machine_id = UUID_TO_BIN(?) "
                + "OR packer_machine_id = UUID_TO_BIN(?)) ORDER BY code");
    }

    private static UsageChecker checker(String entityType, JdbcTemplate jdbc, String sql) {
        return new UsageChecker() {
            @Override
            public String entityType() {
                return entityType;
            }

            @Override
            public List<String> usages(UUID id) {
                List<String> found = new ArrayList<>();
                jdbc.query(sql, rs -> {
                    if (found.size() < MAX_LISTED) {
                        found.add("Lệnh SX " + rs.getString("code") + " (" + rs.getDate("business_date")
                                + " ca " + rs.getString("shift_code") + ")");
                    }
                }, id.toString(), id.toString());
                return found;
            }
        };
    }
}
