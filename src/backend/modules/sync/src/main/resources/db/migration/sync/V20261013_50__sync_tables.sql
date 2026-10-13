-- Module sync (CC-07): nhật ký lệnh đã xử lý (idempotent theo thiết bị) và xung đột đồng bộ.

CREATE TABLE sys_sync_command (
    device_id   BINARY(16)    NOT NULL,
    command_id  BINARY(16)    NOT NULL,
    type        VARCHAR(100)  NOT NULL,
    entity_id   BINARY(16)    NULL,
    status      VARCHAR(30)   NOT NULL,
    message     VARCHAR(1000) NULL,
    result      JSON          NULL,
    user_id     BINARY(16)    NULL,
    occurred_at DATETIME(6)   NULL,
    entered_at  DATETIME(6)   NULL,
    received_at DATETIME(6)   NOT NULL,
    conflict_id BINARY(16)    NULL,
    PRIMARY KEY (device_id, command_id),
    KEY ix_sync_command_received (received_at),
    KEY ix_sync_command_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_sync_conflict (
    id              BINARY(16)    NOT NULL,
    row_version     BIGINT        NOT NULL DEFAULT 0,
    device_id       BINARY(16)    NOT NULL,
    command_id      BINARY(16)    NOT NULL,
    command_type    VARCHAR(100)  NOT NULL,
    entity_id       BINARY(16)    NULL,
    client_payload  JSON          NOT NULL,
    server_snapshot JSON          NULL,
    reason          VARCHAR(1000) NOT NULL,
    status          VARCHAR(40)   NOT NULL DEFAULT 'OPEN',
    created_at      DATETIME(6)   NOT NULL,
    resolved_by     BINARY(16)    NULL,
    resolved_at     DATETIME(6)   NULL,
    resolution_note VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sync_conflict_command (device_id, command_id),
    KEY ix_sync_conflict_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
