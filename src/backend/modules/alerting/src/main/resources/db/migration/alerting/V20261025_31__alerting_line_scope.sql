-- Phạm vi dây chuyền của cảnh báo (FR-CC-006, L07): snapshot dây chuyền của máy tại thời điểm raise.
-- Cảnh báo cũ không có snapshot (NULL): chỉ người có phạm vi ALL nhìn thấy.
ALTER TABLE sys_alert
    ADD COLUMN line_code VARCHAR(50) NULL AFTER machine_id,
    ADD KEY ix_alert_line_code (line_code);
