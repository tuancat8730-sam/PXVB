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
| L11 | Worker chưa nạp module nghiệp vụ | Outbox dispatcher và các scanner (SLA duyệt, leo thang cảnh báo) đang chạy trong `pxvb-api` (cờ `PXVB_OUTBOX_DISPATCHER`, `PXVB_WORKFLOW_SCANNER`, `PXVB_ALERT_SCANNER`); chuyển sang worker + Quartz khi worker nạp module | Vận hành/HA | Dev lead |
| L12 | Realtime nhiều tiến trình | STOMP dùng broker trong tiến trình; cảnh báo phát từ worker chưa đẩy được tới tablet (client dùng polling 60s dự phòng). Cần relay RabbitMQ (cùng L08) | FR-CC-053 | Dev lead |
| L13 | Vai trò/SLA của các luồng duyệt seed (Q15) | `FlowSeeder` đặt vai trò + SLA theo giả định (vd `SHIFT_CLOSE` = ALL của 4 bên; chưa tự bỏ qua bên "không phát sinh") | E03, PL/PR/OH | PXVB |
| L14 | Xác nhận điện tử khi duyệt | Engine duyệt chưa bắt re-auth; `SignatureService` cung cấp ký (hash do server tính) để module nghiệp vụ gọi tại bước cần ký | FR-CC-031 | PXVB + Pháp chế (Q13) |
| L15 | "Bản sửa đổi báo cáo" khi điều chỉnh bản ghi đã FINALIZED (FR-CC-011) | Mới có điều chỉnh + audit + sự kiện `adjustment.applied`; tạo `Revision+1` do phân hệ RP thực hiện khi có | RP | Dev lead |
| L16 | Nguyên nhân xử lý cảnh báo | Hiện là chuỗi tự do (bắt buộc); chuyển sang danh mục `md.ReasonCode(ALERT_CAUSE)` khi có MD | FR-CC-051 | PXVB |
| L17 | Snooze cảnh báo (FR-CC-055, P2), xuất Excel trung tâm cảnh báo (FR-CC-054) | Chưa làm | Alerting | PO |
| L18 | Kênh email cho thông báo (Q22) | Chỉ có in-app + realtime; `Notification.channel` đã sẵn | Alerting | CNTT |
