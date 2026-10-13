-- Module workflow (CC-03/CC-05): luồng duyệt, ủy quyền, xác nhận điện tử, yêu cầu điều chỉnh.

CREATE TABLE sys_approval_flow_def (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    code        VARCHAR(50)  NOT NULL,
    module      VARCHAR(30)  NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    steps       JSON         NOT NULL,
    is_active   BIT(1)       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_flow_def_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_approval_instance (
    id           BINARY(16)   NOT NULL,
    row_version  BIGINT       NOT NULL,
    created_at   DATETIME(6)  NULL,
    created_by   BINARY(16)   NULL,
    updated_at   DATETIME(6)  NULL,
    updated_by   BINARY(16)   NULL,
    flow_code    VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    BINARY(16)   NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    current_step INT          NOT NULL,
    started_by   BINARY(16)   NOT NULL,
    started_at   DATETIME(6)  NOT NULL,
    completed_at DATETIME(6)  NULL,
    note         VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY ix_approval_entity (entity_type, entity_id),
    KEY ix_approval_flow_entity (flow_code, entity_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_approval_step (
    id                  BINARY(16)    NOT NULL,
    row_version         BIGINT        NOT NULL,
    instance_id         BINARY(16)    NOT NULL,
    step_no             INT           NOT NULL,
    role_code           VARCHAR(50)   NULL,
    assignee_user_id    BINARY(16)    NULL,
    mode                VARCHAR(10)   NOT NULL,
    status              VARCHAR(30)   NOT NULL,
    decided_by          BINARY(16)    NULL,
    acting_for          BINARY(16)    NULL,
    decided_at          DATETIME(6)   NULL,
    note                VARCHAR(1000) NULL,
    sla_minutes         INT           NULL,
    on_timeout          VARCHAR(30)   NOT NULL,
    due_at              DATETIME(6)   NULL,
    overdue_notified_at DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY ix_approval_step_instance (instance_id, step_no),
    KEY ix_approval_step_status_due (status, due_at),
    CONSTRAINT fk_approval_step_instance FOREIGN KEY (instance_id) REFERENCES sys_approval_instance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_approval_delegation (
    id           BINARY(16)  NOT NULL,
    row_version  BIGINT      NOT NULL,
    created_at   DATETIME(6) NULL,
    created_by   BINARY(16)  NULL,
    updated_at   DATETIME(6) NULL,
    updated_by   BINARY(16)  NULL,
    from_user_id BINARY(16)  NOT NULL,
    to_user_id   BINARY(16)  NOT NULL,
    valid_from   DATETIME(6) NOT NULL,
    valid_to     DATETIME(6) NOT NULL,
    is_active    BIT(1)      NOT NULL,
    PRIMARY KEY (id),
    KEY ix_delegation_to (to_user_id, is_active),
    KEY ix_delegation_from (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_signature (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   BINARY(16)   NOT NULL,
    purpose     VARCHAR(30)  NOT NULL,
    role_code   VARCHAR(50)  NOT NULL,
    user_id     BINARY(16)   NOT NULL,
    signed_at   DATETIME(6)  NOT NULL,
    device_id   BINARY(16)   NULL,
    method      VARCHAR(30)  NOT NULL,
    hash        VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_signature_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_adjustment_request (
    id                 BINARY(16)    NOT NULL,
    row_version        BIGINT        NOT NULL,
    created_at         DATETIME(6)   NULL,
    created_by         BINARY(16)    NULL,
    updated_at         DATETIME(6)   NULL,
    updated_by         BINARY(16)    NULL,
    code               VARCHAR(50)   NOT NULL,
    module             VARCHAR(30)   NOT NULL,
    entity_type        VARCHAR(100)  NOT NULL,
    entity_id          BINARY(16)    NOT NULL,
    field_path         VARCHAR(200)  NOT NULL,
    old_value          VARCHAR(2000) NULL,
    new_value          VARCHAR(2000) NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    attachment_ids     JSON          NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    requested_by       BINARY(16)    NOT NULL,
    approver_role_code VARCHAR(50)   NOT NULL,
    approved_by        BINARY(16)    NULL,
    decided_at         DATETIME(6)   NULL,
    decision_note      VARCHAR(1000) NULL,
    applied_at         DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_adjustment_code (code),
    KEY ix_adjustment_requester (requested_by, created_at),
    KEY ix_adjustment_approver (approver_role_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
