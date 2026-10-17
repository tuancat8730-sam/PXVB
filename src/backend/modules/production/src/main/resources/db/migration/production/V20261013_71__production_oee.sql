-- OEE (SDD §8.1, §8.3): công thức cấu hình có phiên bản và kết quả theo máy/ca.

CREATE TABLE pr_oee_formula_config (
    id                       BINARY(16)  NOT NULL,
    row_version              BIGINT      NOT NULL DEFAULT 0,
    created_at               DATETIME(6) NULL,
    created_by               BINARY(16)  NULL,
    version_no               INT         NOT NULL,
    planned_time_mode        VARCHAR(30) NOT NULL,
    quality_mode             VARCHAR(40) NOT NULL,
    cap_performance_at_100   BIT(1)      NOT NULL DEFAULT 0,
    effective_from           DATETIME(6) NOT NULL,
    note                     VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_oee_formula_version (version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phiên bản 1: mặc định theo quy ước SDD §8.3 (⚠ giả định, PXVB xác nhận)
INSERT INTO pr_oee_formula_config(id, row_version, created_at, version_no, planned_time_mode, quality_mode,
                                  cap_performance_at_100, effective_from, note)
VALUES (UNHEX('01900000000070008000000000000001'), 0, UTC_TIMESTAMP(6), 1, 'EXCLUDE_PLANNED_STOPS',
        'ASSUME_100', 0, '2000-01-01 00:00:00.000000', 'Công thức mặc định');

CREATE TABLE pr_oee_result (
    id               BINARY(16)    NOT NULL,
    row_version      BIGINT        NOT NULL DEFAULT 0,
    run_id           BINARY(16)    NOT NULL,
    session_id       BINARY(16)    NOT NULL,
    machine_id       BINARY(16)    NOT NULL,
    business_date    DATE          NOT NULL,
    shift_code       VARCHAR(10)   NOT NULL,
    unit             VARCHAR(10)   NOT NULL,
    planned_minutes  INT           NOT NULL,
    run_minutes      INT           NOT NULL,
    total_qty        BIGINT        NOT NULL,
    good_qty         BIGINT        NOT NULL,
    theoretical_qty  DECIMAL(20,3) NULL,
    availability     DECIMAL(9,6)  NULL,
    performance      DECIMAL(9,6)  NULL,
    quality          DECIMAL(9,6)  NULL,
    oee              DECIMAL(9,6)  NULL,
    is_provisional   BIT(1)        NOT NULL,
    formula_id       BINARY(16)    NOT NULL,
    computed_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_oee_run (run_id),
    KEY ix_oee_session (session_id),
    KEY ix_oee_machine_date (machine_id, business_date),
    KEY ix_oee_date (business_date),
    CONSTRAINT fk_oee_run FOREIGN KEY (run_id) REFERENCES pr_machine_run (id),
    CONSTRAINT fk_oee_formula FOREIGN KEY (formula_id) REFERENCES pr_oee_formula_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
