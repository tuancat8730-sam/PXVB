-- Phân hệ MD – Masterdata (SDD §5.2). Cột chuẩn: id, row_version, created_*/updated_*; bảng danh mục có is_active.
-- Tên cột khớp CatalogSpec (camelCase → snake_case).

CREATE TABLE md_machine (
    id                BINARY(16)    NOT NULL,
    row_version       BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NULL,
    created_by        BINARY(16)    NULL,
    updated_at        DATETIME(6)   NULL,
    updated_by        BINARY(16)    NULL,
    is_active         BIT(1)        NOT NULL DEFAULT 1,
    code              VARCHAR(50)   NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    machine_type      VARCHAR(30)   NOT NULL,
    model             VARCHAR(50)   NULL,
    rod_format        VARCHAR(30)   NULL,
    theoretical_rate  DECIMAL(18,3) NULL,
    rate_unit         VARCHAR(30)   NULL,
    line_code         VARCHAR(50)   NULL,
    location          VARCHAR(100)  NULL,
    has_ipc           BIT(1)        NOT NULL DEFAULT 0,
    tablet_count      INT           NOT NULL DEFAULT 0,
    status            VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    install_date      DATE          NULL,
    erp_asset_code    VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_machine_code (code),
    KEY ix_machine_type_status (machine_type, status),
    KEY ix_machine_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_machine_status_history (
    id          BINARY(16)   NOT NULL,
    machine_id  BINARY(16)   NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    at          DATETIME(6)  NOT NULL,
    by_user     BINARY(16)   NULL,
    reason      VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY ix_machine_status_history (machine_id, at),
    CONSTRAINT fk_machine_status_machine FOREIGN KEY (machine_id) REFERENCES md_machine (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_machine_component (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    machine_id  BINARY(16)   NOT NULL,
    parent_id   BINARY(16)   NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    discipline  VARCHAR(30)  NOT NULL,
    kind        VARCHAR(30)  NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_component_machine_code (machine_id, code),
    KEY ix_component_parent (parent_id),
    CONSTRAINT fk_component_machine FOREIGN KEY (machine_id) REFERENCES md_machine (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_stamp_type (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    notes       VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_stamp_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_product (
    id                      BINARY(16)   NOT NULL,
    row_version             BIGINT       NOT NULL DEFAULT 0,
    created_at              DATETIME(6)  NULL,
    created_by              BINARY(16)   NULL,
    updated_at              DATETIME(6)  NULL,
    updated_by              BINARY(16)   NULL,
    is_active               BIT(1)       NOT NULL DEFAULT 1,
    code                    VARCHAR(50)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    product_type            VARCHAR(30)  NOT NULL,
    rod_format              VARCHAR(30)  NULL,
    rods_per_pack           INT          NULL,
    packs_per_carton        INT          NULL,
    cartons_per_case        INT          NULL,
    requires_expiry_inkjet  BIT(1)       NOT NULL DEFAULT 0,
    stamp_type_id           BINARY(16)   NULL,
    stamps_per_pack         INT          NOT NULL DEFAULT 1,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uq_product_code (code),
    KEY ix_product_name (name),
    KEY ix_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_product_machine_allowed (
    id          BINARY(16)  NOT NULL,
    row_version BIGINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NULL,
    created_by  BINARY(16)  NULL,
    updated_at  DATETIME(6) NULL,
    updated_by  BINARY(16)  NULL,
    product_id  BINARY(16)  NOT NULL,
    machine_id  BINARY(16)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_product_machine (product_id, machine_id),
    CONSTRAINT fk_allowed_product FOREIGN KEY (product_id) REFERENCES md_product (id),
    CONSTRAINT fk_allowed_machine FOREIGN KEY (machine_id) REFERENCES md_machine (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_product_physical_spec (
    id             BINARY(16)    NOT NULL,
    row_version    BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NULL,
    created_by     BINARY(16)    NULL,
    updated_at     DATETIME(6)   NULL,
    updated_by     BINARY(16)    NULL,
    product_id     BINARY(16)    NOT NULL,
    indicator      VARCHAR(30)   NOT NULL,
    category       VARCHAR(30)   NOT NULL,
    target         DECIMAL(18,3) NULL,
    min_value      DECIMAL(18,3) NULL,
    max_value      DECIMAL(18,3) NULL,
    uom            VARCHAR(20)   NULL,
    effective_from DATE          NOT NULL,
    effective_to   DATE          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_physical_spec (product_id, indicator, category, effective_from),
    CONSTRAINT fk_spec_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_material_category (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_material_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_material (
    id            BINARY(16)   NOT NULL,
    row_version   BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NULL,
    created_by    BINARY(16)   NULL,
    updated_at    DATETIME(6)  NULL,
    updated_by    BINARY(16)   NULL,
    is_active     BIT(1)       NOT NULL DEFAULT 1,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    uom           VARCHAR(20)  NOT NULL,
    category_code VARCHAR(30)  NOT NULL,
    group_code    VARCHAR(50)  NULL,
    erp_code      VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_material_code (code),
    KEY ix_material_name (name),
    KEY ix_material_category (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_bom_norm (
    id             BINARY(16)    NOT NULL,
    row_version    BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NULL,
    created_by     BINARY(16)    NULL,
    updated_at     DATETIME(6)   NULL,
    updated_by     BINARY(16)    NULL,
    product_id     BINARY(16)    NOT NULL,
    version_no     INT           NOT NULL,
    effective_from DATE          NULL,
    effective_to   DATE          NULL,
    status         VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    approved_by    BINARY(16)    NULL,
    approved_at    DATETIME(6)   NULL,
    change_reason  VARCHAR(1000) NULL,
    active_key     BINARY(16) GENERATED ALWAYS AS (IF(status = 'ACTIVE', product_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bom_norm_version (product_id, version_no),
    UNIQUE KEY uq_bom_norm_active (active_key),
    KEY ix_bom_norm_effective (product_id, status, effective_from),
    CONSTRAINT fk_bom_norm_product FOREIGN KEY (product_id) REFERENCES md_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_bom_norm_line (
    id                  BINARY(16)     NOT NULL,
    norm_id             BINARY(16)     NOT NULL,
    material_id         BINARY(16)     NOT NULL,
    qty_per_1000_packs  DECIMAL(18,6)  NOT NULL,
    uom                 VARCHAR(20)    NOT NULL,
    consumer_type       VARCHAR(30)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bom_line (norm_id, material_id),
    CONSTRAINT fk_bom_line_norm FOREIGN KEY (norm_id) REFERENCES md_bom_norm (id) ON DELETE CASCADE,
    CONSTRAINT fk_bom_line_material FOREIGN KEY (material_id) REFERENCES md_material (id),
    CONSTRAINT ck_bom_line_qty CHECK (qty_per_1000_packs > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_machine_pairing (
    id             BINARY(16)    NOT NULL,
    row_version    BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NULL,
    created_by     BINARY(16)    NULL,
    updated_at     DATETIME(6)   NULL,
    updated_by     BINARY(16)    NULL,
    code           VARCHAR(50)   NOT NULL,
    effective_from DATE          NULL,
    effective_to   DATE          NULL,
    status         VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    change_reason  VARCHAR(1000) NULL,
    active_key     TINYINT GENERATED ALWAYS AS (IF(status = 'ACTIVE', 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pairing_code (code),
    UNIQUE KEY uq_pairing_active (active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_machine_pairing_line (
    id               BINARY(16)    NOT NULL,
    pairing_id       BINARY(16)    NOT NULL,
    shift_code       VARCHAR(10)   NOT NULL,
    maker_machine_id BINARY(16)    NOT NULL,
    packer_machine_id BINARY(16)   NOT NULL,
    feed_ratio       DECIMAL(18,3) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pairing_line (pairing_id, shift_code, packer_machine_id),
    KEY ix_pairing_line_maker (maker_machine_id),
    KEY ix_pairing_line_packer (packer_machine_id),
    CONSTRAINT fk_pairing_line_pairing FOREIGN KEY (pairing_id) REFERENCES md_machine_pairing (id) ON DELETE CASCADE,
    CONSTRAINT fk_pairing_line_maker FOREIGN KEY (maker_machine_id) REFERENCES md_machine (id),
    CONSTRAINT fk_pairing_line_packer FOREIGN KEY (packer_machine_id) REFERENCES md_machine (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_shift_def (
    id                BINARY(16)   NOT NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NULL,
    created_by        BINARY(16)   NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        BINARY(16)   NULL,
    is_active         BIT(1)       NOT NULL DEFAULT 1,
    code              VARCHAR(10)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    start_time        TIME         NOT NULL,
    end_time          TIME         NOT NULL,
    crosses_midnight  BIT(1)       NOT NULL DEFAULT 0,
    seq               INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_shift_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_issue_slot_def (
    id          BINARY(16)    NOT NULL,
    row_version BIGINT        NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NULL,
    created_by  BINARY(16)    NULL,
    updated_at  DATETIME(6)   NULL,
    updated_by  BINARY(16)    NULL,
    is_active   BIT(1)        NOT NULL DEFAULT 1,
    shift_code  VARCHAR(10)   NOT NULL,
    slot_no     INT           NOT NULL,
    label       VARCHAR(30)   NOT NULL,
    start_time  TIME          NULL,
    end_time    TIME          NULL,
    share_pct   DECIMAL(18,3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_issue_slot (shift_code, slot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_employee (
    id            BINARY(16)   NOT NULL,
    row_version   BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NULL,
    created_by    BINARY(16)   NULL,
    updated_at    DATETIME(6)  NULL,
    updated_by    BINARY(16)   NULL,
    is_active     BIT(1)       NOT NULL DEFAULT 1,
    code          VARCHAR(50)  NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    badge_no      VARCHAR(50)  NULL,
    org_unit_code VARCHAR(50)  NULL,
    job_title     VARCHAR(100) NULL,
    trade_skill   VARCHAR(30)  NULL,
    user_id       BINARY(16)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_employee_code (code),
    KEY ix_employee_name (full_name),
    KEY ix_employee_badge (badge_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_stop_reason (
    id                         BINARY(16)   NOT NULL,
    row_version                BIGINT       NOT NULL DEFAULT 0,
    created_at                 DATETIME(6)  NULL,
    created_by                 BINARY(16)   NULL,
    updated_at                 DATETIME(6)  NULL,
    updated_by                 BINARY(16)   NULL,
    is_active                  BIT(1)       NOT NULL DEFAULT 1,
    code                       VARCHAR(10)  NOT NULL,
    name                       VARCHAR(200) NOT NULL,
    category                   VARCHAR(30)  NOT NULL,
    counts_as_planned_downtime BIT(1)       NOT NULL DEFAULT 0,
    requires_tech_confirm      BIT(1)       NOT NULL DEFAULT 0,
    can_create_repair_request  BIT(1)       NOT NULL DEFAULT 0,
    sort_order                 INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_stop_reason_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_reason_code (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    group_code  VARCHAR(30)  NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reason_code (group_code, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_defect_def (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    applies_to  VARCHAR(30)  NOT NULL,
    form_group  VARCHAR(20)  NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_defect_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_defect_grade_rule (
    id              BINARY(16)    NOT NULL,
    row_version     BIGINT        NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NULL,
    created_by      BINARY(16)    NULL,
    updated_at      DATETIME(6)   NULL,
    updated_by      BINARY(16)    NULL,
    is_active       BIT(1)        NOT NULL DEFAULT 1,
    defect_code     VARCHAR(50)   NULL,
    applies_to      VARCHAR(30)   NOT NULL,
    grade           VARCHAR(5)    NOT NULL,
    metric          VARCHAR(30)   NOT NULL,
    threshold_value DECIMAL(18,3) NOT NULL,
    sample_size     INT           NULL,
    effective_from  DATE          NOT NULL,
    effective_to    DATE          NULL,
    PRIMARY KEY (id),
    KEY ix_defect_rule_lookup (defect_code, applies_to, grade, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_qc_form_template (
    id                      BINARY(16)   NOT NULL,
    row_version             BIGINT       NOT NULL DEFAULT 0,
    created_at              DATETIME(6)  NULL,
    created_by              BINARY(16)   NULL,
    updated_at              DATETIME(6)  NULL,
    updated_by              BINARY(16)   NULL,
    code                    VARCHAR(30)  NOT NULL,
    form_group              VARCHAR(20)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    category                VARCHAR(30)  NOT NULL,
    applies_to_machine_type VARCHAR(30)  NULL DEFAULT 'ANY',
    frequency_min           INT          NULL,
    form_schema             JSON         NOT NULL,
    version_no              INT          NOT NULL DEFAULT 1,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    effective_from          DATE         NULL,
    effective_to            DATE         NULL,
    active_key              VARCHAR(30) GENERATED ALWAYS AS (IF(status = 'ACTIVE', code, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qc_form_version (code, version_no),
    UNIQUE KEY uq_qc_form_active (active_key),
    KEY ix_qc_form_group (form_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_maint_task_template (
    id                BINARY(16)   NOT NULL,
    row_version       BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NULL,
    created_by        BINARY(16)   NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        BINARY(16)   NULL,
    code              VARCHAR(50)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    target_type       VARCHAR(30)  NOT NULL,
    target_value      VARCHAR(100) NOT NULL,
    maint_type        VARCHAR(30)  NOT NULL,
    cycle_value       INT          NOT NULL,
    cycle_unit        VARCHAR(30)  NOT NULL,
    estimated_minutes INT          NULL,
    skill_required    VARCHAR(30)  NULL DEFAULT 'ANY',
    executor_unit     VARCHAR(50)  NULL,
    priority          VARCHAR(30)  NULL DEFAULT 'NORMAL',
    checklist         JSON         NULL,
    parts             JSON         NULL,
    version_no        INT          NOT NULL DEFAULT 1,
    status            VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    effective_from    DATE         NULL,
    effective_to      DATE         NULL,
    active_key        VARCHAR(50) GENERATED ALWAYS AS (IF(status = 'ACTIVE', code, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_maint_task_version (code, version_no),
    UNIQUE KEY uq_maint_task_active (active_key),
    KEY ix_maint_task_target (target_type, target_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_overhaul_type_def (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    code        VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_overhaul_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_overhaul_form_template (
    id             BINARY(16)  NOT NULL,
    row_version    BIGINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NULL,
    created_by     BINARY(16)  NULL,
    updated_at     DATETIME(6) NULL,
    updated_by     BINARY(16)  NULL,
    code           VARCHAR(50) NOT NULL,
    kind           VARCHAR(30) NOT NULL,
    form_schema    JSON        NOT NULL,
    version_no     INT         NOT NULL DEFAULT 1,
    status         VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE        NULL,
    effective_to   DATE        NULL,
    active_key     VARCHAR(50) GENERATED ALWAYS AS (IF(status = 'ACTIVE', code, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_overhaul_form_version (code, version_no),
    UNIQUE KEY uq_overhaul_form_active (active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_supplier (
    id          BINARY(16)   NOT NULL,
    row_version BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NULL,
    updated_by  BINARY(16)   NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    contact     VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_supplier_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_spare_part (
    id                  BINARY(16)   NOT NULL,
    row_version         BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NULL,
    created_by          BINARY(16)   NULL,
    updated_at          DATETIME(6)  NULL,
    updated_by          BINARY(16)   NULL,
    is_active           BIT(1)       NOT NULL DEFAULT 1,
    part_no             VARCHAR(50)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    uom                 VARCHAR(20)  NOT NULL,
    stock_card_no       VARCHAR(50)  NULL,
    group_code          VARCHAR(50)  NULL,
    default_supplier_id BINARY(16)   NULL,
    erp_code            VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_spare_part_no (part_no),
    KEY ix_spare_part_name (name),
    KEY ix_spare_part_card (stock_card_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_spare_part_min_stock (
    id             BINARY(16)    NOT NULL,
    row_version    BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NULL,
    created_by     BINARY(16)    NULL,
    updated_at     DATETIME(6)   NULL,
    updated_by     BINARY(16)    NULL,
    spare_part_id  BINARY(16)    NOT NULL,
    min_qty        DECIMAL(18,3) NOT NULL,
    reorder_qty    DECIMAL(18,3) NULL,
    lead_time_days INT           NULL,
    effective_from DATE          NOT NULL,
    effective_to   DATE          NULL,
    change_reason  VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_min_stock (spare_part_id, effective_from),
    CONSTRAINT fk_min_stock_part FOREIGN KEY (spare_part_id) REFERENCES md_spare_part (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_machine_part_link (
    id               BINARY(16)    NOT NULL,
    row_version      BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NULL,
    created_by       BINARY(16)    NULL,
    updated_at       DATETIME(6)   NULL,
    updated_by       BINARY(16)    NULL,
    machine_id       BINARY(16)    NULL,
    model_code       VARCHAR(50)   NULL,
    component_id     BINARY(16)    NULL,
    task_template_id BINARY(16)    NULL,
    spare_part_id    BINARY(16)    NOT NULL,
    typical_qty      DECIMAL(18,3) NULL,
    PRIMARY KEY (id),
    KEY ix_part_link_machine (machine_id),
    KEY ix_part_link_part (spare_part_id),
    CONSTRAINT fk_part_link_part FOREIGN KEY (spare_part_id) REFERENCES md_spare_part (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE md_threshold (
    id              BINARY(16)    NOT NULL,
    row_version     BIGINT        NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NULL,
    created_by      BINARY(16)    NULL,
    updated_at      DATETIME(6)   NULL,
    updated_by      BINARY(16)    NULL,
    metric_code     VARCHAR(100)  NOT NULL,
    scope_type      VARCHAR(30)   NOT NULL DEFAULT 'GLOBAL',
    scope_id        BINARY(16)    NULL,
    scope_value     VARCHAR(100)  NULL,
    level           VARCHAR(10)   NULL,
    threshold_value DECIMAL(18,3) NOT NULL,
    unit            VARCHAR(20)   NULL,
    effective_from  DATE          NOT NULL,
    effective_to    DATE          NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    change_reason   VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    KEY ix_threshold_lookup (metric_code, scope_type, status, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
