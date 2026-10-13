-- Module dataio (CC-08): lần nhập liệu Excel, file nguồn và báo cáo lỗi.

CREATE TABLE sys_import_job (
    id             BINARY(16)    NOT NULL,
    row_version    BIGINT        NOT NULL,
    template_code  VARCHAR(100)  NOT NULL,
    file_name      VARCHAR(255)  NOT NULL,
    status         VARCHAR(30)   NOT NULL,
    mode           VARCHAR(30)   NOT NULL,
    total_rows     INT           NOT NULL,
    ok_rows        INT           NOT NULL,
    error_rows     INT           NOT NULL,
    committed_rows INT           NOT NULL,
    created_by     BINARY(16)    NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    committed_at   DATETIME(6)   NULL,
    message        VARCHAR(1000) NULL,
    source_file    LONGBLOB      NULL,
    error_report   LONGBLOB      NULL,
    PRIMARY KEY (id),
    KEY ix_import_job_user (created_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
