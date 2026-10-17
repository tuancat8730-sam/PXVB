# PXVB – Theo dõi tiến độ triển khai

Nguồn backlog: `docs/PXVB-SRS-SDD.md` §18.2. Điểm cần chốt/giả định: `docs/OPEN-QUESTIONS.md` (mã L01–L50).
Cập nhật lần cuối: 2026-10-17.

**Chú giải trạng thái:** ✅ Xong · 🟡 Xong một phần (còn việc, xem cột Ghi chú) · ⬜ Chưa làm

## 1. Tổng quan

| Hạng mục | Trạng thái |
|---|---|
| Backend (Spring Boot 3.5 / Java 21 / MySQL 8.0 & 8.4) | E00–E14 đã làm (một số còn việc, xem bảng) |
| Frontend web (React: quản trị + dashboard) | ⬜ chưa bắt đầu (`src/web` chưa tồn tại) |
| Tablet (React Native) | 🟡 chỉ có lõi offline TypeScript đã test (`src/tablet`); UI + adapter SQLite thật chưa build/chạy |
| Test backend | 39 unit · 16 ArchUnit · 72 integration (MySQL 8.0 và 8.4) · 10 test lõi tablet |
| Commit chưa push | E13 (đang làm, chưa commit) |

## 2. Backlog theo epic

| Epic | Nội dung | Trạng thái | Commit | Ghi chú / việc còn lại |
|---|---|---|---|---|
| E00 | Scaffolding, CI/CD, môi trường | ✅ | `7b221b3` | CI chạy backend (MySQL 8.0/8.4) + `tablet-core`; `docker-compose.dev.yml`. Chưa có OpenAPI-diff, lint |
| E01 | BuildingBlocks (audit, outbox, idempotency, state machine, lỗi) | ✅ | `7b221b3` | L03 phân vùng audit theo tháng, L08 sink RabbitMQ, L10 job dọn Idempotency-Key chưa làm |
| E02 | Identity, RBAC, scope, device registry, PIN | 🟡 | `7b221b3` | L04 rate limiting, L05 LDAP (hoãn theo quyết định PO), L06 màn tra cứu audit, L07 `ScopeFilter` dùng chung |
| E03 | Workflow phê duyệt + xác nhận điện tử + điều chỉnh sau khóa | ✅ | `e1157af` | L15 "bản sửa đổi báo cáo" chờ RP |
| E04 | Alert engine + Notification + WebSocket/STOMP | 🟡 | `e1157af`, `ba6e225` | Đã thêm email (CRITICAL, yêu cầu duyệt/điều chỉnh) và xuất Excel cảnh báo. Còn: báo cáo định kỳ qua email (chờ RP + SMTP), snooze (FR-CC-055), L11/L12 worker + relay RabbitMQ |
| E05 | Import/Export Excel engine | ✅ | `31f7da2` | Mẫu nhập đã có cho danh mục MD, định mức, KHSX tháng |
| E06 | MD: máy, cụm, ghép cặp, ca, khung giờ, nhân sự | ✅ | `31f7da2` | Ghép cặp nhập trên lưới (L24 đã chốt) |
| E07 | MD: SP, NVL, định mức (phiên bản), ngưỡng, mã dừng/lý do, tem | ✅ | `31f7da2` | Giá trị seed (giờ ca, ngưỡng, mã dừng) là giả định L25; quy cách tem chờ Q11 |
| E08 | MD: biểu mẫu KSCL (store), lỗi/luật, bảo trì, phụ tùng, liên kết | 🟡 | `31f7da2` | Mới có kho lưu; 46 biểu mẫu thật + lỗi + ngưỡng A/B/C chờ PXVB (Q10) |
| E09 | Sync service + tablet shell offline | 🟡 | `ba6e225` | Backend xong (push/pull/xung đột/giám sát). Tablet: chỉ lõi TS; UI RN + adapter op-sqlite chưa kiểm chứng (L31); tombstone bảng hard-delete chưa có (L30) |
| E10 | PL: KHSX tháng/tuần, lệnh SX, bố trí nhân sự, điều chỉnh | 🟡 | `ba6e225` | Thuật toán gợi ý lệnh đơn giản (L32); theo dõi thực hiện kế hoạch FR-PL-007/008 chờ dữ liệu PR (L34) |
| E11 | PR: phiên ca, sản lượng, dừng máy, đổi mác, tồn điếu | 🟡 | `97f1f02` | Chưa có: tồn điếu (FR-PR-007), đối chiếu thống kê (FR-PR-011), giám sát khu vực/nhập hộ UI (FR-PR-010), tạo yêu cầu sửa chữa 1 chạm (chờ PM), cảnh báo >110% công suất (L35–L41) |
| E12 | OEE engine + công thức cấu hình + golden tests | 🟡 | `d3a92a0` | Mới có AC-PR-002 + 9 ca; cần 10 bộ golden của PO. Q chưa nối dữ liệu NC (QC); công suất 77 máy cần nhập (L42–L45) |
| E13 | MT: phiếu cấp phát, cấp/nhận, kiểm đếm, tiêu hao, cảnh báo | 🟡 | (chưa commit) | Xong FR-MT-001..009 + `CHK_MATERIAL` (AC-MT-001..005 có test). Còn: xu hướng/đề xuất định mức (FR-MT-010), đối chiếu ERP (FR-MT-011), xuất PDF/Excel báo cáo, phiếu theo segment đổi mác, test sync `MT.*` (L51–L56) |
| E14 | Shift-close engine (CC-11) + báo cáo ca | 🟡 | `2f60e93` | Xong engine, giải trình, ký + duyệt `SHIFT_CLOSE` (3 bên), báo cáo ca, mở lại qua điều chỉnh. Còn: `CHK_QUALITY/STAMP` (chờ E18/E20; `CHK_MATERIAL` đã có từ E13), `ShiftCloseWatcher`, bước `FINALIZED`, tự bỏ qua bên "không phát sinh" (L46–L50) |
| E15 | RP core: projections/facts, `R_SHIFT`, `R_DAY` | ⬜ | – | Báo cáo ca hiện lưu JSON trong `pr_shift_report`, chờ RP tiêu thụ |
| E16 | Dashboard v1: Trưởng ca, Phó QĐ | ⬜ | – | Cần khung frontend React; API dashboard thời gian thực đã có (`/pr/sessions/{id}/dashboard`) |
| E17 | QC form engine (web + tablet renderer) + import schema | ⬜ | – | Phụ thuộc dữ liệu Q10 |
| E18 | QC inline (lịch 30', phân cấp A/B/C, cảnh báo, leo thang) | ⬜ | – | |
| E19 | QC ĐBCL, SPKPH, B21 tháng, truy ngược | ⬜ | – | |
| E20 | TX: block, xuất dùng, hư/mất, đối chiếu, cảnh báo, chốt, export | ⬜ | – | Cần cho `CHK_STAMP`; chờ quy cách tem (Q11) |
| E21 | PM: danh mục, KH tháng, WO, tablet thợ máy, sự cố/sửa chữa, MTTR/MTBF | ⬜ | – | |
| E22 | SP: kho, nhập/xuất, yêu cầu xuất, tồn min, ERP file | ⬜ | – | |
| E23 | OH: KH năm, hồ sơ, đánh giá, phụ tùng, tiến độ, nghiệm thu | ⬜ | – | |
| E24 | RP đầy đủ: dashboard còn lại, báo cáo tuần/tháng, subscription, kiosk | ⬜ | – | |
| E25 | Hardening ATTT, hiệu năng, kiểm thử tải, tài liệu ATTT cấp độ 2 | ⬜ | – | Gồm rate limiting (L04), phân vùng audit (L03) |
| E26 | Di trú dữ liệu & go-live: chuẩn hóa masterdata, UAT, song song 3 ca, đào tạo | ⬜ | – | |

## 3. Việc ngoài backlog epic (đã làm)

| Việc | Trạng thái | Commit |
|---|---|---|
| Áp dụng quyết định PO (L25/L22/L14/L21/L05/L18/L17/L24/L13/L02/L16) | ✅ | `31f7da2`, `ba6e225` |
| Email SMTP nội bộ (tắt mặc định `PXVB_MAIL_ENABLED`) | ✅ | `ba6e225` |
| Xuất Excel trung tâm cảnh báo (`GET /api/v1/alerting/alerts/export`) | ✅ | `ba6e225` |
| Nguyên nhân xử lý cảnh báo chọn từ danh mục `ALERT_CAUSE` | ✅ | `ba6e225` |
| Luồng `SHIFT_CLOSE` theo quyết định L13 (nay 3 bên duyệt + chữ ký Trưởng ca, L46) | ✅ | `ba6e225`, `2f60e93` |
| ArchUnit kiểm tra ranh giới module (15 luật) | ✅ | cập nhật theo từng epic |

## 4. Chưa làm (không thuộc epic backend)

| Việc | Ghi chú |
|---|---|
| Khung frontend web React (`src/web`: layout, auth, API client) | Quyết định Q16: toàn bộ frontend dùng React |
| Dashboard React (E16/E24) và kiosk | Sau khung web |
| Tablet RN: build UI, adapter op-sqlite, kiểm thử trên thiết bị | Lõi TS đã sẵn (hàng đợi, SyncClient) |
| Cập nhật OpenAPI + kiểm tra OpenAPI-diff trong CI | Chưa có file OpenAPI xuất ra |
| Chuyển scanner/outbox sang `pxvb-worker` + Quartz | Hiện chạy trong `pxvb-api` (L11) |
| Redis/RabbitMQ thật (cache ngưỡng, relay realtime) | Hiện cache trong tiến trình, STOMP trong tiến trình |

## 5. Đang chờ PXVB/CNTT cung cấp

| Dữ liệu | Liên quan |
|---|---|
| Thông tin SMTP + danh sách người nhận báo cáo ngày/tuần/tháng | L18, L26 |
| Danh sách nguyên nhân xử lý cảnh báo thật | L16, L27 |
| 46 biểu mẫu KSCL + danh mục lỗi + ngưỡng A/B/C | Q10, E08, E17–E19 |
| Quy cách tem | Q11, E20 |
| Công suất lý thuyết của 77 máy | L44, OEE |
| 10 bộ số liệu golden OEE | L42, AC-PR-002 |
| Xác nhận nhóm vai trò các bên chốt ca; có chặn cứng khi mở ca thiếu lệnh/nhân sự không | L28, L35, L46 |
| Xác nhận giả định giờ ca, ngưỡng, mã dừng | L25 |

## 6. Cách chạy kiểm thử

- Backend: `cd src/backend && ./mvnw verify` (hoặc bản Docker trong `CLAUDE.md`); MySQL 8.4: thêm `-Dpxvb.test.mysql=8.4`.
- Lõi tablet: `cd src/tablet && npm test` (Node ≥ 22.5).
