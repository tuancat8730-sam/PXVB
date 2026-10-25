# Kế hoạch: Hoàn tất E<nn> (<tên epic>)

<!--
Mẫu kế hoạch epic. Quy trình và quy tắc cập nhật: CLAUDE.md, mục "Kế hoạch theo epic".
Tên tệp: docs/plans/E<nn>-completion.plan.md (hoặc E<nn>-<slug>.plan.md nếu epic chưa làm).
Mọi mục trong ngoặc vuông là hướng dẫn; xóa khi điền.
-->

**Nguồn:** `docs/PROGRESS.md` (E<nn> <trạng thái>), `docs/PXVB-SRS-SDD.md` §<mục> (FR-…, BR-…, AC-…, NFR-…), §18.2 (backlog); `docs/OPEN-QUESTIONS.md` <mã L/Q>; bản vẽ `docs/design/` (nếu có UI).
**Độ phức tạp:** [Trung bình | Lớn | …] (backend) + [...] (UI). **Cập nhật lần cuối:** YYYY-MM-DD.
**Phương pháp:** [rà soát tĩnh mã nguồn `modules/…`, đối chiếu SRS/bản vẽ; test đã chạy hay chưa, lệnh nào]. Các mục ghi "giả thuyết" phải kiểm chứng ở Pha 0.
**Chú giải:** ✅ xong · 🟡 đang làm / xong một phần · ⬜ chưa làm · ⏸ hoãn (có quyết định) · ❌ bị chặn

## 1. Phạm vi và điều kiện "E<nn> hoàn tất"

[Bảng: nhóm việc | nội dung | thuộc epic này? (✅ / ❌ chuyển epic X / ⏸ có quyết định)]

**E<nn> được đánh ✅ khi:** [các pha bắt buộc, IT xanh trên MySQL 8.0 và 8.4, tài liệu cập nhật].

## 2. Đánh giá hiện trạng theo yêu cầu

| Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|
| FR-… | 🟡 | [lớp/endpoint/test đã có; phần còn thiếu] |

## 2b. Đối chiếu với thiết kế (`docs/design/`) — bỏ mục này nếu epic không có UI

| Màn | File | Đã có trong bản vẽ | Thiếu so với FR/backend | Việc |
|---|---|---|---|---|

## 3. Phát hiện từ rà soát (kèm mức độ)

| ID | Mức | Phát hiện | Bằng chứng |
|---|---|---|---|
| F1 | CAO / TB / THẤP | [mô tả ngắn] | [file, dòng hoặc lệnh] |

## 4. Phụ thuộc và việc của epic khác

| Phụ thuộc | Nội dung | Ảnh hưởng |
|---|---|---|

## 5. Giả định đang áp dụng (chưa được PXVB/PO xác nhận; đổi được)

| # | Giả định | Nếu đổi |
|---|---|---|
| G1 | [...] | [...] |

## 6. Quyết định và câu hỏi cần PXVB / PO

| # | Quyết định / câu hỏi | Trạng thái | Mặc định nếu chưa có trả lời |
|---|---|---|---|
| D1 | [...] | ⬜ / ✅ (ngày) | [...] |

## 7. Kế hoạch thực hiện

Quy ước: mỗi việc viết test trước (đỏ), rồi sửa mã (xanh). Sau khi xong **mỗi việc**, cập nhật ngay dòng tương ứng ở đây
và thêm một dòng vào mục 10. Việc có ⏸ hoặc ❌ không tính là thiếu nếu đã ghi quyết định.

### Pha 0 – Xác minh trước khi sửa
| ID | Việc | Trạng thái | Kết quả |
|---|---|---|---|
| 0.1 | Đọc hiện trạng code và các mẫu cần bám | ⬜ | |
| 0.2 | Môi trường: Java, Maven offline, Docker (`env -u DOCKER_HOST`, xem CLAUDE.md) | ⬜ | |
| 0.3 | Baseline `./mvnw verify` trên 8.0 và 8.4 | ⬜ | [số test đỏ, phân loại baseline] |

### Pha 1 – [Tên pha]
| ID | Việc | Trạng thái | Mức / phụ thuộc |
|---|---|---|---|
| 1.1 | [việc] | ⬜ | [...] |

### Pha UI – [chỉ khi có UI] (xem `docs/design/<EPIC>-ui-plan.md`)
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| U.1 | [việc thiết kế] | ⬜ | [...] |

### Pha N – Nghiệm thu và tài liệu
| ID | Việc | Trạng thái |
|---|---|---|
| N.1 | `./mvnw verify` xanh trên 8.0 và 8.4; ghi rõ baseline còn lại | ⬜ |
| N.2 | Cập nhật `PROGRESS.md`, `OPEN-QUESTIONS.md`, OpenAPI (nếu có endpoint mới) | ⬜ |

## 8. Trạng thái theo yêu cầu (đích cần đạt)

| Yêu cầu | Hiện tại | Đích | Pha |
|---|---|---|---|
| FR-… | 🟡 | ✅ | 1.1 |

## 9. Rủi ro

| Mức | Rủi ro | Giảm thiểu |
|---|---|---|
| CAO | [...] | [...] |

## 10. Nhật ký thực hiện

| Ngày | Ghi chú |
|---|---|
| YYYY-MM-DD | [việc đã làm, ID task, kết quả kiểm chứng (số test, lệnh), lệch so với kế hoạch] |
