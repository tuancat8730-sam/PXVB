package vn.pxvb.dataio.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.dataio.application.ExcelFiles.ParsedRow;
import vn.pxvb.dataio.application.ExcelFiles.RowError;
import vn.pxvb.dataio.contract.ImportTemplate;
import vn.pxvb.dataio.domain.ImportJob;

/**
 * Nhập liệu Excel 2 bước (FR-CC-070): tải lên → kiểm tra (báo lỗi theo dòng/cột, xuất file lỗi) → commit các
 * dòng hợp lệ theo lô ≤ 5.000 dòng/giao dịch. Không dòng nào được ghi trước khi commit.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    static final int BATCH_SIZE = 5_000;
    private static final int SAMPLE_ERRORS = 100;
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    public record UploadResult(ImportJob job, List<ErrorSample> sampleErrors) {
    }

    public record ErrorSample(int row, String column, String message) {
    }

    private record Validation(List<ParsedRow> valid, List<RowError> errors, int total) {
    }

    private final Map<String, ImportTemplate> templates = new HashMap<>();
    private final ImportJob.Repo jobs;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate batchTx;
    private final Clock clock;

    public ImportService(List<ImportTemplate.Provider> providers, ImportJob.Repo jobs, JdbcTemplate jdbc,
                         PlatformTransactionManager txManager, Clock clock) {
        providers.forEach(p -> p.templates().forEach(t -> templates.put(t.code(), t)));
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.batchTx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public List<ImportTemplate> templates() {
        return templates.values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
    }

    public ImportTemplate template(String code) {
        ImportTemplate t = templates.get(code);
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không có mẫu nhập liệu: " + code);
        }
        return t;
    }

    public byte[] templateFile(String code, Set<String> permissions) {
        ImportTemplate t = template(code);
        requirePermission(t, permissions);
        return ExcelFiles.template(t);
    }

    /** Kiểm tra file; nếu {@code commitNow} thì ghi luôn các dòng hợp lệ. */
    public UploadResult upload(String templateCode, String fileName, byte[] bytes, boolean commitNow, UUID userId,
                               Set<String> permissions) {
        ImportTemplate template = template(templateCode);
        requirePermission(template, permissions);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES || fileName == null
                || !fileName.toLowerCase().endsWith(".xlsx") || !ExcelFiles.looksLikeXlsx(bytes)) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "Chỉ nhận tệp Excel .xlsx, kích thước tối đa 10MB");
        }
        Validation validation = validate(template, bytes);
        ImportJob job = new ImportJob(templateCode, fileName,
                commitNow ? ImportJob.Mode.UPSERT : ImportJob.Mode.VALIDATE_ONLY, userId, clock.instant());
        job.validated(validation.total(), validation.valid().size(), distinctRows(validation.errors()));
        ImportJob saved = jobs.saveAndFlush(job);
        jdbc.update("UPDATE sys_import_job SET source_file = ?, error_report = ? WHERE id = UUID_TO_BIN(?)",
                bytes, validation.errors().isEmpty() ? null : ExcelFiles.errorReport(validation.errors()),
                saved.getId().toString());
        List<ErrorSample> sample = validation.errors().stream().limit(SAMPLE_ERRORS)
                .map(e -> new ErrorSample(e.row(), e.column(), e.message())).toList();
        if (commitNow) {
            return new UploadResult(commit(saved.getId(), userId, permissions, true), sample);
        }
        return new UploadResult(saved, sample);
    }

    /** Commit các dòng hợp lệ của một lần nhập đã kiểm tra. */
    public ImportJob commit(UUID jobId, UUID userId, Set<String> permissions) {
        return commit(jobId, userId, permissions, false);
    }

    private ImportJob commit(UUID jobId, UUID userId, Set<String> permissions, boolean fromUpload) {
        ImportJob job = get(jobId, userId, permissions);
        if (job.getStatus() != ImportJob.Status.VALIDATED && !(fromUpload && job.getStatus() == ImportJob.Status.VALIDATED)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Lần nhập này đã được xử lý (" + job.getStatus() + ")");
        }
        ImportTemplate template = template(job.getTemplateCode());
        requirePermission(template, permissions);
        byte[] source = jdbc.queryForObject("SELECT source_file FROM sys_import_job WHERE id = UUID_TO_BIN(?)",
                byte[].class, jobId.toString());
        Validation validation = validate(template, source);
        int committed = 0;
        try {
            List<ParsedRow> rows = validation.valid();
            for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
                List<Map<String, String>> batch = rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE))
                        .stream().map(ParsedRow::values).toList();
                batchTx.executeWithoutResult(status -> template.commitBatch(batch));
                committed += batch.size();
            }
        } catch (RuntimeException e) {
            log.warn("Nhập liệu {} lỗi sau {} dòng: {}", jobId, committed, e.toString());
            job.failed("Ghi dữ liệu thất bại sau " + committed + " dòng: " + e.getMessage());
            return jobs.saveAndFlush(job);
        }
        job.committed(committed, distinctRows(validation.errors()), clock.instant());
        jdbc.update("UPDATE sys_import_job SET error_report = ? WHERE id = UUID_TO_BIN(?)",
                validation.errors().isEmpty() ? null : ExcelFiles.errorReport(validation.errors()),
                jobId.toString());
        return jobs.saveAndFlush(job);
    }

    public ImportJob get(UUID jobId, UUID userId, Set<String> permissions) {
        ImportJob job = jobs.findById(jobId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!job.getCreatedBy().equals(userId) && !permissions.contains("dataio.import.view")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return job;
    }

    public byte[] errorReport(UUID jobId, UUID userId, Set<String> permissions) {
        get(jobId, userId, permissions);
        byte[] report = jdbc.queryForObject("SELECT error_report FROM sys_import_job WHERE id = UUID_TO_BIN(?)",
                byte[].class, jobId.toString());
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Lần nhập này không có lỗi");
        }
        return report;
    }

    private Validation validate(ImportTemplate template, byte[] bytes) {
        List<ParsedRow> parsed = ExcelFiles.read(bytes, template.columns());
        List<RowError> errors = new ArrayList<>();
        List<ParsedRow> valid = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        Map<String, String> headers = new HashMap<>();
        template.columns().forEach(c -> headers.put(c.key(), c.header()));
        for (ParsedRow row : parsed) {
            List<RowError> rowErrors = new ArrayList<>();
            for (ImportTemplate.Column c : template.columns()) {
                if (c.required() && row.values().get(c.key()).isEmpty()) {
                    rowErrors.add(new RowError(row.rowNo(), c.header(), "Bắt buộc nhập"));
                }
            }
            if (rowErrors.isEmpty()) {
                template.validate(row.values()).forEach((k, msg) ->
                        rowErrors.add(new RowError(row.rowNo(), headers.getOrDefault(k, k), msg)));
            }
            if (rowErrors.isEmpty()) {
                String key = template.naturalKey(row.values());
                if (key != null && !seenKeys.add(key)) {
                    rowErrors.add(new RowError(row.rowNo(), null, "Dòng trùng khóa nghiệp vụ trong tệp: " + key));
                }
            }
            if (rowErrors.isEmpty()) {
                valid.add(row);
            } else {
                errors.addAll(rowErrors);
            }
        }
        return new Validation(valid, errors, parsed.size());
    }

    private static int distinctRows(List<RowError> errors) {
        return (int) errors.stream().map(RowError::row).distinct().count();
    }

    private static void requirePermission(ImportTemplate template, Set<String> permissions) {
        if (!permissions.contains(template.permission())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Cần quyền " + template.permission() + " để nhập mẫu này");
        }
    }
}
