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
| L19 | Danh mục khai báo bằng đặc tả (`CatalogSpec`) | CRUD/import/export/audit chung bằng JDBC thay vì entity JPA từng bảng; luật riêng ở `BomNormService`, `PairingService`, `ThresholdService` | MD | Dev lead |
| L20 | Checklist, vật tư dự kiến của công việc bảo trì | Lưu dạng cột JSON (`checklist`, `parts`) thay vì bảng `MaintChecklistItem`/`MaintTaskPart` riêng (SDD §5.2); tách bảng nếu PM cần truy vấn theo dòng | MD, PM | Dev lead |
| L21 | Ngưỡng `md.Threshold` | Tạo mới mặc định `ACTIVE` bởi người có quyền ghi (SYS_ADMIN, Phó QĐ); chưa chạy qua luồng duyệt `MD_NORM_CHANGE`. Cache tra cứu trong tiến trình 60s (chưa dùng Redis) | BR-CC-004, BR-MD-008 | PXVB |
| L22 | Duyệt định mức NVL | Duyệt trực tiếp bằng quyền `md.bomNorm.approve` (Phó QĐ/QĐ), chưa nối engine duyệt đa bước của workflow | FR-MD-007 | PXVB |
| L23 | Khóa tham chiếu liên module (BR-MD-003) | Có SPI `UsageChecker`; PL/MT/PM… phải đăng ký để chặn khóa SP/máy/NVL đang có lệnh mở (AC-MD-004) | MD, PL | Dev lead |
| L24 | Nhập Excel bộ ghép cặp vấn–bao | Chưa có mẫu nhập riêng (dùng API/lưới); các danh mục khác và `bom-norms` đã có | FR-CC-070 | PO |
| L25 | Dữ liệu gốc | Giờ ca A/B/C, khung cấp phát 25%, mã dừng A–I, ngưỡng mặc định là giá trị khởi tạo giả định (Q04, Q05, Q19) — cần nghiệp vụ TLSG xác nhận | MD | PXVB |

## Quyết định đã chốt (PO, 2026-10-13)

| # | Quyết định | Việc cần làm tiếp |
|---|---|---|
| L25 | **Giữ** giờ ca A 07–15, B 15–23, C 23–07; 4 khung × 25% mỗi ca. Mã dừng A–I, ngưỡng mặc định vẫn là giá trị khởi tạo, chỉnh qua Excel/API khi có số thực | Không |
| L22 | Duyệt định mức NVL **trực tiếp bằng quyền** (Phó QĐ/QĐ) | Không (giữ hiện trạng) |
| L14 | Xác nhận điện tử (nhập lại mật khẩu/PIN + hash) **chỉ ở bước quan trọng**: chốt ca, duyệt KHSX tuần, xác nhận lệnh SX, nghiệm thu TDT | Module PL/PR/OH gọi `SignatureService` đúng các bước này |
| L21 | Ngưỡng cảnh báo: **Phó QĐ/Quản trị áp dụng trực tiếp** (ACTIVE ngay, có hiệu lực + audit) | Không (giữ hiện trạng) |
| L05 | **Chưa dùng LDAP**, tài khoản cục bộ | Hoãn |
| L18 | **Có gửi email** (SMTP nội bộ) cho: cảnh báo CRITICAL; yêu cầu duyệt/điều chỉnh chờ duyệt; báo cáo ngày/tuần/tháng | Làm kênh email trong alerting; **cần PXVB/CNTT cung cấp host/port/tài khoản SMTP và danh sách người nhận báo cáo** |
| L17 | **Xuất Excel trung tâm cảnh báo làm ngay**; snooze để sau | Làm API xuất Excel cảnh báo |
| L24 | Ghép cặp vấn–bao **nhập trên lưới**, không cần mẫu Excel | Đóng mục |
| L13 | Chốt ca bắt buộc: **Vận hành (Trưởng ca), Thống kê (sản lượng/NVL/tem), KSCL/ĐBCL, Cấp phát NVL**; bên "không phát sinh" tự bỏ qua. Không còn bước "Kỹ thuật" bắt buộc | Sửa luồng seed `SHIFT_CLOSE`; logic tự bỏ qua làm ở PR |
| L02 | DBA **bật `log_bin_trust_function_creators=1`** (hoặc cấp quyền) để migration tạo trigger append-only | Ghi vào hướng dẫn triển khai |
| L16 | Nguyên nhân xử lý cảnh báo **chọn từ danh mục** `ALERT_CAUSE` | Đổi `resolve` sang mã lý do + **PXVB cung cấp danh sách nguyên nhân** |

**Còn chờ PXVB cung cấp dữ liệu:** thông tin SMTP + người nhận báo cáo (L18); danh sách nguyên nhân cảnh báo (L16);
danh sách 46 biểu mẫu KSCL, danh mục lỗi và ngưỡng A/B/C (Q10); quy cách tem (Q11).
