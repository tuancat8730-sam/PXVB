# PXVB – Theo dõi tiến độ triển khai

Nguồn backlog: `docs/PXVB-SRS-SDD.md` §18.2. Điểm cần chốt/giả định: `docs/OPEN-QUESTIONS.md` (mã L01–L63). Quyết định kiến trúc: `docs/adr/`.
Cập nhật lần cuối: 2026-10-30 (duyệt bản vẽ E08 đợt 7; mở build web MD 6b).

**Chú giải trạng thái:** ✅ Xong · 🟡 Xong một phần (còn việc, xem cột Ghi chú) · ⬜ Chưa làm

## 1. Tổng quan

| Hạng mục | Trạng thái |
|---|---|
| Backend (Spring Boot 3.5 / Java 21 / MySQL 8.0 & 8.4) | E00–E14 đã làm (một số còn việc, xem bảng); E15 đang làm (Pha 1 xong, xem §3c) |
| Frontend web (React: quản trị + dashboard) | ⬜ chưa bắt đầu (`src/web` chưa tồn tại) |
| Tablet (React Native) | 🟡 chỉ có lõi offline TypeScript đã test (`src/tablet`); UI + adapter SQLite thật chưa build/chạy |
| Test backend | 39 unit · 16 ArchUnit · 81 integration (toàn bộ xanh trên MySQL 8.4; các IT của PR/MT cũng xanh trên 8.0) · 10 test lõi tablet |
| Commit chưa push | 4 commit trên `main` (từ `67923c3`: quyết định PO L42/L49/L28/L46/L50, ADR-0001 và Pha 1 E15) |

## 2. Backlog theo epic

| Epic | Nội dung | Trạng thái | Commit | Ghi chú / việc còn lại |
|---|---|---|---|---|
| E00 | Scaffolding, CI/CD, môi trường | ✅ | `7b221b3` | CI chạy backend (MySQL 8.0/8.4) + `tablet-core`; `docker-compose.dev.yml`. Chưa có OpenAPI-diff, lint |
| E01 | BuildingBlocks (audit, outbox, idempotency, state machine, lỗi) | ✅ | `7b221b3` | L03 phân vùng audit theo tháng, L08 sink RabbitMQ, L10 job dọn Idempotency-Key chưa làm |
| E02 | Identity, RBAC, scope, device registry, PIN | 🟡 | `7b221b3` | Đã xong L04 (rate limit đăng nhập/PIN/kích hoạt, PXVB-CC-0429) và L06 (API tra cứu/lịch sử/xuất Excel audit, chưa có UI). Còn: L07 `ScopeFilter` (chưa áp dụng: `MachineInfo` chưa có dây chuyền, cần chốt nơi áp dụng); L05 LDAP hoãn theo quyết định PO |
| E03 | Workflow phê duyệt + xác nhận điện tử + điều chỉnh sau khóa | ✅ | `e1157af` | L15 "bản sửa đổi báo cáo" chờ RP |
| E04 | Alert engine + Notification + WebSocket/STOMP | 🟡 | `e1157af`, `ba6e225` | Đã thêm email (CRITICAL, yêu cầu duyệt/điều chỉnh) và xuất Excel cảnh báo. Còn: báo cáo định kỳ qua email (chờ RP + SMTP), snooze (FR-CC-055), L11/L12 worker + relay RabbitMQ |
| E05 | Import/Export Excel engine | ✅ | `31f7da2` | Mẫu nhập đã có cho danh mục MD, định mức, KHSX tháng |
| E06 | MD: máy, cụm, ghép cặp, ca, khung giờ, nhân sự | ✅ | `31f7da2` | Ghép cặp nhập trên lưới (L24 đã chốt) |
| E07 | MD: SP, NVL, định mức (phiên bản), ngưỡng, mã dừng/lý do, tem | ✅ | `31f7da2` | Giá trị seed (giờ ca, ngưỡng, mã dừng) là giả định L25; quy cách tem chờ Q11 |
| E08 | MD: biểu mẫu KSCL (store), lỗi/luật, bảo trì, phụ tùng, liên kết | 🟡 | `31f7da2`, `79b1bd1` (nhánh `feat/e08-masterdata-kscl`) | Xong: validator KSCL/bảo trì/luật lỗi, kiểm tham chiếu, import không ghi khi có lỗi (AC-MD-002), liên kết hai chiều (`MasterdataLinkIT` 5/5, 8.0 và 8.4). Luật cấp B đã chốt (L74); cấp A chờ định nghĩa "liên tiếp" và cấp C (L75, phương án (a) đã chọn, chưa thực thi trong code). Pha 4–5 xong (chất lượng dữ liệu, kiểm soát danh mục). Còn: OpenAPI, 46 biểu mẫu thật + ngưỡng A/B/C (Q10), UI U-1…U-7 đã build 7/7 màn (`src/web`, 94 test; 6b.0–6b.8). Còn: kiểm bàn phím, tương phản, 1366×768 chính xác, dữ liệu thật qua backend (6b.9) |
| E09 | Sync service + tablet shell offline | 🟡 | `ba6e225` | Backend xong (push/pull/xung đột/giám sát). Tablet: chỉ lõi TS; UI RN + adapter op-sqlite chưa kiểm chứng (L31); tombstone bảng hard-delete chưa có (L30) |
| E10 | PL: KHSX tháng/tuần, lệnh SX, bố trí nhân sự, điều chỉnh | 🟡 | `ba6e225` | Thuật toán gợi ý lệnh đơn giản (L32); theo dõi thực hiện kế hoạch FR-PL-007/008 chờ dữ liệu PR (L34) |
| E11 | PR: phiên ca, sản lượng, dừng máy, đổi mác, tồn điếu | 🟡 | `97f1f02` | Chưa có: tồn điếu (FR-PR-007), đối chiếu thống kê (FR-PR-011), giám sát khu vực/nhập hộ UI (FR-PR-010), tạo yêu cầu sửa chữa 1 chạm (chờ PM), cảnh báo >110% công suất (L35–L41) |
| E12 | OEE engine + công thức cấu hình + golden tests | 🟡 | `d3a92a0` | Mới có AC-PR-002 + 9 ca; cần 10 bộ golden của PO. Q chưa nối dữ liệu NC (QC); công suất 77 máy cần nhập (L42–L45) |
| E13 | MT: phiếu cấp phát, cấp/nhận, kiểm đếm, tiêu hao, cảnh báo | 🟡 | `d608733` | Xong FR-MT-001..009 + `CHK_MATERIAL` (AC-MT-001..005 có test). Còn: xu hướng/đề xuất định mức (FR-MT-010), đối chiếu ERP (FR-MT-011), xuất PDF/Excel báo cáo, phiếu theo segment đổi mác, test sync `MT.*` (L51–L56) |
| E14 | Shift-close engine (CC-11) + báo cáo ca | 🟡 | `2f60e93` | Xong engine, giải trình, ký + duyệt `SHIFT_CLOSE` (3 bên), báo cáo ca, mở lại qua điều chỉnh. Còn: `CHK_QUALITY/STAMP` (chờ E18/E20; `CHK_MATERIAL` đã có từ E13), `ShiftCloseWatcher`, bước `FINALIZED`, tự bỏ qua bên "không phát sinh" (L46–L50) |
| E15 | RP core: projections/facts, `R_SHIFT`, `R_DAY` | 🟡 | `b728ba9`, `1b763ef`, `6760c63` | Đã có ADR-0001 (grain fact + khung projection, trạng thái **Đề xuất**) và Pha 1 (contract nguồn PR/MT + sự kiện `pr.output.recorded`/`pr.oee.computed`/`mt.consumption.computed`). Chưa có module `reporting`, bảng fact, projection, báo cáo. Chờ PO/Dev lead: duyệt ADR, L57–L61. Chi tiết: §3c |
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

## 3b. Việc cần làm từ quyết định PO 2026-10-18

Nguồn: `OPEN-QUESTIONS.md` mục "Quyết định đã chốt (PO, 2026-10-18)".

| Việc | Epic | Trạng thái |
|---|---|---|
| OEE: Performance chặn tối đa 100% (công thức mặc định phiên bản mới) + cập nhật golden test (L42) | E12 | ✅ |
| Chốt ca: cho giải trình + duyệt với giao nhận NVL trễ, tranh chấp nhận NVL, thiết bị chưa đồng bộ (L49) | E14, E13 | ✅ |
| Chốt ca: tự bỏ qua bên "không phát sinh" theo dữ liệu (engine duyệt hỗ trợ bỏ bước + audit) (L28/L46) | E03, E14 | ✅ |
| Tự động `CLOSED` → `FINALIZED` sau N ngày (N cấu hình, đề xuất 7; **cần PXVB chọn N**) (L50) | E14 | ✅ |
| MT: xuất PDF/Excel báo cáo tiêu hao (AC-MT-006) (L55) | E13 | ⬜ |
| MT: xu hướng tiêu hao + đề xuất điều chỉnh định mức (FR-MT-010) (L55) | E13 | ⬜ |
| MT: phiếu cấp phát theo segment khi đổi mác (BR-MT-006) (L55) | E13 | ⬜ |
| PR: tồn điếu tại máy bao + `PR_BUFFER_HIGH/LOW` (FR-PR-007) (L40) | E11 | ⬜ |
| PR: đối chiếu sản lượng thống kê (FR-PR-011) (L40) | E11 | ⬜ |
| PR: giám sát khu vực + nhập hộ + phạm vi dữ liệu (FR-PR-010) (L40) | E11 | ⬜ |
| PR: phân tích hiệu suất thiết bị — Pareto dừng máy, xu hướng OEE, so sánh dòng máy (FR-PR-013) (L40) | E12 | ⬜ |
| MT: đối chiếu ERP (FR-MT-011) — hoãn theo quyết định | E13 | ⏸ hoãn |

## 3c. E15 – RP core: tiến độ theo pha

Kế hoạch theo `docs/adr/ADR-0001-rp-fact-grain-projection.md` §5. Câu hỏi mở: `OPEN-QUESTIONS.md` L57–L62 (và L63 về test không ổn định).

| Pha | Nội dung | Trạng thái | Ghi chú |
|---|---|---|---|
| 0 | Chốt quyết định chặn: L57 (cách gộp OEE khi trần hiệu năng), bố cục `R_SHIFT` v1, người nhận `R_DAY`, L58 (revision) | 🟡 | Hội đồng khuyến nghị L57 = trần từng máy/ca (0,80 thay vì 0,90); **chờ PO xác nhận** |
| 1 | Story tiên quyết ở PR/MT: contract nguồn + sự kiện | 🟡 | Xong: `ProductionQueries.sessionsBetween/shiftFacts`, `MaterialQueries.consumption`, sự kiện `pr.output.recorded`, `pr.oee.computed` (chỉ khi kết quả đổi), `mt.consumption.computed` (chỉ khi dòng đổi hoặc giải trình/chốt); test contract, sự kiện và tương đương với `pr_shift_report` / API tiêu hao. **Còn:** sửa `OeeCalculator.aggregate()` theo L57 (chờ PO) |
| 2 | Nền module `reporting`: migration (4 bảng fact, `rp_projection_dirty`, phân vùng tháng + `p_future`), `PartitionMaintainer`, luật ArchUnit | ⬜ | |
| 3 | Cắt dọc đầu tiên: oracle SQL độc lập + dữ liệu 30 ngày, `RpOutboxSink`, `ProjectionWorker` (nhận việc `SKIP LOCKED`, CAS `dirty_seq`, debounce, back-off), fact PR/OEE/dừng máy | ⬜ | |
| 4 | Fact NVL, `ProjectionRebuilder`, đối soát đêm, registry ánh xạ `adjustment.applied`, báo cáo "máy/ca bị áp trần" | ⬜ | |
| 5 | Báo cáo: `ReportSnapshot`, bộ dựng theo mục, renderer PDF/XLSX, `R_SHIFT`, `R_DAY`, tự gửi, revision (L58) | ⬜ | |
| 6 | Nghiệm thu AC-RP-001/003/005 (MySQL 8.0 và 8.4), OpenAPI, tài liệu | ⬜ | |

**Ghi chú kiểm thử:** `AlertingIT.notificationsCanBeMarkedRead` không ổn định (xem L63): đỏ một lần trong lượt chạy toàn bộ trên 8.4, xanh khi chạy riêng, trên `HEAD` sạch và ở lượt chạy toàn bộ lại.

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
| Chọn số ngày N để tự động chuyển ca `CLOSED` → `FINALIZED` (đề xuất 7) | L50 |
| Xác nhận nhóm vai trò các bên chốt ca (Thống kê / KSCL / Cấp phát NVL) | L28 |
| Xác nhận giả định giờ ca, ngưỡng, mã dừng | L25 |
| PO duyệt ADR-0001 và trả lời L57 (gộp OEE khi trần hiệu năng), L58 (revision báo cáo), L60 (thời hạn lưu/đối soát) | E15, AC-RP-003 |
| Dev lead trả lời L59 (ánh xạ điều chỉnh về phiên ca) và L61 (mốc đo SLA READY ≤ 5') | E15, AC-RP-001 |
| QC lead / ĐBCL định nghĩa "liên tiếp" (phạm vi, N, reset) và cấp C để chốt cấp A (L75, đã chọn phương án (a)) | E08, E18, FR-QC-005, AC-QC-003 |

## 6. Cách chạy kiểm thử

- Backend: `cd src/backend && ./mvnw verify` (hoặc bản Docker trong `CLAUDE.md`); MySQL 8.4: thêm `-Dpxvb.test.mysql=8.4`.
- Lõi tablet: `cd src/tablet && npm test` (Node ≥ 22.5).
