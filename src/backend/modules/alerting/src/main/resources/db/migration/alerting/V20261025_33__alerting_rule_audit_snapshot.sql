-- Phiên bản rule và snapshot tại thời điểm raise (E04 3.3, L67): không có bảng phiên bản, chỉ đếm số lần sửa.
-- Alert cũ không có snapshot (NULL).
ALTER TABLE sys_alert_rule
    ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE sys_alert
    ADD COLUMN rule_version INT NULL,
    ADD COLUMN rule_snapshot JSON NULL;
