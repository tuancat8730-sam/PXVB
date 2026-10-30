# E08 – Kế hoạch thiết kế màn hình (MD: biểu mẫu KSCL, lỗi/luật, bảo trì, phụ tùng, liên kết)

**Trạng thái:** đã duyệt 30/10/2026 (PXVB). Build theo `docs/plans/E08-completion.plan.md` mục 6b (chưa xong khi viết dòng này).
**Nguồn:** `docs/PXVB-SRS-SDD.md` §5 (FR-MD-011…022, BR-MD-003/004/009), `docs/DESIGN-SYSTEM.md` §5.7 và §9.4, `docs/design/README.md`, `docs/plans/E08-completion.plan.md` (phần backend).
**Phạm vi UI:** chỉ web quản trị (React). E08 không có màn tablet; hiển thị biểu mẫu KSCL trên tablet thuộc E17.

## 1. Hiện trạng bản vẽ

| Chức năng E08 | Màn đã vẽ | Đánh giá |
|---|---|---|
| FR-MD-013 Biểu mẫu KSCL (`QcFormTemplate`) | `W-KsclDesigner.dc.html` (tĩnh) | Có khung. Thiếu: danh sách biểu mẫu + phiên bản, tương tác (kéo-thả và bàn phím), trạng thái nháp/đang dùng/đã ngừng |
| FR-MD-012 Lỗi & luật phân cấp A/B/C | Không có | **Thiếu** |
| FR-MD-015 Công việc bảo trì chuẩn (checklist, vật tư) | Không có (chỉ có màn thực hiện T-41 và lịch PM) | **Thiếu** |
| FR-MD-016 Biểu mẫu đánh giá/nghiệm thu TDT | Không có | Thiếu; P1, có thể gộp với màn biểu mẫu KSCL theo mẫu W2 |
| FR-MD-017/018 Phụ tùng & tồn tối thiểu | Có `SP – Thẻ kho phụ tùng` (E22), chưa có màn danh mục MD | Thiếu màn danh mục MD |
| FR-MD-019 Liên kết thiết bị–phụ tùng (2 chiều) | Không có | **Thiếu** |
| FR-MD-011/014 Lý do khác, loại tem | Không có | Thiếu, dùng chung màn danh mục chung |
| FR-MD-022 Kiểm soát danh mục (người phụ trách, ngày cập nhật, phiên bản) | Không có | Thiếu; là khối thông tin dùng chung trên mọi màn danh mục |
| FR-MD-021 Chất lượng dữ liệu | `W-MdDataQuality` | Có. Cần bổ sung các nhóm lỗi mới của E08 |

## 2. Màn hình cần thêm hoặc sửa

Mẫu lấy từ §5.7 của hệ thiết kế (W1 danh sách + chi tiết, W2 biểu mẫu có phiên bản, W3 lưới). Mọi màn đều có sáu trạng thái theo §5 (tải, trống, lỗi, một phần, không có quyền, thành công).

| # | Màn | Mẫu | Nội dung chính | Việc cần làm | Ưu tiên |
|---|---|---|---|---|---|
| U-1 | MD – Trình thiết kế biểu mẫu KSCL (sửa) | Riêng (§9.4) | Khung trường theo mục, xem trước form, cảnh báo lược đồ thiếu/sai | Vẽ tương tác: thay kéo-thả bằng nút "Lên/Xuống" và menu "Thêm trường" (bàn phím, §B2); thông báo lỗi lược đồ bằng tiếng Việt | P0 |
| U-2 | MD – Biểu mẫu KSCL: danh sách và phiên bản | W2 | Danh sách mã biểu mẫu, nhóm (B06, B08…), loại, trạng thái; lịch sử phiên bản; nút Nhân bản, Kích hoạt phiên bản (ngày hiệu lực) | Vẽ mới, dùng lại trạng thái của S-W2 | P0 |
| U-3 | MD – Lỗi & luật phân cấp A/B/C | W1 + W3 | Bên trái: danh mục lỗi theo nhóm áp dụng (ROD/PACK/CARTON/MATERIAL/PACKAGING). Bên phải: luật theo lỗi × cấp A/B/C, cách đo (đếm trong mẫu / tỷ lệ %), ngưỡng, cỡ mẫu, hiệu lực | Vẽ mới. Kiểm tra trùng hiệu lực hiển thị ngay trên lưới | P0 |
| U-4 | MD – Công việc bảo trì chuẩn | W2 | Thông tin chung (mã, loại, chu kỳ, kỹ năng, đơn vị); khối checklist (thứ tự, nội dung, kiểu kết quả OK/NG-số-chữ, min/max); khối vật tư dự kiến (chọn phụ tùng, số lượng) | Vẽ mới. Trạng thái "Nháp" và "Đang dùng" theo phiên bản | P0 |
| U-5 | MD – Liên kết thiết bị–phụ tùng | W3 | Chuyển đổi hai chiều: "Theo máy" (chọn máy → danh sách phụ tùng) và "Theo phụ tùng" (chọn phụ tùng → danh sách máy). Dòng có cụm và công việc bảo trì (tùy chọn) | Vẽ mới (FR-MD-019 yêu cầu hiển thị 2 chiều) | P1 |
| U-6 | MD – Danh mục chung (phụ tùng, NCC, lý do khác, loại tem) | W1 | Một mẫu dùng cho 4 danh mục; bên phải là khối "Kiểm soát": người phụ trách, ngày cập nhật, phiên bản, trạng thái | Vẽ một lần, sinh các biến thể theo danh mục (`generator/specs.py`) | P0 |
| U-7 | MD – Chất lượng dữ liệu (sửa) | W1 | Thêm nhóm kiểm tra: biểu mẫu KSCL chưa có bản ĐANG DÙNG; luật lỗi chồng lấn hiệu lực; công việc bảo trì thiếu phụ tùng hợp lệ | Sửa nhãn và cột của màn có sẵn | P1 |

Ghi chú: U-2 và U-4 cùng dùng cơ chế phiên bản (`versioned`), nên một bộ trạng thái và một khung nhìn lịch sử phiên bản dùng chung.

## 3. Quy ước giao diện

- Toàn bộ nhãn, thông báo lỗi, mã lỗi hiển thị tiếng Việt (CLAUDE.md). Mã lỗi giữ dạng `PXVB-MD-nnnn` để tra cứu.
- Chữ "Cấp A/B/C" chỉ dùng cho cấp lỗi QC (theo L71). Không dùng cho cảnh báo chung.
- Mọi ô có giá trị lấy từ danh mục là ô chọn có tìm kiếm, không gõ tự do (tránh sai mã phụ tùng, mã máy).
- Dữ liệu đã `ACTIVE` không sửa trực tiếp: màn hiển thị "Nhân bản thành phiên bản mới" thay vì nút Sửa (theo BR-MD-004/009).
- Không có nút xóa cho bản ghi đã được tham chiếu; hiển thị "Đang dùng ở: …" với số lượng (BR-MD-003).
- Tương phản và cỡ chạm theo DESIGN-SYSTEM §2; màn web kiểm tra tối thiểu ở 1366×768.

## 4. Thứ tự và phụ thuộc

1. Duyệt hướng thiết kế U-1 và U-3 trước (có nhiều quyết định nghiệp vụ nhất).
2. U-6 và U-2 (dùng mẫu có sẵn, ít rủi ro).
3. U-4, U-5, U-7.
4. Mọi bản vẽ mới: thêm vào `docs/design/README.md` (bảng "Đợt 7 – E08") và cập nhật `docs/DESIGN-SYSTEM.md` §10.

Phụ thuộc: màn triển khai cần khung `src/web` (React shell) — hiện chưa có (`docs/PROGRESS.md` §1). Vì vậy E08 làm bản vẽ và dữ liệu trước; triển khai UI nằm sau khi có khung web.

## 5. Câu hỏi cần PXVB chốt

| # | Câu hỏi | Mặc định nếu không có trả lời |
|---|---|---|
| UQ1 | Trình thiết kế KSCL: có bắt buộc kéo-thả không, hay chấp nhận nút Lên/Xuống + bàn phím? | Nút Lên/Xuống + bàn phím (đúng quy tắc tiếp cận, §B2) |
| UQ2 | Lược đồ biểu mẫu KSCL (các loại trường, cách gắn cấp lỗi) có phải lấy từ TLSG-QT-ĐBCL-01 lần 10 (Q10) không? Có file mẫu 2 biểu mẫu tham chiếu chưa? | Lược đồ v1 tối thiểu, đánh dấu ⚠ GIẢ ĐỊNH, chờ mẫu thật |
| UQ3 | Màn lỗi & luật (U-3): cấp A/B/C. Cấp B đã chốt là "ngoài biên một lần = B" (`docs/OPEN-QUESTIONS.md` L74); cấp A ("liên tiếp") và cấp C còn mở (L75) | Cấp B hiển thị theo quy tắc đã chốt. Cấp A và C chỉ là minh họa, không trình bày như quy tắc thật. Đã chọn (a): E08 chưa lưu cấp A (L75) |
| UQ4 | Nhóm FR-MD-022 "thay đổi sau vận hành qua workflow" có cần màn duyệt riêng ngay trong E08 không? | Chỉ hiển thị trạng thái và người duyệt; màn duyệt dùng lại màn phê duyệt chung của E03 |

## 6. Việc chưa làm trong kế hoạch này

- Không vẽ lại các màn đã có (W-MdCatalog gốc, W-MdPairing, W-MdBom, W-MdThreshold).
- Không thiết kế tablet (E17/E18).
- Không đo tương phản hay kiểm tra bàn phím trên trình duyệt thật (vẫn ở DESIGN-SYSTEM §10, mục A).
