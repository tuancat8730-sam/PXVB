-- Module alerting (CC-06): rule, cảnh báo, người nhận, lịch sử xử lý, thông báo.

CREATE TABLE sys_alert_rule (
    id                BINARY(16)   NOT NULL,
    row_version       BIGINT       NOT NULL,
    created_at        DATETIME(6)  NULL,
    created_by        BINARY(16)   NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        BINARY(16)   NULL,
    code              VARCHAR(100) NOT NULL,
    module            VARCHAR(30)  NOT NULL,
    metric            VARCHAR(100) NOT NULL,
    severity          VARCHAR(30)  NOT NULL,
    condition_json    JSON         NOT NULL,
    recipient_policy  JSON         NOT NULL,
    escalation_policy JSON         NOT NULL,
    dedup_window_min  INT          NOT NULL,
    is_active         BIT(1)       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_alert_rule_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_alert (
    id                BINARY(16)    NOT NULL,
    row_version       BIGINT        NOT NULL,
    rule_code         VARCHAR(100)  NOT NULL,
    severity          VARCHAR(30)   NOT NULL,
    module            VARCHAR(30)   NOT NULL,
    subject_type      VARCHAR(100)  NULL,
    subject_id        VARCHAR(100)  NULL,
    machine_id        BINARY(16)    NULL,
    shift_session_id  BINARY(16)    NULL,
    title             VARCHAR(300)  NOT NULL,
    message           VARCHAR(2000) NULL,
    payload           JSON          NOT NULL,
    dedup_key         VARCHAR(200)  NOT NULL,
    status            VARCHAR(30)   NOT NULL,
    raised_at         DATETIME(6)   NOT NULL,
    last_occurred_at  DATETIME(6)   NOT NULL,
    occurrence_count  INT           NOT NULL,
    ack_at            DATETIME(6)   NULL,
    ack_by            BINARY(16)    NULL,
    resolved_at       DATETIME(6)   NULL,
    resolved_by       BINARY(16)    NULL,
    cause             VARCHAR(200)  NULL,
    resolution_note   VARCHAR(1000) NULL,
    escalation_level  INT           NOT NULL,
    PRIMARY KEY (id),
    KEY ix_alert_dedup (dedup_key, status),
    KEY ix_alert_status_raised (status, raised_at),
    KEY ix_alert_module_raised (module, raised_at),
    KEY ix_alert_machine (machine_id, raised_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_alert_history (
    id          BINARY(16)    NOT NULL,
    row_version BIGINT        NOT NULL,
    alert_id    BINARY(16)    NOT NULL,
    from_status VARCHAR(30)   NULL,
    to_status   VARCHAR(30)   NOT NULL,
    by_user_id  BINARY(16)    NULL,
    at          DATETIME(6)   NOT NULL,
    note        VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY ix_alert_history_alert (alert_id, at),
    CONSTRAINT fk_alert_history_alert FOREIGN KEY (alert_id) REFERENCES sys_alert (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_alert_recipient (
    id               BINARY(16)  NOT NULL,
    row_version      BIGINT      NOT NULL,
    alert_id         BINARY(16)  NOT NULL,
    user_id          BINARY(16)  NOT NULL,
    escalation_level INT         NOT NULL,
    delivered_at     DATETIME(6) NOT NULL,
    read_at          DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_alert_recipient_alert (alert_id),
    KEY ix_alert_recipient_user (user_id),
    CONSTRAINT fk_alert_recipient_alert FOREIGN KEY (alert_id) REFERENCES sys_alert (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_notification (
    id          BINARY(16)    NOT NULL,
    row_version BIGINT        NOT NULL,
    user_id     BINARY(16)    NOT NULL,
    type        VARCHAR(50)   NOT NULL,
    title       VARCHAR(300)  NOT NULL,
    body        VARCHAR(2000) NULL,
    deep_link   VARCHAR(300)  NULL,
    alert_id    BINARY(16)    NULL,
    channel     VARCHAR(20)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    read_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY ix_notification_user (user_id, created_at),
    KEY ix_notification_unread (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
