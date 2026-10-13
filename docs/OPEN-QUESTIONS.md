# Điểm cần chốt & giả định phát sinh khi implement

Bổ sung cho SDD §17. Mỗi mục: quyết định đang áp dụng, ảnh hưởng, ai cần xác nhận.

| # | Vấn đề | Đang áp dụng | Ảnh hưởng | Cần ai chốt |
|---|---|---|---|---|
| L01 | Q16 tách React/Angular | **Toàn bộ frontend dùng React** (web quản lý + dashboard + tablet RN), không dùng Angular như SDD §2.2 | `src/web`, §2.4, §15 | PO (đã chốt) |
| L02 | Quyền SUPER cho trigger append-only `sys_audit_log` | Migration tạo trigger chặn UPDATE/DELETE; khi bật binlog cần user migration có `SUPER` hoặc `log_bin_trust_function_creators=1` | Triển khai/DBA | CNTT TLSG |
| L03 | Phân vùng `sys_audit_log` theo tháng (NFR §4.3) | Chưa làm ở E01; cần đổi PK thành (id, occurred_at) khi bật partition | Hiệu năng audit 3 năm | Dev lead + CNTT |
| L04 | Rate limiting (PXVB-CC-0429) | Chưa có; hiện chỉ có khóa tài khoản sau 5 lần sai | ATTT §16.3 | Dev lead (E25) |
| L05 | LDAP v3 (Q17) | `AuthSource.LDAP` đã có trong mô hình, chưa cài đặt đăng nhập LDAP | Identity | CNTT |
| L06 | Màn hình tra cứu audit (FR-CC-022, P1) | Quyền `identity.audit.view` đã seed; chưa có API/UI tra cứu | RP/Admin UI | Dev lead |
| L07 | Phạm vi dữ liệu `ScopeFilter` (FR-CC-006, P1) | Mới có `DataScope` + `permits()`; chưa có filter dùng chung ở repository | Các phân hệ nghiệp vụ | Dev lead |
| L08 | Outbox → RabbitMQ/STOMP | Mới có outbox + dispatcher + `OutboxSink`; chưa có sink RabbitMQ | E04 | Dev lead |
| L09 | Spring Modulith `verify()` | Dùng ArchUnit trong E01–E02; bật Modulith `verify()` khi có ≥ 2 module nghiệp vụ | Kiến trúc | Dev lead |
| L10 | Cột `Idempotency-Key` TTL 30 ngày | Chưa có job dọn (`IdempotencyCleanup` ở Quartz, §4.9) | E04/E05 | Dev lead |
