# Kế hoạch: Hoàn tất E02 (Identity, RBAC, scope, device, PIN)

**Nguồn:** `docs/PROGRESS.md` (E02 🟡), `docs/OPEN-QUESTIONS.md` L04–L07, `docs/PXVB-SRS-SDD.md` FR-CC-006/021/022.
**Độ phức tạp:** Trung bình. **Cập nhật lần cuối:** 2026-10-24.
**Chú giải:** ✅ xong · 🟡 đang làm · ⬜ chưa làm · ⏸ hoãn · ❌ bị chặn

## 1. Phạm vi

| Mã | Việc | Trạng thái |
|---|---|---|
| L06 | Tra cứu audit (API + xuất Excel + lịch sử theo bản ghi), FR-CC-022 | ✅ (chưa có UI) |
| L04 | Rate limiting `PXVB-CC-0429` cho đăng nhập/PIN/kích hoạt thiết bị | ✅ |
| L07 | `ScopeFilter` dùng chung (FR-CC-006) + áp thử một endpoint | ⬜ đã chốt đầy đủ (403; theo thời điểm sự kiện; fail-closed; ca suy từ máy; UNIT mở rộng khi truy vấn; alerting trước). Pha 3 chưa làm. Phân công ca/ĐBCL tách sang L73 (xem `OPEN-QUESTIONS.md`) |
| L05 | LDAP v3 | ⏸ hoãn theo quyết định PO, **ngoài phạm vi** |

## 2. Giả định đã áp dụng (chưa được PXVB/Dev lead xác nhận; đổi được)

| # | Giả định | Nếu đổi |
|---|---|---|
| G1 | L04 chỉ chặn brute-force ở `/auth/login`, đăng nhập PIN, kích hoạt thiết bị. Giới hạn API chung theo người dùng để E25 | Thêm pha giới hạn API chung |
| G2 | Endpoint thử nghiệm `ScopeFilter`: danh sách phiên ca ở module `production` | Đổi module thử nghiệm ở Pha 3 |
| G3 | Tra cứu audit: khoảng thời gian bắt buộc, mặc định 24 giờ, tối đa 31 ngày; xuất Excel tối đa 50.000 dòng | Sửa hằng số cấu hình |
| G4 | Bộ đếm rate limit nằm trong bộ nhớ một tiến trình (chuyển Redis ở E25) | Thêm cổng lưu trữ |

## 3. Mẫu mã nguồn cần bám

| Loại | Nguồn | Mẫu |
|---|---|---|
| Bộ lọc web | `pxvb-building-blocks/.../web/IdempotencyFilter.java` | `OncePerRequestFilter` + `@Order`, trả `ProblemDetail` theo `ErrorCode` |
| Lỗi | `.../error/ErrorCode.java`, `BusinessException` | Mã `PXVB-<MOD>-<nnnn>`, tiếng Việt; `RATE_LIMITED` đã có |
| Audit | `.../audit/AuditLogWriter.java` | JDBC trực tiếp; bảng `sys_audit_log` append-only |
| Xuất Excel | `alerting/.../AlertService` + `dataio.application.ExportService` | Như `GET .../alerts/export` |
| Phân trang | `.../web/PageResponse.java` | Dùng chung |
| Scope | `.../security/DataScope.java`, `DataScopeTest` | `permits(...)`, record `Type {ALL, LINE, UNIT}` |
| Test | `AuthFlowIT`, `AbstractIntegrationTest`, `ArchitectureTest` | IT MySQL (Testcontainers); unit thuần cho logic |

## 4. Công việc và trạng thái

### Pha 0 – Xác minh trước khi viết mã
| ID | Việc | Trạng thái | Kết quả |
|---|---|---|---|
| 0.1 | Kiểm tra `AuditLogWriter`/`HibernateAuditListener` có che trường nhạy cảm (FR-CC-020) chưa | ✅ | `HibernateAuditListener` che trường có tên chứa `hash`/`pin` (`***`). `AuditLogWriter.write()` ghi thẳng `FieldChange` **không che**: bản ghi audit viết tay (vd `AuthService`, `SlipService`) có thể chứa giá trị nhạy cảm nếu người gọi quên. API tra cứu nên che lại lần nữa khi trả `field_changes` (tên trường chứa `hash`/`pin`/`password`/`secret`) |
| 0.2 | Kiểm tra dự án có dùng Spring Data `Specification` chưa | ✅ | Có, ở `alerting` (`Repositories.java`, `AlertService.java`). Dùng `Specification<T>` cho `ScopeFilter` là hợp mẫu hiện có |
| 0.3 | Đọc `AbstractIntegrationTest`, cách kiểm quyền ở controller, ArchUnit | ✅ | Quyền: `@PreAuthorize("hasAuthority('identity.xxx.view')")`. IT: `@SpringBootTest` + Testcontainers MySQL (`-Dpxvb.test.mysql=8.4`), có `adminToken(...)`. ArchUnit không cấm `identity` → `dataio.application` (chỉ cấm chiều ngược lại); module khác chỉ được dùng `identity.contract` |
| 0.4 | Chạy baseline `./mvnw verify` (ghi số test xanh/đỏ trước khi sửa) | ✅ | Đã chạy được (Java 21 + Colima, xem mục 7). Baseline: 81 IT, 13 lỗi sẵn có ở `ProductionIT`/`MaterialIT` (lệch thời gian ca), không thuộc E02 |

### Pha 1 – L06 Tra cứu audit
| ID | Việc | Trạng thái |
|---|---|---|
| 1.1 | Viết test (đỏ) `AuditQueryIT`: lọc phân hệ/đối tượng/người/thời gian, phân trang, thiếu quyền → 403, khoảng thời gian bắt buộc | ✅ (8 test: quyền, lọc module + byUserId, lịch sử theo bản ghi, phân trang, cursor sai, khoảng > 31 ngày, xuất Excel) |
| 1.2 | `AuditQueryService` (JDBC, phân trang theo khóa `(occurred_at, id)`) | ✅ |
| 1.3 | `AuditController`: `GET /api/v1/identity/audit-logs`, `GET .../audit-logs/entities/{type}/{id}` | ✅ |
| 1.4 | Xuất Excel `GET .../audit-logs/export` + ghi audit `EXPORT` (FR-CC-021) + trần 50.000 dòng | ✅ |
| 1.5 | Test xanh, `ArchitectureTest` xanh | ✅ (full verify 8.0 và 8.4: `ArchitectureTest` không lỗi) |

### Pha 2 – L04 Rate limiting
| ID | Việc | Trạng thái |
|---|---|---|
| 2.1 | Viết test (đỏ): vượt ngưỡng → 429 `PXVB-CC-0429` + `Retry-After`; dưới ngưỡng không ảnh hưởng; khóa theo `(IP, deviceId)` | 🟡 đã test 429, `Retry-After`, khóa theo IP, login/pin-login/kích hoạt. **Chưa có test khóa theo `X-Device-Id`** (cùng IP khác thiết bị) |
| 2.2 | `RateLimitFilter` + cấu hình `pxvb.security.rate-limit-*` | ✅ (`RateLimitFilter`, `RequestRateLimiter`; cấu hình `pxvb.security.rate-limit-max-requests`, `rate-limit-window`) |
| 2.3 | Áp cho `/auth/login`, PIN, kích hoạt thiết bị; không tin `X-Forwarded-For` mặc định | ✅ (khóa dùng `getRemoteAddr()`, không đọc `X-Forwarded-For`) |
| 2.4 | Test xanh, các IT cũ (`AuthFlowIT`) vẫn xanh | ✅ |

### Pha 3 – L07 ScopeFilter
| ID | Việc | Trạng thái |
|---|---|---|
| 3.1 | Test đơn vị (đỏ) ALL/LINE/UNIT, nhiều vai trò, không có scope | ⬜ |
| 3.2 | `ScopeFilter` trong building-blocks (cách tạo điều kiện lọc phụ thuộc kết quả 0.2) | ⬜ |
| 3.3 | Áp thử danh sách phiên ca (`production`) + IT: Trưởng ca LINE khác không thấy | ⬜ chặn: ca không có dây chuyền. Đề xuất L07 (c): LINE của ca suy từ máy thuộc ca, chờ xác nhận; xem mục 7 |
| 3.4 | Luật ArchUnit chống quên áp dụng | ⬜ |

### Pha 4 – Hoàn thiện
| ID | Việc | Trạng thái |
|---|---|---|
| 4.1 | `./mvnw verify` trên MySQL 8.0 và 8.4 | ❌ chạy xong nhưng 13 lỗi sẵn có ở `ProductionIT`/`MaterialIT` (cả 8.0 và 8.4), không thuộc E02 |
| 4.2 | Review mã + review bảo mật (xác thực, truy vấn DB) | ⬜ |
| 4.3 | Cập nhật `docs/PROGRESS.md`, `docs/OPEN-QUESTIONS.md` (L04/L06/L07), `CLAUDE.md` nếu đổi quy ước | ✅ `PROGRESS.md` và `OPEN-QUESTIONS.md` (L04/L06) đã cập nhật; L07 còn mở. `CLAUDE.md` không đổi quy ước |
| 4.4 | Cập nhật OpenAPI cho endpoint mới (nếu có cơ chế xuất; hiện chưa có file OpenAPI) | ⬜ không có cơ chế xuất OpenAPI; chưa làm |

## 5. Rủi ro

| Mức | Rủi ro | Giảm thiểu |
|---|---|---|
| CAO | Bảng audit lớn, chưa phân vùng (L03): truy vấn rộng làm chậm | Bắt buộc khoảng thời gian, phân trang theo khóa, trần dòng xuất |
| CAO | `ScopeFilter` quên áp dụng làm lộ dữ liệu | Luật ArchUnit + IT cho endpoint thử |
| TB | Tablet chung IP NAT bị chặn nhầm | Khóa theo `(IP, deviceId)`, ngưỡng PIN cao hơn web |
| TB | Bộ đếm trong bộ nhớ không đúng khi nhiều tiến trình (L11) | Ghi rõ giới hạn; Redis ở E25 |
| TB | `AlertingIT.notificationsCanBeMarkedRead` không ổn định (L63) | Chạy riêng test đó trước khi kết luận lỗi |
| THẤP | `identity` gọi `dataio.application.ExportService` | `alerting`, `masterdata` đã làm; kiểm tra ArchUnit ở 0.3 |

## 6. Tiêu chí hoàn thành
- [ ] Mọi mục Pha 1–4 ✅ (trừ L05 ⏸)
- [ ] Test mới viết trước và đã đỏ rồi xanh; toàn bộ `verify` xanh trên 8.0 và 8.4
- [ ] Không có mã lệch quy ước (`CLAUDE.md`), không hard-code ngưỡng
- [ ] `docs/PROGRESS.md` E02 cập nhật

## 7. Nhật ký thực hiện
| Ngày | Ghi chú |
|---|---|
| 2026-10-24 | Tạo kế hoạch; áp dụng giả định G1–G4 vì chưa có trả lời |
| 2026-10-24 | Pha 0 xong 0.1–0.3. **0.4 bị chặn:** không có Java 21 và không có Docker trên máy này nên không build/chạy test được; dừng trước Pha 1 để hỏi người dùng cách tiếp tục (không cài phần mềm hệ thống khi chưa được đồng ý) |
| 2026-10-24 | Đã có Java 21 + Docker (Colima). Testcontainers cần `DOCKER_HOST=unix:///Users/samfisher/.colima/default/docker.sock` và `api.version` 1.44 (chỉ có trong Failsafe, nên IT chạy qua `verify`, không qua `test`). 0.4 xong: baseline 81 IT, 13 lỗi sẵn có ở `ProductionIT`/`MaterialIT` (lệch thời gian ca), không thuộc E02 |
| 2026-10-24 | Pha 1 (L06) xong: `AuditController`, `AuditQueryService`, `AuditFieldMasker`; thêm dependency `pxvb-identity` → `pxvb-dataio` (không tạo chu trình). Bổ sung test lọc module/byUserId, lịch sử theo bản ghi: `AuditQueryIT` 8/8, `AuditFieldMaskerTest` 2/2 |
| 2026-10-24 | Pha 2 (L04) xong: `RequestRateLimiter`, `RateLimitFilter`, `pxvb.security.rate-limit-*`; `RateLimitIT` 4/4 (gồm kích hoạt thiết bị), `RequestRateLimiterTest` 4/4; `AuthFlowIT` vẫn xanh. Ngưỡng cao trong `AbstractIntegrationTest` để các IT khác không bị chặn. Còn thiếu test khóa theo `X-Device-Id` (2.1) |
| 2026-10-24 | Pha 3 (L07) **chưa làm**: ca làm việc (`pr_shift_session`) không có dây chuyền; `MasterdataQueries.MachineInfo` không có `lineCode`. Cần chốt nơi áp dụng LINE scope trước khi làm |
| 2026-10-24 | Full `verify` 8.0 và 8.4 (trước khi bổ sung test): 90 test, 13 lỗi (đúng danh sách baseline, không có E02). Pha 4 (review bảo mật, OpenAPI) chưa làm vì còn L07 |
| 2026-10-24 | Rà soát trạng thái theo bằng chứng: cập nhật bảng Pha 0–4 và mục 1 cho khớp với kết quả test thực tế |
| 2026-10-25 | Dev-team (PM, Arch, Dev, QA) trả lời L07. Đề xuất đã ghi vào `OPEN-QUESTIONS.md` L07 (chưa chốt). Pha 3 vẫn chưa làm, chờ Dev lead xác nhận |
