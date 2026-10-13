-- Phân hệ PL – Kế hoạch & lệnh sản xuất (SDD §6.1).

CREATE TABLE pl_monthly_plan (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    code        VARCHAR(50)  NOT NULL,
    plan_year   INT          NOT NULL,
    plan_month  INT          NOT NULL,
    revision    INT          NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    source_note VARCHAR(500) NULL,
    active_key  INT GENERATED ALWAYS AS (IF(status = 'ACTIVE', plan_year * 100 + plan_month, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_monthly_plan_code (code),
    UNIQUE KEY uq_monthly_plan_revision (plan_year, plan_month, revision),
    UNIQUE KEY uq_monthly_plan_active (active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_monthly_plan_item (
    id           BINARY(16)   NOT NULL,
    plan_id      BINARY(16)   NOT NULL,
    product_id   BINARY(16)   NOT NULL,
    planned_packs BIGINT      NOT NULL,
    note         VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_monthly_item (plan_id, product_id),
    CONSTRAINT fk_monthly_item_plan FOREIGN KEY (plan_id) REFERENCES pl_monthly_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_monthly_item_packs CHECK (planned_packs > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_weekly_plan (
    id               BINARY(16)    NOT NULL,
    row_version      BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NULL,
    created_by       BINARY(16)    NULL,
    updated_at       DATETIME(6)   NULL,
    updated_by       BINARY(16)    NULL,
    code             VARCHAR(50)   NOT NULL,
    week_start       DATE          NOT NULL,
    revision         INT           NOT NULL,
    status           VARCHAR(30)   NOT NULL,
    monthly_plan_id  BINARY(16)    NULL,
    submitted_by     BINARY(16)    NULL,
    approved_by      BINARY(16)    NULL,
    variance_reason  VARCHAR(1000) NULL,
    note             VARCHAR(1000) NULL,
    active_key       DATE GENERATED ALWAYS AS (IF(status = 'APPROVED', week_start, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_weekly_plan_code (code),
    UNIQUE KEY uq_weekly_plan_revision (week_start, revision),
    UNIQUE KEY uq_weekly_plan_approved (active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_weekly_plan_item (
    id             BINARY(16) NOT NULL,
    weekly_plan_id BINARY(16) NOT NULL,
    product_id     BINARY(16) NOT NULL,
    plan_date      DATE       NOT NULL,
    planned_packs  BIGINT     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_weekly_item (weekly_plan_id, product_id, plan_date),
    CONSTRAINT fk_weekly_item_plan FOREIGN KEY (weekly_plan_id) REFERENCES pl_weekly_plan (id) ON DELETE CASCADE,
    CONSTRAINT ck_weekly_item_packs CHECK (planned_packs > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_production_order (
    id                BINARY(16)   NOT NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NULL,
    created_by        BINARY(16)   NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        BINARY(16)   NULL,
    code              VARCHAR(50)  NOT NULL,
    business_date     DATE         NOT NULL,
    shift_code        VARCHAR(10)  NOT NULL,
    maker_machine_id  BINARY(16)   NOT NULL,
    packer_machine_id BINARY(16)   NOT NULL,
    product_id        BINARY(16)   NOT NULL,
    planned_packs     BIGINT       NOT NULL,
    planned_start     DATETIME(6)  NULL,
    planned_end       DATETIME(6)  NULL,
    norm_version_id   BINARY(16)   NULL,
    weekly_plan_id    BINARY(16)   NULL,
    status            VARCHAR(30)  NOT NULL,
    confirmed_by      BINARY(16)   NULL,
    confirmed_at      DATETIME(6)  NULL,
    source            VARCHAR(10)  NOT NULL,
    revision          INT          NOT NULL DEFAULT 1,
    cancel_reason     VARCHAR(500) NULL,
    slot_key          VARCHAR(80) GENERATED ALWAYS AS (IF(status = 'CANCELLED', NULL,
                          CONCAT(business_date, '|', shift_code, '|', HEX(packer_machine_id)))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_order_code (code),
    UNIQUE KEY uq_order_slot (slot_key),
    KEY ix_order_date_shift (business_date, shift_code),
    KEY ix_order_product (product_id, status),
    KEY ix_order_machines (maker_machine_id, packer_machine_id),
    CONSTRAINT ck_order_packs CHECK (planned_packs > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_order_segment (
    id            BINARY(16)   NOT NULL,
    order_id      BINARY(16)   NOT NULL,
    segment_no    INT          NOT NULL,
    product_id    BINARY(16)   NOT NULL,
    planned_packs BIGINT       NOT NULL,
    start_at      DATETIME(6)  NULL,
    end_at        DATETIME(6)  NULL,
    reason        VARCHAR(300) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_order_segment (order_id, segment_no),
    CONSTRAINT fk_segment_order FOREIGN KEY (order_id) REFERENCES pl_production_order (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_order_adjustment (
    id          BINARY(16)    NOT NULL,
    order_id    BINARY(16)    NOT NULL,
    adj_type    VARCHAR(30)   NOT NULL,
    reason_code VARCHAR(50)   NOT NULL,
    note        VARCHAR(1000) NULL,
    before_json JSON          NULL,
    after_json  JSON          NULL,
    adjusted_at DATETIME(6)   NOT NULL,
    adjusted_by BINARY(16)    NULL,
    PRIMARY KEY (id),
    KEY ix_adjustment_order (order_id, adjusted_at),
    CONSTRAINT fk_adjustment_order FOREIGN KEY (order_id) REFERENCES pl_production_order (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_shift_roster (
    id                 BINARY(16)  NOT NULL,
    row_version        BIGINT      NOT NULL DEFAULT 0,
    created_at         DATETIME(6) NULL,
    created_by         BINARY(16)  NULL,
    updated_at         DATETIME(6) NULL,
    updated_by         BINARY(16)  NULL,
    business_date      DATE        NOT NULL,
    shift_code         VARCHAR(10) NOT NULL,
    leader_employee_id BINARY(16)  NULL,
    status             VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    copied_from_id     BINARY(16)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roster_shift (business_date, shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pl_shift_roster_assignment (
    id          BINARY(16)  NOT NULL,
    roster_id   BINARY(16)  NOT NULL,
    machine_id  BINARY(16)  NOT NULL,
    position    VARCHAR(30) NOT NULL,
    employee_id BINARY(16)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roster_assignment (roster_id, machine_id, position),
    KEY ix_roster_employee (roster_id, employee_id),
    CONSTRAINT fk_assignment_roster FOREIGN KEY (roster_id) REFERENCES pl_shift_roster (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
