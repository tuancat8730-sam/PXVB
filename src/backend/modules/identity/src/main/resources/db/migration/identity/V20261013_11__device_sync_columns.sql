-- Độ lệch đồng hồ thiết bị so với máy chủ, cập nhật mỗi lần đồng bộ (FR-CC-067).
ALTER TABLE sys_device ADD COLUMN clock_offset_ms BIGINT NULL;
