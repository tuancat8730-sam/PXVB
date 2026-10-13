-- Module identity (CC-01/02/03): người dùng, vai trò, quyền, thiết bị, refresh token, đơn vị.

CREATE TABLE sys_org_unit (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    parent_id   BINARY(16)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_org_unit_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user (
    id                   BINARY(16)   NOT NULL,
    row_version          BIGINT       NOT NULL,
    created_at           DATETIME(6)  NULL,
    created_by           BINARY(16)   NULL,
    updated_at           DATETIME(6)  NULL,
    updated_by           BINARY(16)   NULL,
    username             VARCHAR(100) NOT NULL,
    full_name            VARCHAR(200) NOT NULL,
    employee_code        VARCHAR(50)  NULL,
    badge_no             VARCHAR(50)  NULL,
    password_hash        VARCHAR(255) NOT NULL,
    pin_hash             VARCHAR(255) NULL,
    org_unit_id          BINARY(16)   NULL,
    email                VARCHAR(255) NULL,
    phone                VARCHAR(50)  NULL,
    is_active            BIT(1)       NOT NULL,
    failed_count         INT          NOT NULL DEFAULT 0,
    locked_until         DATETIME(6)  NULL,
    last_login_at        DATETIME(6)  NULL,
    must_change_password BIT(1)       NOT NULL,
    auth_source          VARCHAR(30)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_username (username),
    UNIQUE KEY uq_user_badge (badge_no),
    UNIQUE KEY uq_user_employee (employee_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(255) NULL,
    is_system   BIT(1)       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
    code        VARCHAR(100) NOT NULL,
    module      VARCHAR(30)  NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
    role_id         BINARY(16)   NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_permission_perm FOREIGN KEY (permission_code) REFERENCES sys_permission (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    user_id     BINARY(16)   NOT NULL,
    role_id     BINARY(16)   NOT NULL,
    scope_type  VARCHAR(30)  NOT NULL,
    scope_value VARCHAR(100) NULL,
    valid_from  DATETIME(6)  NULL,
    valid_to    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_user_role_user (user_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_refresh_token (
    id          BINARY(16)  NOT NULL,
    row_version BIGINT      NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    device_id   BINARY(16)  NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    replaced_by BINARY(16)  NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    KEY ix_refresh_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_device (
    id                    BINARY(16)   NOT NULL,
    row_version           BIGINT       NOT NULL,
    created_at            DATETIME(6)  NULL,
    created_by            BINARY(16)   NULL,
    updated_at            DATETIME(6)  NULL,
    updated_by            BINARY(16)   NULL,
    device_code           VARCHAR(50)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    position              VARCHAR(30)  NOT NULL,
    machine_ids           JSON         NOT NULL,
    app_version           VARCHAR(50)  NULL,
    last_seen_at          DATETIME(6)  NULL,
    last_sync_at          DATETIME(6)  NULL,
    pending_count         INT          NOT NULL DEFAULT 0,
    is_active             BIT(1)       NOT NULL,
    activation_code_hash  VARCHAR(255) NULL,
    activation_expires_at DATETIME(6)  NULL,
    activated_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_device_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
