# ĐẶC TẢ TÍNH NĂNG & THIẾT KẾ HỆ THỐNG
# PHẦN MỀM SỐ HÓA QUY TRÌNH SẢN XUẤT TẠI PHÂN XƯỞNG VẤN BAO (PXVB)

| Mục | Nội dung |
|---|---|
| Mã tài liệu | PXVB-SRS-SDD-v1.1 (tech stack: Spring Boot + MySQL 8) |
| Nguồn gốc | Báo cáo KTKT "Số hóa PXVB theo Điều 12 NĐ 45/2026" (bản 16-6-2026, đã chỉnh sửa sau họp), **Mục XV – Thiết kế chi tiết** (các mục 2.1–2.14, 3, 5, 7) và các mục III, V, X, XIV liên quan |
| Vai trò soạn | Product Owner |
| Đối tượng đọc | Claude Code / đội phát triển (để implement), BA/QA, Chủ đầu tư (TLSG) |
| Ngôn ngữ | Mô tả: tiếng Việt. Định danh code (bảng, cột, API, enum, permission): tiếng Anh. Giao diện người dùng: **100% tiếng Việt** |


## MỤC LỤC
0. Cách dùng tài liệu (dành cho Claude Code)
1. Tổng quan sản phẩm (mục tiêu, phạm vi, vai trò, lộ trình R0–R4)
2. Kiến trúc hệ thống (công nghệ, modular monolith, cấu trúc repo, quy ước, hạ tầng)
3. Thiết kế dữ liệu dùng chung (nhóm bảng `sys_`)
4. Tính năng dùng chung CC (RBAC, khóa dữ liệu & điều chỉnh, audit, workflow, cảnh báo, offline-sync, import/export, jobs, chốt ca)
5. MD – Masterdata · 6. PL – Kế hoạch & lệnh SX · 7. MT – Cấp phát & tiêu hao NVL · 8. PR – Sản lượng & OEE
9. QC – Kiểm soát chất lượng · 10. TX – Tem thuế · 11. PM – Bảo trì & sửa chữa · 12. OH – Trung tu/đại tu
13. SP – Kho phụ tùng · 14. RP – Báo cáo & Dashboard
15. Ứng dụng tablet · 16. Phi chức năng, bảo mật, kiểm thử
17. Điểm cần chốt & giả định · 18. Kế hoạch triển khai cho Claude Code
Phụ lục A (truy vết báo cáo → ID) · B (thuật ngữ) · C (mã lỗi) · D (sơ đồ trạng thái)

---

## 0. CÁCH DÙNG TÀI LIỆU NÀY (dành cho Claude Code)

1. **Đọc theo thứ tự**: §1 → §2 → §3 → §4 (tính năng dùng chung) → §5…§14 (từng phân hệ) → §15–§18.
2. **Mỗi yêu cầu có ID** để truy vết: `FR-<mã phân hệ>-<số>` (chức năng), `BR-<mã>-<số>` (quy tắc nghiệp vụ), `AC-<mã>-<số>` (tiêu chí nghiệm thu), `NFR-<số>` (phi chức năng). Khi commit/PR hãy ghi ID vào message.
3. **Mã phân hệ**: `MD` Masterdata · `PL` Kế hoạch SX · `MT` Cấp phát & tiêu hao NVL · `PR` Sản lượng & OEE · `QC` Kiểm soát chất lượng · `TX` Tem thuế · `PM` Bảo trì định kỳ & sửa chữa · `OH` Trung tu/Đại tu (TDT) · `SP` Kho phụ tùng · `RP` Báo cáo & Dashboard · `CC` Tính năng dùng chung.
4. **Ưu tiên**: `P0` = bắt buộc cho MVP/chạy thử; `P1` = bắt buộc trước nghiệm thu; `P2` = nên có / có thể lùi sau nghiệm thu. Khi mâu thuẫn giữa nguồn gốc và tài liệu này, ưu tiên mục "§17 Điểm cần chốt" và hỏi lại PO.
5. **Không tự suy diễn nghiệp vụ** ở những chỗ đánh dấu `⚠ GIẢ ĐỊNH` – hãy cài đặt dạng **cấu hình được** (config/masterdata) thay vì hard-code, vì giá trị thực do nghiệp vụ TLSG cung cấp sau.
6. **Quy tắc vàng của hệ thống** (xuyên suốt, nguồn: mục 7 báo cáo): *"nhập liệu tối giản – kiểm tra tự động – phê duyệt có kiểm soát – lưu vết đầy đủ"*. Mọi tính năng phải tuân thủ.

---

## 1. TỔNG QUAN SẢN PHẨM

### 1.1 Bối cảnh & vấn đề
PXVB (Công ty Thuốc lá Sài Gòn – TLSG) có **73 dây chuyền** (42 dây chuyền vấn điếu + 31 dây chuyền đóng bao), chạy **3 ca liên tục**. Hiện toàn bộ ghi nhận bằng giấy + nhiều file Excel rời rạc: không có số liệu thời gian thực, OEE gần như không tính, tổng hợp báo cáo ca tốn 1–2 giờ, cấp phát NVL ký phiếu giấy (3 loại phiếu), KSCL dùng 46 loại biểu mẫu giấy (kiểm tra 30 phút/lần), tem thuế theo dõi Excel, bảo trì/TDT bằng giấy, kho phụ tùng chạy song song ERP và Fox.

### 1.2 Tầm nhìn & mục tiêu đo được
| # | Mục tiêu | Chỉ số đo (nguồn báo cáo) |
|---|---|---|
| G1 | Một nền tảng dữ liệu sản xuất tập trung, liên thông | 100% nghiệp vụ trong phạm vi nhập trên hệ thống; 0 file Excel tổng hợp thủ công |
| G2 | Theo dõi gần thời gian thực | Dashboard cập nhật ≤ 5 phút; báo cáo ca sẵn sàng ≤ 5 phút sau khi chốt ca |
| G3 | OEE tự động cho 100% (73) dây chuyền | OEE theo ca/ngày/tuần/tháng |
| G4 | Giao nhận NVL điện tử 100% | Thay ký giấy; tổng hợp NVL cuối ca tự động (thay 30–45 phút/ca) |
| G5 | Số hóa KSCL | 46 biểu mẫu; cảnh báo lỗi A/B/C ≤ 5 phút; lỗi cấp A xử lý ≤ 30 phút |
| G6 | Kiểm soát tem thuế theo dải seri | Đối chiếu tem – sản lượng bao theo thời gian thực cho toàn bộ máy bao |
| G7 | Số hóa bảo trì/TDT/kho phụ tùng (CMMS) | Truy vết phụ tùng → công việc bảo trì/hồ sơ TDT; MTTR/MTBF tự động |
| G8 | Dữ liệu tin cậy, có vết | Khóa dữ liệu sau xác nhận; mọi chỉnh sửa có yêu cầu–phê duyệt–nhật ký |

### 1.3 Phạm vi
**Trong phạm vi**: Ứng dụng web (quản lý), ứng dụng tablet Android (dây chuyền), dashboard thời gian thực, 10 phân hệ nghiệp vụ, hạ tầng on-premise (máy chủ, wifi công nghiệp, 94 tablet).
**Ngoài phạm vi (v1)**: Tích hợp realtime hai chiều với ERP/Fox (chỉ chuẩn bị REST API + import/export Excel đối chiếu); đọc tín hiệu trực tiếp từ PLC/máy (sản lượng nhập tay trên tablet); chữ ký số pháp lý (chỉ "xác nhận điện tử nội bộ"); thanh toán/quyết toán tài chính (TCKT chỉ khai thác bảng tổng hợp); mobile iOS; SMS.

### 1.4 Người dùng & vai trò (persona)
| Mã vai trò | Tên (theo báo cáo) | Thiết bị chính | Mục tiêu chính |
|---|---|---|---|
| `SYS_ADMIN` | Quản trị hệ thống/CNTT | Web | Tài khoản, masterdata, sao lưu |
| `DIRECTOR` | Quản đốc | Web, màn hình dashboard | KPI toàn PX, phê duyệt lớn |
| `DEPUTY_DIRECTOR` | Phó Quản đốc | Web | Lập KHSX tuần, duyệt lệnh SX, duyệt định mức |
| `PLANNER` | NV Kế hoạch & Điều độ SX | Web | Nhập KHSX tháng, danh mục SP |
| `NORM_CLERK` | NV Định mức NVL | Web | Bảng định mức |
| `SHIFT_LEADER` | Trưởng ca | Web + tablet | Mở/đóng ca, xác nhận lệnh, xử lý cảnh báo |
| `SUPERVISOR` | NV giám sát sản xuất | Tablet | Giám sát nhập liệu khu vực |
| `OPERATOR` | Công nhân vận hành (máy vấn/bao) | Tablet dây chuyền | Nhập sản lượng, dừng máy, KSCL inline, nhận NVL, tem hư |
| `STAT_OUTPUT` | NV thống kê sản lượng | Web | Đối chiếu sản lượng |
| `STAT_MATERIAL` | NV thống kê NVL | Web/PC giao nhận | Duyệt phiếu cấp phát, chốt tiêu hao |
| `MATERIAL_ISSUER` | NV/CN cấp phát NVL | Tablet kho sợi/vật tư | Soạn hàng, ghi nhận cấp thực tế |
| `STAT_STAMP` | NV thống kê tem | Web/tablet | Khai báo block tem, xuất dùng, chốt ca tem |
| `QC_INLINE` | NV KSCL PXVB | Web/tablet | Theo dõi chất lượng inline |
| `QA_INSPECTOR` | NV ĐBCL (công ty) | Tablet ĐBCL | Kiểm tra độc lập B11/B15/B12/B14/B21 |
| `QA_SUPERVISOR` | Giám sát ĐBCL | Web | Xử lý SP không phù hợp |
| `QA_HEAD` | TP/PP ĐBCL | Web | Phê duyệt xử lý, báo cáo chất lượng |
| `MNT_TECH` | Kỹ thuật viên bảo trì | Web | Lập KH bảo trì, danh mục công việc |
| `MNT_LEAD` | Tổ trưởng tổ MMTB | Web + tablet | Phân công, duyệt yêu cầu sửa chữa |
| `MECHANIC` | Thợ máy / thợ sửa chữa (điện/cơ) | Tablet | Thực hiện & ghi nhận bảo trì/sửa chữa |
| `WAREHOUSE_KEEPER` | Thủ kho phụ tùng / NV kho (Kho CK) | Web | Nhập/xuất/tồn phụ tùng |
| `KTCD` | Phòng KTCĐ | Web | KH TDT năm, hồ sơ TDT, duyệt phụ tùng phát sinh |
| `PXCD` | PXCĐ (phần điện, gia công cơ khí) | Web/tablet | Đánh giá/nghiệm thu phần điện |
| `KHVT` | Phòng KHVT | Web | Cung cấp vật tư TDT; cung cấp KHSX tháng |
| `TCKT` | Phòng TCKT / Kế toán–Thuế | Web | Khai thác bảng tổng hợp vật tư TDT, báo cáo tem |
| `BOARD` | Ban Giám đốc / Ban lãnh đạo | Web | Duyệt KH TDT năm, xem KPI |
| `QA_DEPT` | Phòng ĐBCL (nghiệm thu TDT) | Web | Xác nhận chất lượng sau TDT |

### 1.5 Bản đồ phân hệ & phụ thuộc
```
MD (Masterdata) ──► PL (KHSX/Lệnh SX) ──► MT (Cấp phát NVL)
        │                  │                    │
        │                  ▼                    ▼
        │            PR (Sản lượng/OEE) ◄──► TX (Tem thuế)
        │                  │   ▲
        │                  ▼   │
        ├────────────► QC (Chất lượng) ─── (Q của OEE, truy ngược lô NVL)
        │
        ├────────────► PM (Bảo trì định kỳ + sửa chữa) ◄── PR (giờ chạy máy, sự kiện dừng máy C)
        │                  │ yêu cầu xuất kho
        ├────────────► OH (TDT) ───────────┤
        └────────────► SP (Kho phụ tùng) ◄─┘
                           ▲
 Tất cả ──► RP (Báo cáo & Dashboard) ◄── Alerting/Notification ◄── CC (Audit, Workflow, Sync)
```

### 1.6 Lộ trình phát hành (bám phương án 3 ca liên tục, mục X báo cáo)
| Release | Nội dung | Điều kiện chuyển bước (từ báo cáo) |
|---|---|---|
| **R0 – Nền tảng** | CC (auth/RBAC/audit/workflow/alert/sync/import-export), MD | Masterdata được phê duyệt; ma trận phân quyền chốt |
| **R1 – Lõi sản xuất (pilot 1 nhóm máy)** | PL, PR (+OEE), MT, tablet app (vận hành), RP ca/ngày | Dữ liệu khớp thực tế trong ngưỡng chấp nhận |
| **R2 – Chất lượng & tem** | QC (B06/B08/B09 trước, B11/B15/B12/B14/B21 sau), TX, đối chiếu chéo + chốt ca đa bên | Chạy song song 3 ca liên tục, báo cáo đối chiếu |
| **R3 – CMMS** | PM, SP, OH | Nghiệm thu từng phân hệ |
| **R4 – Hoàn thiện & nhân rộng** | RP đầy đủ (tuần/tháng, phân tích), xuất thuế, tối ưu hiệu năng, hardening ATTT cấp độ 2 | Vận hành thử + nghiệm thu tổng thể |

---

## 2. KIẾN TRÚC HỆ THỐNG

### 2.1 Kiến trúc logic (4 tầng theo báo cáo)
```
┌───────────────── Tầng thiết bị đầu cuối ─────────────────────────────────────┐
│ Tablet Android (94) │ PC quản lý ca/KTCĐ/kho │ Tablet-mobile thợ máy │ Màn hình dashboard │
└───────────────────────────────┬──────────────────────────────────────────────┘
                 HTTPS (Nginx reverse proxy) + WebSocket (STOMP)
┌───────────────────────────────┴──────────────────────────────────────────────┐
│ Tầng ứng dụng: Web Admin (Angular 18) · Dashboard (React 19) · Tablet (React Native 0.81)│
└───────────────────────────────┬──────────────────────────────────────────────┘
┌───────────────────────────────┴──────────────────────────────────────────────┐
│ Tầng dịch vụ (Java 21, Spring Boot 3.5, Tomcat): Modular Monolith             │
│  API host ─ Modules(MD,PL,MT,PR,QC,TX,PM,OH,SP,RP,Identity,Alerting)          │
│  Worker host (Quartz jobs, alert evaluators, projections, report builder)      │
│  Redis (cache, distributed lock) · RabbitMQ (events/alerts, STOMP relay)       │
└───────────────────────────────┬──────────────────────────────────────────────┘
┌───────────────────────────────┴──────────────────────────────────────────────┐
│ Tầng dữ liệu: MySQL 8 (CSDL duy nhất) + File store (ảnh/đính kèm) + Backup    │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Công nghệ (khóa theo mục XIV báo cáo)
| Thành phần | Lựa chọn | Ghi chú cho implement |
|---|---|---|
| Backend | **Java 21 LTS** + **Spring Boot 3.5.x** (Maven) | Spring Web (REST), Spring Data JPA/Hibernate 6 (+ **jOOQ** hoặc `JdbcTemplate` cho báo cáo nặng), Spring Security, Jakarta Bean Validation, MapStruct, Flyway, **Spring Modulith** (kiểm soát ranh giới module), Resilience4j, Lombok (tùy chọn). CQRS nhẹ = lớp `*CommandService` / `*QueryService`, không cần thư viện mediator |
| Web quản lý | **Angular 18.x** + TypeScript, Angular Material hoặc PrimeNG, ngx-translate (chỉ `vi`) | Standalone components, signals, lazy route theo phân hệ |
| Dashboard realtime | **React 19.x** + Vite + TypeScript, ECharts | App riêng, nhúng được vào Angular qua route/iframe, kiosk-mode cho màn hình phòng quản đốc |
| Tablet | **React Native 0.81.x** (Android 11+; thiết bị đích Android ≥ 11, khuyến nghị 15) | SQLite cục bộ (op-sqlite/WatermelonDB), react-native-vision-camera (QR/barcode), kiosk qua MDM |
| CSDL | **MySQL 8.0** (InnoDB, `utf8mb4`; SQL/DDL viết tương thích 8.0 và chạy được trên **8.4 LTS** — ⚠ 8.0 đã hết vòng đời hỗ trợ từ 04/2026, khuyến nghị vận hành 8.4, xem Q01) | Mọi nghiệp vụ + dữ liệu phân tích trong cùng 1 CSDL (database `pxvb`), tách module bằng **tiền tố bảng** (MySQL không có schema riêng) |
| Cache / Queue | **Redis 7.x**, **RabbitMQ 3.x** | Outbox pattern đảm bảo không mất sự kiện |
| Realtime | Spring **WebSocket + STOMP** (broker relay qua RabbitMQ STOMP plugin, hoặc Redis pub/sub) | Dashboard + thông báo tablet; fallback polling 60s |
| Lập lịch | **Quartz** (`spring-boot-starter-quartz`, JDBC JobStore trên MySQL, clustered) | Xem §4.9 |
| OS/Web server | Ubuntu 24.04 LTS, **Nginx 1.26** (reverse proxy, TLS) + Tomcat nhúng (Spring Boot) | Ảo hóa vCenter; máy chủ tối đa 8C/16T, 32GB RAM → dùng **modular monolith**, không microservice |
| Xuất file | Apache POI (Excel; `SXSSFWorkbook` cho dữ liệu lớn), OpenPDF hoặc JasperReports (PDF; license LGPL/MPL) | Template báo cáo dạng code + file cấu hình |
| Xác thực | JWT (access 15') + refresh token xoay vòng; mật khẩu băm **Argon2id** (`Argon2PasswordEncoder` của Spring Security + BouncyCastle; hoặc PBKDF2-SHA512 ≥ 210k vòng); hỗ trợ tùy chọn **LDAP v3** (Spring Security LDAP) (báo cáo bắt buộc chuẩn LDAPv3 cho truy cập thư mục) | Xem §4.1 |
| Quan sát | Logback (JSON, `logstash-logback-encoder`) → file + ELK/Loki tùy chọn; Micrometer + OpenTelemetry; Spring Boot Actuator health groups `/health/live`, `/health/ready` | |
| API doc | OpenAPI 3 (**springdoc-openapi**) | Bắt buộc có cho mọi endpoint |

### 2.3 Kiểu kiến trúc: Modular Monolith
- Mỗi phân hệ là một **module độc lập** (Maven module, package gốc `vn.pxvb.<module>`) gồm các package `domain` / `application` / `infrastructure` / `api`; **chỉ giao tiếp qua (a) public contract ở package `<module>.contract` (interface + DTO), hoặc (b) domain event qua RabbitMQ/Spring `ApplicationEventPublisher`**. Cấm tham chiếu trực tiếp `@Entity`/`Repository` của module khác (kiểm bằng Spring Modulith `ApplicationModules.verify()` + ArchUnit). Mỗi module sở hữu **nhóm bảng riêng (tiền tố riêng)** và `EntityManager`/repository riêng; **không FK vật lý chéo module** (chỉ lưu `uid` tham chiếu; toàn vẹn kiểm ở tầng ứng dụng).
- Một DB MySQL, nhiều **nhóm bảng theo tiền tố**: `sys`, `md`, `pl`, `mt`, `pr`, `qc`, `tx`, `pm`, `oh`, `sp`, `rp`. **Quy ước ký hiệu trong tài liệu**: `md.Machine` là tên *logic*; tên vật lý MySQL là `md_machine` (snake_case, `schema.Table` → `schema_table`); cột `PascalCase` → `snake_case` (Hibernate `CamelCaseToUnderscoresNamingStrategy`). Tiền tố `sys_` (không dùng database tên `sys` vì trùng schema hệ thống của MySQL).
- Hai tiến trình triển khai (2 Spring Boot app dùng chung các module): `pxvb-api` (HTTP + WebSocket) và `pxvb-worker` (Quartz, consumer RabbitMQ, projection). Active–backup: 2 nút, Nginx upstream với health-check; Worker chỉ chạy trên một nút tại một thời điểm (Quartz clustered mode, JDBC JobStore).

### 2.4 Cấu trúc repo (monorepo)
```
pxvb/
├─ docs/                      # tài liệu này, ADR, mẫu import Excel
├─ src/
│  ├─ backend/
│  │  ├─ pom.xml               # parent POM (Maven multi-module, BOM Spring Boot)
│  │  ├─ pxvb-building-blocks/ # BaseEntity, AuditableEntity, Result, Outbox, IdempotencyFilter, AuditEntityListener,
│  │  │                       #  StateMachine, Specification, ProblemDetail mapper, IClock/CurrentUser
│  │  ├─ modules/
│  │  │  ├─ identity/  masterdata/  planning/  materials/  production/
│  │  │  ├─ quality/   taxstamp/    maintenance/  overhaul/  spares/
│  │  │  ├─ reporting/ alerting/    (mỗi module: package domain, application, infrastructure, api, contract;
│  │  │  │                           migration Flyway: src/main/resources/db/migration/<module>/V*.sql)
│  │  ├─ hosts/ pxvb-api/  pxvb-worker/   # @SpringBootApplication
│  │  └─ tests/               # Unit (JUnit 5), Integration (Testcontainers MySQL 8), Architecture (Spring Modulith/ArchUnit)
│  ├─ web/                    # Angular workspace (apps/admin + libs/{ui,api-client,auth,i18n})
│  ├─ dashboard/              # React 19 + Vite
│  └─ tablet/                 # React Native 0.81 (Android)
├─ deploy/                    # nginx.conf, systemd units / docker-compose, backup scripts, MDM profile
└─ tools/                     # seed, data-migration, load-test (k6)
```
- Kiểm thử kiến trúc (Spring Modulith `verify()` / ArchUnit) bắt buộc: module không phụ thuộc chéo ngoài package `contract`.
- Sinh API client TypeScript từ OpenAPI (`openapi-typescript-codegen`) dùng chung cho web/dashboard/tablet.

### 2.5 Quy ước chung
| Chủ đề | Quy ước |
|---|---|
| Khóa chính | `Id BINARY(16)` sinh phía ứng dụng, **UUID v7** (tuần tự → ít phân mảnh B-tree của InnoDB; tablet có thể tạo offline); ánh xạ JPA `UUID` ↔ `BINARY(16)` (`@JdbcTypeCode(SqlTypes.BINARY)`/converter), thư viện `uuid-creator`. Khóa nghiệp vụ: cột `Code` unique |
| Cột chung (mọi bảng) | `CreatedAt, CreatedBy, UpdatedAt, UpdatedBy, RowVersion (BIGINT, JPA `@Version` — tự tăng mỗi lần cập nhật)`; bảng danh mục thêm `IsActive`, `IsDeleted` (xóa mềm). Bảng giao dịch **không xóa** – chỉ hủy (`Status=CANCELLED`) |
| Thời gian | Lưu `DATETIME(6)` **UTC** (JDBC `connectionTimeZone=UTC`, `hibernate.jdbc.time_zone=UTC`, Java `Instant`); hiển thị Asia/Ho_Chi_Minh (UTC+7). Mỗi bản ghi giao dịch có thêm `BusinessDate (DATE)` + `ShiftCode` (ca đêm vắt ngày thuộc ngày bắt đầu ca). Dữ liệu nhập tay có đủ 4 mốc: `OccurredAt` (thời điểm phát sinh), `EnteredAt` (thiết bị), `ReceivedAt` (server), `EnteredBy`, `DeviceId`, `SyncStatus` |
| Số lượng | `DECIMAL(18,3)` (`BigDecimal`); sản lượng điếu dùng `bigint` (đơn vị: điếu) và quy đổi hiển thị theo "nghìn điếu"; bao = `bigint` |
| API | REST, prefix `/api/v1/<module>/...`, JSON camelCase, phân trang `?page=&pageSize=&sort=&q=`, lọc theo field. Lỗi theo **RFC 7807 ProblemDetails**: `{type,title(vi),status,code,detail,errors{field:[msg]},traceId}`; mã lỗi nghiệp vụ dạng `PXVB-<MOD>-<nnnn>` |
| Đồng thời | Optimistic concurrency bằng `RowVersion` ↔ header `ETag`/`If-Match`; xung đột → 409 `PXVB-CC-0409` |
| Idempotency | Mọi lệnh ghi từ tablet gửi `Idempotency-Key` = `clientCommandId` (UUID) + `X-Device-Id`; server lưu 30 ngày, trả lại kết quả cũ nếu trùng |
| Phân quyền | Policy theo `permission code` dạng `<mod>.<resource>.<action>` (xem §4.1); kiểm tra ở API + ẩn UI; lọc dữ liệu theo phạm vi (dây chuyền/đơn vị) |
| Xác thực dữ liệu | Jakarta Bean Validation (`@Valid`) + validator nghiệp vụ ở application; ràng buộc CHECK (MySQL 8.0.16+ thực thi)/UNIQUE/FK ở DB; thông báo lỗi tiếng Việt |
| Nhật ký | Mọi thay đổi bản ghi nghiệp vụ → `sys.AuditLog` tự động qua Hibernate/JPA entity listener (§4.3) |
| Cấu hình thay vì hard-code | Ngưỡng, khung giờ, chu kỳ, mã lý do, công thức OEE đều ở masterdata có phiên bản & ngày hiệu lực |
| Đặt tên | Java: PascalCase (class), camelCase (field); DB: bảng `<prefix>_<snake_case>`, cột `snake_case` (xem §2.3); enum lưu `VARCHAR(30)` (UPPER_SNAKE, `@Enumerated(STRING)`) – không dùng số. Collation `utf8mb4_0900_ai_ci` mặc định; cột `Code` nghiệp vụ dùng `utf8mb4_0900_as_cs` hoặc `utf8mb4_bin` để phân biệt hoa/thường (⚠ chốt ở Q01) |
| Migration | **Flyway** theo module (`db/migration/<module>/V<yyyymmdd>__<mô_tả>.sql`; MySQL không rollback DDL → mỗi migration nhỏ, có script hoàn tác thủ công); script seed idempotent trong `tools/seed` |
| Log/PII | Không log mật khẩu/token; log có `traceId`, `userId`, `deviceId` (SLF4J MDC) |
| Giao dịch | `@Transactional` ở application service; `READ COMMITTED` (mặc định dự án thay cho REPEATABLE READ của InnoDB để giảm gap-lock); khóa bi quan dùng `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`); tránh giao dịch dài |
| Kiểu JSON | Cột `json` dùng kiểu `JSON` của MySQL (ánh xạ `@JdbcTypeCode(SqlTypes.JSON)`); cần lọc/ràng buộc thì tạo *generated column* + index |

### 2.6 Hạ tầng triển khai (tóm tắt, nguồn mục 2.1.e & 3)
- Máy chủ on-premise ảo hóa, **active–backup**, tính sẵn sàng ≥ 99,5%/năm (< 1 giờ/tháng ngoài bảo trì), phục hồi ≤ 3 giờ; UPS ≥ 30 phút cho máy chủ & mạng lõi.
- Wifi công nghiệp Wi-Fi 6, ~20 AP, phủ ~10.000 m², chịu ≥ 94 tablet đồng thời (≥ 50 thiết bị/AP). Mạng: 1 switch core + 4 switch nhánh PoE, firewall.
- **94 tablet** (Android, ≥10", IP54+, MDM quản lý): 42 vấn + 4 phụ trợ (cấp đầu lọc) + 36 bao (26 dây chuyền đã có IPC dùng 1 tablet; 5 dây chuyền dùng 2 tablet) + 2 kho sợi + 4 giao nhận vật tư + 6 ĐBCL. Phần mềm tablet cũng phải chạy được trên **máy tính công nghiệp (IPC) hiện hữu** (Android/Windows – ⚠ xác nhận; nếu IPC chạy Windows thì cung cấp bản **web responsive (PWA)** của màn hình vận hành dùng chung API).
- Sao lưu: Full + Incremental theo lịch, lưu thiết bị độc lập (2 × SSD backup, tối thiểu 4TB), lưu dự phòng dài hạn (tối thiểu 20 năm theo bảng công nghệ). Đề xuất cấu hình mặc định: **Percona XtraBackup** (hoặc MySQL Enterprise Backup nếu có license) Full hằng đêm 01:00, Incremental mỗi 6 giờ, **binlog** (`binlog_format=ROW`, `sync_binlog=1`, `innodb_flush_log_at_trx_commit=1`) sao chép liên tục ra thiết bị sao lưu (RPO ≤ 15 phút) để point-in-time recovery. HA CSDL: nút chính + nút phụ **bán đồng bộ (semi-sync) replication** (hoặc InnoDB Cluster/Group Replication nếu ≥ 3 nút — ⚠ Q01); chuyển nút thủ công/bán tự động qua MySQL Router/Orchestrator.

### 2.7 Luồng dữ liệu tổng thể (end-to-end)
1. **Khởi tạo**: KHSX tháng (KHVT cấp → PLANNER nhập) → KHSX tuần (Phó QĐ) → lệnh SX ngày/ca (gợi ý tự động theo ghép cặp vấn–bao) → Trưởng ca xác nhận → **sinh phiếu cấp phát NVL** (3 loại, 4 khung giờ) + **lịch KSCL 30'** + khởi tạo phiên ghi nhận ca.
2. **Trong ca** (tablet): giao nhận NVL điện tử; sản lượng (định kỳ, mặc định 2 giờ/lần – cấu hình); dừng máy (mã A–I); kiểm tra chất lượng inline; tem xuất dùng/tem hư; báo sự cố.
3. **Xử lý & cảnh báo** (≤ 5 phút): sản lượng lệch KH, hao hụt NVL, lỗi A/B/C, hao hụt tem, tồn điếu, trễ lịch KSCL, sự cố máy, bảo trì quá hạn, TDT chậm, tồn kho phụ tùng < min.
4. **Chốt ca**: đối chiếu chéo tự động → xác nhận đa bên → khóa ca → sinh báo cáo ca (≤ 5 phút).
5. **CMMS song song**: kế hoạch bảo trì tháng → D-1 tổng hợp vật tư → yêu cầu xuất kho → thợ máy thực hiện → lịch sử/MTTR/MTBF; KH TDT năm → hồ sơ TDT → phụ tùng theo định khoản → nghiệm thu → bảng tổng hợp cho TCKT.
6. **Tổng hợp**: dashboard theo vai trò, báo cáo ca/ngày/tuần/tháng, phân tích xu hướng, lưu ≥ 3 năm truy vấn trực tuyến.

---

## 3. THIẾT KẾ DỮ LIỆU DÙNG CHUNG (nhóm bảng `sys`, vật lý `sys_*`)

> Các bảng nghiệp vụ được mô tả ngay trong từng phân hệ (§5–§14). Phần này là bảng nền tảng dùng chung. Kiểu dữ liệu rút gọn: `str(n)`=VARCHAR(n) `utf8mb4`, `dt`=DATETIME(6) (UTC), `d`=DATE, `dec`=DECIMAL(18,3), `json`=JSON (MySQL), `uid`=BINARY(16). Tên vật lý bảng = `<prefix>_<snake_case>` (xem §2.3). Cột chung (§2.5) mặc định có ở mọi bảng, không liệt kê lại.

| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `sys.OrgUnit` | Id, Code, Name, ParentId | PXVB, PXCD, KTCD, KHVT, TCKT, DBCL, KHO_CK, BGD |
| `sys.User` | Id, Username(unique), FullName, EmployeeCode, BadgeNo(unique, nullable), PasswordHash, PinHash, OrgUnitId, Email, Phone, IsActive, FailedCount, LockedUntil, LastLoginAt, MustChangePassword, AuthSource(LOCAL/LDAP) | PIN 4–6 số dùng cho đổi người dùng nhanh trên tablet |
| `sys.Role` | Id, Code(unique), Name, Description, IsSystem | Mã vai trò xem §1.4 |
| `sys.Permission` | Code(PK, vd `pr.output.create`), Module, Description | Seed bằng code, không sửa qua UI |
| `sys.RolePermission` | RoleId, PermissionCode | |
| `sys.UserRole` | UserId, RoleId, ScopeType(ALL/LINE/UNIT), ScopeValue, ValidFrom, ValidTo | Phạm vi dữ liệu: toàn PX / theo nhóm dây chuyền / theo đơn vị |
| `sys.RefreshToken` | Id, UserId, TokenHash, DeviceId, ExpiresAt, RevokedAt, ReplacedBy | |
| `sys.Device` | Id, DeviceCode(unique), Name, Position(VAN/BAO/FIBER_STORE/MATERIAL_HANDOVER/QA/MAINT/OFFICE), MachineIds(json), AppVersion, LastSeenAt, LastSyncAt, PendingCount, IsActive | Đăng ký thiết bị; một tablet gắn với 1 hoặc nhiều máy (VD IPC + tablet) |
| `sys.AuditLog` | Id, OccurredAt, Module, EntityType, EntityId, Action(CREATE/UPDATE/STATE/DELETE/LOGIN/EXPORT/APPROVE…), FieldChanges(json: [{field,old,new}]), Reason, ByUserId, ApprovedByUserId, DeviceId, Ip, CorrelationId, AdjustmentRequestId | Append-only; không có API xóa; DB role ứng dụng chỉ INSERT/SELECT |
| `sys.AdjustmentRequest` | Id, Code, Module, EntityType, EntityId, FieldPath, OldValue, NewValue, Reason, AttachmentIds, Status(PENDING/APPROVED/REJECTED/APPLIED/CANCELLED), RequestedBy, ApproverRoleCode, ApprovedBy, DecidedAt, DecisionNote, AppliedAt | §4.3 |
| `sys.ApprovalFlowDef` | Id, Code, Module, EntityType, Steps(json), IsActive | Định nghĩa luồng duyệt, cấu hình được |
| `sys.ApprovalInstance` | Id, FlowCode, EntityType, EntityId, Status(PENDING/APPROVED/REJECTED/CANCELLED), CurrentStep, StartedBy, StartedAt, CompletedAt | |
| `sys.ApprovalStep` | Id, InstanceId, StepNo, RoleCode, AssigneeUserId?, Status, DecidedBy, DecidedAt, Note | |
| `sys.Signature` | Id, EntityType, EntityId, Purpose(CONFIRM/APPROVE/ACCEPT), RoleCode, UserId, SignedAt, DeviceId, Method(PASSWORD/PIN), Hash(SHA-256 của bản ghi tại thời điểm ký) | "Xác nhận điện tử đa bên" (không phải chữ ký số pháp lý) |
| `sys.AlertRule` | Id, Code(unique), Module, Metric, Severity, Condition(json), RecipientPolicy(json), EscalationPolicy(json), DedupWindowMin, IsActive | Ngưỡng tham chiếu masterdata `md.Threshold` |
| `sys.Alert` | Id, RuleCode, Severity(INFO/WARN/CRITICAL), Module, SubjectType, SubjectId, MachineId?, ShiftSessionId?, Title, Message, Payload(json), DedupKey, Status(OPEN/ACK/IN_PROGRESS/RESOLVED/CLOSED), RaisedAt, AckAt, AckBy, ResolvedAt, ResolvedBy, Cause, ResolutionNote, EscalationLevel | |
| `sys.AlertRecipient` | AlertId, UserId, DeliveredAt, ReadAt | |
| `sys.Notification` | Id, UserId, Type, Title, Body, DeepLink, AlertId?, CreatedAt, ReadAt, Channel(INAPP/EMAIL) | |
| `sys.Attachment` | Id, OwnerType, OwnerId, FileName, ContentType, SizeBytes, Sha256, StoragePath, UploadedBy | Cho phép jpg/png/pdf/xlsx ≤ 10MB; quét định dạng bằng magic bytes |
| `sys.ImportJob` | Id, TemplateCode, FileName, Status(UPLOADED/VALIDATED/COMMITTED/FAILED), TotalRows, OkRows, ErrorRows, ErrorReportPath, Mode(VALIDATE_ONLY/UPSERT) | |
| `sys.IdempotencyRecord` | Key, DeviceId, RequestHash, ResponseStatus, ResponseBody, CreatedAt | TTL 30 ngày |
| `sys.SyncConflict` | Id, DeviceId, CommandId, EntityType, EntityId, ClientPayload(json), ServerSnapshot(json), Reason, Status(OPEN/RESOLVED_ACCEPT_CLIENT/RESOLVED_KEEP_SERVER/DISCARDED), ResolvedBy, ResolvedAt | §4.7 |
| `sys.OutboxMessage` | Id, Type, Payload(json), CreatedAt, ProcessedAt, Attempts | Transactional outbox → RabbitMQ |
| `sys.NumberSequence` | Key, Prefix, LastNumber, ResetPolicy(NONE/YEARLY/MONTHLY/DAILY) | Sinh mã chứng từ: `LSX-20261013-A-001` v.v. |
| `sys.Setting` | Key, Value(json), Scope | Tham số hệ thống (vd chu kỳ ghi sản lượng 120 phút) |

---

## 4. TÍNH NĂNG DÙNG CHUNG (CC)

### 4.1 CC-01/02 Xác thực, phiên và phân quyền (RBAC)
| ID | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-CC-001 | Đăng nhập web bằng `username + password`; sau đăng nhập cấp access JWT (15') + refresh token (8h, xoay vòng, thu hồi khi đăng xuất). Khóa tài khoản 15' sau 5 lần sai liên tiếp; ghi `AuditLog(LOGIN)` kể cả thất bại. | P0 |
| FR-CC-002 | **Tablet**: thiết bị được đăng ký (`sys.Device`) và đăng nhập "thiết bị" bằng mã kích hoạt một lần; sau đó người dùng thao tác bằng **quét thẻ/nhập mã nhân viên + PIN** (đổi người dùng nhanh ≤ 5 giây). Mỗi bản ghi lưu `EnteredBy` = người đang đăng nhập, không phải tài khoản thiết bị. Tự khóa về màn hình đăng nhập sau 5' không thao tác (cấu hình). | P0 |
| FR-CC-003 | RBAC: `Role → Permission` nhiều-nhiều; `UserRole` có **phạm vi** (ALL / LINE / UNIT). Quản trị tạo/sửa/khóa user, gán vai trò, cấu hình quyền chi tiết theo phân hệ & chức năng (UI ma trận). Thay đổi quyền ghi audit. | P0 |
| FR-CC-004 | Mật khẩu: tối thiểu 10 ký tự, kiểm tra độ mạnh, băm Argon2id; bắt buộc đổi ở lần đầu; không truyền/ghi mật khẩu dạng rõ; chỉ HTTPS (TLS ≥ 1.2). Tùy chọn xác thực LDAP v3 (`AuthSource=LDAP`) – bật bằng cấu hình. | P0 |
| FR-CC-005 | Quy tắc tách biệt: `SYS_ADMIN` **không** được sửa dữ liệu nghiệp vụ đã khóa; mọi thao tác quản trị ghi audit. `QA_*` có quyền độc lập với `QC_INLINE` (xem BR-QC-010). | P0 |
| FR-CC-006 | Chính sách quyền cấp dòng (row-level): API luôn lọc theo `UserRole.Scope` (vd Trưởng ca chỉ thấy máy/ca mình được phân công; ĐBCL chỉ thấy dữ liệu của mình trước khi nộp). Cài đặt bằng `IScopeFilter` dùng chung ở repository. | P1 |

**Danh mục quyền (seed)** – mẫu đặt tên `<mod>.<resource>.<action>`, action ∈ {`view`,`create`,`update`,`delete`,`submit`,`confirm`,`approve`,`lock`,`adjust.request`,`adjust.approve`,`export`,`config`}. Ví dụ: `pl.monthlyPlan.update`, `pr.output.create`, `qc.inline.create`, `qc.nc.approve`, `tx.issue.create`, `sp.issue.confirm`, `oh.dossier.close`, `rp.dashboard.view`. Mỗi phân hệ liệt kê ma trận **Vai trò × Hành động** ở mục "Phân quyền" của mình; dev sinh danh sách `Permission` từ đó.

### 4.2 CC-03 Vòng đời dữ liệu, khóa nghiệp vụ và điều chỉnh sau xác nhận (nguồn mục 7.3)
**Trạng thái dữ liệu giao dịch dùng chung** (enum `RecordState`):
`DRAFT` (nháp) → `SUBMITTED` (đã gửi) → `CONFIRMED` (đã xác nhận) → `SHIFT_LOCKED` (đã khóa ca) → `FINALIZED` (đã chốt báo cáo).

| ID | Yêu cầu | ƯT |
|---|---|---|
| FR-CC-010 | Người nhập chỉ sửa/xóa bản ghi ở `DRAFT` (và `SUBMITTED` nếu chưa ai xác nhận – cấu hình). Từ `CONFIRMED` trở đi **không sửa trực tiếp** (API trả 423 `PXVB-CC-0423`). | P0 |
| FR-CC-011 | **Yêu cầu điều chỉnh** (`AdjustmentRequest`): người dùng chọn bản ghi + trường, nhập giá trị đề nghị + lý do (bắt buộc) + đính kèm tùy chọn → người có thẩm quyền (theo `ApproverRoleCode` cấu hình theo phân hệ) duyệt/từ chối → khi duyệt, hệ thống **tự áp giá trị mới** trong 1 transaction, ghi audit (giá trị trước/sau, người đề nghị, người duyệt, lý do, thiết bị/IP) và **tính lại** các chỉ số phụ thuộc (OEE, tiêu hao, báo cáo ca nếu chưa `FINALIZED`; nếu đã `FINALIZED` → tạo "bản sửa đổi báo cáo" `Revision+1`, giữ bản cũ). | P0 |
| FR-CC-012 | Nhật ký chỉnh sửa lưu tối thiểu: mã giao dịch, phân hệ, trường thay đổi, giá trị trước/sau, người đề nghị, người phê duyệt, thời điểm, thiết bị/IP, lý do, trạng thái xử lý; **không** có chức năng xóa qua UI. | P0 |
| FR-CC-013 | Người duyệt không được tự duyệt yêu cầu của chính mình (BR-CC-003). | P0 |
| FR-CC-014 | Màn hình "Yêu cầu điều chỉnh của tôi / chờ tôi duyệt" + badge số lượng chờ; thông báo khi có yêu cầu mới/quyết định. | P1 |

**Quy tắc**: BR-CC-001 Trạng thái chỉ tiến tới (trừ thao tác "trả lại" có lý do do người có quyền). BR-CC-002 `SHIFT_LOCKED` được đặt đồng loạt cho mọi bản ghi của ca khi chốt ca. BR-CC-003 Người đề nghị ≠ người duyệt. BR-CC-004 Thay đổi danh mục chuẩn (masterdata) sau vận hành chính thức phải qua luồng duyệt thay đổi (§4.5) và có ngày hiệu lực.

### 4.3 CC-04 Nhật ký kiểm toán (Audit)
- FR-CC-020 (P0): Hibernate/JPA `@EntityListeners` (hoặc Hibernate `Interceptor`/Envers tùy chọn) ghi tự động `AuditLog` cho mọi entity thừa kế `Auditable` (bao gồm masterdata). Ghi diff theo trường; trường nhạy cảm (hash) bị che.
- FR-CC-021 (P0): Ghi riêng sự kiện nghiệp vụ: đăng nhập/đăng xuất, xuất báo cáo, đổi quyền, duyệt/từ chối, đổi trạng thái, chốt/mở ca.
- FR-CC-022 (P1): Màn hình tra cứu audit (lọc theo phân hệ, đối tượng, người, thời gian), xuất Excel; xem "lịch sử thay đổi" ngay trên form của bản ghi (tab/ngăn kéo).
- NFR: audit lưu ≥ 3 năm online; phân vùng theo tháng; chỉ `INSERT` cho tài khoản DB của ứng dụng.

### 4.4 CC-05 Luồng phê duyệt & xác nhận điện tử
- FR-CC-030 (P0): Engine phê duyệt tuần tự/song song cấu hình bằng JSON `Steps: [{no, roleCode|userId, mode: ANY|ALL, slaMinutes, onTimeout: ESCALATE|NOTIFY}]`; hỗ trợ **trả lại**, **ủy quyền tạm thời** (có thời hạn), lịch sử đầy đủ.
- FR-CC-031 (P0): **Xác nhận điện tử** (`sys.Signature`): người dùng nhập lại mật khẩu/PIN → ghi chữ ký kèm hash bản ghi; nếu bản ghi đổi sau khi ký → chữ ký vô hiệu và yêu cầu ký lại. Dùng cho: duyệt kế hoạch, xác nhận phiếu cấp phát, chốt ca, biên bản đánh giá/nghiệm thu TDT (đa bên).
- Luồng cấu hình sẵn (seed): `PL_WEEKLY_APPROVAL`, `PL_ORDER_CONFIRM`, `MD_NORM_CHANGE`, `PM_MONTHLY_PLAN`, `PM_REPAIR_REQUEST` (Tổ trưởng MMTB → Kho), `SP_ISSUE_UNPLANNED`, `OH_YEAR_PLAN` (KTCĐ lập → BGĐ), `OH_PARTS_PLAN`, `OH_EXTRA_PART` (KTCĐ), `OH_ACCEPTANCE` (đa bên), `QC_NC_DISPOSITION`, `SHIFT_CLOSE` (đa bên), `CC_ADJUSTMENT`.

### 4.5 CC-12 Cấu hình có phiên bản & ngày hiệu lực
Áp dụng cho: định mức NVL, ngưỡng cảnh báo, chu kỳ & checklist bảo trì, mức tồn kho tối thiểu, ghép cặp vấn–bao, mẫu biểu mẫu KSCL, công thức OEE.
- Mẫu bảng: `{Entity}Version(Id, ParentId, VersionNo, EffectiveFrom, EffectiveTo?, Status(DRAFT/PENDING/ACTIVE/RETIRED), Payload…)`. Chỉ **một** phiên bản `ACTIVE` tại một thời điểm cho cùng khóa nghiệp vụ (MySQL không có filtered index → dùng *generated column* `active_key = IF(Status='ACTIVE', <khóa nghiệp vụ>, NULL)` + `UNIQUE INDEX` trên cột đó (nhiều NULL được phép) + kiểm tra chồng chéo thời gian ở application).
- BR-CC-010: Giao dịch lưu **tham chiếu phiên bản đã áp dụng lúc phát sinh** (vd `NormVersionId` trên phiếu cấp phát) để tra cứu/đối chiếu không đổi theo thời gian.
- FR-CC-040 (P0): UI so sánh 2 phiên bản (diff), xem lịch sử, nhân bản phiên bản, đặt ngày hiệu lực tương lai.

### 4.6 CC-06 Động cơ cảnh báo (Alert engine) & thông báo
**Kiến trúc**: nghiệp vụ phát *domain event* (qua outbox → RabbitMQ exchange `pxvb.events`) → `AlertEvaluator` (consumer theo module) đọc `sys.AlertRule` + `md.Threshold` → tạo/cập nhật `sys.Alert` (khử trùng theo `DedupKey` trong `DedupWindowMin`) → `Notifier` đẩy qua WebSocket/STOMP (web/tablet) + in-app list + email tùy chọn. **Thời gian từ lúc phát hiện đến lúc người nhận thấy thông báo ≤ 5 phút** (mục tiêu thực tế < 30 giây với sự kiện; các rule dạng "quét định kỳ" chạy mỗi 1 phút).

| ID | Yêu cầu | ƯT |
|---|---|---|
| FR-CC-050 | Rule cấu hình được (bật/tắt, ngưỡng, người nhận, leo thang); thay đổi ghi lịch sử + ngày hiệu lực. | P0 |
| FR-CC-051 | Vòng đời cảnh báo: `OPEN → ACK → IN_PROGRESS → RESOLVED → CLOSED`; người xử lý bắt buộc chọn **nguyên nhân** (danh mục) + ghi chú khi `RESOLVED`. Lưu lịch sử trạng thái xử lý (yêu cầu của nhiều phân hệ). | P0 |
| FR-CC-052 | **Leo thang**: nếu chưa `ACK` sau N phút → gửi cấp trên (cấu hình theo rule). | P0 |
| FR-CC-053 | Tablet nhận cảnh báo qua kết nối WebSocket (STOMP); khi offline → hiện khi online lại; hiển thị banner màu theo mức (xanh/vàng/đỏ) + rung/âm báo cấu hình được. | P0 |
| FR-CC-054 | Trung tâm cảnh báo trên web: lọc theo phân hệ/mức/trạng thái/máy/ca; xuất Excel; thống kê tần suất cảnh báo. | P1 |
| FR-CC-055 | Chế độ "tạm lặng" (snooze) có thời hạn cho cảnh báo mức INFO/WARN; CRITICAL không được snooze. | P2 |

**Danh mục cảnh báo (seed `sys.AlertRule`)**

| Mã | Điều kiện kích hoạt | Mức | Người nhận (leo thang) | Phân hệ |
|---|---|---|---|---|
| `PR_OUTPUT_BEHIND` | Sản lượng tích lũy máy/ca < kế hoạch-đến-thời-điểm × (1 − ngưỡng), sau ≥ 60' kể từ giờ vào ca | WARN | Trưởng ca → Phó QĐ | PR |
| `PR_OEE_LOW` | OEE-tạm-tính ca của máy < ngưỡng | WARN | Trưởng ca, Phó QĐ | PR |
| `PR_STOP_LONG` | Sự kiện dừng máy đang mở > ngưỡng phút (theo mã lý do) | WARN→CRIT | Trưởng ca → Phó QĐ | PR |
| `PR_BUFFER_HIGH/LOW` | Tồn điếu máy bao > max hoặc < min | WARN | Trưởng ca | PR |
| `MT_ISSUE_OVER_SLIP` | Cấp thực tế vượt phiếu > ngưỡng % | WARN | STAT_MATERIAL, Trưởng ca | MT |
| `MT_WASTE_HIGH` | Hao hụt NVL (máy hoặc tổng ca) > ngưỡng theo nhóm vật tư | WARN→CRIT | Trưởng ca, Phó QĐ (≤ 5') | MT |
| `MT_SLOT_LATE` | Quá khung giờ cấp phát + dung sai mà chưa có xác nhận nhận | WARN | STAT_MATERIAL, Trưởng ca | MT |
| `QC_DEFECT_C/B/A` | Lỗi vượt ngưỡng cấp C/B/A | INFO/WARN/CRIT | C: Operator · B: Trưởng ca (+Giám sát) · A: Phó QĐ + ĐBCL | QC |
| `QC_CHECK_LATE` | Quá giờ kiểm tra inline > 10' | WARN | Operator → Giám sát | QC |
| `QC_CHECK_MISSED` | Bỏ lỡ lượt kiểm tra (> 30' sau hạn) | WARN | Giám sát, Trưởng ca | QC |
| `QC_PHYSICAL_OOS` | Chỉ tiêu vật lý ngoài tiêu chuẩn sản phẩm | WARN→CRIT | ĐBCL, Trưởng ca | QC |
| `TX_WASTE_HIGH` | Hao hụt tem (hư+mất)/1.000 bao > ngưỡng **riêng từng máy bao** | WARN | Trưởng ca, STAT_STAMP | TX |
| `TX_MISMATCH` | |Tem dùng − bao thành phẩm| > dung sai | CRIT | Trưởng ca, STAT_STAMP, Phó QĐ | TX |
| `TX_BLOCK_LOW` | Block tem đang dùng còn < X tem | INFO | STAT_STAMP | TX |
| `PM_DUE_SOON/OVERDUE` | Công việc bảo trì đến hạn trong N ngày / quá hạn | INFO/WARN | MNT_LEAD, MNT_TECH | PM |
| `PM_WEEK_AT_RISK` | Khối lượng bảo trì tuần có nguy cơ không hoàn thành | WARN | MNT_LEAD | PM |
| `PM_REPAIR_PENDING` | Yêu cầu sửa chữa chờ duyệt > 15' | WARN | MNT_LEAD → Phó QĐ | PM |
| `PM_MTTR_MTBF_BREACH` | MTTR/MTBF máy vượt ngưỡng | WARN | MNT_TECH | PM |
| `OH_DELAYED` | Hồ sơ TDT trễ mốc kế hoạch | WARN | KTCĐ | OH |
| `OH_YEAR_AT_RISK` | Tiến độ tích lũy có nguy cơ không đạt KH năm | WARN | KTCĐ, BGĐ | OH |
| `OH_PART_REPEAT` | Cùng phụ tùng thay > N lần/máy trong T tháng | INFO | KTCĐ, MNT_TECH | OH/PM |
| `OH_EXTRA_PART_PENDING` | Yêu cầu phụ tùng ngoài dự kiến chờ duyệt > SLA | WARN | KTCĐ | OH |
| `SP_STOCK_BELOW_MIN` | Tồn < mức tối thiểu | WARN | WAREHOUSE_KEEPER, KTCĐ | SP |
| `CC_DEVICE_UNSYNCED` | Thiết bị còn bản ghi chưa đồng bộ > 30' | WARN | Trưởng ca, CNTT | CC |
| `CC_SYNC_CONFLICT` | Phát sinh xung đột đồng bộ | WARN | Trưởng ca, CNTT | CC |
| `CC_SHIFT_NOT_CLOSED` | Quá 30' sau giờ hết ca chưa chốt | WARN | Trưởng ca → Phó QĐ | CC |

### 4.7 CC-07 Ngoại tuyến & đồng bộ tablet (nguồn 2.12.7 & 7.5)
**Nguyên tắc**: tablet là *offline-first* cho mọi màn hình nhập liệu; mọi lệnh ghi đi qua **hàng đợi lệnh cục bộ (outbox)**.

| ID | Yêu cầu | ƯT |
|---|---|---|
| FR-CC-060 | Mỗi thao tác ghi tạo `Command{ clientCommandId(UUIDv7), type, entityId(UUIDv7 do client sinh), payload, occurredAt, enteredAt, userId, deviceId, baseVersion, shiftSessionId, machineId }` lưu SQLite (có WAL) trước khi gọi API; UI hiển thị ngay (optimistic) kèm biểu tượng "chưa đồng bộ". | P0 |
| FR-CC-061 | `POST /api/v1/sync/push` nhận **lô lệnh theo thứ tự thời gian** (≤ 100/lô); server xử lý tuần tự từng lệnh theo thiết bị, **idempotent** theo `(deviceId, clientCommandId)`, trả kết quả từng lệnh: `APPLIED / DUPLICATE / REJECTED(reason) / CONFLICT(conflictId)`. | P0 |
| FR-CC-062 | `GET /api/v1/sync/pull?since=<token>&scope=...` trả **delta** masterdata (máy, SP, NVL, định mức, mã dừng, mẫu KSCL, người dùng/PIN-hash, ngưỡng) + dữ liệu ca đang chạy của các máy gắn với thiết bị. Lần đầu: tải đầy đủ có phân trang. | P0 |
| FR-CC-063 | **Xung đột**: khi `baseVersion` ≠ phiên bản server (vd bản ghi đã `CONFIRMED`/đã bị sửa) hoặc quy tắc nghiệp vụ không thỏa (ca đã khóa) → **không ghi đè**; lưu `SyncConflict` vào danh sách "cần xử lý" cho người có thẩm quyền (Trưởng ca/CNTT) đối chiếu: *chấp nhận bản tablet / giữ bản server / hủy*. | P0 |
| FR-CC-064 | Dữ liệu offline gắn: mã thiết bị, người nhập, thời điểm nhập, phiên ca, máy, `SyncStatus ∈ {PENDING, SYNCING, SYNCED, CONFLICT, REJECTED}`. Đồng bộ **theo thứ tự thời gian**, kiểm tra trùng. | P0 |
| FR-CC-065 | Mất điện/tắt đột ngột: ghi SQLite theo giao dịch; khi mở lại phục hồi hàng đợi và phiên nhập đang dở (form nháp tự lưu mỗi thay đổi trường). | P0 |
| FR-CC-066 | Màn hình web "Giám sát thiết bị & đồng bộ": liệt kê thiết bị, lần đồng bộ cuối, số bản ghi chưa đồng bộ, xung đột; cho Trưởng ca & CNTT theo dõi. | P1 |
| FR-CC-067 | Đồng hồ: tablet gửi `deviceClockOffset` mỗi lần sync; server cảnh báo nếu lệch > 2'; thời điểm tin cậy là `OccurredAt` (thiết bị) đã hiệu chỉnh offset + `ReceivedAt` (server). | P1 |
| FR-CC-068 | Ngưỡng dung lượng offline: tối thiểu lưu được 72 giờ dữ liệu thao tác (≈ 3 ca); cảnh báo khi hàng đợi > 500 lệnh. | P1 |
| FR-CC-069 | Các thao tác **bắt buộc online**: đăng nhập lần đầu của thiết bị, duyệt/chốt ca, duyệt điều chỉnh, phát hành phiếu cấp phát mới (nhận qua pull). | P0 |

Ví dụ lệnh: `PR.RecordOutput`, `PR.StartStop`, `PR.EndStop`, `PR.RecordProductChange`, `MT.ConfirmReceipt`, `MT.RecordIssueActual`, `QC.SubmitInspection`, `TX.RecordStampWaste`, `PM.SubmitTaskResult`, `PM.CreateRepairRequest`… Mỗi loại có JSON schema payload (đặt trong `docs/sync-commands/*.schema.json`).

### 4.8 CC-08 Import / Export Excel
- FR-CC-070 (P0): Mọi danh mục masterdata có **template Excel chuẩn** (tải về; sheet "Hướng dẫn" + sheet dữ liệu + sheet "Danh mục tham chiếu"). Quy trình 2 bước: *Upload → Validate (báo lỗi theo dòng/cột, xuất file lỗi) → Commit (upsert theo `Code`)*. Có chế độ `VALIDATE_ONLY`. Commit trong 1 transaction theo lô (≤ 5.000 dòng/lô).
- FR-CC-071 (P0): Export danh sách/báo cáo ra Excel (Apache POI) giữ định dạng, tiếng Việt UTF-8, mọi cột có tiêu đề rõ; export dữ liệu lớn chạy nền (job) và thông báo khi xong.
- Danh sách template tối thiểu: `machines`, `machine-components`, `machine-pairings`, `products`, `materials`, `bom-norms`, `stop-reasons`, `defect-catalog`, `stamp-types`, `maintenance-tasks`, `spare-parts`, `machine-part-links`, `employees/users`, `thresholds`.

### 4.9 CC-10 Công việc nền (Quartz)
| Job | Lịch | Mô tả |
|---|---|---|
| `ShiftOpenReminder` | trước giờ vào ca 15' | Nhắc Trưởng ca xác nhận lệnh SX/mở ca |
| `QcScheduleGenerator` | khi mở ca + mỗi phút | Sinh lượt kiểm tra 30' kế tiếp, đánh dấu trễ, leo thang |
| `OutputReminder` | theo chu kỳ ghi sản lượng | Nhắc nhập sản lượng; đánh dấu thiếu |
| `AlertScanner` | 1 phút | Rule quét định kỳ (dừng máy kéo dài, slot trễ, sync tồn đọng…) |
| `OeeProjection` | 1–5 phút | Tính lại OEE tạm tính + snapshot dashboard |
| `ShiftCloseWatcher` | 5 phút | Cảnh báo ca chưa chốt |
| `PmDayBeforePrep` | 15:00 hằng ngày (cấu hình) | D-1: tổng hợp vật tư bảo trì ngày mai → tạo yêu cầu xuất kho |
| `PmOverdueScanner` | hằng ngày 06:00 | Đánh dấu OVERDUE, cảnh báo |
| `OhProgressScanner` | hằng ngày | Trễ mốc TDT, rủi ro KH năm |
| `MinStockScanner` | 1 giờ | Cảnh báo tồn < min |
| `DailyReport` | sau ca C | Báo cáo ngày + gửi người nhận |
| `WeeklyReport/MonthlyReport` | cuối tuần/tháng | Tổng hợp & gửi |
| `B21MonthlyStats` | cuối tháng | Thống kê chỉ tiêu vật lý |
| `AuditPartitionMaintenance`, `IdempotencyCleanup`, `BackupVerifier` | hằng ngày | Bảo trì hệ thống |
- Quartz chạy chế độ **clustered** (chỉ 1 nút thực thi một job); job idempotent; lịch lưu trong DB; có màn hình xem trạng thái/chạy tay (SYS_ADMIN).

### 4.10 CC-11 Chốt ca & đối chiếu chéo tự động (nguồn bảng mục 7.3)
**Phiên ca** `pr.ShiftSession` (xem §8) là trục để chốt. Luồng chốt: `OPEN → CLOSING → CLOSED(=SHIFT_LOCKED) → FINALIZED`.

| ID | Yêu cầu | ƯT |
|---|---|---|
| FR-CC-080 | Khi Trưởng ca bấm "Kết thúc ca": chuyển `CLOSING`, chạy **bộ kiểm tra chốt ca** (dưới), hiển thị kết quả theo nhóm: ✅ Đạt · ⚠ Cần giải trình · ⛔ Chặn chốt. | P0 |
| FR-CC-081 | Mỗi sai lệch phân loại: **CẢNH BÁO** (cho chốt), **CẦN GIẢI TRÌNH** (cho chốt sau khi nhập giải trình hợp lệ), **KHÔNG CHO PHÉP CHỐT** (chặn đến khi sửa/giải trình+duyệt). Ngưỡng cấu hình `md.Threshold`. | P0 |
| FR-CC-082 | **Xác nhận cuối ca đa bên** cấu hình thành workflow `SHIFT_CLOSE`: Vận hành (Trưởng ca) · Thống kê (sản lượng/NVL/tem) · KSCL/ĐBCL · Cấp phát NVL · Kỹ thuật (nếu có dừng máy do kỹ thuật/phát sinh sự cố). Chỉ chốt khi các bên **bắt buộc** đã xác nhận; bên "không phát sinh" được tự bỏ qua. | P0 |
| FR-CC-083 | Chốt xong: đặt `SHIFT_LOCKED` cho mọi bản ghi của ca, tính OEE chính thức, sinh `ReportSnapshot` ca (≤ 5'), phát sự kiện `ShiftClosed` (RP, MT, TX, PR tổng hợp). Mở lại ca chỉ qua `AdjustmentRequest` cấp Phó QĐ/Quản đốc, có audit. | P0 |

**Bộ kiểm tra chốt ca (seed rule set `ShiftCloseChecks`)**

| Mã | Đối chiếu | Mức mặc định | Hành vi |
|---|---|---|---|
| `CHK_OUTPUT_PLAN` | Sản lượng thực tế (máy/ca) ↔ lệnh SX & kế hoạch | Cần giải trình khi lệch > ngưỡng | Bắt chọn nguyên nhân thiếu/vượt |
| `CHK_OUTPUT_COMPLETE` | Mọi máy có lệnh SX đều có ≥ số lần ghi sản lượng tối thiểu của ca | Chặn | |
| `CHK_MATERIAL` | Tiêu hao thực tế ↔ định mức × sản lượng đạt ↔ cấp phát/hoàn trả | Cần giải trình khi > ngưỡng hao hụt; Chặn nếu thiếu xác nhận nhận NVL của khung giờ đã qua | Giải trình: hao hụt/hủy/đổi mác/sự cố |
| `CHK_QUALITY` | Sản lượng đạt/không đạt ↔ lỗi A/B/C ↔ inline + ĐBCL; số lượt KSCL bắt buộc | **Chặn** nếu thiếu lượt kiểm tra bắt buộc (không chốt báo cáo chất lượng) | |
| `CHK_DOWNTIME` | Thời gian chạy + dừng ↔ thời gian ca; mọi dừng có mã lý do; dừng do kỹ thuật có xác nhận kỹ thuật | Chặn nếu tổng thời gian không khớp (> dung sai) hoặc dừng chưa có lý do | OEE chỉ cập nhật chính thức sau xác nhận |
| `CHK_STAMP` | Tem xuất dùng − hư − mất − tồn cuối ↔ bao/tút thành phẩm | Chặn nếu chênh > dung sai chưa kiểm kê/giải trình | Yêu cầu kiểm kê |
| `CHK_UNSYNCED` | Không còn lệnh chưa đồng bộ/xung đột mở của thiết bị thuộc ca | Chặn | Hiển thị danh sách thiết bị |
| `CHK_OPEN_ALERT` | Cảnh báo CRITICAL của ca chưa xử lý | Cần giải trình | |

### 4.11 CC-13 Bảng điều khiển quản trị hệ thống (SYS_ADMIN)
FR-CC-090 (P1): màn hình trạng thái dịch vụ (API, Worker, DB, Redis, RabbitMQ), dung lượng DB/file, kết quả backup gần nhất, jobs Quartz, phiên bản app tablet, danh sách thiết bị (online/offline), hàng đợi outbox, lỗi gần đây. FR-CC-091 (P1): cấu hình tham số hệ thống (`sys.Setting`), mẫu email, người nhận báo cáo định kỳ. FR-CC-092 (P2): xuất gói chẩn đoán (log + cấu hình, đã che bí mật).

### 4.12 CC-14 Tìm kiếm & chọn nhanh
FR-CC-095 (P1): Ô tìm kiếm nhanh toàn cục theo mã máy/mã SP/mã NVL/mã phụ tùng/thẻ kho/mã hồ sơ TDT (prefix + fuzzy nhẹ, ≤ 300 ms). Các picker trên tablet hỗ trợ **quét QR/barcode** và danh sách "gần đây/hay dùng".

---

## 5. PHÂN HỆ MD – QUẢN LÝ DỮ LIỆU CƠ SỞ (MASTERDATA)  *(báo cáo mục 2.2)*

### 5.1 Mục tiêu
Nguồn dữ liệu tham chiếu duy nhất cho sản xuất, bảo trì/CMMS, kho phụ tùng. **Một thiết bị = một mã định danh** dùng chung giữa sản xuất và bảo trì.

### 5.2 Mô hình dữ liệu (nhóm bảng `md`, vật lý `md_*`)
| Bảng | Cột chính | Ràng buộc / ghi chú |
|---|---|---|
| `md.Machine` | Code(unique), Name, MachineType(`MAKER`/`PACKER`/`FEEDER`/`OTHER`), Model (vd Protos, Max8/Mark8, Max9, Decoufle, HLP, FOCKE, AMF, GD), RodFormat(`COMPACT`/`DEMI`/`SLIM`/`SUPER_SLIM`), TheoreticalRate dec, RateUnit(`K_RODS_PER_MIN`/`PACKS_PER_MIN`), LineCode, Location, HasIpc bit, TabletCount int, Status(`ACTIVE`/`INACTIVE`/`UNDER_MAINTENANCE`/`DECOMMISSIONED`), InstallDate, ErpAssetCode | 42 vấn + 31 bao + 4 cấp đầu lọc phụ trợ (≈ 77 bản ghi); thay đổi `Status` ghi lịch sử |
| `md.MachineStatusHistory` | MachineId, FromStatus, ToStatus, At, By, Reason | |
| `md.MachineComponent` | MachineId, ParentId (cây), Code, Name, Discipline(`MECHANICAL`/`ELECTRICAL`/`OTHER`), Kind(`ASSEMBLY`/`MOTOR`/`SENSOR`/`CABINET`/`FEEDER`/`PACKING_UNIT`/`DRIVE`…), SortOrder | Cây cụm/bộ phận; dùng cho bảo trì/TDT/phân tích hư hỏng lặp |
| `md.MachinePairing` | Id, Code, EffectiveFrom, EffectiveTo, Status | Bộ cấu hình ghép cặp (phiên bản) |
| `md.MachinePairingLine` | PairingId, ShiftCode(`A`/`B`/`C`/`ALL`), MakerMachineId, PackerMachineId, FeedRatio dec(default 1) | Máy vấn cấp điếu cho 1..n máy bao; ràng buộc BR-MD-002 |
| `md.Product` | Code, Name, ProductType(`DOMESTIC`/`EXPORT`/`PROCESSING`), RodFormat, RodsPerPack, PacksPerCarton, CartonsPerCase, RequiresExpiryInkjet bit, StampTypeId?, StampsPerPack tinyint(default 1), Status(`ACTIVE`/`DISCONTINUED`) | ≥ 200 mã đồng thời; ~47 mã/tháng sản xuất |
| `md.ProductMachineAllowed` | ProductId, MachineId | Chủng loại được phép chạy trên máy (cấu hình/kiểm tra khi lập lệnh) |
| `md.ProductPhysicalSpec` | ProductId, Indicator(`WEIGHT`/`PRESSURE_DROP`/`CIRCUMFERENCE`/`MOISTURE`/`HARDNESS`/`DRAW_POINT`), Category(`DOMESTIC`/`EXPORT`/`PROCESSING`), Target, Min, Max, Uom, EffectiveFrom, EffectiveTo | Tiêu chuẩn cơ sở cho QC (B21) |
| `md.MaterialCategory` | Code(`FIBER` nguyên liệu/sợi · `ROLLING` vật tư vấn · `PACKING` vật tư bao), Name | Tương ứng 3 loại phiếu cấp phát |
| `md.Material` | Code, Name, Uom, CategoryCode, GroupCode (nhóm vật tư dùng cho ngưỡng hao hụt), ErpCode, Status | ≥ 2.500 mã; import Excel |
| `md.BomNorm` | Id, ProductId, VersionNo, EffectiveFrom, EffectiveTo, Status(`DRAFT`/`PENDING`/`ACTIVE`/`RETIRED`), ApprovedBy, ApprovedAt, ChangeReason | Phiên bản định mức (CC-12) |
| `md.BomNormLine` | NormId, MaterialId, QtyPer1000Packs dec(18,6), Uom, ConsumerType?(`MAKER`/`PACKER`; null = suy ra từ nhóm NVL) | unique(NormId, MaterialId); hàng trăm dòng/SP |
| `md.ShiftDef` | Code(`A`,`B`,`C`), Name, StartTime, EndTime, CrossesMidnight, Seq | ⚠ Giờ ca: báo cáo chỉ cho biết ca bảo trì bắt đầu 07:00 → seed gợi ý A 07:00–15:00, B 15:00–23:00, C 23:00–07:00 (**cấu hình được**) |
| `md.IssueSlotDef` | ShiftCode, SlotNo(1..4), Label(`SANG`/`TRUA`/`CHIEU`/`DEM`), StartTime, EndTime, SharePct | 4 khung giờ cấp phát/ca; `SharePct` tổng = 100 để chia phiếu |
| `md.Employee` | Code, FullName, BadgeNo, OrgUnitId, JobTitle, TradeSkill(`MECHANICAL`/`ELECTRICAL`/…), UserId?, IsActive | Dùng phân công ca/bảo trì; không phải ai cũng có tài khoản |
| `md.StopReason` | Code(`A`…`I`), Name, Category(`PLANNED`/`UNPLANNED`/`TECHNICAL`/`MATERIAL`/`OTHER`), CountsAsPlannedDowntime bit, RequiresTechConfirm bit, CanCreateRepairRequest bit, SortOrder, IsActive | Seed 9 mã (xem 5.5); mở rộng được |
| `md.ReasonCode` | Group(`REPAIR_CAUSE`/`FAILURE_CAUSE`/`SPARE_ISSUE`/`STAMP_WASTE`/`MATERIAL_VARIANCE`/`ALERT_CAUSE`/`ORDER_ADJUST`/`PLAN_VARIANCE`/`NC_DISPOSITION`/`PART_DISPOSITION`), Code, Name, IsActive | Danh mục lý do dùng chung |
| `md.DefectDef` | Code, Name, AppliesTo(`ROD`/`PACK`/`CARTON`/`MATERIAL`/`PACKAGING`), FormGroup, SortOrder | 13 lỗi điếu vấn; 15–20 lỗi bao thuốc (⚠ danh sách cụ thể do ĐBCL cung cấp qua import) |
| `md.DefectGradeRule` | DefectCode?, AppliesTo, Grade(`A`/`B`/`C`), Metric(`COUNT_IN_SAMPLE`/`RATE_PCT`), Threshold dec, SampleSize?, EffectiveFrom, EffectiveTo | Ngưỡng chuyển cấp theo TLSG-QT-ĐBCL-01 (phiên bản 10) – dữ liệu do ĐBCL cung cấp |
| `md.QcFormTemplate` | Code(vd `B06.1`), FormGroup(`B06`,`B08`,`B09`,`B11`,`B12`,`B14`,`B15`,`B21`,…), Name, Category(`INLINE`/`QA`), AppliesToMachineType(`MAKER`/`PACKER`/`ANY`), FrequencyMin?, Schema(json), VersionNo, Status, EffectiveFrom | 46 biểu mẫu; dựng form động từ `Schema` (xem §9.4) |
| `md.StampType` | Code, Name, Notes | Loại tem thuế điện tử (từ 01/07/2022) |
| `md.Threshold` | Id, MetricCode, ScopeType(`GLOBAL`/`MODEL`/`MACHINE`/`PRODUCT`/`MATERIAL_GROUP`/`STAMP_TYPE`), ScopeId?, Level?(`INFO`/`WARN`/`CRIT`/`A`/`B`/`C`), Value dec, Unit, EffectiveFrom, EffectiveTo, Status, ChangeReason | Tra cứu theo ngữ cảnh – BR-MD-008 |
| `md.MaintTaskTemplate` | Code, Name, TargetType(`MODEL`/`MACHINE_GROUP`/`MACHINE`), TargetValue, MaintType(`CLEANING`/`LUBRICATION`/`INSPECTION`/`PERIODIC`/`OTHER`), CycleValue int, CycleUnit(`DAY`/`WEEK`/`MONTH`/`OP_HOURS`), EstimatedMinutes, SkillRequired(`MECHANICAL`/`ELECTRICAL`/`ANY`), ExecutorUnit, Priority(`CRITICAL`/`NORMAL`), VersionNo, Status | Danh mục công việc bảo trì chuẩn |
| `md.MaintChecklistItem` | TemplateId, Seq, Text, ResultType(`OK_NG`/`NUMBER`/`TEXT`), Min?, Max?, Uom?, IsMandatory | |
| `md.MaintTaskPart` | TemplateId, SparePartId, QtyPerExecution dec | Vật tư dự kiến mỗi lần bảo trì |
| `md.OverhaulTypeDef` | Code(`BT` bảo trì · `TT` trung tu · `DT` đại tu), Name | |
| `md.OverhaulFormTemplate` | Code, Kind(`ASSESS_MECH`/`ASSESS_ELEC`/`ACCEPTANCE`), Schema(json), VersionNo, Status | Biểu mẫu đánh giá trước TDT (tách cơ/điện) & nghiệm thu |
| `md.Supplier` | Code, Name, Contact, IsActive | |
| `md.SparePart` | PartNo(mã hàng, unique), Name, Uom, StockCardNo (thẻ kho), GroupCode, DefaultSupplierId?, ErpCode, Status | Theo hệ thống thẻ kho hiện có TLSG |
| `md.SparePartMinStock` | SparePartId, MinQty dec, ReorderQty dec?, LeadTimeDays?, EffectiveFrom, EffectiveTo, ChangeReason | Phiên bản hóa mức tồn tối thiểu |
| `md.MachinePartLink` | MachineId?, ModelCode?, ComponentId?, TaskTemplateId?, SparePartId, TypicalQty | n-n: 1 máy/cụm ↔ nhiều phụ tùng; 1 phụ tùng ↔ nhiều máy |

### 5.3 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-MD-001 | Quản lý máy móc thiết bị | CRUD, khóa/ngừng sử dụng (không xóa nếu đã có giao dịch), tra cứu theo dây chuyền/loại/dòng/trạng thái; import/export Excel; lịch sử đổi trạng thái | P0 |
| FR-MD-002 | Cấu trúc cụm/bộ phận | Cây nhiều cấp theo máy; sao chép cấu trúc từ máy cùng dòng; kéo-thả sắp xếp | P1 |
| FR-MD-003 | Ghép cặp vấn–bao | Màn hình lưới (hàng = máy bao, cột = ca A/B/C) chọn máy vấn cấp điếu; phiên bản + hiệu lực; kiểm tra trùng/thiếu | P0 |
| FR-MD-004 | Danh mục sản phẩm | CRUD; kích hoạt/ngừng; quy cách đóng gói; loại tem; máy được phép; import | P0 |
| FR-MD-005 | Thông số vật lý sản phẩm | Chỉ tiêu Target/Min/Max theo Category (nội tiêu/xuất khẩu/gia công) | P1 |
| FR-MD-006 | Danh mục NVL | CRUD, phân loại 3 nhóm, nhóm ngưỡng, import hàng loạt, tìm nhanh (≤ 300ms với 2.500+ mã) | P0 |
| FR-MD-007 | Bảng định mức tiêu hao | Theo cặp (SP, NVL) trên 1.000 bao; **phiên bản hóa**; so sánh 2 phiên bản & 2 SP; sao chép định mức từ SP khác; import; phê duyệt Phó QĐ; xem lịch sử | P0 |
| FR-MD-008 | Cấu hình ca & khung giờ cấp phát | 3 ca + 4 khung/ca (`SharePct`), danh sách nhân sự theo ca, trưởng ca | P0 |
| FR-MD-009 | Cấu hình ngưỡng cảnh báo | Ngưỡng hao hụt NVL theo nhóm, lỗi A/B/C, hao hụt tem theo máy bao, bảo trì/TDT/tồn kho; có phiên bản, ngày hiệu lực | P0 |
| FR-MD-010 | Danh mục lý do dừng máy | Bộ A–I + mở rộng; cờ ảnh hưởng OEE & yêu cầu xác nhận kỹ thuật | P0 |
| FR-MD-011 | Danh mục lý do khác | Sửa chữa, nguyên nhân sự cố, xuất kho phụ tùng, tem hư, chênh lệch NVL, xử lý SPKPH… | P1 |
| FR-MD-012 | Danh mục lỗi & luật phân cấp A/B/C | Cấu hình lỗi theo biểu mẫu & ngưỡng chuyển cấp; import | P0 |
| FR-MD-013 | Biểu mẫu KSCL/ĐBCL | Quản lý `QcFormTemplate` (46 mẫu) – xem/nhân bản/sửa schema có phiên bản; xem trước form | P0 |
| FR-MD-014 | Loại tem thuế | CRUD | P1 |
| FR-MD-015 | Danh mục công việc bảo trì chuẩn | Theo dòng máy/nhóm/máy; chu kỳ, thời lượng, kỹ năng, checklist, vật tư; import | P0 |
| FR-MD-016 | Danh mục phục vụ TDT | Hình thức BT/TT/ĐT, biểu mẫu đánh giá (cơ/điện) & nghiệm thu, trạng thái hồ sơ | P1 |
| FR-MD-017 | Danh mục phụ tùng & thẻ kho | CRUD, tra cứu mã/tên/thẻ kho, import khởi tạo, đối chiếu ERP (file) | P0 |
| FR-MD-018 | Mức tồn tối thiểu | Cấu hình theo phụ tùng, phiên bản; gợi ý từ lịch sử dùng (RP) | P1 |
| FR-MD-019 | Liên kết thiết bị–phụ tùng | Ma trận n-n; nhập từ Excel; hiển thị 2 chiều (máy→phụ tùng, phụ tùng→máy) | P1 |
| FR-MD-020 | Người dùng, đơn vị, vai trò, phân quyền | Xem CC-01/02; thêm màn hình nhân sự `md.Employee` và liên kết tài khoản | P0 |
| FR-MD-021 | Import/Export & kiểm soát chất lượng dữ liệu | Template chuẩn, validate theo dòng, báo cáo "dữ liệu thiếu/sai" (SP thiếu định mức, máy thiếu công suất, NVL thiếu nhóm…) để chuẩn hóa trước triển khai (mục 7.4) | P0 |
| FR-MD-022 | Danh mục kiểm soát | Mỗi danh mục có người phụ trách, ngày cập nhật, phiên bản, trạng thái; thay đổi sau vận hành chính thức qua workflow `MD_NORM_CHANGE`/duyệt thay đổi | P1 |

### 5.4 Quy tắc nghiệp vụ
- **BR-MD-001** Mã máy duy nhất toàn hệ thống; PR, PM, OH, SP đều tham chiếu `md.Machine.Id`.
- **BR-MD-002** Ghép cặp: tại một thời điểm & ca, **một máy bao có đúng 1 máy vấn cấp điếu**; một máy vấn có thể cấp cho nhiều máy bao; không cho lưu nếu máy bao bị gán 2 máy vấn, hoặc máy vấn/bao có `Status` ≠ `ACTIVE` (cảnh báo, không chặn).
- **BR-MD-003** Không xóa vật lý bản ghi đã được tham chiếu; chỉ `IsActive=false`/`Status=INACTIVE` (kiểm tra tham chiếu trước khi khóa → hiển thị nơi đang dùng).
- **BR-MD-004** Định mức: `QtyPer1000Packs > 0`; kích hoạt phiên bản mới → phiên bản đang `ACTIVE` tự `RETIRED` vào `EffectiveFrom` của bản mới; không cho `EffectiveFrom` trong quá khứ (trừ SYS_ADMIN + lý do); lệnh SX tham chiếu phiên bản tại thời điểm xác nhận.
- **BR-MD-005** Mã dừng máy `A`–`I` do hệ thống seed, không xóa; được đổi tên/ngừng dùng có kiểm soát; mã mới đặt theo `J`, `K`… hoặc số.
- **BR-MD-006** Công suất lý thuyết bắt buộc > 0 với máy `MAKER`/`PACKER` (đầu vào OEE); thiếu → màn hình chất lượng dữ liệu báo đỏ và chặn bật máy vào lệnh SX.
- **BR-MD-007** Quy mô tối thiểu: 200 SP hoạt động; 2.500+ NVL; hàng trăm NVL/SP; 77 máy; phụ tùng ≥ 10.000 mã. Danh sách phân trang server-side, index theo `Code`, `Name` (full-text/`LIKE` prefix).
- **BR-MD-008** Tra cứu ngưỡng `Resolve(metric, context, asOf)`: chọn bản ghi hiệu lực tại `asOf`, ưu tiên phạm vi hẹp nhất (`MACHINE` > `PRODUCT` > `MODEL`/`MATERIAL_GROUP`/`STAMP_TYPE` > `GLOBAL`); cache Redis 60s, invalidate khi thay đổi.
- **BR-MD-009** Mọi thay đổi masterdata ghi audit; các nhóm (định mức, ngưỡng, chu kỳ bảo trì, checklist, tồn tối thiểu, ghép cặp, mẫu KSCL, công thức OEE) phải có phiên bản & ngày hiệu lực (CC-12).

### 5.5 Dữ liệu khởi tạo (seed)
- **Mã dừng máy** (⚠ thứ tự chữ cái–lý do theo thứ tự liệt kê trong báo cáo; cần xác nhận với PXVB): `A` Đổi mác thuốc · `B` Vệ sinh bảo trì · `C` Máy hỏng · `D` Không ổn định · `E` Giờ ăn · `F` Thiếu điếu/bao · `G` Không có yêu cầu sản xuất · `H` Thiếu NVL · `I` Lý do khác. Cờ mặc định: `E`,`G` → `CountsAsPlannedDowntime=true` (⚠ xác nhận); `C`,`D` → `RequiresTechConfirm=true`, `CanCreateRepairRequest=true`.
- **Ca**: A 07:00–15:00 · B 15:00–23:00 · C 23:00–07:00 (⚠). **Khung cấp phát**: SANG/TRUA/CHIEU/DEM mỗi ca, `SharePct` 25 (⚠).
- **Vai trò** (§1.4) + quyền; **OrgUnit**; **MaterialCategory** (3); **OverhaulTypeDef** (BT/TT/ĐT); **ReasonCode** nhóm `STAMP_WASTE` (JAM tem kẹt máy, MISALIGNED dán lệch, TORN rách, PRINT_ERROR lỗi in, OTHER).
- **Ngưỡng mặc định** (⚠ *giá trị khởi tạo để chạy thử, nghiệp vụ TLSG xác nhận/điều chỉnh*): `PR_OUTPUT_BEHIND_PCT=15`, `PR_STOP_LONG_MIN=30`, `PR_OEE_LOW_PCT=60`, `PL_WEEK_VS_MONTH_PCT=10`, `MT_ISSUE_OVER_SLIP_PCT=10`, `MT_SLOT_TOLERANCE_MIN=30`, `MT_WASTE_PCT[group]=3`, `QC_CHECK_INTERVAL_MIN=30`, `QC_LATE_ESCALATE_MIN=10`, `QC_GRADE_A_SLA_MIN=30`, `TX_WASTE_PER_1000[machine]` (từ số liệu thực tế: máy thấp nhất ~0,11 tem/1.000 bao, cao nhất ~19 tem/1.000 bao → nhập theo từng máy bằng import), `TX_MISMATCH_TOL=0`, `PM_DUE_SOON_DAYS=3`, `PM_REPAIR_APPROVAL_SLA_MIN=15`, `OH_REPEAT_COUNT=3`, `OH_REPEAT_MONTHS=12`, `SYNC_STALE_MIN=30`, `OUTPUT_INTERVAL_MIN=120`.

### 5.6 API (prefix `/api/v1/md`)
Mỗi tài nguyên dưới đây có bộ chuẩn: `GET /{res}` (phân trang, lọc, sắp xếp), `GET /{res}/{id}`, `POST /{res}`, `PUT /{res}/{id}` (If-Match), `POST /{res}/{id}/deactivate|activate`, `POST /{res}/import` (multipart; `?mode=validate|commit`), `GET /{res}/export`, `GET /import-templates/{templateCode}`.
Tài nguyên: `machines`, `machines/{id}/components`, `machine-pairings`, `products`, `products/{id}/physical-specs`, `products/{id}/allowed-machines`, `material-categories`, `materials`, `bom-norms`, `shift-defs`, `issue-slots`, `employees`, `stop-reasons`, `reason-codes`, `defects`, `defect-grade-rules`, `qc-form-templates`, `stamp-types`, `thresholds`, `maint-task-templates`, `overhaul-forms`, `suppliers`, `spare-parts`, `spare-part-min-stock`, `machine-part-links`.
Endpoint đặc thù:
| Method & Path | Mục đích |
|---|---|
| `GET /machine-pairings/resolve?date=&shift=` | Trả cặp vấn–bao hiệu lực (cho PL/PR) |
| `GET /bom-norms/active?productId=&asOf=` | Định mức hiệu lực |
| `POST /bom-norms/{id}/submit` · `/approve` · `/reject` · `/clone` | Vòng đời phiên bản định mức |
| `GET /bom-norms/compare?a=&b=` | So sánh 2 phiên bản/2 SP |
| `GET /thresholds/resolve?metric=&machineId=&productId=&materialGroup=&asOf=` | Tra cứu ngưỡng áp dụng |
| `GET /data-quality/report` | Báo cáo thiếu/sai dữ liệu trước go-live |
| `GET /sync/masterdata?since=` | (alias của CC sync pull) |

### 5.7 Màn hình web
Danh sách + form chi tiết cho từng danh mục (bảng dữ liệu có lọc cột, chọn cột hiển thị, xuất Excel); Lưới ghép cặp vấn–bao; Trình soạn định mức (bảng có tìm NVL, dán từ Excel, so sánh); Trình thiết kế biểu mẫu KSCL (xem §9.4); Ma trận ngưỡng; Màn hình "Chất lượng dữ liệu"; Quản lý người dùng & ma trận phân quyền.

### 5.8 Phân quyền (C=tạo, U=sửa, A=duyệt, V=xem)
| Danh mục | SYS_ADMIN | DEPUTY_DIRECTOR | PLANNER | NORM_CLERK | MNT_TECH | KTCD | WAREHOUSE_KEEPER | Khác |
|---|---|---|---|---|---|---|---|---|
| Máy/cụm/ghép cặp | C U | A | U (điều chỉnh dây chuyền) | V | V (cụm) | V | V | V |
| Sản phẩm | C U | A | C U | V | – | – | – | V |
| NVL & định mức | C U | A | V | C U | – | – | – | V |
| Ca, khung giờ, ngưỡng, mã dừng | C U | A | V | – | – | – | – | V |
| Mẫu KSCL/lỗi/luật A-B-C | C U | V | – | – | – | – | – | `QA_HEAD` C U A |
| Công việc bảo trì, phụ tùng, liên kết, tồn min | C U | V | – | – | C U | A | C U (phụ tùng) | V |
| Người dùng/phân quyền | C U | – | – | – | – | – | – | – |

### 5.9 Tiêu chí nghiệm thu
- **AC-MD-001** *Given* máy bao M-B05 đã được ghép với máy vấn M-V02 ca A, *When* gán M-V07 cho cùng máy bao cùng ca trong cùng hiệu lực, *Then* hệ thống từ chối với mã `PXVB-MD-1002` và hiển thị cặp hiện có.
- **AC-MD-002** Import 2.500 NVL từ Excel có 30 dòng lỗi → trả file lỗi chỉ rõ dòng/cột; không dòng nào được ghi cho đến khi người dùng chọn Commit các dòng hợp lệ (hoặc sửa file).
- **AC-MD-003** Tạo phiên bản định mức mới hiệu lực ngày mai; lệnh SX tạo hôm nay vẫn tham chiếu phiên bản cũ; lệnh tạo từ ngày mai tham chiếu phiên bản mới; so sánh hiển thị chênh lệch từng NVL.
- **AC-MD-004** Khóa một SP đang có lệnh SX mở → bị chặn kèm danh sách lệnh; khóa SP không còn lệnh mở → thành công và SP biến mất khỏi picker tạo lệnh mới nhưng vẫn xuất hiện trong báo cáo lịch sử.
- **AC-MD-005** `thresholds/resolve` cho máy có ngưỡng riêng trả ngưỡng máy; máy không có ngưỡng riêng trả ngưỡng `GLOBAL`; thay đổi có hiệu lực kể từ `EffectiveFrom`.

---

## 6. PHÂN HỆ PL – KẾ HOẠCH SẢN XUẤT & LỆNH SẢN XUẤT  *(báo cáo mục 2.3)*

### 6.1 Mô hình dữ liệu (nhóm bảng `pl`, vật lý `pl_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `pl.MonthlyPlan` | Id, Code, Year, Month, Revision int, Status(`DRAFT`/`ACTIVE`/`SUPERSEDED`), SourceNote (KHVT cấp), ApprovedBy | Mỗi lần điều chỉnh tạo `Revision+1` (giữ lịch sử); chỉ 1 `ACTIVE` mỗi tháng |
| `pl.MonthlyPlanItem` | PlanId, ProductId, PlannedPacks bigint (hoặc K-bao), Note | ≥ 50 mã/tháng |
| `pl.WeeklyPlan` | Id, Code, WeekStartDate, Revision, Status(`DRAFT`/`SUBMITTED`/`APPROVED`/`SUPERSEDED`), MonthlyPlanId, SubmittedBy, ApprovedBy | Điều chỉnh ≥ 3 lần/tuần |
| `pl.WeeklyPlanItem` | WeeklyPlanId, ProductId, PlanDate, PlannedPacks | Phân bổ theo ngày × SP |
| `pl.ProductionOrder` | Id, Code(`LSX-yyyymmdd-<ca>-nnn`), BusinessDate, ShiftCode, MakerMachineId, PackerMachineId, ProductId, PlannedPacks, PlannedStart, PlannedEnd, NormVersionId, WeeklyPlanItemId?, Status(`SUGGESTED`/`CONFIRMED`/`RUNNING`/`COMPLETED`/`CANCELLED`), ConfirmedBy, ConfirmedAt, Source(`AUTO`/`MANUAL`), Revision | 1 lệnh = 1 máy bao (+ máy vấn cấp điếu) × 1 SP × 1 ca. Một máy có thể có nhiều lệnh trong ca (đổi mác) – xem `OrderSegment` |
| `pl.OrderSegment` | OrderId, SegmentNo, ProductId, PlannedPacks, StartAt?, EndAt?, Reason | Phân đoạn khi đổi mác/chạy phụ trong ca |
| `pl.OrderAdjustment` | Id, OrderId, Type(`PRODUCT_CHANGE`/`FIBER_OUT`/`BREAKDOWN`/`SIDE_RUN`/`QTY_CHANGE`/`OTHER`), ReasonCode, Note, Before(json), After(json), At, By, PropagatedTo(json) | Ghi lý do & lan truyền sang các ca còn lại |
| `pl.ShiftRoster` | Id, BusinessDate, ShiftCode, LeaderEmployeeId, Status(`DRAFT`/`PUBLISHED`), CopiedFromId? | |
| `pl.ShiftRosterAssignment` | RosterId, MachineId, Position(`OPERATOR_1`/`OPERATOR_2`/`MECHANIC`/`SUPERVISOR`/…), EmployeeId | |

### 6.2 Chức năng
| ID | Chức năng | Mô tả chi tiết | ƯT |
|---|---|---|---|
| FR-PL-001 | KHSX tháng | PLANNER nhập theo SP (nhập tay/import Excel); điều chỉnh nhiều lần (sinh `Revision`); hiển thị: đã phân bổ xuống tuần, thực tế tích lũy (từ PR), còn lại | P0 |
| FR-PL-002 | KHSX tuần | Lưới SP × 7 ngày; hiển thị song song: còn lại của tháng, **lịch bảo trì máy** (từ PM) và **tồn NVL** (từ MT/ERP-file) để Phó QĐ cân nhắc; cảnh báo khi tổng tuần vượt/thiếu so với phần còn lại của tháng quá ngưỡng `PL_WEEK_VS_MONTH_PCT`; gửi duyệt → phê duyệt; điều chỉnh sau duyệt → Revision mới (≥ 3 lần/tuần) | P0 |
| FR-PL-003 | Lệnh SX ngày/ca (gợi ý tự động) | Từ KHSX tuần đã duyệt: với mỗi (ngày, ca), gợi ý phân bổ SP → máy bao → máy vấn theo ghép cặp (`resolve`), tôn trọng `ProductMachineAllowed`, trạng thái máy, lịch bảo trì (loại máy đang bảo trì). Người dùng điều chỉnh tay (kéo-thả SP giữa các máy) | P0 |
| FR-PL-004 | Xác nhận lệnh trước ca | Trưởng ca/Phó QĐ xem, sửa, **xác nhận** (ký điện tử) – kích hoạt: sinh phiếu cấp phát NVL (MT), lịch KSCL (QC), khởi tạo phiên ghi nhận (PR). Khóa tham chiếu `NormVersionId` | P0 |
| FR-PL-005 | Bố trí nhân sự theo ca | Gán nhân sự vào máy/vị trí; **sao chép từ ca trước (bắt buộc)** chỉ bằng 1 thao tác; tìm theo tên/mã thẻ; cảnh báo nhân sự trùng máy; xuất bảng bố trí ra PDF/Excel để in | P0 |
| FR-PL-006 | Điều chỉnh lệnh trong ca | Trưởng ca ghi nhận đổi mác/hết sợi/máy hỏng/chạy phụ/đổi sản lượng + lý do (bắt buộc, danh mục); hệ thống tạo `OrderSegment`, **tự cập nhật** kế hoạch ca còn lại trong ngày & các ngày còn lại trong tuần (đề xuất bù, người dùng xác nhận) | P0 |
| FR-PL-007 | Theo dõi thực hiện kế hoạch | Bảng thực tế vs kế hoạch theo SP/máy/ca realtime (nguồn PR); cảnh báo `PR_OUTPUT_BEHIND` | P0 |
| FR-PL-008 | Báo cáo thực hiện kế hoạch | Ca/ngày/tuần/tháng; % hoàn thành theo SP; xuất Excel/PDF | P1 |
| FR-PL-009 | Lưu trữ & tra cứu lịch sử | Toàn bộ KHSX tháng/tuần/lệnh (mọi revision) tra cứu & so sánh revision | P1 |

### 6.3 Quy tắc nghiệp vụ
- **BR-PL-001** Tổng `PlannedPacks` KHSX tuần theo SP không được vượt `còn lại tháng × (1 + ngưỡng)`; vượt/thiếu → cảnh báo (cho phép lưu), duyệt phải nhập lý do.
- **BR-PL-002** Lệnh `CONFIRMED` không sửa trực tiếp số lượng/SP/máy; chỉ thay đổi bằng `OrderAdjustment` (ghi lý do, tạo segment), trừ khi ca chưa bắt đầu (Trưởng ca/Phó QĐ được sửa & xác nhận lại → tăng `Revision`, sinh lại phiếu cấp phát chưa phát).
- **BR-PL-003** Một máy bao chỉ có một lệnh `RUNNING` tại một thời điểm; nhiều segment tuần tự.
- **BR-PL-004** Gợi ý lệnh không bao giờ ghi đè lệnh đã `CONFIRMED`; sinh lại gợi ý chỉ áp dụng cho các (ca, máy) chưa xác nhận.
- **BR-PL-005** Sản lượng kế hoạch lệnh tính theo **bao**; điếu = bao × `RodsPerPack`; hiển thị cả hai.
- **BR-PL-006** Máy `UNDER_MAINTENANCE`/có công việc bảo trì chiếm ca (từ PM) bị loại khỏi gợi ý hoặc gắn cảnh báo "xung đột bảo trì".
- **BR-PL-007** Khi đổi mác: thời gian chuyển đổi gắn mã dừng máy `A` (do PR ghi), PL chỉ giữ ý định kế hoạch.
- **BR-PL-008** Sao chép nhân sự: bản sao tạo ở trạng thái `DRAFT`, hiển thị diff so với ca trước (nhân sự nghỉ/đổi).

### 6.4 Vòng đời
`MonthlyPlan`: DRAFT → ACTIVE → SUPERSEDED. `WeeklyPlan`: DRAFT → SUBMITTED → APPROVED → SUPERSEDED. `ProductionOrder`: SUGGESTED → CONFIRMED → RUNNING (khi Trưởng ca mở ca) → COMPLETED (khi chốt ca) | CANCELLED.

### 6.5 API (prefix `/api/v1/pl`)
| Method & Path | Mục đích |
|---|---|
| `GET/POST /monthly-plans`, `GET/PUT /monthly-plans/{id}`, `POST /monthly-plans/{id}/revise`, `POST /monthly-plans/import` | KHSX tháng |
| `GET /monthly-plans/{id}/progress` | Phân bổ/thực tế/còn lại |
| `GET/POST /weekly-plans`, `PUT /weekly-plans/{id}`, `POST /weekly-plans/{id}/submit` · `/approve` · `/reject` · `/revise` | KHSX tuần |
| `GET /weekly-plans/{id}/context` | Ngữ cảnh: còn lại tháng, lịch bảo trì, tồn NVL |
| `POST /orders/suggest` (body: weeklyPlanId, date range) | Sinh gợi ý lệnh |
| `GET /orders?date=&shift=&machineId=&status=` · `GET/PUT /orders/{id}` | Truy vấn/sửa lệnh |
| `POST /orders/{id}/confirm` · `POST /orders/batch-confirm` | Xác nhận (kèm signature) |
| `POST /orders/{id}/adjustments` | Điều chỉnh trong ca |
| `GET/POST /rosters`, `PUT /rosters/{id}`, `POST /rosters/{id}/copy-from/{srcId}`, `POST /rosters/{id}/publish`, `GET /rosters/{id}/export` | Bố trí nhân sự |
| `GET /execution?date=&shift=` | Thực hiện kế hoạch realtime |

### 6.6 Màn hình
Lịch KHSX tháng; Lưới KHSX tuần (nhập, kiểm tra, ngữ cảnh bên phải); Bảng điều độ lệnh theo ca (hàng = máy, cột = ca, kéo-thả); Màn hình xác nhận lệnh đầu ca (tablet Trưởng ca + web); Bố trí nhân sự; Theo dõi thực hiện.

### 6.7 Phân quyền
`PLANNER`: tạo/sửa KHSX tháng, import, điều chỉnh tuần (dự thảo), theo dõi. `DEPUTY_DIRECTOR`: lập/duyệt tuần, duyệt/điều chỉnh lệnh. `SHIFT_LEADER`: xác nhận lệnh ca của mình, bố trí nhân sự, điều chỉnh trong ca. `DIRECTOR`/`BOARD`/`LEADERSHIP`: xem. Các vai trò vận hành: xem lệnh của máy mình.

### 6.8 Tiêu chí nghiệm thu
- **AC-PL-001** Nhập KHSX tuần có tổng 120% phần còn lại của tháng → hiển thị cảnh báo vàng + yêu cầu lý do khi gửi duyệt.
- **AC-PL-002** Từ KHSX tuần đã duyệt, bấm "Gợi ý lệnh" cho ngày D → sinh lệnh cho mọi ca/máy bao theo ghép cặp; máy đang bảo trì bị bỏ qua và có ghi chú; chạy lại gợi ý không làm mất lệnh đã xác nhận.
- **AC-PL-003** Trưởng ca xác nhận lệnh → ≤ 10 giây sau: có phiếu cấp phát 3 loại theo 4 khung giờ, lịch KSCL 30', phiên PR khởi tạo (kiểm tra bằng API từng module).
- **AC-PL-004** Đổi mác giữa ca (A→B) → tạo 2 segment, lý do bắt buộc; kế hoạch ca còn lại được đề xuất cập nhật; cập nhật `Revision`; audit đủ.
- **AC-PL-005** "Sao chép từ ca trước" tạo bố trí đầy đủ ≤ 2 giây cho 77 máy, hiển thị người vắng mặt cần thay.

---

## 7. PHÂN HỆ MT – CẤP PHÁT & KIỂM SOÁT TIÊU HAO NGUYÊN VẬT LIỆU  *(báo cáo mục 2.4)*

### 7.1 Mô hình dữ liệu (nhóm bảng `mt`, vật lý `mt_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `mt.IssueSlip` | Id, Code(`PCP-<ngày>-<ca>-<loại>-nnn`), OrderId, BusinessDate, ShiftCode, SlipType(`FIBER`/`ROLLING`/`PACKING`), ConsumerMachineId, ProductId, NormVersionId, Revision, Status(`AUTO`/`CONFIRMED`/`ISSUING`/`COMPLETED`/`CANCELLED`), ConfirmedBy, ConfirmedAt | Thay 3 loại phiếu giấy: Phiếu cấp phát *Nguyên liệu*, *Vật tư vấn*, *Vật tư bao* |
| `mt.IssueSlipLine` | SlipId, SlotNo(1–4), MaterialId, PlannedQty dec, Uom | Số lượng = `QtyPer1000Packs × PlannedPacks/1000 × SharePct(slot)` |
| `mt.IssueRecord` | Id, SlipId?, SlipLineId?, BusinessDate, ShiftCode, SlotNo, ConsumerMachineId, MaterialId, LotNo?, IssuedQty dec, Uom, IssuedBy, IssuedAt, VarianceQty, VarianceReasonCode?, VarianceNote, RecordState, DeviceId, SyncStatus | Cấp phát thực tế (có thể ≠ phiếu; có thể cấp ngoài phiếu → `SlipId` null + lý do) |
| `mt.ReceiptConfirmation` | Id, IssueRecordId, ReceivedQty dec, ReceivedBy, ReceivedAt, RemainingBeforeReceipt dec?, Status(`CONFIRMED`/`DISPUTED`/`RESOLVED`), DisputeNote, ResolvedBy | Xác nhận 2 chiều: người giao (IssueRecord) ↔ người nhận (Receipt) |
| `mt.ReturnRecord` | Id, ConsumerMachineId, MaterialId, Qty, Reason, ReturnedBy, ReturnedAt, ConfirmedBy | Hoàn trả vật tư từ máy |
| `mt.StockCount` | Id, ShiftSessionId, ConsumerMachineId, MaterialId, CountedQty, CountedBy, CountedAt, Kind(`OPENING`/`MIDSHIFT`/`CLOSING`) | Kiểm đếm tồn tại máy |
| `mt.ConsumptionSummary` | Id, ShiftSessionId, ConsumerMachineId, MaterialId, ProductId?, OpeningQty, IssuedQty, ReturnedQty, ClosingQty, ActualConsumed, TheoreticalConsumed, WasteQty, WastePct, ThresholdPct, Flag(`OK`/`WARN`/`CRIT`), Explanation, Status(`DRAFT`/`CONFIRMED`/`LOCKED`) | Chốt theo ca – máy – NVL |
| `mt.ErpReconciliation` / `…Line` | Period, SourceFile, Lines(MaterialId, PxvbQty, ErpQty, Diff, Note), Status | Đối chiếu ERP bằng file (v1) |

### 7.2 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-MT-001 | Tự sinh phiếu cấp phát | Khi lệnh SX ca được xác nhận → hệ thống tính số lượng từng NVL theo **định mức hiệu lực × sản lượng kế hoạch**, tách 3 loại phiếu, phân bổ vào 4 khung giờ theo `SharePct`. Phiếu ở trạng thái `AUTO` | P0 |
| FR-MT-002 | Rà soát & xác nhận phiếu | STAT_MATERIAL xem, chỉnh số lượng (có lý do), xác nhận (ký điện tử) → `CONFIRMED`; phiếu hiển thị trên tablet của người cấp phát | P0 |
| FR-MT-003 | Ghi nhận cấp phát thực tế theo khung giờ | Trên tablet kho: chọn khung giờ → danh sách soạn hàng theo máy → nhập số lượng thực tế (mặc định = phiếu; nút +/−; quét mã NVL/lô); chênh lệch vượt `MT_ISSUE_OVER_SLIP_PCT` bắt buộc chọn lý do; hỗ trợ cấp bổ sung ngoài phiếu | P0 |
| FR-MT-004 | Xác nhận nhận NVL điện tử | Công nhân vận hành xác nhận nhận trên tablet dây chuyền (thay ký giấy); xác nhận số lượng nhận (mặc định = số giao; sửa được → `DISPUTED` nếu lệch); lưu người giao/nhận/thời gian/số lượng | P0 |
| FR-MT-005 | Kiểm đếm tồn tại máy & hoàn trả | Nhập tồn còn lại tại máy (đầu ca tự điền = tồn cuối ca trước; giữa ca/cuối ca nhập tay); ghi nhận hoàn trả | P0 |
| FR-MT-006 | Theo dõi tiêu hao thời gian thực | So sánh tiêu hao thực tế vs định mức theo máy × mác × NVL; tính % hao hụt; cập nhật khi có sản lượng mới hoặc kiểm đếm mới | P0 |
| FR-MT-007 | Cảnh báo hao hụt bất thường | Khi `WastePct` vượt ngưỡng (theo nhóm vật tư) → cảnh báo `MT_WASTE_HIGH` đến Trưởng ca & Phó QĐ **≤ 5 phút**; ghi lịch sử xử lý | P0 |
| FR-MT-008 | Quản lý tồn NVL tại phân xưởng | Tồn đầu ca = tồn cuối ca trước (liên tục theo máy–NVL); tồn cuối ca được kiểm đếm hoặc đề xuất `Opening + Issued − Returned − Theoretical` để xác nhận | P0 |
| FR-MT-009 | Tổng hợp & chốt tiêu hao ca | Tự tổng hợp `ConsumptionSummary`; STAT_MATERIAL xác nhận/chốt số liệu; báo cáo tiêu hao ca sinh tự động (thay 30–45 phút thủ công) | P0 |
| FR-MT-010 | Phân tích xu hướng tiêu hao | Theo thời gian/máy/mác/ca; xếp hạng NVL hao hụt cao; hỗ trợ rà soát định mức (nút "đề xuất điều chỉnh định mức" tạo bản nháp phiên bản mới ở MD) | P1 |
| FR-MT-011 | Đối chiếu ERP | Import file xuất/tồn từ ERP theo kỳ → báo cáo chênh lệch theo NVL (REST API chuẩn bị cho tích hợp sau) | P1 |
| FR-MT-012 | Truy vết lô NVL | Lưu `LotNo` theo lần cấp phát để QC truy ngược lỗi theo lô sợi/vật tư | P1 |

### 7.3 Quy tắc nghiệp vụ
- **BR-MT-001** `PlannedQty(line) = QtyPer1000Packs × (PlannedPacks/1000) × SharePct/100`; làm tròn theo `Uom` (cấu hình số lẻ); tổng các khung giờ = tổng nhu cầu ca (phần dư dồn vào khung cuối).
- **BR-MT-002** *Máy tiêu thụ*: NVL nhóm `FIBER`,`ROLLING` → máy vấn; `PACKING` → máy bao (⚠ cho phép ghi đè bằng cột `ConsumerType` trên dòng định mức). Sản lượng kế hoạch/thực tế để tính cho máy vấn = tổng bao của các máy bao được nó cấp điếu (theo `FeedRatio`).
- **BR-MT-003** `TheoreticalConsumed = QtyPer1000Packs × GoodPacksAttributed/1000` (định mức hiệu lực **tại thời điểm xác nhận lệnh**; theo segment khi đổi mác). `ActualConsumed = Opening + Issued − Returned − Closing(kiểm đếm)`. `WastePct = (Actual − Theoretical)/Theoretical`.
- **BR-MT-004** Cần ≥ 1 lần kiểm đếm giữa/cuối ca để tính `ActualConsumed`. ⚠ Nếu nghiệp vụ không kiểm đếm, dùng `Closing = Opening + Issued − Returned − Theoretical` (hao hụt = 0 theo thiết kế) và hao hụt chỉ phát hiện qua **cấp vượt định mức lũy kế** (`Issued_cum − Opening − Theoretical_cum > ngưỡng`). Cấu hình `MT_REQUIRE_COUNT` theo nhóm.
- **BR-MT-005** Mỗi bản ghi nhận NVL cần cả `IssueRecord` và `ReceiptConfirmation` mới tính là *đã giao nhận*; khung giờ đã qua + dung sai (`MT_SLOT_TOLERANCE_MIN`) mà chưa xác nhận → cảnh báo `MT_SLOT_LATE` và **chặn chốt ca** (CHK_MATERIAL).
- **BR-MT-006** Đổi mác trong ca: phiếu cấp phát của mác mới được sinh thêm (theo segment); NVL dư của mác cũ ghi nhận `ReturnRecord` hoặc chuyển dùng tiếp (ghi nhận lý do).
- **BR-MT-007** Chênh lệch `IssuedQty` vs `PlannedQty` > ngưỡng → bắt lý do; chênh lệch `Received` vs `Issued` → `DISPUTED`, STAT_MATERIAL phải xử lý trước khi chốt ca.
- **BR-MT-008** Tồn cuối ca của ca N tự thành tồn đầu ca N+1 cùng (máy, NVL) – kể cả khi sang ngày; nếu máy chuyển lệnh thì tồn vẫn theo máy.
- **BR-MT-009** Sau `CONFIRMED`, `IssueSlipLine` không sửa trực tiếp; chỉnh bằng `Revision` mới (nếu ca chưa bắt đầu) hoặc `AdjustmentRequest`.

### 7.4 Vòng đời
`IssueSlip`: AUTO → CONFIRMED → ISSUING (≥ 1 khung đã cấp) → COMPLETED (4 khung xong hoặc chốt ca) | CANCELLED. `ReceiptConfirmation`: CONFIRMED | DISPUTED → RESOLVED.

### 7.5 API (prefix `/api/v1/mt`)
| Method & Path | Mục đích |
|---|---|
| `GET /slips?date=&shift=&type=&machineId=&status=` · `GET /slips/{id}` | Truy vấn phiếu |
| `POST /slips/{id}/confirm` · `PUT /slips/{id}/lines/{lineId}` (khi AUTO) · `POST /slips/{id}/regenerate` | Rà soát/xác nhận |
| `GET /issue-worklist?date=&shift=&slotNo=` | Danh sách soạn hàng theo khung giờ (cho tablet kho) |
| `POST /issue-records` (Idempotency-Key) | Ghi nhận cấp thực tế |
| `POST /receipts` · `POST /receipts/{id}/resolve` | Xác nhận nhận / xử lý tranh chấp |
| `POST /returns` · `POST /stock-counts` | Hoàn trả / kiểm đếm |
| `GET /consumption?shiftSessionId=&machineId=` · `GET /consumption/live` | Tiêu hao realtime |
| `POST /consumption/confirm` (ShiftSessionId) | Chốt tiêu hao ca |
| `GET /analysis/trend?...` · `GET /reports/shift/{shiftSessionId}` | Phân tích/báo cáo |
| `POST /erp-reconciliations/import` · `GET /erp-reconciliations/{id}` | Đối chiếu ERP |

### 7.6 Phân quyền
`STAT_MATERIAL`: xem/sửa AUTO, xác nhận phiếu, chốt tiêu hao, xử lý tranh chấp, đối chiếu ERP. `MATERIAL_ISSUER`: ghi nhận cấp phát, hoàn trả. `OPERATOR`: xác nhận nhận, kiểm đếm tại máy mình. `SHIFT_LEADER`: xem, xử lý cảnh báo, ghi nguyên nhân. `DEPUTY_DIRECTOR`/`DIRECTOR`: xem, duyệt đề xuất điều chỉnh định mức.

### 7.7 Tiêu chí nghiệm thu
- **AC-MT-001** Lệnh ca A: máy bao 20.000 bao, định mức giấy bao 2,5 kg/1.000 bao, SharePct 25 → mỗi khung phiếu 12,5 kg; tổng 50 kg; phiếu `AUTO` xuất hiện ≤ 10 giây sau xác nhận lệnh.
- **AC-MT-002** Cấp thực tế khung 1 = 14 kg (+12%) > ngưỡng 10% → form bắt buộc chọn lý do; sau lưu phát `MT_ISSUE_OVER_SLIP`.
- **AC-MT-003** Người nhận nhập 13 kg khi người giao ghi 14 kg → trạng thái `DISPUTED`, ca không thể chốt cho đến khi STAT_MATERIAL `resolve`.
- **AC-MT-004** Tiêu hao thực tế vượt định mức 5% (ngưỡng 3%) → cảnh báo đến Trưởng ca & Phó QĐ ≤ 5 phút; lịch sử ghi nguyên nhân khi xử lý.
- **AC-MT-005** Tồn cuối ca A của (máy X, NVL Y) = 8 kg → ca B hiển thị tồn đầu 8 kg tự động, sửa được nhưng phải kèm lý do + ghi audit.
- **AC-MT-006** Báo cáo tiêu hao ca sinh ≤ 5 phút sau chốt, có cấp phát/tiêu hao/tồn/hao hụt theo từng loại NVL & máy; xuất PDF/Excel.

---

## 8. PHÂN HỆ PR – GHI NHẬN SẢN LƯỢNG & THEO DÕI HIỆU SUẤT THIẾT BỊ (OEE)  *(báo cáo mục 2.5)*

### 8.1 Mô hình dữ liệu (nhóm bảng `pr`, vật lý `pr_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `pr.ShiftSession` | Id, BusinessDate, ShiftCode, PlannedStart, PlannedEnd, ActualStart, ActualEnd, Status(`PLANNED`/`OPEN`/`CLOSING`/`CLOSED`/`FINALIZED`), LeaderEmployeeId, OpenedBy, ClosedBy, ReportSnapshotId | Unique(BusinessDate, ShiftCode); trục chốt ca |
| `pr.MachineRun` | Id, ShiftSessionId, MachineId, OrderId?, Status(`NOT_STARTED`/`RUNNING`/`STOPPED`/`ENDED`), PlannedMinutes, RunMinutes, PlannedStopMinutes, UnplannedStopMinutes, OutputQty (rods hoặc packs), GoodQty, OeeId? | Một dòng cho mỗi máy trong ca (kể cả máy không có lệnh → `NO_ORDER`) |
| `pr.OutputEntry` | Id, MachineRunId, MachineId, ProductId, SegmentNo, PeriodFrom, PeriodTo, Qty bigint, Unit(`RODS`/`PACKS`), CounterReading?, Note, EnteredBy, OccurredAt, RecordState, DeviceId, SyncStatus | Mặc định mỗi **120 phút/lần** (`OUTPUT_INTERVAL_MIN`); cho phép nhiều lần/ca |
| `pr.StopEvent` | Id, MachineRunId, MachineId, ReasonCode, StartAt, EndAt?, DurationMin (computed), Note, TechCauseCode?, TechConfirmedBy?, TechConfirmedAt?, RepairRequestId?, RecordState | Không chồng lấn trên cùng máy |
| `pr.ProductChange` | Id, MachineRunId, FromProductId, ToProductId, ChangedAt, StopEventId?, EnteredBy | Tạo `OrderSegment` mới |
| `pr.BufferReading` | Id, PackerMachineId, ShiftSessionId, At, QtyRods, Source(`COMPUTED`/`MANUAL`), EnteredBy | Tồn điếu đầu ca/hiện tại tại máy bao |
| `pr.OeeResult` | Id, MachineRunId?, MachineId, Grain(`SHIFT`/`DAY`/`WEEK`/`MONTH`), PeriodKey, PlannedMinutes, RunMinutes, TotalQty, GoodQty, TheoreticalQty, Availability, Performance, Quality, Oee, IsProvisional, FormulaVersionId, ComputedAt | |
| `pr.OeeFormulaConfig` | Id, VersionNo, PlannedTimeMode(`EXCLUDE_PLANNED_STOPS`/`FULL_SHIFT`), QualityMode(`FROM_NC_RECORDS`/`FROM_INLINE_REJECT_RATE`/`ASSUME_100`), CapPerformanceAt100 bit, EffectiveFrom | "Công thức OEE cấu hình theo quy ước PXVB" |
| `pr.OutputReconciliation` | ShiftSessionId, MachineId, SystemQty, StatQty?, Diff, ConfirmedBy | NV thống kê sản lượng đối chiếu & xác nhận |

### 8.2 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-PR-001 | Quản lý phiên ca | Trưởng ca **bắt đầu ca** (điều kiện: có lệnh xác nhận + bố trí nhân sự đã công bố; cảnh báo nếu thiếu) → tạo `MachineRun` cho **toàn bộ 77 máy**; **kết thúc ca** → chạy CC-11 | P0 |
| FR-PR-002 | Ghi nhận sản lượng định kỳ | Tablet từng máy: nút "Ghi sản lượng" → nhập số lượng khoảng (bàn phím số lớn) hoặc số đồng hồ lũy kế (hệ thống trừ ra); tự gắn SP/segment hiện hành; kiểm tra hợp lý (≤ công suất lý thuyết × thời gian × 1,1; ≥ 0); nhắc nhở theo chu kỳ; hỗ trợ nhiều mác/máy/ca | P0 |
| FR-PR-003 | Ghi nhận đổi mác | Chọn "Đổi mác" → chọn SP mới (lọc theo `ProductMachineAllowed`) → hệ thống tách sản lượng theo segment, đề xuất bắt đầu sự kiện dừng mã `A` | P0 |
| FR-PR-004 | Ghi nhận dừng máy | Nút lớn "DỪNG" → chọn 1 trong 9 mã (A–I, nút màu lớn) → đếm thời gian; "CHẠY LẠI" kết thúc; cho nhập bù thời gian (bắt đầu/kết thúc) trong ca; mã yêu cầu xác nhận kỹ thuật (`C`,`D`) cho phép **tạo yêu cầu sửa chữa** 1 chạm (→ PM) | P0 |
| FR-PR-005 | Xác nhận nguyên nhân kỹ thuật | Kỹ thuật/MNT_LEAD xác nhận nguyên nhân dừng do kỹ thuật (`TechCauseCode`) – điều kiện để OEE chính thức | P1 |
| FR-PR-006 | Theo dõi sản lượng realtime | Dashboard ca: lũy kế thực tế vs kế hoạch-đến-thời-điểm theo máy/toàn PX (cập nhật ≤ 5'); `PR_OUTPUT_BEHIND` | P0 |
| FR-PR-007 | Theo dõi tồn điếu | Tính tồn điếu tại mỗi máy bao: `Buffer(t) = Opening + Σ điếu máy vấn cấp (theo ghép cặp & FeedRatio) − Σ bao đóng × RodsPerPack`; cho phép nhập hiệu chỉnh tay; cảnh báo vượt max/min | P1 |
| FR-PR-008 | Tính OEE tự động | A/P/Q theo máy: ca/ngày/tuần/tháng; OEE tạm tính mỗi 1–5' trong ca, chính thức khi chốt ca; công thức cấu hình (8.3) | P0 |
| FR-PR-009 | Cảnh báo sản lượng & hiệu suất | `PR_OUTPUT_BEHIND`, `PR_OEE_LOW`, `PR_STOP_LONG`, `PR_BUFFER_*`; lịch sử + trạng thái xử lý | P0 |
| FR-PR-010 | Giám sát khu vực (SUPERVISOR) | Màn hình lưới máy khu vực: màu theo trạng thái (chạy/dừng/chưa nhập quá hạn/offline); chạm vào máy xem chi tiết & nhập hộ (ghi `EnteredBy`+`OnBehalfOf`) | P1 |
| FR-PR-011 | Đối chiếu sản lượng (thống kê) | STAT_OUTPUT so sánh số hệ thống với số liệu thống kê riêng (nếu có), xác nhận | P1 |
| FR-PR-012 | Báo cáo sản lượng & hiệu suất ca | Tự sinh khi chốt: sản lượng theo mác, OEE từng máy, dừng theo lý do, so kế hoạch | P0 |
| FR-PR-013 | Phân tích hiệu suất thiết bị | Pareto lý do dừng, xu hướng OEE, so sánh máy cùng loại/dòng máy, phân tích theo ca | P1 |
| FR-PR-014 | Sao lưu số liệu giờ chạy máy cho PM | Phát `MachineRunTimeUpdated` để PM tính chu kỳ theo giờ vận hành | P1 |

### 8.3 Công thức OEE (mặc định, cấu hình qua `pr.OeeFormulaConfig`)
```
ShiftMin        = thời lượng ca theo ShiftDef (phút)
PlannedStopMin  = Σ thời gian dừng có mã CountsAsPlannedDowntime=1 (vd E giờ ăn, G không có yêu cầu SX)
PlannedMin      = ShiftMin − PlannedStopMin                      (PlannedTimeMode = EXCLUDE_PLANNED_STOPS)
UnplannedStopMin= Σ thời gian dừng các mã còn lại
RunMin          = PlannedMin − UnplannedStopMin
Availability    = RunMin / PlannedMin                             (PlannedMin = 0 → OEE = N/A, loại khỏi trung bình)
Performance     = TotalQty / (RunMin × TheoreticalRate)           (Rate theo đơn vị máy; cap 100% nếu cấu hình)
Quality         = GoodQty / TotalQty                              (GoodQty = TotalQty − NC; NC lấy từ bản ghi SPKPH/lỗi QC; không có dữ liệu → 100%)
OEE             = Availability × Performance × Quality
```
- Gộp theo ngày/tuần/tháng/dòng máy: **cộng dồn tử – mẫu** (ΣRun/ΣPlanned, ΣTotal/Σ(Run×Rate), ΣGood/ΣTotal), **không** lấy trung bình cộng phần trăm.
- Máy vấn đo bằng điếu, máy bao đo bằng bao. OEE cặp vấn–bao (tùy chọn P2): lấy OEE máy bao nút cổ chai.
- OEE `IsProvisional=1` khi ca chưa chốt hoặc dừng kỹ thuật chưa xác nhận.

### 8.4 Quy tắc nghiệp vụ
- **BR-PR-001** Chỉ ghi nhận cho `ShiftSession.Status = OPEN`; ca `CLOSING/CLOSED` chỉ qua điều chỉnh (CC-03).
- **BR-PR-002** Sự kiện dừng của cùng máy không chồng lấn; không vượt biên ca (dừng đang mở lúc hết ca tự đóng tại giờ kết thúc ca, đánh dấu `AutoClosed`, yêu cầu xác nhận).
- **BR-PR-003** Tổng sản lượng các entry của máy trong ca = `OutputQty`; entry sau ≠ trùng khoảng với entry trước (kiểm tra `PeriodFrom` ≥ `PeriodTo` trước); nhập thiếu chu kỳ → lượt nhập bù có ghi chú.
- **BR-PR-004** `PeriodTo − PeriodFrom` ≤ `OUTPUT_INTERVAL_MIN` × 2; sản lượng/giờ > 110% công suất lý thuyết → cảnh báo dữ liệu (bắt xác nhận), không chặn.
- **BR-PR-005** Mỗi máy trong ca có tối thiểu số lần ghi sản lượng = `floor(ShiftMin/OUTPUT_INTERVAL_MIN)` (cấu hình) mới đủ điều kiện chốt (CHK_OUTPUT_COMPLETE); máy `NO_ORDER` được miễn.
- **BR-PR-006** Mã dừng `A` kèm `ProductChange`; mã `C` có thể liên kết `RepairRequest`; mã `H` (thiếu NVL) có thể liên kết `IssueSlip`/MT.
- **BR-PR-007** Dữ liệu offline giữ `OccurredAt` (thời điểm phát sinh) – dùng để xếp thứ tự và tính thời gian, không dùng thời điểm đồng bộ.
- **BR-PR-008** Giờ ca vắt ngày: `BusinessDate` = ngày bắt đầu ca.

### 8.5 API (prefix `/api/v1/pr`)
| Method & Path | Mục đích |
|---|---|
| `GET /shifts/current` · `GET /shifts?date=` · `GET /shifts/{id}` | Phiên ca |
| `POST /shifts/{id}/open` · `/close-request` · `/close` · `/reopen-request` | Mở/đóng ca (kèm kết quả kiểm tra chốt ca) |
| `GET /shifts/{id}/close-checks` | Kết quả bộ kiểm tra CC-11 |
| `GET /machine-runs?shiftId=&machineId=` · `GET /machine-runs/{id}` | Chi tiết máy trong ca |
| `POST /outputs` · `PUT /outputs/{id}` (nháp) | Ghi nhận sản lượng |
| `POST /product-changes` | Đổi mác |
| `POST /stops` (bắt đầu) · `POST /stops/{id}/end` · `PUT /stops/{id}` · `POST /stops/{id}/tech-confirm` · `POST /stops/{id}/repair-request` | Dừng máy |
| `POST /buffer-readings` · `GET /buffers?shiftId=` | Tồn điếu |
| `GET /oee?grain=&from=&to=&machineId=&model=` | OEE |
| `GET /live/summary?shiftId=` | Dữ liệu dashboard realtime |
| `GET /analysis/pareto-stops?...` · `GET /analysis/oee-compare?...` | Phân tích |
| `GET/PUT /oee-formula` | Công thức OEE |
| `POST /reconciliations` | Đối chiếu sản lượng |

### 8.6 Phân quyền
`SHIFT_LEADER`: mở/đóng ca, ghi đổi mác, xử lý cảnh báo. `OPERATOR`/`SUPERVISOR`: ghi sản lượng/dừng máy máy mình (SUPERVISOR: khu vực). `STAT_OUTPUT`: xem tất cả, đối chiếu/xác nhận. `MNT_LEAD`/`MNT_TECH`: xác nhận nguyên nhân kỹ thuật. `DEPUTY_DIRECTOR`/`DIRECTOR`/`LEADERSHIP`: xem dashboard, phân tích. `SYS_ADMIN`: cấu hình công thức OEE.

### 8.7 Tiêu chí nghiệm thu
- **AC-PR-001** Mở ca A: sau ≤ 5 giây có đủ `MachineRun` cho 77 máy, đúng lệnh/mác/kế hoạch; máy không lệnh ở trạng thái `NO_ORDER`.
- **AC-PR-002** Máy bao ca 480', dừng `E` 30' (kế hoạch), dừng `C` 40' (ngoài kế hoạch), công suất 400 bao/phút, sản lượng 150.000 bao, không có NC: Planned=450', Run=410', Availability=91,11%, Performance=150.000/(410×400)=91,46%, Quality=100%, **OEE=83,33%**. Hệ thống phải cho kết quả này và khớp bảng tính tham chiếu (golden test ≥ 10 bộ số liệu do PO cung cấp, gồm trường hợp có NC, đổi mác, máy không lệnh).
- **AC-PR-003** Sự kiện dừng chồng lấn thời gian trên cùng máy bị từ chối với thông báo tiếng Việt chỉ ra sự kiện xung đột.
- **AC-PR-004** Tablet mất wifi 40 phút: nhập 3 lần sản lượng + 2 sự kiện dừng; khi có mạng đồng bộ đúng thứ tự `OccurredAt`, không trùng, dashboard cập nhật ≤ 5 phút kể từ lúc đồng bộ.
- **AC-PR-005** Sản lượng tích lũy máy X = 80% kế hoạch-đến-thời-điểm (ngưỡng lệch 15%) → trong ≤ 5 phút Trưởng ca & Phó QĐ nhận `PR_OUTPUT_BEHIND`.
- **AC-PR-006** Chốt ca khi còn máy chưa đủ lượt ghi sản lượng → bị chặn với danh sách máy thiếu; sau khi bổ sung (hoặc nhập giải trình được duyệt) chốt được; OEE chính thức (`IsProvisional=0`) xuất hiện ≤ 5 phút.

---

## 9. PHÂN HỆ QC – SỐ HÓA KIỂM SOÁT CHẤT LƯỢNG (KSCL inline + ĐBCL)  *(báo cáo mục 2.6)*

### 9.1 Bối cảnh nghiệp vụ cần giữ đúng
- Quy trình gốc: **TLSG-QT-ĐBCL-01 (lần ban hành 10, 15/4/2025)**. Hai cấp kiểm tra **độc lập**: (1) *KSCL inline* của PXVB – công nhân vận hành kiểm tra tại chuyền **30 phút/lần**; (2) *ĐBCL* của công ty – kiểm tra độc lập, tần suất ĐBCL ~**180 phút/lần**.
- Biểu mẫu inline: **B06** (điếu vấn), **B08** (bao thuốc), **B09** (tút thuốc) – mỗi loại chia **4 form con** theo 4 nhóm: *nguyên vật liệu đầu vào* (sợi, giấy vấn, đầu lọc, giấy sáp, mực in, keo) · *ngoại hình sản phẩm* (13 lỗi điếu vấn; 15–20 lỗi bao) · *trọng lượng & chu vi điếu* · *vật liệu bao bì* (nhôm, lưỡi gà, tem, bóng kính, chỉ xé, keo).
- Biểu mẫu ĐBCL: **B11** (cảm quan điếu vấn), **B15** (cảm quan thùng/tút/bao), **B12** (sổ kiểm tra thành phẩm), **B14** (thống kê số liệu), **B21** (chỉ tiêu vật lý: trọng lượng, độ giảm áp, chu vi, độ ẩm, độ cứng, điểm bình hút; tách nội tiêu/xuất khẩu/gia công). (Báo cáo còn nhắc B07 – sổ kiểm tra điếu vấn.)
- Tổng **46 loại biểu mẫu** hiện hành phải được số hóa **không sót thông tin**. Phân cấp lỗi ngoại quan **A/B/C** và chỉ tiêu vật lý so với tiêu chuẩn cơ sở theo từng chủng loại. SP không phù hợp dán nhãn "SPKPH".
- ⚠ **Nội dung chi tiết từng biểu mẫu (trường, danh mục lỗi, ngưỡng A/B/C) KHÔNG nằm trong báo cáo** → thiết kế theo hướng **biểu mẫu động (form engine) + dữ liệu cấu hình do ĐBCL cung cấp**. Dev chỉ cài đặt engine, 2 form mẫu tham chiếu (xem 9.4) và công cụ import schema; không hard-code field.

### 9.2 Mô hình dữ liệu (nhóm bảng `qc`, vật lý `qc_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `qc.InlineSchedule` | Id, ShiftSessionId, MachineId, FormGroup, DueAt, GraceUntil, Status(`PENDING`/`DONE`/`LATE`/`MISSED`/`SKIPPED_STOP`), InspectionId?, EscalatedAt? | Lịch kiểm tra tự sinh (30'/lần theo `FrequencyMin` của mẫu) |
| `qc.Inspection` | Id, Code, Category(`INLINE`/`QA`), FormTemplateId (đúng phiên bản), FormGroup, BusinessDate, ShiftSessionId, MachineId, OrderId, ProductId, SegmentNo, InspectedAt, InspectorUserId, SampleSize?, Answers(json), ResultSummary(`PASS`/`FAIL`), HighestGrade?(`C`/`B`/`A`), LotRefs(json), ScheduleId?, RecordState, DeviceId, SyncStatus, Revision | `Answers` lưu nguyên bản trả lời theo schema; các bảng dưới là bản **chuẩn hóa** phục vụ truy vấn |
| `qc.InspectionDefect` | InspectionId, DefectCode, Count, SampleSize, RatePct, Grade?(`C`/`B`/`A`) | Sinh tự động từ `Answers` |
| `qc.InspectionMeasurement` | InspectionId, Indicator, Value dec, Min, Max, InSpec bit, Category | Chỉ tiêu vật lý (B21, trọng lượng/chu vi B06.3…) |
| `qc.NonconformingProduct` | Id, Code(`SPKPH-…`), InspectionId, OrderId, MachineId, ProductId, Grade, QtyAffected, Unit, Description, LotRefs, Status(`OPEN`/`PROPOSED`/`APPROVED`/`EXECUTED`/`CLOSED`), Disposition?(`REWORK`/`SCRAP`/`PARTIAL_REUSE`/`CONCESSION`), DispositionNote, ProposedBy, ApprovedBy, RootCause, CorrectiveAction | Quy trình xử lý sản phẩm không phù hợp |
| `qc.MonthlyPhysicalStat` | PeriodMonth, ProductId, Category, Indicator, N, Avg, Min, Max, StdDev, OutOfSpecCount, ComputedAt | Sinh từ B21 cuối tháng |
| `qc.QaVisibilityGrant` | InspectionId, UserId, GrantedAt | Mở quyền xem dữ liệu inline cho ĐBCL sau khi nộp (BR-QC-010) |

### 9.3 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-QC-001 | Lịch kiểm tra inline tự động | Khi ca mở: sinh lượt kiểm tra cho từng máy đang chạy theo mẫu áp dụng (B06 máy vấn · B08 máy bao · B09 tút) mỗi 30'; bỏ qua khoảng máy dừng liên tục (`SKIPPED_STOP`); nhắc trên tablet đúng giờ (thông báo + rung) | P0 |
| FR-QC-002 | Leo thang trễ lịch | Trễ > 10' → cảnh báo `QC_CHECK_LATE` tới NV giám sát + ghi `LATE` vào lịch sử; quá 30' → `MISSED` | P0 |
| FR-QC-003 | Form KSCL inline B06/B08/B09 | Form động theo schema; tự điền thời gian, máy, mác, ca; tối ưu tablet: **pass/fail ≤ 3 thao tác**; bàn phím số; lỗi chọn từ lưới nút; lưu nháp tự động; nộp → chấm điểm ngay | P0 |
| FR-QC-004 | Form ĐBCL B11/B15/B12/B14/B21 | Nhập độc lập trên tablet ĐBCL theo lịch riêng (~180'); dữ liệu tách biệt với inline | P1 |
| FR-QC-005 | Phân cấp lỗi A/B/C tự động | Sau khi nộp: so `InspectionDefect` với `md.DefectGradeRule` + thông số vật lý so tiêu chuẩn → xác định `HighestGrade`; phát cảnh báo ≤ 5': **C → Công nhân; B → Trưởng ca (+Giám sát); A → Phó QĐ + ĐBCL**; lỗi A phải được tiếp nhận & xử lý trong **30'** (leo thang QA_HEAD/Quản đốc nếu quá 10' chưa ACK) | P0 |
| FR-QC-006 | SP không phù hợp (SPKPH) | ĐBCL/Trưởng ca ghi nhận NC từ một lượt kiểm tra (chọn lượng, mô tả, lô); đề xuất xử lý → duyệt (QA_HEAD / Quản đốc) → ghi kết quả xử lý; liên kết lệnh SX, máy, lô NVL | P0 |
| FR-QC-007 | Tỷ lệ lỗi thời gian thực | Dashboard: tỷ lệ lỗi theo máy/mác/loại lỗi lũy kế ca; cảnh báo khi vượt ngưỡng; so ca trước | P0 |
| FR-QC-008 | Báo cáo chất lượng ca | Tự sinh cuối ca: số lượt kiểm tra (đạt/không), % theo loại lỗi, số lỗi A/B/C, chỉ tiêu vật lý đạt/không, **biểu đồ phân loại nguyên nhân lỗi**, so ca trước | P0 |
| FR-QC-009 | Thống kê chỉ tiêu vật lý tháng (B21) | Cuối tháng tự tổng hợp: trung bình, min/max, độ lệch chuẩn, số mẫu ngoài chuẩn theo SP × nhóm (nội tiêu/xuất khẩu/gia công) × chỉ tiêu; xuất Excel | P1 |
| FR-QC-010 | Phân tích xu hướng lỗi | Theo thời gian, máy, mác, ca; loại lỗi phổ biến; so sánh máy cùng loại | P1 |
| FR-QC-011 | Truy ngược nguyên nhân lỗi | Từ một lỗi/lượt kiểm tra → ca, máy, mác, lệnh SX, lô NVL (từ MT `LotNo` trong khung giờ liên quan), công nhân vận hành, các lượt kiểm tra liền kề | P1 |
| FR-QC-012 | Quản lý & phiên bản biểu mẫu | Trình thiết kế/nhập schema, xem trước, kích hoạt phiên bản mới có hiệu lực (không làm sai dữ liệu cũ); sửa quy trình KSCL **không cần can thiệp kỹ thuật phức tạp** | P0 |
| FR-QC-013 | Tài liệu căn cứ quy trình khác | Lưu biểu mẫu ghi chép phụ trợ (giao nhận sợi/vật tư, hướng dẫn tem điện tử, truy xuất nguồn gốc, xử lý sợi Dechets) dưới dạng form động cùng engine, phục vụ truy vết | P2 |
| FR-QC-014 | Nhập số liệu từ máy đo | Import file kết quả từ máy đo chỉ tiêu vật lý (CSV/Excel) vào B21 (⚠ định dạng cần mẫu từ ĐBCL) | P2 |
| FR-QC-015 | KPI chất lượng năm | Theo dõi chỉ tiêu "bao thành phẩm xuất kho không đạt < 0,03%/năm" (nhập số liệu xuất kho không đạt từ ĐBCL) | P2 |

### 9.4 Form engine (biểu mẫu động)
**Mục tiêu**: render cùng một `schema` trên tablet (RN) và web (Angular), kiểm tra hợp lệ phía client + server, chuẩn hóa kết quả.

```jsonc
{
  "code": "B06.2", "version": 3, "title": "Phiếu KT chất lượng điếu vấn – Ngoại hình",
  "appliesTo": {"machineType": "MAKER"}, "frequencyMin": 30,
  "sampleSize": {"default": 20, "editable": true},
  "sections": [
    { "key": "appearance", "title": "Lỗi ngoại hình",
      "fields": [
        { "key": "defects", "type": "defect-grid",
          "items": [ {"defectCode": "D01", "label": "Điếu lỏng đầu"}, {"defectCode": "D02", "label": "Rụng đầu lọc"} ],
          "countUnit": "rod", "normalize": "InspectionDefect" },
        { "key": "overall", "type": "pass-fail", "required": true, "computed": "no defect with grade>=C" }
      ]},
    { "key": "weight", "title": "Trọng lượng & chu vi",
      "fields": [ { "key": "w", "type": "number", "unit": "g", "decimals": 3, "indicator": "WEIGHT",
                    "bounds": {"fromSpec": "ProductPhysicalSpec"}, "normalize": "InspectionMeasurement" } ]}
  ],
  "rules": [ { "when": "defects.D01.count > 0 && defects.D01.count/sampleSize > 0.1", "gradeHint": "B" } ]
}
```
- **Kiểu field hỗ trợ**: `pass-fail` · `ok-ng` · `number` (min/max/decimals/unit, bounds từ spec) · `text` · `select` · `multi-select` · `defect-grid` (danh sách lỗi × số lượng) · `table` (nhiều dòng mẫu) · `photo` · `signature-confirm` · `computed` (biểu thức an toàn: dùng thư viện `jsonlogic`/ `expr-eval`; **không** `eval`).
- Điều kiện hiển thị (`visibleIf`), bắt buộc (`required`), phụ thuộc loại SP/khổ điếu; tự điền (`prefill`: máy, mác, ca, nhân viên, lô NVL).
- **Mục tiêu UX tablet**: nút ≥ 56dp, tối đa 3 chạm cho pass/fail, bàn phím số tùy biến, tự chuyển ô tiếp theo, ghi nhớ giá trị gần nhất, chế độ ban ngày/ngoài sáng.
- Lưu `Inspection.FormTemplateId` + `Answers` nguyên bản → luôn dựng lại được đúng form tại thời điểm kiểm tra.
- Cung cấp lệnh CLI/endpoint `POST /qc/form-templates/import` nhận file JSON hoặc Excel (mẫu cột: nhóm, mã trường, nhãn, kiểu, đơn vị, min, max, bắt buộc, mã lỗi) để ĐBCL cấu hình **46 biểu mẫu**; kèm bảng đối chiếu "biểu mẫu giấy ↔ form số" để nghiệm thu không sót thông tin.
- **Tham chiếu bắt buộc phải hoàn thiện ở R2**: `B06.*`, `B08.*`, `B09.*` (inline). Các form ĐBCL (`B11`,`B15`,`B12`,`B14`,`B21`) hoàn thiện trước nghiệm thu (P1).

### 9.5 Quy tắc nghiệp vụ
- **BR-QC-001** Tần suất mặc định 30' (`QC_CHECK_INTERVAL_MIN`) cho từng (máy × form); ĐBCL theo lịch riêng (180' – cấu hình).
- **BR-QC-002** Lượt kiểm tra trễ > 10' → `LATE` + leo thang; > 30' hoặc ca kết thúc chưa làm → `MISSED`. Số lượt bắt buộc/ca = số lượt lịch không `SKIPPED_STOP`; thiếu → **chặn chốt báo cáo chất lượng** (CHK_QUALITY).
- **BR-QC-003** Phân cấp: lấy cấp cao nhất trong các lỗi/chỉ tiêu của lượt; `HighestGrade` quyết định định tuyến cảnh báo; luật theo `md.DefectGradeRule` hiệu lực lúc kiểm tra.
- **BR-QC-004** Lỗi cấp A: tạo `Alert` CRITICAL, **đồng hồ SLA 30'**; hiển thị đếm ngược; quá hạn leo thang `QA_HEAD`, `DIRECTOR`; việc xử lý (hành động khắc phục) bắt buộc ghi trước khi `RESOLVED`.
- **BR-QC-005** Kết quả `FAIL` ở form có `requiresNcPrompt=true` → hệ thống gợi ý tạo SPKPH.
- **BR-QC-006** Sau nộp (`SUBMITTED`) công nhân không tự sửa; sửa qua `AdjustmentRequest` (QC_INLINE/Trưởng ca duyệt).
- **BR-QC-007** `InspectionMeasurement.InSpec` tính theo `ProductPhysicalSpec` hiệu lực của SP & nhóm (nội tiêu/xuất khẩu/gia công) tại thời điểm kiểm tra.
- **BR-QC-008** Thống kê tháng B21 chỉ dùng bản ghi `CONFIRMED`+; chạy lại được (idempotent) khi có điều chỉnh.
- **BR-QC-009** Sản lượng NC (SPKPH) trừ vào `GoodQty` của OEE (Quality) khi `FROM_NC_RECORDS`.
- **BR-QC-010** **Tính độc lập ĐBCL**: dữ liệu `Category=QA` do `QA_*` nhập; `QA_INSPECTOR` **không xem/sửa** kết quả inline của (máy, ca) cho đến khi lượt ĐBCL tương ứng đã `SUBMITTED` (hoặc ca đã `FINALIZED`); `QC_INLINE`/Công nhân không sửa dữ liệu ĐBCL. Cài đặt bằng chính sách truy cập (policy + `QaVisibilityGrant` hoặc điều kiện truy vấn), có test tự động.
- **BR-QC-011** Mỗi lượt kiểm tra phải gắn `OrderId/ProductId/SegmentNo` hiện hành lúc `InspectedAt` (để truy ngược).

### 9.6 API (prefix `/api/v1/qc`)
| Method & Path | Mục đích |
|---|---|
| `GET /schedules?shiftId=&machineId=&status=` | Lịch kiểm tra |
| `GET /form-templates?group=&machineType=&active=` · `GET /form-templates/{id}` · `POST /form-templates/import` · `POST /form-templates/{id}/activate` | Biểu mẫu |
| `POST /inspections` (Idempotency-Key) · `PUT /inspections/{id}` (DRAFT) · `POST /inspections/{id}/submit` | Nhập/nộp |
| `GET /inspections?...` · `GET /inspections/{id}` | Tra cứu |
| `GET /inspections/{id}/traceback` | Truy ngược |
| `POST /nc` · `GET /nc` · `POST /nc/{id}/propose` · `/approve` · `/execute` · `/close` | SPKPH |
| `GET /live/defect-rate?shiftId=` | Tỷ lệ lỗi realtime |
| `GET /reports/shift/{shiftSessionId}` · `GET /reports/physical-monthly?month=` | Báo cáo |
| `GET /analysis/trend?...` | Xu hướng |

### 9.7 Phân quyền
`OPERATOR`: nhập B06/B08/B09 máy mình. `SUPERVISOR`: xem khu vực, nhận cảnh báo trễ. `QC_INLINE`: xem inline toàn PX, xử lý cảnh báo B+. `QA_INSPECTOR`: nhập B11/B15/B12/B14/B21, ghi NC. `QA_SUPERVISOR`: đề xuất xử lý NC. `QA_HEAD`/`DIRECTOR`/`DEPUTY_DIRECTOR`: duyệt xử lý NC, xem xu hướng. `SYS_ADMIN`/`QA_HEAD`: quản lý biểu mẫu.

### 9.8 Tiêu chí nghiệm thu
- **AC-QC-001** Ca mở 07:00 → lịch kiểm tra từng máy tại 07:30, 08:00…; máy dừng liên tục 08:00–09:00 → các lượt bị `SKIPPED_STOP`; nhắc hiển thị trên đúng tablet ≤ 5 giây.
- **AC-QC-002** Công nhân không làm lượt 08:00 đến 08:10 → `QC_CHECK_LATE` tới NV giám sát; 08:30 → `MISSED`; ca không chốt được phần chất lượng cho tới khi giải trình/hoàn tất.
- **AC-QC-003** Nhập B06.2 với lỗi D01 vượt ngưỡng cấp B → cảnh báo cấp B tới Trưởng ca ≤ 5 phút; không gửi Phó QĐ; lỗi cấp A → Phó QĐ + ĐBCL nhận, đồng hồ 30' chạy; quá 10' chưa ACK → leo thang.
- **AC-QC-004** Nhập pass/fail một lượt B08 bằng ≤ 3 chạm (đo bằng kịch bản UI test tablet).
- **AC-QC-005** Tài khoản `QA_INSPECTOR` gọi API xem inspection inline của máy X ca hiện tại khi chưa nộp lượt ĐBCL → 403 `PXVB-QC-0403`; sau khi nộp → xem được.
- **AC-QC-006** Từ một lỗi B hiển thị đầy đủ: ca, máy, mác, lệnh SX, người vận hành, lô sợi/giấy cấp trong khung giờ, 3 lượt kiểm tra liền kề.
- **AC-QC-007** Kích hoạt phiên bản 4 của B06.2: lượt cũ vẫn mở đúng form phiên bản 3; lượt mới dùng phiên bản 4.
- **AC-QC-008** Tạo dữ liệu B21 100 mẫu → báo cáo tháng tính đúng avg/min/max/stddev (so với numpy) sai số < 1e-6.

---

## 10. PHÂN HỆ TX – THEO DÕI TEM THUẾ  *(báo cáo mục 2.7)*

### 10.1 Bối cảnh
Tem điện tử đã triển khai tại TLSG từ **01/07/2022**; quản lý theo **dải số seri**. Quy mô ≈ **3,2 triệu tem/ngày**, theo dõi cho **máy bao** (báo cáo nêu 32 máy bao ở mục tem và 31 dây chuyền đóng bao ở mục tổng quan → ⚠ dùng danh mục máy bao `md.Machine` thực tế, không hard-code số lượng). Hao hụt tem thực tế khác biệt lớn giữa máy (từ ~0,11 đến ~19 tem/1.000 bao) → **ngưỡng riêng từng máy**. Liên thông PR (đối chiếu bao) và hệ thống truy vết tem điện tử cấp công ty (ngoài phạm vi, qua file/adapter).

### 10.2 Mô hình dữ liệu (nhóm bảng `tx`, vật lý `tx_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `tx.StampBlock` | Id, StampTypeId, BatchRef, SerialPrefix?, SerialFromNum bigint, SerialToNum bigint, Qty (= To−From+1), Status(`IN_STORE`/`ASSIGNED`/`IN_USE`/`EXHAUSTED`/`RETURNED`), AssignedMachineId?, ReceivedAt, Note | Block tem chuẩn bị dùng; CHECK Qty |
| `tx.StampIssue` | Id, BusinessDate, ShiftSessionId, PackerMachineId, StampBlockId?, SerialPrefix?, SerialFromNum, SerialToNum, Qty, IssuedAt, IssuedBy, RecordState, DeviceId | Xuất dùng thêm cho máy bao; **không chồng dải** (BR-TX-002) |
| `tx.StampWaste` | Id, ShiftSessionId, PackerMachineId, Kind(`DAMAGED`/`LOST`), ReasonCode, Qty, RecordedBy, At, Note, RecordState | Tem hư/mất |
| `tx.StampWasteSerial` | WasteId, SerialPrefix?, SerialNum | Số seri cụ thể của tem hư (khi xác định được) |
| `tx.StampClosing` | Id, ShiftSessionId, PackerMachineId, RemainingQty, RemainingFromNum?, RemainingToNum?, CountedBy, CountedAt | Tồn tem cuối ca tại máy |
| `tx.StampShiftSummary` | Id, ShiftSessionId, PackerMachineId, OpeningQty, IssuedQty, DamagedQty, LostQty, ClosingQty, UsedQty, PacksProduced, ExpectedStamps, Diff, WastePer1000, ThresholdPer1000, Flag, Status(`DRAFT`/`CONFIRMED`/`LOCKED`), ConfirmedBy | |
| `tx.ExportProfile` | Code(`TAX_AUTHORITY`,`TLSG_INTERNAL`), Format(`XLSX`/`CSV`/`XML`), ColumnMapping(json), Version | Cấu hình định dạng báo cáo (⚠ cần mẫu thực tế từ TCKT/cơ quan thuế) |

### 10.3 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-TX-001 | Khai báo ca & tồn tem đầu ca | STAT_STAMP khai báo ca trực; tồn cuối ca trước **tự chuyển** thành tồn đầu ca (xác nhận/điều chỉnh có lý do); cập nhật block tem chuẩn bị dùng cho từng máy bao | P0 |
| FR-TX-002 | Quản lý block tem | Tạo block theo dải (`from–to`), loại tem, gán máy; import danh sách block từ Excel | P0 |
| FR-TX-003 | Xuất dùng tem theo dải seri | Ghi nhận xuất dùng thêm cho máy bao: dải bắt đầu–kết thúc (hoặc quét mã đầu/cuối), số lượng tự tính, thời gian, ca; nhiều lần/ca/máy; cộng dồn lũy kế realtime | P0 |
| FR-TX-004 | Ghi nhận tem hư/mất | Công nhân hoặc STAT_STAMP ghi trên tablet: số lượng + lý do (tem kẹt máy/dán lệch/rách/lỗi in/…); nhập dải/ danh sách seri khi xác định được; tem mất có lý do | P0 |
| FR-TX-005 | Đối chiếu tự động sản lượng–tem | So sánh `PacksProduced × StampsPerPack` (từ PR) với tem dùng thực tế `Used − Damaged − Lost` theo từng máy bao, realtime; hiển thị chênh lệch & hao hụt/1.000 bao | P0 |
| FR-TX-006 | Cảnh báo hao hụt tem | `TX_WASTE_HIGH` theo ngưỡng từng máy (Trưởng ca, STAT_STAMP); `TX_MISMATCH`; `TX_BLOCK_LOW` | P0 |
| FR-TX-007 | Chốt tem cuối ca | STAT_STAMP nhập tồn cuối (`StampClosing`), xem tổng hợp (tồn đầu, xuất dùng, hư, mất, tồn cuối, % hao hụt) → xác nhận; sinh báo cáo (thay 30–45'/ca) | P0 |
| FR-TX-008 | Theo dõi hao hụt theo máy | Bảng xếp hạng/biểu đồ xu hướng hao hụt/1.000 bao theo máy, ca, mác; phát hiện máy bất thường | P1 |
| FR-TX-009 | Phân tích xu hướng sử dụng tem | Theo ca/máy/mác/thời gian; gợi ý nguyên nhân | P1 |
| FR-TX-010 | Xuất báo cáo cơ quan thuế/nội bộ | Theo `ExportProfile`; kỳ ca/ngày/tháng; TCKT khai thác trực tiếp không xử lý thêm | P1 |
| FR-TX-011 | Giao diện kế toán/thuế | TCKT xem báo cáo ca/tháng, xuất file đối soát | P1 |

### 10.4 Quy tắc nghiệp vụ
- **BR-TX-001** `Qty = SerialToNum − SerialFromNum + 1`, `> 0`; hợp lệ khi thuộc một `StampBlock` đã gán cho máy đó (hoặc cho phép cấp ngoài block với cảnh báo + lý do).
- **BR-TX-002** **Không chồng dải seri**: không cho `StampIssue` mới giao với bất kỳ dải đã xuất (cùng loại tem, cùng prefix) — kiểm tra bằng truy vấn khoảng + khóa bi quan trên dòng `tx_stamp_type` (`SELECT … FOR UPDATE`, hoặc `GET_LOCK('stamp:<StampTypeId>', 10)` khi cần khóa ngoài giao dịch) để chống đua khi nhiều tablet nhập.
- **BR-TX-003** Công thức: `Used = Opening + Issued − Closing`; `ExpectedStamps = PacksProduced × StampsPerPack` (theo từng segment/mác); `Diff = Used − (ExpectedStamps + Damaged + Lost)`; `WastePer1000 = (Damaged + Lost)/PacksProduced × 1000`.
- **BR-TX-004** Ngưỡng hao hụt lấy từ `md.Threshold[TX_WASTE_PER_1000, MACHINE]`; dung sai `Diff` (`TX_MISMATCH_TOL`) mặc định 0 → vượt = chặn chốt (CHK_STAMP) tới khi kiểm kê & giải trình.
- **BR-TX-005** Không có bao thành phẩm nào được ghi nhận mà không có tem tương ứng và ngược lại (kiểm tra tại chốt ca bằng `Diff`).
- **BR-TX-006** Tồn cuối ca → tồn đầu ca kế tiếp cùng máy (liên tục xuyên ngày); thay đổi tay phải có lý do + audit.
- **BR-TX-007** Hiệu năng: mọi thao tác ghi tem ≤ 3 giây (P95) ở tải 3,2 triệu tem/ngày — chỉ lưu *dải*, không lưu từng tem (trừ tem hư có seri).
- **BR-TX-008** Dữ liệu tem `LOCKED` sau chốt ca; chỉnh sửa qua `AdjustmentRequest`.

### 10.5 API (prefix `/api/v1/tx`)
| Method & Path | Mục đích |
|---|---|
| `GET/POST /blocks` · `POST /blocks/import` · `PUT /blocks/{id}/assign` | Block tem |
| `POST /issues` · `GET /issues?shiftId=&machineId=` | Xuất dùng |
| `POST /wastes` · `GET /wastes?...` | Tem hư/mất |
| `POST /closings` | Tồn cuối ca |
| `GET /summary?shiftId=&machineId=` · `GET /live?shiftId=` | Tổng hợp/đối chiếu realtime |
| `POST /summary/confirm` | Chốt ca tem |
| `GET /reports/shift/{id}` · `GET /reports/period?from=&to=&profile=` | Báo cáo/xuất theo profile |
| `GET /analysis/waste-by-machine?...` | Phân tích |

### 10.6 Phân quyền
`STAT_STAMP`: toàn bộ ghi nhận/chốt. `OPERATOR` (máy bao): ghi tem hư máy mình. `SHIFT_LEADER`: xem, xử lý cảnh báo. `DEPUTY_DIRECTOR`: xem, nhận cảnh báo nghiêm trọng. `TCKT`: xem/xuất báo cáo. `SYS_ADMIN`: cấu hình ngưỡng/profile.

### 10.7 Tiêu chí nghiệm thu
- **AC-TX-001** Hai tablet cùng ghi xuất dùng có dải giao nhau → tablet thứ hai nhận lỗi `PXVB-TX-2002` kèm dải đã xuất (kể cả khi gửi gần đồng thời).
- **AC-TX-002** Máy bao M-B07 ngưỡng 0,5 tem/1.000 bao; ca sản xuất 200.000 bao, hư 60 + mất 70 → hao hụt 0,65/1.000 → `TX_WASTE_HIGH` ≤ 5 phút.
- **AC-TX-003** Mở ca B: tồn đầu = tồn cuối ca A tự động; sửa tay yêu cầu lý do + ghi audit.
- **AC-TX-004** Chốt ca khi `Diff` ≠ 0 vượt dung sai → bị chặn; sau khi nhập kiểm kê và giải trình được Trưởng ca duyệt, chốt được.
- **AC-TX-005** Báo cáo tem theo profile `TAX_AUTHORITY` xuất ≤ 30 giây cho 1 tháng, đúng cột mapping cấu hình.
- **AC-TX-006** Test tải: 32 máy × 50 thao tác/giờ + 3,2 triệu tem/ngày: P95 ≤ 3 giây.

---

## 11. PHÂN HỆ PM – BẢO TRÌ BẢO DƯỠNG ĐỊNH KỲ & SỬA CHỮA (CMMS)  *(báo cáo mục 2.8)*

### 11.1 Mô hình dữ liệu (nhóm bảng `pm`, vật lý `pm_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `pm.Plan` | Id, Year, Month, Revision, Status(`DRAFT`/`SUBMITTED`/`APPROVED`/`SUPERSEDED`), CreatedBy, ApprovedBy, ApprovedAt | Kế hoạch bảo trì tháng (KTV lập → Quản đốc/Phó QĐ duyệt) |
| `pm.WorkOrder` | Id, Code(`BT-yyyymm-nnnn`), PlanId?, MachineId, ComponentId?, TaskTemplateId, TemplateVersionNo, TriggerType(`CYCLE_DATE`/`OP_HOURS`/`MANUAL`/`FROM_REPAIR`), PlannedDate, PlannedShift, EstimatedMinutes, SkillRequired, Priority(`CRITICAL`/`NORMAL`), Status(`PLANNED`/`ASSIGNED`/`IN_PROGRESS`/`DONE`/`PARTIAL`/`OVERDUE`/`SKIPPED`/`CANCELLED`), StartedAt, CompletedAt, ResultSummary, SkipReason, RescheduledFromDate, RescheduleReason, RecordState | Công việc bảo trì cụ thể |
| `pm.WorkOrderAssignee` | WorkOrderId, EmployeeId, AssignedBy, AssignedAt, Role(`LEAD`/`MEMBER`) | Giao cho 1..n thợ máy |
| `pm.WorkOrderChecklistResult` | WorkOrderId, ChecklistItemId, ResultOkNg?, ResultNumber?, ResultText?, Note, At, ByEmployeeId | |
| `pm.WorkOrderPartPlan` | WorkOrderId, SparePartId, PlannedQty | Sinh từ `MaintTaskPart` |
| `pm.WorkOrderPartUsage` | WorkOrderId, SparePartId, QtyUsed, QtyReturned, IssueNoteLineId?, Note | Thực tế dùng (đối chiếu xuất kho) |
| `pm.RepairRequest` | Id, Code(`SC-yyyymmdd-nnn`), MachineId, ComponentId?, Severity(`MINOR`/`MAJOR`/`CRITICAL`), Symptom, ReportedBy, ReportedAt (=thời điểm phát hiện), SourceStopEventId?, AcceptedBy, AcceptedAt, ApprovedBy, ApprovedAt, RepairStartAt, RepairEndAt, CauseCode, ActionTaken, IsBreakdown bit, Status(`REPORTED`/`ACCEPTED`/`PARTS_REQUESTED`/`APPROVED`/`IN_REPAIR`/`DONE`/`CANCELLED`), RecordState | Sửa chữa phát sinh ngoài kế hoạch |
| `pm.RepairPartRequest` | RepairId, SparePartId, Qty, Status, IssueRequestId? | |
| `pm.MachineMeter` | MachineId, TotalRunMinutes, UpdatedAt | Đồng hồ giờ chạy (từ PR) |
| `pm.TaskLastDone` | MachineId, TaskTemplateId, LastDoneAt, LastDoneRunMinutes | Phục vụ gợi ý chu kỳ |
| `pm.ReliabilityStat` | MachineId, PeriodKey, FailureCount, TotalDowntimeMin, TotalRunMin, Mttr, Mtbf | Tính định kỳ & khi đóng sửa chữa |

### 11.2 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-PM-001 | Danh mục công việc bảo trì chuẩn | Dùng danh mục `md.MaintTaskTemplate` (Protos, Max8/Mark8, Max9, Decoufle; AMF, GD, HLP, Focke); màn hình tra cứu/chỉnh trong PM cho `MNT_TECH` | P0 |
| FR-PM-002 | Lập kế hoạch bảo trì tháng | Nút "Gợi ý kế hoạch": với mỗi (máy × công việc) tính `NextDue = LastDone + Cycle` (ngày) hoặc theo giờ chạy; xếp vào lịch tháng theo ngày, cân tải theo năng lực (số thợ × phút/ca), tránh trùng lịch SX quan trọng; hiển thị lịch tuần/ngày; **kéo-thả/hoán đổi**; phê duyệt Quản đốc/Phó QĐ | P0 |
| FR-PM-003 | Cảnh báo kế hoạch | Công việc quan trọng có nguy cơ bị bỏ sót trong tháng (`PM_WEEK_AT_RISK`); khối lượng tuần vượt năng lực | P1 |
| FR-PM-004 | Chuẩn bị vật tư D-1 | Job tổng hợp vật tư của công việc ngày mai → tự tạo `sp.IssueRequest` (nguồn `PM_PLAN`, trạng thái đã duyệt theo kế hoạch) gửi Thủ kho; Thủ kho xác nhận chuẩn bị/xuất | P0 |
| FR-PM-005 | Phân công | Đầu ca bảo trì (⚠ 07:00): Tổ trưởng MMTB/Trưởng ca xem danh sách công việc ngày, phân công theo kỹ năng (cơ/điện), theo dõi trạng thái realtime, cảnh báo công việc trễ | P0 |
| FR-PM-006 | Thực hiện & ghi nhận trên tablet | Thợ máy: nhận việc, `Bắt đầu` → làm checklist (OK/NG/số), ghi vật tư thực dùng (đối chiếu phiếu xuất), ghi chú phát sinh, chụp ảnh, `Hoàn thành`/`Chưa hoàn thành`/`Phát sinh`; giao diện tối giản, thao tác tối thiểu; offline | P0 |
| FR-PM-007 | Dừng máy do bảo trì | Khi bắt đầu công việc cần dừng máy: đề xuất tạo sự kiện dừng mã `B` (vệ sinh bảo trì) để vận hành xác nhận; kết thúc công việc → đề xuất đóng | P1 |
| FR-PM-008 | Báo sự cố & yêu cầu sửa chữa | Công nhân vận hành "Báo sự cố" (chọn máy/cụm, mô tả, mức độ, ảnh) hoặc từ sự kiện dừng `C`; Thợ máy **tiếp nhận** → tạo/hoàn thiện phiếu yêu cầu sửa chữa, đề xuất vật tư; Tổ trưởng/Trưởng ca **phê duyệt trên hệ thống (SLA 15')**; hệ thống tự thông báo kho phụ tùng chuẩn bị; sau sửa thợ máy cập nhật kết quả, nguyên nhân, thời gian khắc phục | P0 |
| FR-PM-009 | Hoán đổi/điều chỉnh lịch | Hoán đổi công việc giữa các ngày trong tuần, bắt buộc lý do; cảnh báo tuần có nguy cơ không hoàn thành | P0 |
| FR-PM-010 | Tính MTTR & MTBF | Tự động theo máy/dòng máy/PX, ca/tháng; hiển thị xu hướng; cảnh báo vượt ngưỡng | P0 |
| FR-PM-011 | Lịch sử bảo trì theo máy | Lưu toàn bộ: ngày, việc đã làm, vật tư, thợ máy, kết quả; tra cứu theo máy/loại việc/khoảng thời gian/thợ máy; xuất báo cáo | P0 |
| FR-PM-012 | Báo cáo bảo trì định kỳ | Tháng: % hoàn thành KH, việc trễ/bỏ qua, vật tư dùng, chỉ số MTTR/MTBF, (P2: chi phí khi có đơn giá) | P1 |
| FR-PM-013 | Phát hiện hư hỏng lặp lại | Thống kê sự cố/phụ tùng thay theo máy–cụm; cảnh báo `OH_PART_REPEAT` | P1 |
| FR-PM-014 | Kích hoạt theo giờ vận hành | Công việc chu kỳ `OP_HOURS` nhận `MachineMeter` từ PR để đến hạn | P1 |
| FR-PM-015 | Theo dõi sửa chữa MMTB (thợ điện/cơ) | Màn hình riêng cho thợ sửa chữa: danh sách phiếu được giao, trạng thái, thời gian | P1 |

### 11.3 Quy tắc nghiệp vụ
- **BR-PM-001** Không tạo công việc cho máy `DECOMMISSIONED`; máy `INACTIVE` chỉ khi có lý do.
- **BR-PM-002** Thuật toán gợi ý: (a) công việc `CYCLE_DATE`: due = `LastDoneAt + cycle` (nếu chưa có lịch sử → theo `InstallDate`/mốc khởi tạo do người dùng nhập); (b) `OP_HOURS`: due khi `TotalRunMinutes − LastDoneRunMinutes ≥ cycle×60`; (c) ưu tiên `CRITICAL`; (d) tối đa N công việc/ngày/thợ (năng lực); (e) tránh ca máy đang chạy lệnh ưu tiên cao nếu có thể (gợi ý, không chặn).
- **BR-PM-003** Công việc `PLANNED` quá ngày kế hoạch mà chưa hoàn thành → `OVERDUE` (job 06:00) + cảnh báo.
- **BR-PM-004** `DONE` yêu cầu: checklist bắt buộc (`IsMandatory`) đã trả lời; ít nhất người thực hiện; vật tư dùng đã khai (hoặc xác nhận "không dùng"). Sau `DONE` + chốt: không sửa trực tiếp.
- **BR-PM-005** Vật tư thực dùng ≤ vật tư đã xuất (cho phép trả lại kho `QtyReturned` → phát sinh `RETURN` ở SP); dùng nhiều hơn đã xuất → yêu cầu xuất bổ sung (luồng phát sinh).
- **BR-PM-006** Yêu cầu sửa chữa: `ReportedAt` = thời điểm phát hiện (cho phép sửa ≤ 30' có lý do nếu nhập sau); `RepairStartAt`, `RepairEndAt` thợ máy xác nhận. Phê duyệt bởi `MNT_LEAD`/Trưởng ca; vắng → `DEPUTY_DIRECTOR` (ủy quyền).
- **BR-PM-007** **MTTR** (mặc định) = trung bình (`RepairEndAt − ReportedAt`) của các sự cố `IsBreakdown=1` trong kỳ (tùy chọn cấu hình `MTTR_BASIS = REPORT_TO_END | START_TO_END`). **MTBF** = Tổng giờ chạy máy trong kỳ (từ PR `RunMinutes`) ÷ số sự cố `IsBreakdown=1` (kỳ không có sự cố → hiển thị `≥ tổng giờ chạy`).
- **BR-PM-008** `IsBreakdown` mặc định true khi `Severity ≥ MAJOR` hoặc máy dừng > ngưỡng (cấu hình); người duyệt có thể đổi.
- **BR-PM-009** Phụ tùng cho sửa chữa chỉ xuất khi phiếu `APPROVED` (liên kết SP).
- **BR-PM-010** Kế hoạch tháng đã duyệt chỉ điều chỉnh bằng hoán đổi/ghi lý do; thêm việc ngoài kế hoạch → `TriggerType=MANUAL` có audit.

### 11.4 Vòng đời
`WorkOrder`: PLANNED → ASSIGNED → IN_PROGRESS → DONE | PARTIAL (tạo bản kế tiếp) | SKIPPED(có lý do) ; PLANNED/ASSIGNED → OVERDUE (tự động, vẫn thực hiện được). `RepairRequest`: REPORTED → ACCEPTED → (PARTS_REQUESTED →) APPROVED → IN_REPAIR → DONE ; CANCELLED.

### 11.5 API (prefix `/api/v1/pm`)
| Method & Path | Mục đích |
|---|---|
| `GET/POST /plans` · `POST /plans/suggest?year=&month=` · `POST /plans/{id}/submit` · `/approve` · `/revise` | KH tháng |
| `GET /calendar?from=&to=&machineId=` · `POST /work-orders/{id}/reschedule` · `POST /work-orders/swap` | Lịch & hoán đổi |
| `GET /work-orders?date=&status=&assignee=&machineId=` · `GET /work-orders/{id}` | Truy vấn |
| `POST /work-orders` (manual) · `POST /work-orders/{id}/assign` · `/start` · `/checklist` · `/parts-usage` · `/complete` · `/skip` | Thực hiện |
| `POST /repair-requests` · `POST /repair-requests/{id}/accept` · `/parts` · `/approve` · `/start` · `/complete` · `/cancel` | Sự cố & sửa chữa |
| `GET /history?machineId=&from=&to=&type=` | Lịch sử bảo trì |
| `GET /reliability?machineId=&model=&from=&to=` | MTTR/MTBF |
| `GET /reports/monthly?year=&month=` | Báo cáo tháng |

### 11.6 Phân quyền
`MNT_TECH`: lập KH, danh mục, phân tích. `MNT_LEAD`: phân công, duyệt sửa chữa, hoán đổi. `MECHANIC`: thực hiện, tạo phiếu sửa chữa. `OPERATOR`: báo sự cố. `WAREHOUSE_KEEPER`: nhận yêu cầu vật tư. `DEPUTY_DIRECTOR`/`DIRECTOR`: duyệt KH tháng, dashboard.

### 11.7 Tiêu chí nghiệm thu
- **AC-PM-001** Gợi ý KH tháng cho 10 máy × 6 công việc tạo đúng ngày theo chu kỳ & lần làm gần nhất; kéo một việc sang ngày khác bắt nhập lý do; tuần quá tải cảnh báo.
- **AC-PM-002** 15:00 ngày D-1 hệ thống tạo yêu cầu xuất kho tổng hợp vật tư đúng số lượng theo `MaintTaskPart`; Thủ kho nhận thông báo; phiếu liên kết tới các công việc.
- **AC-PM-003** Thợ máy hoàn thành công việc trên tablet offline, đồng bộ sau; công việc chuyển `DONE`, lịch sử máy cập nhật, `TaskLastDone` cập nhật.
- **AC-PM-004** Công nhân báo sự cố → thợ máy tiếp nhận ≤ 2 thao tác; phiếu chờ duyệt > 15' → `PM_REPAIR_PENDING` đến Tổ trưởng rồi Phó QĐ; sau duyệt, kho nhận thông báo tự động.
- **AC-PM-005** 3 sự cố trong tháng: thời gian sửa (60', 90', 30') và giờ chạy 600h → MTTR=60', MTBF=200h; trùng kết quả tính tay.
- **AC-PM-006** Tìm lịch sử bảo trì máy X 12 tháng ≤ 2 giây, hiển thị vật tư và thợ máy từng lần.

---

## 12. PHÂN HỆ OH – TRUNG TU & ĐẠI TU MÁY MÓC THIẾT BỊ (TDT)  *(báo cáo mục 2.9)*

### 12.1 Bối cảnh
TDT gồm 3 hình thức: **BT** (bảo trì) · **TT** (trung tu) · **ĐT** (đại tu). Mỗi hồ sơ gắn **số định khoản/mã tham chiếu nội bộ**; nhiều đơn vị tham gia: Phòng KTCĐ (điều phối), PXVB/Tổ cơ khí (phần cơ), PXCĐ (phần điện, gia công cơ khí), Phòng KHVT (cấp vật tư), Kho CK (xuất phụ tùng), Phòng ĐBCL (xác nhận chất lượng sau TDT), Phòng TCKT (khai thác bảng tổng hợp), Ban Giám đốc (duyệt KH năm / quyết toán).

### 12.2 Mô hình dữ liệu (nhóm bảng `oh`, vật lý `oh_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `oh.YearPlan` | Id, Year, Revision, Status(`DRAFT`/`SUBMITTED`/`APPROVED`/`SUPERSEDED`), SubmittedBy, ApprovedBy, ApprovedAt | KH TDT năm (PXVB đề xuất từ Q4 năm trước → KTCĐ lập → BGĐ duyệt) |
| `oh.YearPlanItem` | PlanId, MachineId, Type(`BT`/`TT`/`DT`), PlannedQuarter, PlannedStart?, PlannedEnd?, ProposedBy(unit), LastOverhaulDate?, LastOverhaulType?, Status(`PLANNED`/`DOSSIER_CREATED`/`IN_PROGRESS`/`DONE`/`DEFERRED`/`CANCELLED`), DeferReason | Hiển thị lịch sử TDT lần trước (tính từ `oh.Dossier`) |
| `oh.Dossier` | Id, Code(`TDT-yyyy-nnn`), AccountRefNo (**số định khoản / mã tham chiếu**, unique), YearPlanItemId?, MachineId, Type, Status(`DRAFT`/`ASSESSING`/`PARTS_PLANNING`/`PARTS_APPROVED`/`IN_PROGRESS`/`ACCEPTANCE`/`CLOSED`/`CANCELLED`), PlannedStart, PlannedEnd, ActualStart, ActualEnd, NotifiedUnits(json), CreatedBy, ClosedBy, ClosedAt | |
| `oh.Assessment` | Id, DossierId, Discipline(`MECH`/`ELEC`), FormTemplateId, Answers(json), Status(`DRAFT`/`SIGNING`/`SIGNED`), AssessedAt | Biên bản đánh giá tình trạng trước TDT (tách cơ/điện) |
| `oh.AssessmentItem` | AssessmentId, ComponentId, ConditionGrade(`GOOD`/`FAIR`/`POOR`/`FAILED`), Proposed(`REPLACE`/`RESTORE`/`KEEP`), Note | Từng cụm/bộ phận; ảnh qua `sys.Attachment` |
| `oh.PlannedPart` | DossierId, VersionNo, SparePartId, PlannedQty, Uom, FromAssessmentItemId?, ApprovalStatus(`DRAFT`/`APPROVED`), SupplyStatus(`NOT_SUPPLIED`/`PARTIAL`/`SUPPLIED`), SuppliedBy(KHVT), SuppliedAt | Danh mục phụ tùng dự kiến (phiên bản) |
| `oh.ActualPart` | DossierId, SparePartId, Qty, Source(`WAREHOUSE_ISSUE`/`RECORDED_USE`), IssueNoteLineId?, IsUnplanned bit, ExtraRequestId?, UsedAt | Tự sinh từ phiếu xuất kho có `LinkType=OH_DOSSIER`, hoặc ghi nhận sử dụng |
| `oh.ExtraPartRequest` | Id, DossierId, SparePartId, Qty, Reason, Status(`PENDING`/`APPROVED`/`REJECTED`), RequestedBy, DecidedBy(KTCĐ), DecidedAt | Phụ tùng ngoài danh mục dự kiến |
| `oh.ProgressItem` | DossierId, Name, Discipline, ExecutorUnit(`PXVB`/`PXCD`), PlannedDate, ActualDate?, PercentDone, Status(`NOT_STARTED`/`DOING`/`DONE`/`BLOCKED`), Note | Mốc/hạng mục thực hiện |
| `oh.Acceptance` | Id, DossierId, FormTemplateId, Answers(json: số liệu kỹ thuật, kiểm tra chất lượng SP sau TDT), Status(`DRAFT`/`SIGNING`/`SIGNED`), RequiredParties(json) | Biên bản nghiệm thu |
| `oh.OldPartEvaluation` | DossierId, SparePartId?, Description, Qty, Result(`DAMAGED`/`RESTORABLE`), Disposition(`LIQUIDATE`/`REUSE`/`RESTORE`), EvaluatedBy, HandedToWarehouseAt | Đánh giá phụ tùng cũ tháo ra |
| `oh.FabricationItem` | DossierId, Description, Qty, Status, Owner(PXCD) | P2: gia công chi tiết cơ khí |

### 12.3 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-OH-001 | KH TDT năm | PXVB gửi danh mục máy đề xuất (Q4); KTCĐ lập KH: máy, hình thức BT/TT/ĐT, quý, lịch sử TDT lần trước; điều chỉnh linh hoạt; BGĐ phê duyệt; theo dõi **tiến độ tích lũy vs KH năm**, cảnh báo sớm | P0 |
| FR-OH-002 | Tạo & quản lý hồ sơ TDT | Khi đến hạn, KTCĐ tạo hồ sơ cho từng máy với **số định khoản** (unique); tự liên kết thông tin máy + KH năm; **thông báo tự động** PXVB, PXCĐ, Kho CK, KHVT; theo dõi trạng thái | P0 |
| FR-OH-003 | Biên bản đánh giá tình trạng trước TDT | Form điện tử đúng cấu trúc hiện hành, **tách cơ khí/điện**; đánh giá từng cụm/bộ phận; đề xuất xử lý (thay mới/phục hồi/giữ nguyên); đính kèm ảnh; **xác nhận điện tử** của các bên (KTCĐ, PXVB, PXCĐ) | P0 |
| FR-OH-004 | Danh mục phụ tùng dự kiến | Từ đánh giá → lập danh sách (mã, tên, ĐVT, SL dự kiến) tra cứu thẻ kho tự động (hiển thị tồn); trình duyệt (KTCĐ); quản lý phiên bản; KHVT cập nhật trạng thái cung cấp vật tư & báo PXVB/PXCĐ đến lãnh | P0 |
| FR-OH-005 | Theo dõi tiến độ TDT | Hạng mục/mốc theo đơn vị thực hiện, % hoàn thành, Gantt đơn giản; cảnh báo trễ `OH_DELAYED` | P0 |
| FR-OH-006 | Xuất phụ tùng theo định khoản | Liên thông SP: mọi phụ tùng xuất cho hồ sơ được ghi tự động vào `ActualPart` (mã, tên, SL, ĐVT, thời điểm, phiếu xuất); phụ tùng ngoài dự kiến → `ExtraPartRequest` → KTCĐ duyệt (`OH_EXTRA_PART_PENDING`) | P0 |
| FR-OH-007 | Đối chiếu dự kiến–thực tế | Theo chủng loại/mã/số lượng: `MATCH`/`OVER`/`UNDER`/`UNPLANNED`/`UNUSED`; chỉ số độ chính xác lập kế hoạch | P0 |
| FR-OH-008 | Biên bản nghiệm thu sau TDT | Form điện tử: thông tin thiết bị, thời gian, số liệu nghiệm thu kỹ thuật, **kết quả kiểm tra chất lượng SP sau TDT** (liên kết QC: chạy thử + lượt kiểm tra); **xác nhận điện tử đa bên** (PXVB, PXCĐ, KTCĐ, ĐBCL) | P0 |
| FR-OH-009 | Đánh giá phụ tùng cũ | Ghi nhận phụ tùng tháo ra (hư hỏng/phục hồi được), liên kết hồ sơ; cập nhật sang kho phụ tùng để thanh lý/tái sử dụng | P1 |
| FR-OH-010 | Đóng hồ sơ | KTCĐ đóng khi đủ điều kiện (BR-OH-005) → khóa hồ sơ; cập nhật `Machine` lịch sử TDT & trạng thái máy | P0 |
| FR-OH-011 | Tổng hợp vật tư/phụ tùng sử dụng | Bảng tổng hợp theo hồ sơ (mã, tên, SL, ĐVT, phiếu xuất, thiết bị/hạng mục, định khoản, thời gian); TCKT xuất Excel; (cũng áp dụng cho bảo trì định kỳ) | P0 |
| FR-OH-012 | Lịch sử & thống kê TDT | Theo máy/dòng máy/năm; tần suất thay phụ tùng, mức tiêu hao; hạng mục lặp lại; `OH_PART_REPEAT` | P1 |
| FR-OH-013 | Gia công cơ khí (PXCĐ) | Danh sách chi tiết cần gia công & trạng thái | P2 |
| FR-OH-014 | Duyệt quyết toán (BGĐ) | BGĐ duyệt hồ sơ hoàn thành (xác nhận điện tử); hệ thống chỉ cung cấp dữ liệu tổng hợp, không xử lý tài chính | P2 |

### 12.4 Quy tắc nghiệp vụ
- **BR-OH-001** `AccountRefNo` duy nhất; mọi xuất kho TDT bắt buộc gắn `DossierId` (SP chặn nếu thiếu).
- **BR-OH-002** Hồ sơ chỉ tạo cho máy có `YearPlanItem` đã duyệt, hoặc ngoại lệ do KTCĐ + lý do (audit).
- **BR-OH-003** Trình tự trạng thái bắt buộc: `DRAFT → ASSESSING → PARTS_PLANNING → PARTS_APPROVED → IN_PROGRESS → ACCEPTANCE → CLOSED`; không bỏ bước (trừ khi `Type=BT` cấu hình rút gọn).
- **BR-OH-004** Biên bản đánh giá/nghiệm thu chỉ `SIGNED` khi đủ các bên bắt buộc đã xác nhận; sửa sau ký → mất chữ ký, ký lại (hash mismatch).
- **BR-OH-005** Điều kiện đóng hồ sơ: nghiệm thu `SIGNED` đủ bên; không còn `ExtraPartRequest` `PENDING`; mọi `ActualPart` có phiếu xuất hợp lệ; phụ tùng cũ đã đánh giá (hoặc đánh dấu N/A); không còn `ProgressItem` `BLOCKED`.
- **BR-OH-006** Đối chiếu: `OVER` = thực tế > dự kiến; `UNPLANNED` = không có trong danh mục dự kiến; chỉ `UNPLANNED` bắt buộc `ExtraPartRequest`.
- **BR-OH-007** Cảnh báo "phụ tùng thay lặp lại": cùng `SparePartId` (hoặc cụm) trên cùng máy ≥ `OH_REPEAT_COUNT` lần trong `OH_REPEAT_MONTHS` (PM + OH).
- **BR-OH-008** KH năm: `PlannedQuarter` điều chỉnh được; lưu lịch sử điều chỉnh & lý do; cảnh báo `OH_YEAR_AT_RISK` khi tiến độ lũy kế < kế hoạch tương ứng quý hiện tại.

### 12.5 API (prefix `/api/v1/oh`)
| Method & Path | Mục đích |
|---|---|
| `GET/POST /year-plans` · `PUT /year-plans/{id}/items` · `POST /year-plans/{id}/submit` · `/approve` · `/revise` · `GET /year-plans/{id}/progress` | KH năm |
| `GET/POST /dossiers` · `GET /dossiers/{id}` · `POST /dossiers/{id}/advance` · `POST /dossiers/{id}/close` · `POST /dossiers/{id}/cancel` | Hồ sơ |
| `GET/PUT /dossiers/{id}/assessments/{discipline}` · `POST …/submit-sign` · `POST …/sign` | Đánh giá & ký |
| `GET/PUT /dossiers/{id}/planned-parts` · `POST …/approve` · `POST …/supply-status` | Phụ tùng dự kiến |
| `GET /dossiers/{id}/actual-parts` · `GET /dossiers/{id}/parts-comparison` | Thực tế & đối chiếu |
| `POST /dossiers/{id}/extra-part-requests` · `POST /extra-part-requests/{id}/approve|reject` | Phụ tùng phát sinh |
| `GET/POST /dossiers/{id}/progress` | Tiến độ |
| `GET/PUT /dossiers/{id}/acceptance` · `POST …/sign` | Nghiệm thu |
| `POST /dossiers/{id}/old-parts` | Phụ tùng cũ |
| `GET /reports/material-summary?dossierId=|from=&to=` | Bảng tổng hợp vật tư (Excel) |
| `GET /history?machineId=` · `GET /analysis/repeat-parts?...` | Lịch sử/thống kê |

### 12.6 Phân quyền
`KTCD`: tạo/lập KH, hồ sơ, duyệt phụ tùng, đóng hồ sơ. `BOARD`: duyệt KH năm/quyết toán. Đơn vị thực hiện (`MNT_LEAD`/PXVB cơ khí, `PXCD`): đánh giá, tiến độ, nghiệm thu phần mình. `KHVT`: cập nhật cung cấp vật tư. `QA_DEPT`: ký kết quả chất lượng. `WAREHOUSE_KEEPER`: xuất kho/nhận phụ tùng cũ. `TCKT`: xem/xuất tổng hợp.

### 12.7 Tiêu chí nghiệm thu
- **AC-OH-001** Tạo hồ sơ TDT cho máy M-V05 (ĐT, Q3): `AccountRefNo` trùng → từ chối; thành công → PXVB, PXCĐ, Kho CK, KHVT nhận thông báo ≤ 1 phút.
- **AC-OH-002** Biên bản đánh giá cơ có 3 bên: chỉ khi cả 3 xác nhận mới `SIGNED`; sửa 1 ô sau đó → chữ ký bị vô hiệu, yêu cầu ký lại.
- **AC-OH-003** Kho xuất 5 loại phụ tùng cho hồ sơ; 1 loại không có trong dự kiến → tự tạo `ExtraPartRequest`; KTCĐ duyệt thì mới ghi `ActualPart` (xuất kho vẫn bị chặn đến khi duyệt).
- **AC-OH-004** Bảng đối chiếu hiển thị đúng `MATCH/OVER/UNDER/UNPLANNED/UNUSED` cho bộ dữ liệu test 20 dòng.
- **AC-OH-005** Không thể đóng hồ sơ khi còn yêu cầu phát sinh chờ duyệt; sau đóng, mọi chỉnh sửa trực tiếp bị 423.
- **AC-OH-006** Xuất bảng tổng hợp vật tư của 1 hồ sơ ra Excel đủ cột: mã VT, tên, SL, ĐVT, phiếu xuất, thiết bị/hạng mục, số định khoản, thời gian.

---

## 13. PHÂN HỆ SP – QUẢN LÝ KHO PHỤ TÙNG  *(báo cáo mục 2.10)*

### 13.1 Mô hình dữ liệu (nhóm bảng `sp`, vật lý `sp_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `sp.StockTxn` | Id, TxnNo, Type(`RECEIPT`/`ISSUE`/`RETURN`/`ADJUST_IN`/`ADJUST_OUT`/`OLD_PART_IN`/`SCRAP_OUT`/`OPENING`), SparePartId, Qty (dấu: +nhập/−xuất), TxnAt, DocRef, ByUserId, LinkType(`PM_WORK_ORDER`/`REPAIR`/`OH_DOSSIER`/`OTHER`), LinkId, MachineId?, ReasonCode?, Note, ReversalOfId? | **Sổ cái bất biến**; sửa bằng bút toán đảo |
| `sp.StockBalance` | SparePartId, OnHandQty, UpdatedAt | Cập nhật cùng transaction với `StockTxn` |
| `sp.GoodsReceipt` / `…Line` | ReceiptNo, SupplierId, DocumentNo, ReceivedAt, Status(`DRAFT`/`POSTED`/`CANCELLED`); Line: SparePartId, Qty, LotNo?, UnitCost? | Phiếu nhập |
| `sp.IssueRequest` / `…Line` | RequestNo, Source(`PM_PLAN`/`REPAIR`/`OH_DOSSIER`/`OTHER`), SourceId, MachineId?, RequestedBy, NeededBy, Reason, Status(`DRAFT`/`SUBMITTED`/`APPROVED`/`PARTIALLY_ISSUED`/`ISSUED`/`REJECTED`/`CANCELLED`), ApprovalInstanceId?; Line: SparePartId, RequestedQty, IssuedQty | Yêu cầu xuất kho |
| `sp.IssueNote` / `…Line` | NoteNo, RequestId, IssuedAt, IssuedBy, ReceiverEmployeeId, Lines(SparePartId, Qty, StockTxnId) | Phiếu xuất |
| `sp.PurchaseRequest` | Id, SparePartId, Qty, Status(`OPEN`/`ORDERED`/`RECEIVED`/`CANCELLED`), RequestedBy, ExpectedDate, Note | Theo dõi đề nghị mua/bổ sung (nhẹ) |
| `sp.UsageMonthly` | Month, SparePartId, MachineId, SourceType, Qty | Bảng tổng hợp phục vụ báo cáo |
| `sp.ErpSnapshot` / `…Line` | ImportJobId, AsOf, SparePartId/ErpCode, ErpQty | Đối chiếu ERP bằng file |

### 13.2 Chức năng
| ID | Chức năng | Mô tả | ƯT |
|---|---|---|---|
| FR-SP-001 | Danh mục phụ tùng theo thẻ kho | Dùng `md.SparePart`; tra cứu mã/tên/thẻ kho; màn hình "thẻ kho" (sổ cái giao dịch của 1 mã) | P0 |
| FR-SP-002 | Nhập kho | Tạo phiếu nhập (mã hàng, thẻ kho, SL, NCC, ngày, chứng từ); `POST` → ghi sổ cái, cập nhật tồn; đối chiếu `PurchaseRequest` và `IssueRequest` đang thiếu hàng → thông báo | P0 |
| FR-SP-003 | Tiếp nhận & xuất kho theo bảo trì định kỳ | Yêu cầu tự sinh từ PM (D-1); Thủ kho kiểm tồn, xác nhận chuẩn bị, xuất (một phần được); xuất liên kết `WorkOrder` | P0 |
| FR-SP-004 | Xuất kho theo TDT | Yêu cầu theo hồ sơ/định khoản; xuất liên kết `DossierId` → `oh.ActualPart` | P0 |
| FR-SP-005 | Xuất kho phát sinh | Yêu cầu ngoài kế hoạch (sửa chữa đột xuất…): lý do, người yêu cầu, người duyệt (Lãnh đạo PXVB/PXCĐ), liên kết phiếu sửa chữa/công việc | P0 |
| FR-SP-006 | Theo dõi tồn kho | Tồn theo mã/thẻ kho; danh sách dưới mức tối thiểu; tra cứu nhanh | P0 |
| FR-SP-007 | Cảnh báo tồn tối thiểu | `SP_STOCK_BELOW_MIN` cho Thủ kho & KTCĐ; danh sách rà soát/đề xuất mua; tự đóng khi vượt min | P0 |
| FR-SP-008 | Lịch sử nhập–xuất | Theo thời gian, máy, công việc/hồ sơ, loại giao dịch, chứng từ | P0 |
| FR-SP-009 | Báo cáo sử dụng phụ tùng | Tháng/năm/tùy chọn: xuất nhiều, theo máy, theo công việc bảo trì, sửa chữa đột xuất, hồ sơ TDT, tồn cuối kỳ; **đề xuất điều chỉnh min** theo tần suất dùng | P1 |
| FR-SP-010 | Trả hàng & nhận phụ tùng cũ | Nhận lại vật tư không dùng (`RETURN`); nhận phụ tùng cũ từ TDT (`OLD_PART_IN`) + hướng xử lý | P1 |
| FR-SP-011 | Kiểm kê & điều chỉnh tồn | Phiếu kiểm kê → chênh lệch → `ADJUST_*` qua phê duyệt | P1 |
| FR-SP-012 | Đối chiếu ERP/Fox | Import file tồn/phát sinh từ ERP; báo cáo chênh lệch theo mã | P1 |
| FR-SP-013 | Nhập tồn đầu kỳ | Import Excel `OPENING` lúc khởi tạo | P0 |

### 13.3 Quy tắc nghiệp vụ
- **BR-SP-001** *Không có yêu cầu → không xuất*: `StockTxn(Type=ISSUE)` chỉ tạo từ `IssueNote` thuộc `IssueRequest` ở trạng thái `APPROVED/PARTIALLY_ISSUED` và có `LinkType/LinkId` hợp lệ (công việc bảo trì, phiếu sửa chữa, hoặc hồ sơ TDT/định khoản).
- **BR-SP-002** Tồn không âm (`SP_ALLOW_NEGATIVE=false`): xuất > tồn → chỉ xuất phần có, phần còn lại ở `PARTIALLY_ISSUED` + gợi ý tạo `PurchaseRequest`.
- **BR-SP-003** Sổ cái bất biến; tồn = Σ `StockTxn.Qty`; `StockBalance` đối soát hằng đêm (job) – lệch → cảnh báo CRITICAL.
- **BR-SP-004** Cảnh báo tồn: so `OnHand` với `MinQty` hiệu lực khi có giao dịch + quét 1 giờ; khử trùng theo mã; hiển thị gợi ý số lượng đặt lại.
- **BR-SP-005** Luồng duyệt theo nguồn: `PM_PLAN` → coi như duyệt theo KH tháng; `REPAIR` → duyệt phiếu sửa chữa (Tổ trưởng/Trưởng ca); `OH_DOSSIER` dự kiến → duyệt danh mục phụ tùng (KTCĐ), ngoài dự kiến → KTCĐ duyệt bổ sung; `OTHER` → Lãnh đạo PXVB/PXCĐ.
- **BR-SP-006** Nhập kho: tự động kiểm tra `PurchaseRequest` mở & `IssueRequest` đang thiếu hàng cùng mã → thông báo đơn vị liên quan.
- **BR-SP-007** Đề xuất `MinQty` = `ceil( TB tiêu thụ tháng (12 tháng) × (LeadTimeDays/30) + tồn an toàn )` (công thức cấu hình); chỉ là đề xuất, cần duyệt (CC-12).
- **BR-SP-008** Đơn vị tính theo thẻ kho; quy đổi không hỗ trợ v1 (một ĐVT/mã).
- **BR-SP-009** SP là nguồn dữ liệu sử dụng phụ tùng của PXVB; không đồng bộ hai chiều với ERP/Fox ở v1 (đối chiếu qua file).

### 13.4 API (prefix `/api/v1/sp`)
| Method & Path | Mục đích |
|---|---|
| `GET /stock?partNo=&card=&belowMin=` · `GET /stock/{partId}/ledger` | Tồn & thẻ kho |
| `POST /receipts` · `POST /receipts/{id}/post` · `…/cancel` | Nhập kho |
| `GET/POST /issue-requests` · `POST /issue-requests/{id}/submit` · `/approve` · `/reject` · `/cancel` | Yêu cầu xuất |
| `POST /issue-requests/{id}/issue` (lines) | Xuất kho (tạo IssueNote + StockTxn) |
| `POST /returns` · `POST /old-parts` | Trả/nhận cũ |
| `POST /stocktakes` · `POST /stocktakes/{id}/approve` | Kiểm kê |
| `GET /purchase-requests` · `POST /purchase-requests` | Đề nghị mua |
| `GET /reports/usage?from=&to=&groupBy=` · `GET /reports/min-suggestion` | Báo cáo |
| `POST /erp-snapshots/import` · `GET /erp-snapshots/{id}/diff` | Đối chiếu |

### 13.5 Phân quyền
`WAREHOUSE_KEEPER`: nhập/xuất/kiểm kê/trả. `MNT_TECH`/lãnh đạo PXVB, PXCĐ: tạo yêu cầu, tra cứu tồn; lãnh đạo duyệt phát sinh. `KTCD`: xem tổng quan, rà soát cảnh báo, duyệt bổ sung TDT. `KHVT`: cập nhật cung cấp vật tư TDT, xem đề nghị mua. `TCKT`: xem/xuất bảng tổng hợp. `SYS_ADMIN`: cấu hình.

### 13.6 Tiêu chí nghiệm thu
- **AC-SP-001** Cố xuất kho 2 cái phụ tùng cho công việc không có yêu cầu duyệt → bị chặn `PXVB-SP-3001`.
- **AC-SP-002** Tồn 3, min 5 → cảnh báo; nhập thêm 10 → cảnh báo tự đóng; yêu cầu xuất bị thiếu hàng được thông báo có hàng.
- **AC-SP-003** Xuất 5 khi tồn 3 → xuất 3, yêu cầu `PARTIALLY_ISSUED` còn 2, đề xuất tạo đề nghị mua.
- **AC-SP-004** Thẻ kho của một mã hiển thị đủ giao dịch với liên kết ngược tới công việc bảo trì/hồ sơ TDT; tổng = tồn hiện tại.
- **AC-SP-005** Báo cáo sử dụng tháng đúng với tổng hợp tay từ sổ cái (test 200 giao dịch).

---

## 14. PHÂN HỆ RP – BÁO CÁO & DASHBOARD  *(báo cáo mục 2.11)*

### 14.1 Mô hình dữ liệu (nhóm bảng `rp`, vật lý `rp_*`)
| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `rp.FactShiftMachine` | ShiftSessionId, BusinessDate, ShiftCode, MachineId, ProductId, PlanQty, OutputQty, GoodQty, RunMin, PlannedMin, StopMinByReason(json), Availability, Performance, Quality, Oee, MaterialWastePct, DefectRatePct, StampWastePer1000, IsProvisional, UpdatedAt | Bảng sự kiện tổng hợp theo (ca × máy × SP); cập nhật bởi projection từ các sự kiện module; index phủ `(business_date, machine_id, shift_code)`; **phân vùng `RANGE COLUMNS(business_date)` theo tháng** (khóa phân vùng nằm trong PRIMARY KEY phức hợp); MySQL không có columnstore |
| `rp.FactShiftMaterial` | ShiftSessionId, MachineId, MaterialId, Issued, Consumed, Theoretical, WastePct | |
| `rp.FactShiftQuality` | ShiftSessionId, MachineId, ProductId, DefectCode, Count, Grade, Inspections, FailedInspections | |
| `rp.FactDailyKpi` / `FactWeeklyKpi` / `FactMonthlyKpi` | Grain keys + KPI chính (sản lượng, % KH, OEE, hao hụt NVL, lỗi, tem, MTTR/MTBF, hoàn thành bảo trì) | Tính từ Fact ca |
| `rp.ReportSnapshot` | Id, ReportCode, Grain(`SHIFT`/`DAY`/`WEEK`/`MONTH`/`CUSTOM`), PeriodKey, ShiftSessionId?, Revision, Status(`BUILDING`/`READY`/`FINAL`/`SUPERSEDED`), Payload(json), XlsxPath?, PdfPath?, GeneratedAt, DurationMs | Bất biến theo `Revision`; sửa đổi → revision mới |
| `rp.ReportDefinition` | Code, Name, Grain, Sections(json), TemplateXlsx, TemplatePdf, IsActive | |
| `rp.Subscription` | ReportCode, Grain, RecipientType(`USER`/`ROLE`/`EMAIL`), RecipientRef, Channel(`INAPP`/`EMAIL`), IsActive | Danh sách người nhận tự động |
| `rp.DashboardDef` | Code, Name, RoleCodes, Layout(json widgets), RefreshSec | |
| `rp.KpiBaseline` | KpiCode, Value, Source(`TLSG-QT-ĐBCL-01`/`MANUAL`), EffectiveFrom | KPI baseline (vd tỷ lệ lỗi) |

**Chiến lược dữ liệu**: nghiệp vụ ghi vào schema gốc → sự kiện (outbox) → `ProjectionWorker` cập nhật `rp.Fact*` (idempotent theo key) → dashboard/báo cáo **đọc từ Fact**, không quét bảng giao dịch (đảm bảo ≤ 5 phút & P95 < 3s). Lưu **≥ 3 năm** trực tuyến, chỉ các bảng `rp_fact_*` phân vùng theo tháng (MySQL yêu cầu cột phân vùng nằm trong mọi unique key nên bảng giao dịch dùng `BINARY(16)` PK **không** phân vùng; dùng index + archive); archive cũ hơn theo chính sách sao lưu.

### 14.2 Dashboard theo vai trò (nguồn: 2.11.c, d)
| Mã | Dashboard | Đối tượng | Nội dung (widget chính) | Làm mới |
|---|---|---|---|---|
| `DB_SHIFT_LEADER` | Dashboard Trưởng ca | `SHIFT_LEADER` | Lưới 77 máy (màu trạng thái chạy/dừng/chưa nhập/offline); sản lượng thực tế vs KH ca theo máy; dừng máy đang mở + thời lượng; cảnh báo đang mở (mọi phân hệ); lịch KSCL trễ; tiến độ giao nhận NVL theo khung giờ; tem hao hụt; trạng thái chốt ca (checklist) | Push ≤ 1', tối đa 5' |
| `DB_DEPUTY` | Dashboard Phó Quản đốc | `DEPUTY_DIRECTOR` | Tổng quan PX: sản lượng vs KH (ca/ngày/tuần), OEE theo máy & xếp hạng, Pareto dừng máy theo mã A–I, hao hụt NVL theo nhóm, tỷ lệ lỗi & cảnh báo A/B/C, hao hụt tem theo máy, bảo trì hôm nay, cảnh báo CRITICAL | ≤ 5' |
| `DB_DIRECTOR` | KPI Quản đốc/Ban lãnh đạo | `DIRECTOR`,`BOARD`,`LEADERSHIP` | KPI ngày + xu hướng tháng: % hoàn thành KH, OEE PX, hao hụt NVL, lỗi, hao hụt tem, MTTR/MTBF, hoàn thành bảo trì, tiến độ TDT năm, tồn phụ tùng dưới min | ≤ 5' (ngày), hằng ngày (tháng) |
| `DB_OEE` | Sản lượng & OEE | Phó QĐ, Quản đốc | Sản lượng lũy kế ca; xu hướng theo giờ; bảng xếp hạng OEE; phân tích dừng; so ca trước/KH | ≤ 5' |
| `DB_MATERIAL` | NVL | STAT_MATERIAL, Phó QĐ | Cấp phát/tiêu hao realtime; hao hụt theo nhóm/máy; NVL vượt ngưỡng; tranh chấp giao nhận | ≤ 5' |
| `DB_QUALITY` | Chất lượng | QC, ĐBCL, Phó QĐ | Tỷ lệ lỗi theo máy/loại; cảnh báo A/B/C & trạng thái; so ca trước & KPI; lượt kiểm tra trễ/bỏ lỡ | ≤ 5' |
| `DB_STAMP` | Tem thuế | STAT_STAMP, TCKT | Hao hụt/1.000 bao theo máy (xếp hạng); đối chiếu tem–bao; tồn tem | ≤ 5' |
| `DB_CMMS` | Bảo trì & kho | MNT_*, KTCĐ | Tiến độ bảo trì tháng, việc trễ, sự cố đang mở, MTTR/MTBF, tiến độ TDT, phụ tùng dưới min | ≤ 5' |
| `DB_WALL` | Màn hình phòng quản đốc | Kiosk | Bản rút gọn full-screen xoay vòng 4–5 trang (OEE, sản lượng, cảnh báo, chất lượng) | ≤ 5' |

- **Quy ước màu chuẩn hóa**: **Xanh lá** = bình thường · **Vàng** = cần chú ý · **Đỏ** = cần xử lý ngay; ngưỡng màu cấu hình từ `md.Threshold`; không chỉ dựa vào màu (kèm icon/nhãn). Hỗ trợ màn hình desktop và tablet.
- Cơ chế: WebSocket/STOMP đẩy cập nhật khi Fact đổi; client fallback poll 60s; hiển thị "cập nhật lúc hh:mm:ss" và cảnh báo khi dữ liệu cũ > 5 phút.

### 14.3 Danh mục báo cáo
| Mã | Báo cáo | Kỳ | Nội dung chính | Người nhận mặc định | Định dạng |
|---|---|---|---|---|---|
| `R_SHIFT` | Báo cáo ca | Ca | Sản lượng theo mác/máy vs KH; OEE từng máy (A/P/Q); dừng theo lý do; tiêu hao NVL (cấp/dùng/tồn/hao hụt); KSCL (lượt kiểm tra, đạt/không, A/B/C, chỉ tiêu vật lý, biểu đồ nguyên nhân lỗi); tem (đầu/xuất/hư/mất/cuối/hao hụt); sự cố & bảo trì trong ca; cảnh báo & cách xử lý; đối chiếu chéo; xác nhận các bên | Trưởng ca, Phó QĐ, Quản đốc | PDF, XLSX; **sẵn sàng ≤ 5' sau chốt ca** |
| `R_DAY` | Báo cáo ngày | Ngày (3 ca) | Tổng hợp 3 ca, so KH ngày, so ngày trước | Quản đốc, Phó QĐ, ban lãnh đạo liên quan (tự gửi) | PDF, XLSX |
| `R_WEEK` / `R_MONTH` | Báo cáo tuần/tháng | Tuần/Tháng | Chỉ số đầy đủ, so KH & kỳ trước, xu hướng | Quản đốc, Ban lãnh đạo | PDF, XLSX |
| `R_PLAN` | Thực hiện kế hoạch | Ca/ngày/tuần/tháng | % hoàn thành theo SP/máy/ca | Phó QĐ, Kế hoạch | XLSX |
| `R_OEE` | Phân tích OEE | Tùy chọn | Theo máy/dòng/ca/tháng; đóng góp A/P/Q; xếp hạng; nguyên nhân OEE thấp | Phó QĐ, Quản đốc | XLSX, PDF |
| `R_MATERIAL` | Phân tích tiêu hao NVL | Tùy chọn | Thực tế vs định mức theo NVL/máy/mác; xu hướng; NVL hao hụt bất thường cần rà soát định mức | STAT_MATERIAL, Phó QĐ | XLSX |
| `R_QUALITY` | Thống kê chất lượng | Ca/ngày/tháng | Tỷ lệ lỗi theo loại/máy/mác; xu hướng; so KPI baseline TLSG-QT-ĐBCL-01; **thống kê B21 tháng** | QA_HEAD, Quản đốc | XLSX, PDF |
| `R_STAMP` | Sử dụng tem thuế | Ca/ngày/tháng | Theo máy bao: xuất dùng, hư, mất, hao hụt; **theo định dạng cơ quan thuế & nội bộ** | TCKT, STAT_STAMP | XLSX/CSV/XML theo profile |
| `R_PM_MONTH` | Bảo trì định kỳ tháng | Tháng | % hoàn thành, trễ/bỏ qua, vật tư, MTTR/MTBF, (chi phí – P2) | MNT_LEAD, Quản đốc | XLSX, PDF |
| `R_OH_YEAR` | TDT năm | Năm/quý | Tiến độ vs KH, danh sách hồ sơ, chi phí vật tư (số lượng) | KTCĐ, BGĐ | XLSX |
| `R_OH_MATERIAL` | Tổng hợp vật tư–phụ tùng TDT/bảo trì | Hồ sơ/kỳ | Mã, tên, SL, ĐVT, phiếu xuất, thiết bị/hạng mục, định khoản, thời gian | TCKT | XLSX |
| `R_SP_USAGE` | Sử dụng phụ tùng | Tháng/năm | Xuất nhiều, theo máy/việc/hồ sơ, tồn cuối kỳ, đề xuất min | KTCĐ, Thủ kho | XLSX |
| `R_AUDIT` | Nhật ký thay đổi | Tùy chọn | Điều chỉnh sau khóa, người đề nghị/duyệt | Quản đốc, CNTT | XLSX |
| `R_DATAQ` | Chất lượng dữ liệu | Tùy chọn | Dữ liệu thiếu/sai masterdata, ca chưa chốt, thiết bị chưa sync | SYS_ADMIN | XLSX |

### 14.4 Chức năng
| ID | Mô tả | ƯT |
|---|---|---|
| FR-RP-001 | Dashboard phân cấp theo vai trò (§14.2), WebSocket/STOMP + fallback poll | P0 |
| FR-RP-002 | Tự động sinh báo cáo ca/ngày/tuần/tháng không cần thao tác tay; ≤ 5' sau khi kỳ kết thúc; so sánh KH & kỳ trước | P0 |
| FR-RP-003 | Xuất PDF & Excel định dạng chuyên nghiệp, sẵn trình bày họp (logo, tiêu đề, chữ ký, ngày giờ, người chốt); số liệu theo `vi-VN` | P0 |
| FR-RP-004 | Tự động gửi báo cáo theo danh sách người nhận cấu hình (in-app + email nội bộ) | P1 |
| FR-RP-005 | Báo cáo chuyên sâu OEE/NVL/chất lượng/tem (tham số: kỳ, máy, dòng máy, mác, ca) với bộ lọc & drill-down tới bản ghi gốc | P1 |
| FR-RP-006 | Phân tích xu hướng: biểu đồ đường/cột/Pareto/heatmap; so sánh kỳ; chọn khoảng thời gian | P1 |
| FR-RP-007 | Lưu trữ lịch sử ≥ 3 năm, truy vấn nhanh (P95 < 3s cho khoảng ≤ 1 tháng; < 30s cho báo cáo tổng hợp nhiều kỳ) | P0 |
| FR-RP-008 | Revision báo cáo: điều chỉnh sau khóa → báo cáo mới `Revision+1`, giữ bản cũ, đánh dấu khác biệt | P1 |
| FR-RP-009 | Tùy chỉnh dashboard (ẩn/hiện widget, lưu bố cục theo người dùng) | P2 |
| FR-RP-010 | Màn hình tường (kiosk) xoay vòng cho phòng quản đốc | P1 |
| FR-RP-011 | KPI "bao thành phẩm xuất kho không đạt < 0,03%/năm" | P2 |

### 14.5 API (prefix `/api/v1/rp`)
| Method & Path | Mục đích |
|---|---|
| `GET /dashboards/{code}` | Dữ liệu dashboard (đã lọc theo quyền) |
| `GET /dashboards/{code}/stream` (WebSocket/STOMP endpoint `/ws`, topic `/topic/dashboard/{code}`) | Cập nhật realtime |
| `GET /reports?code=&grain=&period=` · `GET /reports/{id}` · `GET /reports/{id}/download?format=pdf|xlsx` | Báo cáo đã sinh |
| `POST /reports/run` (code, params) | Sinh báo cáo theo yêu cầu (job nền) |
| `GET/POST /subscriptions` | Người nhận tự động |
| `GET /analysis/{type}?...` | Phân tích |

### 14.6 Tiêu chí nghiệm thu
- **AC-RP-001** Sau khi Trưởng ca chốt ca, `R_SHIFT` ở trạng thái `READY` với PDF+XLSX tải được ≤ 5 phút (đo 20 lần, P95).
- **AC-RP-002** Dashboard Phó QĐ hiển thị dữ liệu thay đổi trong ≤ 5 phút kể từ khi tablet đồng bộ bản ghi sản lượng mới (đo từ `ReceivedAt`).
- **AC-RP-003** Báo cáo tháng khớp (sai số 0) với tổng hợp độc lập từ bảng giao dịch trên bộ dữ liệu kiểm thử 30 ngày.
- **AC-RP-004** Màu dashboard tuân quy ước xanh/vàng/đỏ theo ngưỡng cấu hình; đổi ngưỡng → áp dụng sau ≤ 60 giây.
- **AC-RP-005** Báo cáo lịch sử 3 năm (dữ liệu giả lập 3 năm) truy vấn 1 tháng ≤ 3 giây (P95), 12 tháng ≤ 30 giây.

---

## 15. ỨNG DỤNG TABLET (ANDROID) – ĐẶC TẢ GIAO DIỆN & VẬN HÀNH

### 15.1 Nền tảng & nguyên tắc
- React Native 0.81 (TypeScript), Android 11+; **offline-first** (CC-07); lưu cục bộ SQLite có mã hóa (SQLCipher); khóa kiosk qua MDM; bản cài phân phối nội bộ (APK ký số qua MDM, cập nhật theo đợt, kiểm tra tương thích API version).
- Giao diện tiếng Việt; nút ≥ 56dp; font ≥ 18sp (nội dung), ≥ 24sp (số liệu); độ tương phản cao, chống chói; hỗ trợ găng tay (vùng chạm lớn, tránh thao tác kéo phức tạp); phản hồi rung/âm thanh; không yêu cầu gõ chữ nếu có thể chọn/quét.
- **Hồ sơ thiết bị** (`sys.Device.Position`) quyết định màn hình chủ: `VAN` (máy vấn), `BAO` (máy bao), `FIBER_STORE` (kho sợi), `MATERIAL_HANDOVER` (giao nhận vật tư), `QA` (ĐBCL), `MAINT` (thợ máy/bảo trì), `SUPERVISOR`, `LEADER`.
- **Đổi người dùng nhanh**: quét thẻ hoặc nhập mã NV + PIN; mỗi thao tác ghi `EnteredBy`; tự khóa sau 5' không dùng.
- Thanh trạng thái trên cùng: tên máy/ca/mác hiện hành · đồng hồ · **biểu tượng đồng bộ** (xanh = đã sync, vàng = đang chờ + số bản ghi, đỏ = lỗi/xung đột) · chuông cảnh báo (số chưa đọc).

### 15.2 Danh mục màn hình
| Mã | Màn hình | Thiết bị/vai trò | Thành phần & hành vi chính | Module | Offline |
|---|---|---|---|---|---|
| T-01 | Đăng nhập nhanh | Tất cả | Quét thẻ/nhập mã + PIN; danh sách "người dùng gần đây" | CC | ✔ (PIN hash đồng bộ) |
| T-02 | Trang chủ theo vị trí | Tất cả | Ô lớn theo chức năng; trạng thái ca/lệnh; số việc cần làm | CC | ✔ |
| T-03 | Chi tiết máy – ca hiện tại | VAN/BAO | Lệnh SX (mác, KH, đã làm, % ), trạng thái chạy/dừng, nút lớn: **Ghi sản lượng · DỪNG · Đổi mác · KSCL · Báo sự cố**; (BAO) thêm **Tem** | PR | ✔ |
| T-04 | Ghi sản lượng | VAN/BAO | Bàn phím số lớn; khoảng thời gian tự điền; hiển thị so sánh KH; xác nhận | PR | ✔ |
| T-05 | Dừng máy | VAN/BAO | 9 nút mã A–I màu/biểu tượng, đếm giờ; "CHẠY LẠI"; nhập bù giờ; tạo yêu cầu sửa chữa 1 chạm với mã C/D | PR/PM | ✔ |
| T-06 | Đổi mác | VAN/BAO/LEADER | Chọn SP mới (lọc theo máy), thời điểm; gợi ý dừng mã A | PR/PL | ✔ |
| T-07 | Tồn điếu (máy bao) | BAO | Hiển thị tồn tính toán; nhập hiệu chỉnh; cảnh báo | PR | ✔ |
| T-10 | Danh sách lượt KSCL | VAN/BAO | Các lượt đến hạn/trễ (màu), đếm ngược; thông báo đến giờ | QC | ✔ |
| T-11 | Form KSCL động | VAN/BAO/QA | Render theo schema; pass/fail ≤ 3 chạm; lưới lỗi; số; ảnh; lưu nháp; **nộp** → hiển thị kết quả cấp A/B/C ngay | QC | ✔ |
| T-12 | Cảnh báo | Tất cả | Danh sách, ACK, ghi nguyên nhân khi xử lý; leo thang đếm ngược (cấp A) | CC | ✔ (hiển thị khi online) |
| T-20 | Soạn hàng NVL theo khung giờ | FIBER_STORE/MATERIAL_HANDOVER | Chọn khung giờ → danh sách máy × NVL; +/−; quét mã; lý do khi chênh lệch; chốt khung | MT | ✔ (phiếu đã tải) |
| T-21 | Nhận NVL | VAN/BAO | Danh sách chờ nhận; xác nhận/sửa SL; nhập tồn còn lại; ký xác nhận (PIN) | MT | ✔ |
| T-22 | Kiểm đếm tồn & hoàn trả | VAN/BAO | Nhập tồn giữa/cuối ca; hoàn trả | MT | ✔ |
| T-30 | Tem – xuất dùng | BAO/STAT_STAMP | Nhập/quét dải đầu–cuối; số lượng tự tính; báo trùng dải | TX | ✔ (kiểm trùng cuối cùng ở server) |
| T-31 | Tem – hư/mất | BAO | Số lượng + lý do (nút) + seri tùy chọn | TX | ✔ |
| T-32 | Tem – chốt cuối ca | STAT_STAMP | Tồn cuối, tóm tắt, xác nhận | TX | ✘ (online) |
| T-40 | Việc bảo trì hôm nay | MAINT | Danh sách theo thợ máy; trạng thái; bắt đầu | PM | ✔ |
| T-41 | Thực hiện bảo trì | MAINT | Checklist (OK/NG/số), vật tư dùng, ghi chú, ảnh, hoàn thành | PM | ✔ |
| T-42 | Báo sự cố / Tiếp nhận & phiếu sửa chữa | VAN/BAO/MAINT | Chọn máy/cụm, mô tả, ảnh; thợ máy tiếp nhận, đề xuất vật tư; Tổ trưởng duyệt | PM | ✔ (duyệt cần online) |
| T-50 | ĐBCL – lịch & form B11/B15/B12/B14/B21 | QA | Lịch ~180'; form động; nhập số đo | QC | ✔ |
| T-60 | Mở/Đóng ca (Trưởng ca) | LEADER | Checklist trước khi mở; checklist chốt ca (CC-11) với ✅/⚠/⛔; xác nhận đa bên | PR/CC | ✘ (online) |
| T-61 | Xác nhận lệnh SX & bố trí | LEADER | Xem/chỉnh/xác nhận lệnh; sao chép bố trí ca trước | PL | ✘ |
| T-62 | Xử lý xung đột đồng bộ | LEADER | Danh sách xung đột, so sánh, quyết định | CC | ✘ |
| T-70 | Trạng thái đồng bộ & thiết bị | Tất cả | Hàng đợi, lần sync cuối, thử lại, xuất log | CC | ✔ |

### 15.3 Hành vi chung
- Tự lưu nháp mỗi thay đổi trường; mất điện → mở lại khôi phục đúng màn hình (FR-CC-065).
- Xác nhận có thể hoàn tác trong 10 giây (snackbar "Hoàn tác") trước khi đẩy `SUBMITTED`.
- Chống nhập sai: giới hạn min/max theo máy; cảnh báo xác nhận khi ngoài biên; không cho lưu giá trị âm/rỗng bắt buộc.
- Pin: cảnh báo < 20%; hỗ trợ đang sạc khi dùng; chế độ tiết kiệm không tắt đồng bộ nền.
- Nhật ký lỗi cục bộ (xoay vòng 7 ngày) có thể gửi lên server khi có mạng.
- Bảo mật: không lưu mật khẩu; PIN hash; token trong Keystore; chặn chụp màn hình (cấu hình); xóa dữ liệu cục bộ đã đồng bộ sau 72h.

---

## 16. YÊU CẦU PHI CHỨC NĂNG (NFR), BẢO MẬT & CHẤT LƯỢNG  *(mục 2.12, 3, 5, 6, 7.5)*

### 16.1 Hiệu năng & dung lượng
| ID | Yêu cầu | Chỉ tiêu đo |
|---|---|---|
| NFR-001 | Thời gian phản hồi trung bình < 3 giây; tối đa < 30 giây (kể cả báo cáo/thống kê nhiều kỳ) | P95 < 3s cho API nghiệp vụ; báo cáo nặng < 30s (k6) |
| NFR-002 | CPU TB máy chủ ứng dụng & CSDL ≤ 75% | Giám sát 24h tải kiểm thử |
| NFR-003 | ≥ **500 truy cập đồng thời**, ≥ **200 người dùng hoạt động đồng thời** trong giờ cao điểm (đầu/cuối ca) | Kịch bản k6 mô phỏng đổi ca: 94 tablet + 100 web |
| NFR-004 | Dashboard cập nhật ≤ 5 phút; cảnh báo ≤ 5 phút | Đo end-to-end `ReceivedAt → hiển thị` |
| NFR-005 | Quy mô dữ liệu: 77 máy × 3 ca; ~1.000 entry sản lượng/ngày; ~4.000–15.000 lượt/dòng kiểm tra chất lượng/ngày; 3,2 triệu tem/ngày (dạng dải); hàng chục nghìn phụ tùng/giao dịch; lưu ≥ 3 năm online (ước tính vài chục GB/năm) | Thiết kế index/partition; test dữ liệu 3 năm |
| NFR-006 | Tablet: thao tác nhập thông thường phản hồi UI < 200ms (ghi cục bộ) | Đo trên thiết bị đích |

### 16.2 Tin cậy, sẵn sàng, phục hồi
- **99,5%/năm** (< 1 giờ/tháng ngoài bảo trì kế hoạch); chịu lỗi 1 máy chủ vật lý (active–backup); **phục hồi ≤ 3 giờ**; khoảng cách sự cố ≥ 6 tháng. RPO mục tiêu ≤ 15 phút (sao chép binlog liên tục/15').
- Không treo hệ thống khi lỗi; thông báo lỗi tiếng Việt, hệ thống tiếp tục hoạt động; không tràn số/vòng lặp vô hạn; kiểm tra dữ liệu đầu vào mọi nơi.
- Cài đặt toàn hệ thống ≤ 12 giờ, có hướng dẫn; script triển khai idempotent (Ansible/bash) và rollback.
- Kiểm thử phục hồi (restore drill) mỗi quý; tổng kiểm tra rà soát hệ thống hàng quý.

### 16.3 An toàn thông tin (ATTT cấp độ 2 – NĐ 85/2016, TT 12/2022, TCVN 11930:2017; vận hành thử theo Điều 10 TT 16/2024)
| Hạng mục | Yêu cầu triển khai |
|---|---|
| Truyền tải | HTTPS/TLS ≥ 1.2 (HSTS), không truyền tài khoản/mật khẩu không mã hóa; WebSocket qua WSS |
| Xác thực | Mục 4.1; khóa tài khoản; chính sách mật khẩu; phiên hết hạn; (tùy chọn) LDAP |
| Phân quyền | RBAC + phạm vi; kiểm tra phía server cho **mọi** endpoint (deny by default); kiểm thử phân quyền tự động |
| Mã hóa dữ liệu | Mật khẩu băm; bí mật cấu hình trong vault/biến môi trường; mã hóa nghỉ cho SQLite tablet; tùy chọn mã hóa nghỉ InnoDB (tablespace encryption + keyring) cho DB |
| Ứng dụng | OWASP ASVS L2: chống SQLi (tham số hóa/JPA/JPQL/jOOQ, cấm nối chuỗi SQL), XSS (CSP, escape), CSRF (nếu cookie), SSRF, upload (kiểm tra magic bytes, giới hạn kích thước, quét), IDOR (kiểm tra phạm vi sở hữu); rate limiting & chống brute-force; header bảo mật; phụ thuộc quét SCA định kỳ |
| Nhật ký & chống chối bỏ | Audit đầy đủ (4.3); đồng bộ giờ NTP; log tập trung; bảo vệ log khỏi sửa/xóa |
| Hạ tầng | Firewall; phân vùng mạng (VLAN) cho tablet/máy chủ; chỉ mở cổng cần thiết; WPA3 wifi; MDM chính sách thiết bị; phòng máy theo mục 3 |
| Sao lưu | Full + Incremental (+ binlog); lưu thiết bị độc lập; mã hóa bản sao lưu; thử khôi phục định kỳ; lưu dài hạn |
| Quản lý rủi ro/giám sát/ứng cứu | Tài liệu phương án ATTT 7 nội dung theo Điều 19 NĐ 85/2016 (thiết kế, vận hành, kiểm tra–đánh giá, rủi ro, giám sát, dự phòng–ứng cứu–khôi phục, kết thúc–thanh lý); kiểm thử xâm nhập trước nghiệm thu |
| Dữ liệu cá nhân | Chỉ lưu thông tin nhân sự cần thiết (mã, tên, chức danh); tuân thủ quy định bảo vệ dữ liệu cá nhân |

### 16.4 Tương thích & giao diện
- Web: Chrome/Edge/Firefox bản ổn định 2 phiên bản gần nhất; responsive ≥ 1366×768 và tablet 10"; tablet: Android ≥ 11 (đích Android 15 theo bảng công nghệ), wifi 802.11ac/ax. API RESTful chuẩn để tích hợp ERP sau.
- Ngôn ngữ UI 100% tiếng Việt; định dạng số/ngày `vi-VN` (dấu chấm nghìn, dấu phẩy thập phân, `dd/MM/yyyy HH:mm`); múi giờ Asia/Ho_Chi_Minh.
- Truy cập: tương phản ≥ 4.5:1, không chỉ dựa vào màu, thao tác bàn phím trên web.

### 16.5 Chiến lược kiểm thử
| Tầng | Công cụ | Phạm vi bắt buộc |
|---|---|---|
| Unit | JUnit 5 + AssertJ + Mockito; Jest/Vitest | Công thức OEE, tiêu hao NVL, tem, MTTR/MTBF, ghép cặp, ngưỡng, state machine — **golden dataset** do PO cung cấp (`tests/golden/*.json`) |
| Integration | Spring Boot Test + Testcontainers (MySQL 8, Redis, RabbitMQ) | Mỗi API: quyền, validate, concurrency, idempotency, audit |
| Contract | OpenAPI diff trong CI | Không phá API tablet đã phát hành (versioning) |
| E2E web | Playwright | Luồng: lập KH → xác nhận lệnh → mở ca → nhập → chốt ca → báo cáo |
| E2E tablet | Detox hoặc Maestro | Nhập sản lượng, dừng máy, KSCL ≤ 3 chạm, offline→sync, xung đột |
| Tải | k6 | Đổi ca 94 tablet + 100 web; 3,2 triệu tem/ngày; báo cáo 3 năm |
| Bảo mật | ZAP, Dependency-Check, Semgrep; kiểm thử phân quyền tự động | Trước mỗi release R1–R4 |
| Kiến trúc | Spring Modulith `verify()` + ArchUnit | Ranh giới module |
| UAT | Kịch bản do PXVB ký | Theo phân hệ + chạy song song 3 ca |

### 16.6 Quy trình phát triển
- Git trunk-based + PR bắt buộc review; CI (build, test, lint, SCA, OpenAPI check); CD ra môi trường `dev` → `test` → `uat` → `prod` (thủ công có phê duyệt). Conventional Commits có ID yêu cầu.
- **Definition of Done**: có code + migration + seed; unit/integration test đạt; quyền & audit đã áp; thông báo lỗi tiếng Việt; OpenAPI cập nhật; log/metrics; tài liệu người dùng (1 trang) cập nhật; đạt AC liên quan; không lỗi mức High còn mở.

---

## 17. ĐIỂM CẦN CHỐT VỚI NGHIỆP VỤ / GIẢ ĐỊNH ĐANG ÁP DỤNG

> Các mục dưới đây là chỗ báo cáo gốc **mâu thuẫn, thiếu dữ liệu hoặc chỉ mô tả mức khái quát**. Cột "Mặc định áp dụng" là giả định để Claude Code vẫn implement được (luôn ở dạng cấu hình, không hard-code). PO sẽ cập nhật bảng này khi có xác nhận.

| # | Vấn đề | Nguồn / mâu thuẫn | Mặc định áp dụng | Ảnh hưởng | Cần ai chốt |
|---|---|---|---|---|---|
| Q01 | Phiên bản & mô hình HA MySQL | Chủ đầu tư/PO chọn **MySQL 8** (thay SQL Server trong báo cáo gốc); MySQL 8.0 hết hỗ trợ 04/2026 | Viết SQL tương thích 8.0, **kiểm thử trên 8.0 và 8.4 LTS**; HA = 1 chính + 1 phụ semi-sync; backup XtraBackup + binlog; collation `utf8mb4_0900_ai_ci` (mã nghiệp vụ phân biệt hoa thường) | CSDL, vận hành, license (Community/Enterprise) | CNTT TLSG |
| Q02 | Số máy bao | 31 dây chuyền bao (tổng quan) vs 32 máy bao (tem) vs "26 IPC + 5 dây chuyền 2 tablet" | Số lượng lấy từ `md.Machine`; không hard-code | TX, tablet, báo cáo | PXVB |
| Q03 | Tablet trên 26 IPC hiện hữu | "Phần mềm cài trên IPC giống trên tablet" nhưng OS IPC chưa rõ | Chuẩn bị bản **PWA/web responsive** cho màn hình vận hành dùng chung API | Tablet app | CNTT/PXVB |
| Q04 | Giờ ca A/B/C, giờ 4 khung cấp phát | Chỉ biết ca bảo trì bắt đầu 7:00 | A 07–15, B 15–23, C 23–07; slot chia đều 25% | PL, MT, PR | PXVB |
| Q05 | Ánh xạ chữ cái A–I ↔ lý do dừng | Báo cáo liệt kê 9 lý do không kèm chữ | Theo thứ tự liệt kê (xem 5.5) | PR, OEE | PXVB |
| Q06 | Quy ước OEE (thời gian kế hoạch, mã dừng tính kế hoạch, nguồn Quality, cap Performance) | "Công thức cấu hình theo quy ước PXVB" | EXCLUDE_PLANNED_STOPS; `E`,`G` là dừng kế hoạch; Q từ NC; cap 100% | PR, RP | PXVB + Quản đốc |
| Q07 | Cách nhập sản lượng | 2.5: "nhập bao nhiêu lần trong ca"; mục 3: "cập nhật 2 giờ/lần"; đơn vị máy vấn (điếu) vs bao | Nhập theo khoảng, chu kỳ mặc định 120', tùy chọn đọc số đồng hồ; máy vấn: điếu, máy bao: bao | PR | PXVB |
| Q08 | Cách đo tiêu hao thực tế NVL | Báo cáo chỉ nói "tồn cuối ca tự tính" | Cần kiểm đếm tồn giữa/cuối ca (`MT_REQUIRE_COUNT`); nếu không, dùng so sánh cấp lũy kế vs định mức (BR-MT-004) | MT | PXVB + KHVT |
| Q09 | Nguồn dữ liệu lô NVL (`LotNo`) | QC cần truy ngược theo lô | Nhập/quét `LotNo` khi cấp phát (tùy chọn theo nhóm) | MT, QC | PXVB, ĐBCL |
| Q10 | Chi tiết 46 biểu mẫu, danh mục lỗi, ngưỡng A/B/C, tiêu chuẩn cơ sở chỉ tiêu vật lý | TLSG-QT-ĐBCL-01 (lần 10) | Form engine + import schema; 2 mẫu tham chiếu | QC | ĐBCL |
| Q11 | Quy cách tem: định dạng seri, số tem/bao, định dạng báo cáo thuế & nội bộ, tích hợp truy vết tem công ty | Chưa mô tả | Seri = prefix + số; 1 tem/bao; `ExportProfile` cấu hình; tích hợp qua file | TX | TCKT/PXVB |
| Q12 | Đơn giá phụ tùng / chi phí bảo trì | "Chi phí bảo trì" xuất hiện trong báo cáo | Chi phí chỉ hiển thị số lượng; đơn giá (nếu có) import từ ERP — P2 | PM, SP | TCKT |
| Q13 | Mức pháp lý của "xác nhận điện tử" | Dùng cho biên bản TDT/nghiệm thu | Xác nhận nội bộ (re-auth + hash); không phải chữ ký số | OH | Pháp chế/CNTT |
| Q14 | Phạm vi tích hợp ERP/Fox | v1: chỉ chuẩn bị API + đối chiếu file | Import/Export Excel; adapter `IErpGateway` rỗng | MT, SP | CNTT |
| Q15 | Vai trò phê duyệt có tên khác nhau | "Phó QĐ (II)", "(III)", "Tổ trưởng MMTB" vs "Trưởng ca" duyệt sửa chữa | Gộp thành vai trò `DEPUTY_DIRECTOR`, `MNT_LEAD`; luồng duyệt cấu hình được | Workflow | PXVB |
| Q16 | Tách React (dashboard) và Angular (admin) | Báo cáo chọn cả hai | Giữ như báo cáo; nếu tải nhân lực hạn chế có thể gộp dashboard vào Angular (ECharts) — quyết ở sprint 0 | Frontend | PO + Dev lead |
| Q17 | LDAP/AD | "LDAP v3 bắt buộc áp dụng" (tiêu chuẩn) | Mặc định tài khoản cục bộ; LDAP bật bằng cấu hình | Identity | CNTT |
| Q18 | Lưu trữ 20 năm vs 3 năm online | Bảng công nghệ: dự phòng ≥ 20 năm; RP: ≥ 3 năm truy vấn | 3 năm online + archive nén ≥ 20 năm | DB/backup | CNTT |
| Q19 | Giá trị ngưỡng mặc định (lệch KH, dừng dài, OEE thấp, hao hụt NVL, tồn điếu min/max…) | Chưa có số | Seed ở 5.5, nghiệp vụ điều chỉnh | Alert | PXVB |
| Q20 | Tên dòng máy "Mark8/Max8" | Mục 2.2 "Mark8", mục 2.8 "Max8" | Cho phép cả hai alias trong `Model` | MD, PM | PXVB |
| Q21 | Ai được "nhập hộ" dữ liệu | Giám sát nhập thay công nhân? | Cho phép `SUPERVISOR`, ghi `OnBehalfOf` | PR, QC | PXVB |
| Q22 | Kênh thông báo ngoài app | Email nội bộ? | In-app + email tùy chọn (SMTP nội bộ) | RP | CNTT |
| Q23 | Máy đo vật lý tự động | Dữ liệu đo "lưu trên máy tính" | Nhập tay + import file (P2) | QC | ĐBCL |
| Q24 | Khung "Compact/Demi/Slim/Super Slim" và công suất theo khổ | Công suất lý thuyết/máy | Công suất đặt theo máy; có thể mở rộng `MachineProductRate` | OEE | PXVB |
| Q25 | Phiên bản Java/Spring Boot, công cụ build | PO chỉ định Spring Boot | Java 21 LTS, Spring Boot 3.5.x, Maven (có thể đổi Gradle/Boot 4.x nếu CNTT yêu cầu) | Toàn bộ backend | CNTT TLSG / Dev lead |

---

## 18. KẾ HOẠCH TRIỂN KHAI CHO CLAUDE CODE

### 18.1 Bộ khung dự án (làm đầu tiên – Sprint 0)
1. Khởi tạo monorepo theo §2.4; CI (build/test/lint/OpenAPI-diff); `docker-compose.dev.yml` (MySQL 8.0/8.4, Redis, RabbitMQ, MailHog); build Maven wrapper (`./mvnw verify`).
2. `CLAUDE.md` ở thư mục gốc (xem 18.4) trỏ tới tài liệu này (`docs/PXVB-SRS-SDD.md`).
3. building-blocks: `BaseEntity<ID>`, `AuditableEntity`, `Auditable` (marker), `Result<T>` + `@RestControllerAdvice` map sang `ProblemDetail` (RFC 7807), `StateMachine<S,T>`, `Clock`, `CurrentUser`, `DeviceContext`, outbox + dispatcher (`@Scheduled`/Quartz, `SELECT … FOR UPDATE SKIP LOCKED`), idempotency filter (`OncePerRequestFilter`), `Specification` (Spring Data), `ScopeFilter`, pagination, Bean Validation, Logback JSON, OpenTelemetry, Actuator health.
4. Identity (CC-01..06), Audit (CC-04), Settings, Numbering.
5. Frontend shells: Angular admin (layout, auth, API client), React dashboard shell, RN tablet shell (auth, SQLite, sync client, theme).

### 18.2 Backlog theo epic và phát hành
| Epic | Nội dung | Phụ thuộc | Release | Cỡ |
|---|---|---|---|---|
| E00 | Scaffolding, CI/CD, môi trường | – | R0 | M |
| E01 | BuildingBlocks (audit, outbox, idempotency, state machine, lỗi) | E00 | R0 | L |
| E02 | Identity, RBAC, scope, device registry, PIN (CC-01..06) | E01 | R0 | L |
| E03 | Workflow phê duyệt + xác nhận điện tử (CC-05) + điều chỉnh sau khóa (CC-03) | E01,E02 | R0 | L |
| E04 | Alert engine + Notification + WebSocket/STOMP (CC-06) | E01 | R0 | L |
| E05 | Import/Export Excel engine (CC-08) | E01 | R0 | M |
| E06 | MD: máy, cụm, ghép cặp, ca, khung giờ, người dùng/nhân sự | E02,E05 | R0 | L |
| E07 | MD: SP, NVL, định mức (phiên bản), ngưỡng, mã dừng/lý do, tem | E06 | R0 | L |
| E08 | MD: biểu mẫu KSCL (store), lỗi/luật, bảo trì, phụ tùng, liên kết | E06 | R0/R2 | M |
| E09 | Sync service (push/pull/conflict) + tablet shell offline | E02 | R0 | XL |
| E10 | PL: KHSX tháng/tuần, lệnh SX, bố trí nhân sự, điều chỉnh | E07 | R1 | L |
| E11 | PR: phiên ca, sản lượng, dừng máy, đổi mác, tồn điếu | E10,E09 | R1 | XL |
| E12 | OEE engine + công thức cấu hình + golden tests | E11 | R1 | M |
| E13 | MT: phiếu cấp phát, cấp/nhận, kiểm đếm, tiêu hao, cảnh báo | E10,E09 | R1 | XL |
| E14 | Shift-close engine (CC-11) + báo cáo ca | E11,E13 | R1 | L |
| E15 | RP core: projections/facts, `R_SHIFT`, `R_DAY` | E11–E14 | R1 | L |
| E16 | Dashboard v1: Trưởng ca, Phó QĐ | E15 | R1 | L |
| E17 | QC form engine (web + tablet renderer) + import schema | E08,E09 | R2 | XL |
| E18 | QC inline (lịch 30', B06/08/09, phân cấp A/B/C, cảnh báo, leo thang) | E17,E04 | R2 | XL |
| E19 | QC ĐBCL (B11/15/12/14/21), SPKPH, độc lập dữ liệu, B21 tháng, truy ngược | E18 | R2 | L |
| E20 | TX: block, xuất dùng, hư/mất, đối chiếu, cảnh báo, chốt, export | E11,E09 | R2 | L |
| E21 | PM: danh mục, KH tháng, WO, tablet thợ máy, sự cố/sửa chữa, MTTR/MTBF | E08,E22(min) | R3 | XL |
| E22 | SP: kho, nhập/xuất, yêu cầu xuất, tồn min, ERP file | E08,E03 | R3 | L |
| E23 | OH: KH năm, hồ sơ, đánh giá, phụ tùng, tiến độ, nghiệm thu, tổng hợp | E22,E03 | R3 | XL |
| E24 | RP đầy đủ: dashboard còn lại, báo cáo tuần/tháng/chuyên sâu, subscription, kiosk | E15–E23 | R4 | L |
| E25 | Hardening ATTT, hiệu năng, kiểm thử tải, tài liệu ATTT cấp độ 2 | tất cả | R4 | L |
| E26 | Di trú dữ liệu & go-live: chuẩn hóa masterdata, UAT, song song 3 ca, đào tạo | tất cả | R4 | L |

### 18.3 Thứ tự "lát cắt dọc" đề xuất cho từng phân hệ
Với mỗi epic, Claude Code thực hiện theo thứ tự: (1) migration + entity + seed → (2) domain rules + unit test (dùng golden data) → (3) API + validator + permission + audit + integration test → (4) event/alert/projection → (5) UI web → (6) UI tablet (nếu có) + test offline → (7) cập nhật OpenAPI, tài liệu → (8) chạy AC tương ứng. Commit nhỏ, mỗi commit nêu ID (`FR-PR-004`, `BR-PR-002`, `AC-PR-003`).

### 18.4 Gợi ý nội dung `CLAUDE.md`
```markdown
# PXVB – Hướng dẫn cho Claude Code
- Nguồn sự thật yêu cầu: docs/PXVB-SRS-SDD.md. Tham chiếu ID (FR/BR/AC/NFR) trong commit & PR.
- Kiến trúc: modular monolith Java 21 + Spring Boot 3.5 (Maven); mỗi module có package domain/application/infrastructure/api/contract; không tham chiếu chéo trừ `contract` (Spring Modulith verify); nhóm bảng MySQL riêng theo tiền tố, Flyway migration theo module, DB MySQL 8 (tương thích 8.0/8.4).
- Quy ước: UUIDv7 khóa chính; UTC trong DB; BusinessDate+ShiftCode; enum lưu chuỗi UPPER_SNAKE; lỗi RFC7807 tiếng Việt, mã PXVB-<MOD>-<nnnn>.
- Mọi lệnh ghi từ tablet: Idempotency-Key + X-Device-Id; xung đột không ghi đè.
- Dữ liệu đã CONFIRMED/LOCKED không sửa trực tiếp → AdjustmentRequest.
- Không hard-code ngưỡng/giờ ca/mã lý do/công thức: dùng masterdata có phiên bản.
- UI 100% tiếng Việt, định dạng vi-VN; tablet: nút ≥56dp, pass/fail ≤3 chạm.
- Trước khi coi là xong: build + test xanh, permission & audit đã áp, AC liên quan đạt, OpenAPI cập nhật.
- Chỗ nào "⚠ GIẢ ĐỊNH" trong tài liệu: làm cấu hình được và ghi vào docs/OPEN-QUESTIONS.md.
```

### 18.5 Prompt khởi động đề xuất (dán vào Claude Code)
> *"Đọc `docs/PXVB-SRS-SDD.md`. Thực hiện **Sprint 0 (§18.1)** rồi epic **E01–E02**. Làm theo lát cắt dọc (§18.3). Sau mỗi epic: chạy toàn bộ test, liệt kê AC đã đạt/chưa đạt, các giả định mới (ghi vào `docs/OPEN-QUESTIONS.md`), rồi dừng chờ xác nhận trước khi sang epic tiếp theo."*

### 18.6 Dữ liệu thử nghiệm bắt buộc (để test không phụ thuộc đoán)
- `tests/golden/oee-*.json` (≥ 10 ca: bình thường, đổi mác, dừng chồng, máy không lệnh, có NC, Performance > 100%).
- `tests/golden/material-*.json` (định mức nhiều phiên bản, đổi mác, hoàn trả).
- `tests/golden/stamp-*.json` (dải seri chồng, tem hư/mất, chênh lệch).
- `tests/golden/mttr-mtbf.json`, `tests/golden/b21-stats.json`.
- Bộ seed dev: 5 máy vấn + 5 máy bao + 3 máy phụ trợ, 10 SP, 200 NVL, 3 ca × 7 ngày; bộ "đầy đủ 77 máy" cho test tải.

---

## PHỤ LỤC A – TRUY VẾT: CHỨC NĂNG TRONG BÁO CÁO (MỤC XV) → ID TRONG TÀI LIỆU NÀY

| Báo cáo | Chức năng trong báo cáo | ID |
|---|---|---|
| 2.2 Masterdata | Danh mục MMTB · Cụm/bộ phận · Ghép cặp vấn–bao · Danh mục SP · NVL · Định mức · Ca & khung giờ · Bảo trì định kỳ · TDT · Phụ tùng · Liên kết thiết bị–phụ tùng · Ngưỡng cảnh báo · Lý do dừng máy · Người dùng & phân quyền · Import/Export | FR-MD-001, 002, 003, 004–005, 006, 007, 008, 015, 016, 017–018, 019, 009, 010–011, 020 (+FR-CC-001..006), 021 (+FR-CC-070/071) |
| 2.3 KHSX | KHSX tháng · tuần · lệnh ngày · nhân sự ca · theo dõi · điều chỉnh lệnh · báo cáo | FR-PL-001, 002, 003–004, 005, 007, 006, 008 |
| 2.4 NVL | Sinh phiếu · cấp thực tế theo khung giờ · giao nhận điện tử · theo dõi tiêu hao · cảnh báo hao hụt · tồn NVL · báo cáo tiêu hao ca · xu hướng · đối chiếu ERP | FR-MT-001–002, 003, 004, 006, 007, 008, 009, 010, 011 |
| 2.5 Sản lượng/OEE | Phiên ca · ghi nhận sản lượng · dừng máy · realtime · tồn điếu · OEE · cảnh báo · báo cáo ca · phân tích | FR-PR-001, 002–003, 004, 006, 007, 008, 009, 012, 013 |
| 2.6 Chất lượng | Lịch kiểm tra · B06/08/09 · B11/15/12/14/21 · cảnh báo A/B/C · SPKPH · tỷ lệ lỗi realtime · báo cáo ca · thống kê B21 tháng · xu hướng · truy ngược | FR-QC-001–002, 003, 004, 005, 006, 007, 008, 009, 010, 011 |
| 2.7 Tem thuế | Khai báo ca/tồn đầu · xuất dùng theo dải · tem hư/mất · đối chiếu · cảnh báo · báo cáo ca · theo dõi hao hụt · xu hướng · xuất báo cáo thuế | FR-TX-001, 003 (+002), 004, 005, 006, 007, 008, 009, 010–011 |
| 2.8 Bảo trì | Danh mục công việc chuẩn · KH tháng · phân công · ghi nhận tablet · yêu cầu sửa chữa · MTTR/MTBF · lịch sử · báo cáo | FR-PM-001, 002–003, 005, 006, 008, 010, 011, 012 |
| 2.9 TDT | KH năm · hồ sơ (định khoản) · biên bản đánh giá · phụ tùng dự kiến · tiến độ · xuất phụ tùng theo định khoản · nghiệm thu · phụ tùng cũ · tổng hợp vật tư · thống kê | FR-OH-001, 002, 003, 004, 005, 006, 008, 009, 011, 012 |
| 2.10 Kho phụ tùng | Danh mục · nhập · xuất theo bảo trì · xuất theo TDT · xuất phát sinh · tồn · cảnh báo min · báo cáo sử dụng · lịch sử | FR-SP-001, 002, 003, 004, 005, 006, 007, 009, 008 |
| 2.11 Báo cáo & Dashboard | Dashboard tổng quan · sản lượng/OEE · NVL · chất lượng · báo cáo ca · ngày/tuần/tháng · OEE · NVL · chất lượng · tem | FR-RP-001 (§14.2), FR-RP-002–003, 004, 005–006 (§14.3) |
| 2.12 | Phi chức năng (CSDL, phản hồi, đồng thời, tương thích, giao diện, tin cậy, ATTT, duy trì, tích hợp) | §2, §16 (NFR-001…006, 16.2–16.4) |
| 2.13–2.14 | Đào tạo, chuyển giao, bảo hành, vận hành thử | §18.2 (E26), §16.6; tài liệu hướng dẫn theo vai trò là sản phẩm bàn giao của E26 |
| 3 | Phòng máy, điện, wifi, tablet, danh mục thiết bị | §2.6, §15.1 (hạ tầng không thuộc phạm vi code; ràng buộc cho triển khai) |
| 5, 6 | ATTT, PCCC | §16.3 |
| 7.1–7.5 | Nhập liệu tối giản, ma trận phân quyền, khóa dữ liệu & nhật ký, đối chiếu chéo, chuẩn hóa dữ liệu, offline | §4.1–§4.3, §4.7, §4.10, FR-MD-021/022, mục "Phân quyền" từng phân hệ |

## PHỤ LỤC B – THUẬT NGỮ
| Viết tắt | Nghĩa |
|---|---|
| PXVB | Phân xưởng Vấn Bao |
| TLSG | Công ty Thuốc lá Sài Gòn |
| KSCL | Kiểm soát chất lượng (inline tại chuyền) |
| ĐBCL | Đảm bảo chất lượng (kiểm tra độc lập của công ty) |
| SPKPH / NC | Sản phẩm không phù hợp |
| NVL | Nguyên vật liệu (nguyên liệu/sợi, vật tư vấn, vật tư bao) |
| OEE | Hiệu suất thiết bị tổng thể = Availability × Performance × Quality |
| TDT | Trung tu – Đại tu (và bảo trì lớn: BT/TT/ĐT) |
| KTCĐ / PXCĐ | Phòng Kỹ thuật Cơ điện / Phân xưởng Cơ điện |
| KHVT / TCKT | Phòng Kế hoạch Vật tư / Tài chính Kế toán |
| Kho CK | Kho cơ khí (phụ tùng) |
| MMTB | Máy móc thiết bị |
| MTTR / MTBF | Thời gian sửa chữa trung bình / Thời gian trung bình giữa hai sự cố |
| CMMS | Hệ thống quản lý bảo trì |
| Máy vấn / máy bao | Máy vấn điếu / máy đóng bao (cấp điếu từ máy vấn) |
| Khung giờ cấp phát | 4 đợt cấp NVL trong ca: sáng, trưa, chiều, đêm |
| Số định khoản | Mã tham chiếu nội bộ của hồ sơ TDT |
| Dechets | Sợi phế (quy trình xử lý sợi Dechets) |

## PHỤ LỤC C – MÃ LỖI & QUY ƯỚC
`PXVB-<MOD>-<nnnn>`: `CC` chung (0400 validate, 0403 forbidden, 0404 not found, 0409 conflict, 0423 locked, 0429 rate limit) · `MD-1xxx` · `PL-2xxx` · `MT-2xxx` · `PR-2xxx` · `QC-3xxx` · `TX-2xxx` · `PM-4xxx` · `OH-4xxx` · `SP-3xxx` · `RP-5xxx`. Mỗi mã có thông điệp tiếng Việt trong tệp tài nguyên `errors.vi.json`; không lộ chi tiết nội bộ cho người dùng cuối (chỉ `traceId`).

## PHỤ LỤC D – SƠ ĐỒ TRẠNG THÁI TÓM TẮT
```
RecordState : DRAFT → SUBMITTED → CONFIRMED → SHIFT_LOCKED → FINALIZED       (CC-03)
ShiftSession: PLANNED → OPEN → CLOSING → CLOSED → FINALIZED                    (PR/CC-11)
Order       : SUGGESTED → CONFIRMED → RUNNING → COMPLETED | CANCELLED          (PL)
IssueSlip   : AUTO → CONFIRMED → ISSUING → COMPLETED | CANCELLED               (MT)
Inspection  : DRAFT → SUBMITTED → CONFIRMED → LOCKED                            (QC)   NC: OPEN→PROPOSED→APPROVED→EXECUTED→CLOSED
PM WorkOrder: PLANNED → ASSIGNED → IN_PROGRESS → DONE | PARTIAL | SKIPPED ; (→OVERDUE)
Repair      : REPORTED → ACCEPTED → [PARTS_REQUESTED] → APPROVED → IN_REPAIR → DONE
OH Dossier  : DRAFT → ASSESSING → PARTS_PLANNING → PARTS_APPROVED → IN_PROGRESS → ACCEPTANCE → CLOSED
SP Request  : DRAFT → SUBMITTED → APPROVED → PARTIALLY_ISSUED → ISSUED | REJECTED | CANCELLED
Alert       : OPEN → ACK → IN_PROGRESS → RESOLVED → CLOSED
Adjustment  : PENDING → APPROVED → APPLIED | REJECTED | CANCELLED
Sync cmd    : PENDING → SYNCING → SYNCED | CONFLICT | REJECTED
```
