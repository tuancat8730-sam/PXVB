package vn.pxvb.masterdata.contract;

import java.util.List;
import java.util.UUID;

/**
 * SPI: module khác khai báo nơi đang sử dụng một danh mục (BR-MD-003). Khi khóa/ngừng một bản ghi masterdata,
 * mọi {@code UsageChecker} được hỏi; có kết quả thì thao tác bị chặn kèm danh sách nơi đang dùng
 * (vd PL trả về các lệnh SX đang mở của một sản phẩm — AC-MD-004).
 */
public interface UsageChecker {

    /** Loại đối tượng masterdata, vd {@code Product}, {@code Machine}, {@code Material}. */
    String entityType();

    /** Mô tả ngắn từng nơi đang dùng (rỗng nếu không dùng). */
    List<String> usages(UUID id);
}
