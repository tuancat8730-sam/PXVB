# Kế hoạch: Hoàn tất E08 (MD: biểu mẫu KSCL, lỗi/luật, bảo trì, phụ tùng, liên kết)

**Nguồn:** `docs/PROGRESS.md` (E08 🟡), `docs/PXVB-SRS-SDD.md` §5 (FR-MD-011…022, BR-MD-003/004/009, AC-MD-001…005), §18.2 (E08: M, R0/R2, phụ thuộc E06); `docs/OPEN-QUESTIONS.md` Q10, Q11, L25, L71; bản vẽ `docs/design/` (`W-KsclDesigner`, `W-MdDataQuality`, `DESIGN-SYSTEM.md` §5.7, §9.4).
**Độ phức tạp:** Trung bình (backend) + trung bình (thiết kế UI). **Cập nhật lần cuối:** 2026-10-25.
**Phương pháp:** rà soát tĩnh `modules/masterdata` (`Catalogs`, `CatalogSpec`, `CatalogService`, `CatalogImportTemplates`, migration `md_*`), đối chiếu với SRS §5 và bản vẽ. Đã làm theo TDD: test đỏ (không biên dịch được) → xanh. **Chưa chạy IT** (Docker không hoạt động trên máy này).
**Chú giải:** ✅ xong · 🟡 xong một phần · ⬜ chưa làm · ⏸ hoãn (có quyết định) · ❌ bị chặn

## 1. Phạm vi và điều kiện "E08 hoàn tất"

| Nhóm | Nội dung | Thuộc E08? |
|---|---|---|
| Kho danh mục (CRUD, audit, phiên bản, import/export, kiểm tra tham chiếu) | `Catalogs`, `CatalogService`, `CatalogImportTemplates` | ✅ Đã có trước E08, không đổi |
| Kiểm nội dung các trường JSON (biểu mẫu KSCL/TDT, checklist, vật tư bảo trì) | Validator cấu trúc | ✅ Có (Pha 1–2) |
| Luật lỗi A/B/C (không chồng lấn, miền giá trị) | Validator + kiểm DB | ✅ Có (Pha 2) |
| Liên kết thiết bị–phụ tùng: ghi (cụm thuộc máy) và đọc 2 chiều | Validator + API đọc | 🟡 Ghi có; đọc 2 chiều ⬜ (Pha 3) |
| Đối chiếu ERP (FR-MD-017 "file") | Import/đối chiếu file ERP | ❌ Chuyển E22 (D3) |
| Tồn tối thiểu gợi ý từ lịch sử (FR-MD-018 P1) | Cần dữ liệu sử dụng từ RP | ⏸ Chờ RP |
| Dữ liệu thật: 46 biểu mẫu KSCL, danh mục lỗi, ngưỡng A/B/C (Q10) | Import Excel, không đổi code | ⬜ Chờ PXVB |
| Giao diện web quản trị masterdata (U-1…U-7) | `docs/design/E08-ui-plan.md` | ⬜ Bản vẽ chưa làm; build chờ khung `src/web` |

**E08 được đánh ✅ khi:** Pha 1–5 xong, IT chạy xanh trên MySQL 8.0 và 8.4, tài liệu cập nhật (OpenAPI, OPEN-QUESTIONS, PROGRESS). Bản vẽ U-1…U-7 được duyệt và vẽ (không chặn đánh ✅ backend, nhưng chặn mục UI). Task 8 (Q10) là thao tác dữ liệu, không chặn đánh ✅ kỹ thuật.

## 2. Đánh giá hiện trạng theo yêu cầu

| Yêu cầu | Trạng thái | Bằng chứng / ghi chú |
|---|---|---|
| FR-MD-013 Biểu mẫu KSCL | 🟡 | `qc-form-templates` có kho versioned; `QcFormSchemaRules` kiểm lược đồ v1 (đã nối vào `validateRules`). Còn: xem trước, nhân bản UI, chờ mẫu thật (UQ2) |
| FR-MD-012 Lỗi & luật A/B/C | 🟡 | `defects`, `defect-grade-rules` có sẵn. `DefectGradeRules` (cỡ mẫu, ngưỡng ≥ 0, % ≤ 100) và `CatalogReferenceChecks.overlapsExistingGradeRule` (không chồng lấn). IT chưa chạy |
| FR-MD-015 Bảo trì chuẩn | 🟡 | `maint-task-templates` có kho. `MaintTemplateRules` kiểm checklist, vật tư, chu kỳ; `CatalogReferenceChecks.unknownPartNos` kiểm phụ tùng tồn tại. Checklist và vật tư vẫn lưu JSON (không bảng riêng) |
| FR-MD-016 TDT | 🟡 | `overhaul-forms` dùng cùng `QcFormSchemaRules` (lược đồ dùng chung) |
| FR-MD-017 Phụ tùng & thẻ kho | 🟡 | `spare-parts` có sẵn (tìm theo thẻ kho). Đối chiếu ERP chuyển E22 (D3) |
| FR-MD-018 Tồn tối thiểu | 🟡 | `spare-part-min-stock` có phiên bản. Gợi ý từ lịch sử ⏸ (RP) |
| FR-MD-019 Liên kết 2 chiều | 🟡 | Ghi: `componentId` phải thuộc `machineId`, phải có máy hoặc dòng máy (code). Đọc 2 chiều ⬜ (Pha 3) |
| FR-MD-011 / FR-MD-014 | ✅ | `reason-codes`, `stamp-types` đã có; không cần thay đổi |
| FR-MD-021 Chất lượng dữ liệu | ⬜ | `DataQualityService` có sẵn; nhóm kiểm tra E08 chưa thêm (Pha 4) |
| FR-MD-022 Kiểm soát danh mục | ⬜ | Có `status`, `approvers`; chưa hiển thị người phụ trách/ngày cập nhật (Pha 5). Không làm luồng duyệt mới (D4) |
| Kiểm thử | 🟡 | Unit: 20 test mới xanh (3 lớp quy tắc). IT: `MasterdataE08IT` (4 test) biên dịch được, **chưa chạy**. `MasterdataIT` có sẵn, chưa chạy lại |
| ArchUnit | ✅ | `ArchitectureTest` 16/16 xanh (chạy offline) |

## 3. Phát hiện từ rà soát và từ triển khai

| ID | Mức | Phát hiện | Xử lý |
|---|---|---|---|
| F1 | ✅ đã xử lý | Không có Docker: IT (Testcontainers) không chạy được | Đã chạy: `MasterdataIT` 9/9 và `MasterdataE08IT` 4/4 trên MySQL 8.0 và 8.4 (2026-10-25) |
| F9 | CAO (đã sửa) | Full `verify` 8.0 cho thấy hồi quy: `MasterdataIT.qcFormVersionsAreImmutableOnceActive` dùng lược đồ không có `schemaVersion`, bị validator mới từ chối | Cập nhật fixture sang lược đồ v1 hợp lệ (test kiểm tính bất biến, không kiểm hình dạng lược đồ). Đã chạy lại xanh |
| F10 | TB | Kỳ vọng HTTP của lỗi kiểm dữ liệu trong kế hoạch là 422; module dùng 400 cho VALIDATION (`MasterdataIT`), 422 cho vi phạm trạng thái | Sửa test sang 400; kế hoạch đã cập nhật |
| F2 | TB | Kế hoạch cũ ghi "mirror `ThresholdService`" cho kiểm chồng lấn. Khi triển khai thấy không có logic đó để tái dùng | Viết SQL mới trong `CatalogReferenceChecks`. Đã sửa khỏi kế hoạch |
| F3 | TB | Kiểm `activate-version` chưa làm. Nội dung phiên bản đã được kiểm khi lưu, và phiên bản đã kích hoạt không sửa được | Chấp nhận (ghi ở mục 6) |
| F4 | TB | Chưa có chỉ mục cho truy vấn chồng lấn `md_defect_grade_rule (defect_code, applies_to, grade, effective_from)` | Thêm migration khi đo được (Pha 7). Số lượng luật nhỏ nên hiện chấp nhận |
| F5 | THẤP | Kiểm phụ tùng là N truy vấn (một lần cho mỗi `partNo`) | Chấp nhận: vật tư mỗi bảo trì ít dòng. Đo khi có dữ liệu thật |
| F6 | TB | Lỗi vẫn dùng mã `VALIDATION` chung, chưa có mã `PXVB-MD-nnnn` riêng | Ghi nhận; đồng bộ với các module khác khi chuẩn hóa mã |
| F7 | TB (thiết kế) | Bản vẽ masterdata chưa có: lỗi & luật A/B/C, công việc bảo trì chuẩn, liên kết 2 chiều, danh sách biểu mẫu KSCL có phiên bản | Kế hoạch U-1…U-7 (`docs/design/E08-ui-plan.md`) |
| F8 | TB (thiết kế) | `W-KsclDesigner` là bản tĩnh, chưa có thao tác bàn phím thay kéo-thả (DESIGN-SYSTEM §10, B2) | U-1: nút Lên/Xuống + bàn phím (UQ1, mặc định) |

## 4. Phụ thuộc và rủi ro dữ liệu

| Phụ thuộc | Nội dung | Ảnh hưởng |
|---|---|---|
| Q10 | 46 biểu mẫu KSCL, danh mục lỗi, ngưỡng A/B/C | Lược đồ v1 có thể phải đổi khi có mẫu thật (UQ2); E17 chờ Q10 |
| Q11 | Quy cách tem | Không ảnh hưởng E08 (chỉ loại tem đã có) |
| Khung `src/web` | Chưa tồn tại (`PROGRESS.md` §1) | Chặn Pha 6b (build UI) |
| Docker | Không chạy trên máy này | Chặn chạy IT (F1) |

## 5. Giả định đang áp dụng (chưa được PXVB xác nhận; đổi được)

| # | Giả định | Nếu đổi |
|---|---|---|
| G1 | Lược đồ KSCL v1: `schemaVersion = 1`, `sections[]`, `fields[]` với kiểu `NUMBER/TEXT/CHECK/SELECT` | Tăng `SUPPORTED_VERSION` và viết thêm nhánh kiểm trong `QcFormSchemaRules` |
| G2 | Cấp A/B/C: "ngoài biên một lần = B, liên tiếp = A" (UQ3). E08 chỉ lưu luật, việc đánh giá thuộc E18 | Đổi trong E18, không ảnh hưởng E08 |
| G3 | Phụ tùng trong vật tư bảo trì chỉ cần **tồn tại**, không kiểm trạng thái (bảng không có cột trạng thái trong đặc tả) | Thêm kiểm trạng thái khi bảng có cột đó |
| G4 | Chỉ kiểm phụ tùng khi có vật tư; không kiểm phụ tùng có NCC mặc định (D5) | Thêm vào chất lượng dữ liệu (Pha 4) |
| G5 | Seed hiện có (ngưỡng, giờ ca) là giá trị giả định (L25) | Thay bằng dữ liệu thật qua import |

## 6. Quyết định (PO/PXVB đã duyệt 2026-10-25, theo mặc định)

| # | Quyết định | Trạng thái |
|---|---|---|
| D1 | Lược đồ `formSchema` v1 tối thiểu có `schemaVersion`, chờ 2 biểu mẫu tham chiếu | ✅ Chốt mặc định (UQ2 vẫn mở, chờ mẫu thật) |
| D2 | E08 chỉ lưu luật A/B/C; đánh giá thuộc E18 | ✅ Chốt mặc định |
| D3 | Đối chiếu ERP để E22; E08 chỉ giữ `erpCode` | ✅ Chốt mặc định |
| D4 | Không làm luồng duyệt mới (`MD_NORM_CHANGE`); dùng `approvers` và luồng E03 | ✅ Chốt mặc định |
| D5 | "Phụ tùng phải có NCC mặc định" chỉ là cảnh báo, không chặn | ✅ Chốt mặc định |
| UQ1 | Trình thiết kế KSCL dùng nút Lên/Xuống và bàn phím thay kéo-thả | ✅ Chốt mặc định (dùng trong U-1) |
| UQ3 | Quy tắc cấp A/B/C | ⬜ Chờ ĐBCL xác nhận (ghi OPEN-QUESTIONS) |

## 7. Kế hoạch thực hiện

Quy ước như E04: mỗi việc viết test trước (đỏ), rồi sửa mã (xanh). Việc IT chưa chạy được vẫn ghi ⬜ cho đến khi chạy xanh.

### Pha 0 – Chuẩn bị và xác minh
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 0.1 | Rà soát hiện trạng `masterdata` và bản vẽ | ✅ | Mục 2, F7–F8 |
| 0.2 | Môi trường: Java 21, Maven chạy offline (`./mvnw -o`) | ✅ | Bản Java 21.0.13 |
| 0.3 | Docker cho Testcontainers | ✅ | Colima chạy. Quy trình ở `CLAUDE.md` mục "Docker và test tích hợp" (2026-10-25) |
| 0.4 | Chốt D1–D5, UQ1 | ✅ | Mục 6 |
| 0.5 | Baseline `./mvnw verify` trên 8.0 | ✅ | 14 đỏ: 13 baseline (`ProductionIT` 11, `MaterialIT` 2) + 1 hồi quy E08 đã sửa (F9). Chờ chạy 8.4 đầy đủ |

### Pha 1 – Validator cấu trúc (FR-MD-013, 015, 016)
| ID | Việc | Trạng thái | Kết quả |
|---|---|---|---|
| 1.1 | Test đỏ: `QcFormSchemaRulesTest` (7), `MaintTemplateRulesTest` (8), `DefectGradeRuleRulesTest` (5) | ✅ | Không biên dịch được (lớp chưa có) |
| 1.2 | `QcFormSchemaRules`: lược đồ v1 (version, sections, mã trường duy nhất, kiểu, nhãn, SELECT cần options) | ✅ | Xanh |
| 1.3 | `MaintTemplateRules`: checklist (seq duy nhất, kiểu kết quả, min ≤ max), vật tư (partNo trùng, qty > 0), `partNumbers`, chu kỳ > 0 | ✅ | Xanh |
| 1.4 | `DefectGradeRules`: cỡ mẫu cho đếm trong mẫu, ngưỡng ≥ 0, % ≤ 100 | ✅ | Xanh |
| 1.5 | Đạt 20/20 test unit mới; toàn bộ test unit (`-am`) xanh | ✅ | Log 2026-10-25 |

### Pha 2 – Tích hợp vào CatalogService (FR-MD-012, 015, 019; Task 5 của kế hoạch cũ)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 2.1 | `CatalogReferenceChecks`: phụ tùng tồn tại, cụm thuộc máy, luật lỗi không chồng lấn (SQL) | ✅ code | Chưa chạy IT |
| 2.2 | `CatalogService.validateRules`: nhánh cho 5 danh mục (`qc-form-templates`, `overhaul-forms`, `maint-task-templates`, `defect-grade-rules`, `machine-part-links`); áp cho create, update, import | ✅ code | Lệch: không áp ở `activate-version` (F3) |
| 2.3 | Giữ nguyên chữ ký constructor `CatalogService`; `Catalogs.java` không đổi | ✅ | Đã kiểm: host (`test-compile`) biên dịch được với các nơi tham chiếu |
| 2.4 | IT `MasterdataE08IT` (4 test: lược đồ sai, phụ tùng không tồn tại, luật chồng lấn, cụm không thuộc máy → 400 VALIDATION) | ✅ | Xanh trên MySQL 8.0 và 8.4 (2026-10-25) |
| 2.5 | IT import 30 dòng lỗi không ghi dòng nào (AC-MD-002) | ⬜ | Chưa viết |

### Pha 3 – Liên kết hai chiều (FR-MD-019; Task 4 của kế hoạch cũ)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 3.1 | `GET /api/v1/md/machines/{id}/parts` (kèm cụm, công việc) | ⬜ | |
| 3.2 | `GET /api/v1/md/spare-parts/{id}/machines` | ⬜ | |
| 3.3 | IT: một phụ tùng gắn 2 máy, hiện đủ 2 chiều | ⬜ | |

### Pha 4 – Chất lượng dữ liệu (FR-MD-021; Task 6)
| ID | Việc | Trạng thái |
|---|---|---|
| 4.1 | Thêm nhóm: biểu mẫu KSCL không có bản ACTIVE; luật lỗi chồng lấn; công việc bảo trì có phụ tùng không tồn tại | ⬜ |
| 4.2 | Cảnh báo phụ tùng không có NCC mặc định (D5) | ⬜ |
| 4.3 | IT: dữ liệu thiếu → báo đúng nhóm | ⬜ |

### Pha 5 – Kiểm soát danh mục (FR-MD-022; Task 7)
| ID | Việc | Trạng thái |
|---|---|---|
| 5.1 | Phản hồi danh mục có `owner`, `updatedAt`, `version`, `status` | ⬜ |
| 5.2 | IT đọc danh mục trả đủ trường | ⬜ |

### Pha 6 – Thiết kế và build UI (xem `docs/design/E08-ui-plan.md`)
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| 6a.1 | Kế hoạch UI (U-1…U-7, quy ước, câu hỏi) | ✅ | |
| 6a.2 | Vẽ U-2 (danh sách biểu mẫu KSCL và phiên bản) và U-6 (danh mục chung + kiểm soát) theo mẫu W2/W1 | ⬜ | 6a.1 |
| 6a.3 | Vẽ U-1 (trình thiết kế KSCL, bản tương tác), U-3 (lỗi & luật), U-4 (bảo trì chuẩn) | ⬜ | 6a.1, UQ1 |
| 6a.4 | Vẽ U-5 (liên kết 2 chiều) và U-7 (chất lượng dữ liệu, sửa) | ⬜ | 6a.1 |
| 6a.5 | Cập nhật `docs/design/README.md` (đợt 7) và `DESIGN-SYSTEM.md` §10 | ⬜ | 6a.2–6a.4 |
| 6b | Build màn hình web | ⬜ | Khung `src/web` (❌ chưa có), Pha 2–5 |

### Pha 7 – Dữ liệu, tài liệu và nghiệm thu (Task 8 và mục Acceptance cũ)
| ID | Việc | Trạng thái |
|---|---|---|
| 7.1 | Chạy IT trên Docker: `MasterdataE08IT`, `MasterdataIT`, IT mới ở Pha 3–5 | ⬜ (F1) |
| 7.2 | `./mvnw verify` xanh trên MySQL 8.0 và 8.4 | ⬜ |
| 7.3 | Đo coverage lớp mới (mục tiêu ≥ 80%) | ⬜ |
| 7.4 | Chỉ mục `md_defect_grade_rule` nếu đo thấy chậm (F4) | ⬜ |
| 7.5 | OpenAPI: endpoint mới (Pha 3) và lỗi validation | ⬜ |
| 7.6 | `docs/OPEN-QUESTIONS.md`: ghi UQ2, UQ3, D1, G1–G5 như giả định ⚠ | ⬜ |
| 7.7 | `docs/PROGRESS.md`: cập nhật E08 khi xong Pha 1–5 | ⬜ |
| 7.8 | Task 8: nạp dữ liệu thật qua import (Q10) và chạy `DataQualityService` | ⬜ (chờ PXVB) |

## 8. Trạng thái theo yêu cầu (đích cần đạt)

| Yêu cầu | Hiện tại | Đích E08 | Pha |
|---|---|---|---|
| FR-MD-012 | 🟡 | ✅ (không chồng lấn, miền giá trị) | 2.1, 2.4, 7.1 |
| FR-MD-013 | 🟡 | ✅ (lược đồ v1; UI U-1/U-2) | 1.2, 2.2, 6a |
| FR-MD-015 | 🟡 | ✅ (checklist, vật tư, phụ tùng tồn tại; UI U-4) | 1.3, 2.2, 6a |
| FR-MD-016 | 🟡 | ✅ | 1.2, 2.2 |
| FR-MD-017 | 🟡 | ✅ (ERP ở E22) | D3 |
| FR-MD-018 | 🟡 | ⏸ gợi ý từ lịch sử (RP) | – |
| FR-MD-019 | 🟡 | ✅ (ghi, đọc 2 chiều; UI U-5) | 2.2, 3.1–3.3 |
| FR-MD-021 | ⬜ | ✅ | 4.1–4.3 |
| FR-MD-022 | ⬜ | ✅ (hiển thị; không luồng mới) | 5.1, 5.2 |
| Dữ liệu thật | ⬜ | ✅ (import, không đổi code) | 7.8 |

## 9. Rủi ro

| Mức | Rủi ro | Giảm thiểu |
|---|---|---|
| CAO | IT chưa chạy: lỗi DB (ví dụ truy vấn chồng lấn, JSON) chưa được phát hiện | Pha 7.1 bắt buộc trước khi đánh ✅ |
| CAO | Lược đồ KSCL v1 sai với 46 biểu mẫu thật → E17 phải đổi contract | Có `schemaVersion`; không chốt E17 trước Q10 |
| TB | Dữ liệu thật chậm (Q10) | E08 hoàn thành kho, validator, import; dữ liệu nạp sau |
| TB | Validator chậm khi nhập hàng loạt (2.500 NVL, ≥ 10.000 phụ tùng) | Đo ở 7.2 với dữ liệu cỡ thật; kiểm phụ tùng theo lô nếu cần (F5) |
| TB | Thiếu khung `src/web` nên UI không chạy được | Pha 6b chờ khung; E08 giao bản vẽ trước |
| THẤP | Thiếu chỉ mục cho kiểm chồng lấn | Đo rồi thêm (F4, 7.4) |

## 10. Nhật ký thực hiện

| Ngày | Ghi chú |
|---|---|
| 2026-10-25 | Rà soát `masterdata` và SRS §5, lập kế hoạch backend và kế hoạch UI (`docs/design/E08-ui-plan.md`). Chưa sửa mã |
| 2026-10-25 | Người dùng duyệt ("yes"); chốt D1–D5 và UQ1 theo mặc định |
| 2026-10-25 | TDD: viết 20 test đỏ cho 3 lớp quy tắc (không biên dịch được), rồi viết `QcFormSchemaRules`, `MaintTemplateRules`, `DefectGradeRules` (xanh, 20/20) |
| 2026-10-25 | Thêm `CatalogReferenceChecks` và nối 5 nhánh vào `CatalogService.validateRules` (create, update, import). Viết `MasterdataE08IT` (4 test). `test-compile` host xanh; ArchUnit 16/16 xanh; toàn bộ test unit (`-am`) xanh |
| 2026-10-25 | **IT chưa chạy:** Docker daemon không hoạt động trên máy này. Cần chạy trên máy/CI có Docker |
| 2026-10-25 | Lệch khỏi kế hoạch cũ: không mirror `ThresholdService` (F2); không kiểm trạng thái phụ tùng (G3); không kiểm ở `activate-version` (F3); chưa thêm chỉ mục (F4); tên lớp đổi thành `QcFormSchemaRules`, `MaintTemplateRules`, `DefectGradeRules` |
| 2026-10-25 | Viết lại kế hoạch này theo cấu trúc E04, cập nhật trạng thái thực tế |
| 2026-10-25 | Docker chạy được (Colima). `DOCKER_HOST` trong shell trỏ tới socket Docker Desktop không tồn tại; chạy với `env -u DOCKER_HOST` là đủ. IT qua failsafe chạy được với Testcontainers 1.21.3 mặc định (không cần nâng phiên bản); Ryuk không cần tắt. Quy trình ghi vào `CLAUDE.md` |
| 2026-10-25 | Chạy `MasterdataE08IT` và `MasterdataIT`: 4/4 và 9/9 xanh trên 8.0 và 8.4. Lần đầu có 4 lỗi 400 vs 422: sửa kỳ vọng test sang 400 (đúng quy ước VALIDATION) |
| 2026-10-25 | Full `verify` 8.0: phát hiện hồi quy do validator mới (`MasterdataIT.qcFormVersionsAreImmutableOnceActive`, lược đồ không có `schemaVersion`). Sửa fixture sang lược đồ v1 (F9). Baseline 13 lỗi ProductionIT/MaterialIT không thuộc E08 |
