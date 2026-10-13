package vn.pxvb.masterdata.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.masterdata.application.DataQualityService;
import vn.pxvb.masterdata.application.DataQualityService.Report;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.ThresholdValue;

/** Truy vấn đặc thù: tra cứu ngưỡng áp dụng, lịch sử trạng thái máy, báo cáo chất lượng dữ liệu. */
@RestController
@RequestMapping("/api/v1/md")
public class QueryController {

    private final MasterdataQueries queries;
    private final DataQualityService quality;
    private final JdbcTemplate jdbc;

    public QueryController(MasterdataQueries queries, DataQualityService quality, JdbcTemplate jdbc) {
        this.queries = queries;
        this.quality = quality;
        this.jdbc = jdbc;
    }

    @GetMapping("/thresholds/resolve")
    @PreAuthorize("hasAuthority('md.threshold.view')")
    public ThresholdValue resolveThreshold(@RequestParam String metric,
                                           @RequestParam(required = false) UUID machineId,
                                           @RequestParam(required = false) UUID productId,
                                           @RequestParam(required = false) String materialGroup,
                                           @RequestParam(required = false) String stampType,
                                           @RequestParam(required = false) String level,
                                           @RequestParam(required = false) LocalDate asOf) {
        return queries.resolveThreshold(metric, machineId, productId, materialGroup, stampType, level,
                asOf == null ? LocalDate.now() : asOf).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không có ngưỡng áp dụng cho " + metric));
    }

    @GetMapping("/data-quality/report")
    @PreAuthorize("hasAuthority('md.dataQuality.view')")
    public Report dataQuality() {
        return quality.report();
    }

    @GetMapping("/machines/{id}/status-history")
    @PreAuthorize("hasAuthority('md.machine.view')")
    public List<Map<String, Object>> statusHistory(@PathVariable UUID id) {
        return jdbc.queryForList("SELECT from_status AS fromStatus, to_status AS toStatus, at, "
                + "BIN_TO_UUID(by_user) AS byUser, reason FROM md_machine_status_history "
                + "WHERE machine_id = UUID_TO_BIN(?) ORDER BY at DESC", id.toString());
    }
}
