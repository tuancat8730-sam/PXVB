-- Chốt ca CC-11: giải trình, báo cáo ca (ReportSnapshot), liên kết luồng duyệt SHIFT_CLOSE.

ALTER TABLE pr_shift_session
    ADD COLUMN approval_instance_id BINARY(16) NULL,
    ADD COLUMN closed_at DATETIME(6) NULL;

CREATE TABLE pr_close_explanation (
    id           BINARY(16)    NOT NULL,
    row_version  BIGINT        NOT NULL DEFAULT 0,
    session_id   BINARY(16)    NOT NULL,
    check_code   VARCHAR(50)   NOT NULL,
    subject_key  VARCHAR(100)  NOT NULL DEFAULT '',
    reason_code  VARCHAR(50)   NULL,
    note         VARCHAR(1000) NULL,
    status       VARCHAR(20)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    created_by   BINARY(16)    NULL,
    decided_at   DATETIME(6)   NULL,
    decided_by   BINARY(16)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_close_explanation (session_id, check_code, subject_key),
    CONSTRAINT fk_explanation_session FOREIGN KEY (session_id) REFERENCES pr_shift_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pr_shift_report (
    id          BINARY(16)  NOT NULL,
    session_id  BINARY(16)  NOT NULL,
    revision    INT         NOT NULL,
    payload     JSON        NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BINARY(16)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_shift_report_revision (session_id, revision),
    CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES pr_shift_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
