package vn.pxvb.dataio.contract;

import java.util.List;
import java.util.Map;

/**
 * SPI: một mẫu nhập liệu Excel của một danh mục (FR-CC-070). Module sở hữu dữ liệu hiện thực mẫu này và đăng ký
 * qua {@link Provider}. Engine dataio lo đọc file, báo lỗi theo dòng/cột, commit theo lô.
 */
public interface ImportTemplate {

    /** {@code key} dùng trong dữ liệu dòng; {@code header} là tiêu đề cột hiển thị (tiếng Việt). */
    record Column(String key, String header, boolean required, String hint) {
    }

    String code();

    String title();

    /** Quyền cần có để nhập mẫu này (vd {@code md.machine.update}). */
    String permission();

    List<Column> columns();

    /** Lỗi theo cột (key → thông báo tiếng Việt); rỗng nếu dòng hợp lệ. Có thể truy vấn DB để kiểm tra tham chiếu. */
    Map<String, String> validate(Map<String, String> row);

    /** Khóa nghiệp vụ để phát hiện dòng trùng trong cùng file; {@code null} = không kiểm tra. */
    default String naturalKey(Map<String, String> row) {
        return null;
    }

    /** Ghi một lô dòng hợp lệ (chạy trong một giao dịch). Mặc định upsert từng dòng. */
    default void commitBatch(List<Map<String, String>> rows) {
        rows.forEach(this::upsert);
    }

    void upsert(Map<String, String> row);

    /** Danh mục tham chiếu cho sheet "Danh mục tham chiếu": tên → các giá trị hợp lệ. */
    default Map<String, List<String>> references() {
        return Map.of();
    }

    interface Provider {
        List<ImportTemplate> templates();
    }
}
