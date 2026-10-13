-- Nhóm bảng dùng chung `sys_` (SDD §3): audit, outbox, idempotency, sequence, setting.

CREATE TABLE sys_audit_log (
    id                    BINARY(16)   NOT NULL,
    occurred_at           DATETIME(6)  NOT NULL,
    module                VARCHAR(30)  NOT NULL,
    entity_type           VARCHAR(100) NOT NULL,
    entity_id             VARCHAR(64)  NULL,
    action                VARCHAR(30)  NOT NULL,
    field_changes         JSON         NULL,
    reason                VARCHAR(500) NULL,
    by_user_id            BINARY(16)   NULL,
    approved_by_user_id   BINARY(16)   NULL,
    device_id             BINARY(16)   NULL,
    ip                    VARCHAR(45)  NULL,
    correlation_id        VARCHAR(64)  NULL,
    adjustment_request_id BINARY(16)   NULL,
    PRIMARY KEY (id),
    KEY ix_audit_entity (entity_type, entity_id),
    KEY ix_audit_time (occurred_at),
    KEY ix_audit_user (by_user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Append-only (FR-CC-012): chặn UPDATE/DELETE ở tầng DB, ngoài việc cấp quyền INSERT/SELECT cho tài khoản ứng dụng.
CREATE TRIGGER trg_audit_log_no_update BEFORE UPDATE ON sys_audit_log
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log la append-only';

CREATE TRIGGER trg_audit_log_no_delete BEFORE DELETE ON sys_audit_log
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log la append-only';

CREATE TABLE sys_outbox_message (
    id           BINARY(16)   NOT NULL,
    type         VARCHAR(100) NOT NULL,
    payload      JSON         NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    processed_at DATETIME(6)  NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_outbox_pending (processed_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_idempotency_record (
    idem_key              VARCHAR(100) NOT NULL,
    device_id             VARCHAR(100) NOT NULL DEFAULT '',
    request_hash          CHAR(64)     NOT NULL,
    response_status       INT          NULL,
    response_content_type VARCHAR(100) NULL,
    response_body         MEDIUMTEXT   NULL,
    created_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (idem_key, device_id),
    KEY ix_idem_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_number_sequence (
    seq_key      VARCHAR(100) NOT NULL,
    prefix       VARCHAR(30)  NOT NULL,
    last_number  BIGINT       NOT NULL DEFAULT 0,
    reset_policy VARCHAR(30)  NOT NULL DEFAULT 'NONE',
    period_key   VARCHAR(20)  NOT NULL DEFAULT '',
    PRIMARY KEY (seq_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_setting (
    setting_key   VARCHAR(100) NOT NULL,
    scope         VARCHAR(50)  NOT NULL DEFAULT 'GLOBAL',
    setting_value JSON         NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (setting_key, scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
