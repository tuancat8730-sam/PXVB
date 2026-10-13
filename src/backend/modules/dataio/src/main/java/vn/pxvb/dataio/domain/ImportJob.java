package vn.pxvb.dataio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.pxvb.common.domain.BaseEntity;

/**
 * Một lần nhập liệu (sys_import_job). File nguồn và báo cáo lỗi là các cột blob không ánh xạ trong entity
 * (đọc/ghi bằng JDBC) để tránh nạp dữ liệu lớn khi truy vấn danh sách.
 */
@Entity
@Table(name = "sys_import_job")
public class ImportJob extends BaseEntity {

    public enum Status { UPLOADED, VALIDATED, COMMITTED, FAILED }

    public enum Mode { VALIDATE_ONLY, UPSERT }

    public interface Repo extends JpaRepository<ImportJob, UUID> {
    }

    @Column(nullable = false, length = 100)
    private String templateCode;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.UPLOADED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Mode mode;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int okRows;

    @Column(nullable = false)
    private int errorRows;

    @Column(nullable = false)
    private int committedRows;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant committedAt;

    @Column(length = 1000)
    private String message;

    protected ImportJob() {
    }

    public ImportJob(String templateCode, String fileName, Mode mode, UUID createdBy, Instant createdAt) {
        this.templateCode = templateCode;
        this.fileName = fileName;
        this.mode = mode;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void validated(int total, int ok, int errors) {
        this.status = Status.VALIDATED;
        this.totalRows = total;
        this.okRows = ok;
        this.errorRows = errors;
    }

    public void committed(int rows, int errors, Instant at) {
        this.status = Status.COMMITTED;
        this.committedRows = rows;
        this.errorRows = errors;
        this.committedAt = at;
    }

    public void failed(String message) {
        this.status = Status.FAILED;
        this.message = message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getFileName() {
        return fileName;
    }

    public Status getStatus() {
        return status;
    }

    public Mode getMode() {
        return mode;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getOkRows() {
        return okRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public int getCommittedRows() {
        return committedRows;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public String getMessage() {
        return message;
    }
}
