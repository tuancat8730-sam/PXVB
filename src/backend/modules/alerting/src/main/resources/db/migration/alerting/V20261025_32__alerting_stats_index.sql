-- Thống kê cảnh báo theo rule và ngày (E04 3.2, L68): đếm trong khoảng raised_at của một rule.
ALTER TABLE sys_alert
    ADD KEY ix_alert_rule_raised (rule_code, raised_at);
