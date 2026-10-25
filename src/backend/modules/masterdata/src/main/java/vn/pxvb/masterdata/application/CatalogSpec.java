package vn.pxvb.masterdata.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Đặc tả khai báo của một danh mục masterdata (bảng, cột, khóa nghiệp vụ, quyền, nơi sử dụng). Động cơ
 * {@link CatalogService} dùng đặc tả này để cung cấp CRUD, import/export, audit, kiểm tra tham chiếu.
 * Tên bảng/cột lấy từ đặc tả tĩnh (không từ đầu vào người dùng) nên an toàn khi nối vào SQL.
 */
public final class CatalogSpec {

    public enum Type { STRING, INT, DECIMAL, BOOL, DATE, TIME, ENUM, REF, CODE_REF, JSON, UUID }

    public record Col(String name, String label, Type type, boolean required, int maxLen, List<String> values,
                      String refTable, String refKey, boolean immutable, boolean searchable, Object defaultValue) {

        public String column() {
            return snake(name);
        }

        /** Khóa dùng trong file import: tham chiếu theo mã (machineId → machineCode). */
        public String importKey() {
            return type == Type.REF ? name.substring(0, name.length() - 2) + "Code" : name;
        }

        public Col req() {
            return new Col(name, label, type, true, maxLen, values, refTable, refKey, immutable, searchable,
                    defaultValue);
        }

        public Col imm() {
            return new Col(name, label, type, required, maxLen, values, refTable, refKey, true, searchable,
                    defaultValue);
        }

        public Col srch() {
            return new Col(name, label, type, required, maxLen, values, refTable, refKey, immutable, true,
                    defaultValue);
        }

        public Col def(Object value) {
            return new Col(name, label, type, required, maxLen, values, refTable, refKey, immutable, searchable,
                    value);
        }
    }

    /** Nơi đang dùng: câu SQL đếm với một tham số {@code UUID_TO_BIN(?)} là id bản ghi. */
    public record Usage(String label, String countSql) {
    }

    private final String resource;
    private final String table;
    private final String entityType;
    private final String permKey;
    private final String title;
    private final List<Col> cols;
    private final List<String> keys;
    private final boolean hasActive;
    private final boolean versioned;
    private final String statusProp;
    private final String activeStatus;
    private final String inactiveStatus;
    private final Set<String> writers;
    private final Set<String> approvers;
    private final List<Usage> usages;

    private CatalogSpec(Builder b) {
        this.resource = b.resource;
        this.table = b.table;
        this.entityType = b.entityType;
        this.permKey = b.permKey;
        this.title = b.title;
        this.cols = List.copyOf(b.cols);
        this.keys = List.copyOf(b.keys);
        this.hasActive = b.hasActive;
        this.versioned = b.versioned;
        this.statusProp = b.statusProp;
        this.activeStatus = b.activeStatus;
        this.inactiveStatus = b.inactiveStatus;
        this.writers = Set.copyOf(b.writers);
        this.approvers = Set.copyOf(b.approvers);
        this.usages = List.copyOf(b.usages);
    }

    public static Builder builder(String resource, String table, String entityType, String permKey, String title) {
        return new Builder(resource, table, entityType, permKey, title);
    }

    public static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public String resource() {
        return resource;
    }

    public String table() {
        return table;
    }

    public String entityType() {
        return entityType;
    }

    public String permKey() {
        return permKey;
    }

    public String title() {
        return title;
    }

    public List<Col> cols() {
        return cols;
    }

    public List<String> keys() {
        return keys;
    }

    public boolean hasActive() {
        return hasActive;
    }

    public boolean versioned() {
        return versioned;
    }

    public String statusProp() {
        return statusProp;
    }

    public String activeStatus() {
        return activeStatus;
    }

    public String inactiveStatus() {
        return inactiveStatus;
    }

    public Set<String> writers() {
        return writers;
    }

    public Set<String> approvers() {
        return approvers;
    }

    /** Người phụ trách danh mục (FR-MD-022, G7): vai trò duyệt đầu tiên, không có thì vai trò ghi đầu tiên. */
    public String owner() {
        return approvers.stream().findFirst().or(() -> writers.stream().findFirst()).orElse("");
    }

    public List<Usage> usages() {
        return usages;
    }

    public String importCode() {
        return resource;
    }

    public Col col(String name) {
        return cols.stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }

    public static final class Builder {
        private final String resource;
        private final String table;
        private final String entityType;
        private final String permKey;
        private final String title;
        private final List<Col> cols = new ArrayList<>();
        private final List<String> keys = new ArrayList<>();
        private boolean hasActive = true;
        private boolean versioned;
        private String statusProp;
        private String activeStatus;
        private String inactiveStatus;
        private final Set<String> writers = new LinkedHashSet<>();
        private final Set<String> approvers = new LinkedHashSet<>();
        private final List<Usage> usages = new ArrayList<>();

        private Builder(String resource, String table, String entityType, String permKey, String title) {
            this.resource = resource;
            this.table = table;
            this.entityType = entityType;
            this.permKey = permKey;
            this.title = title;
        }

        public Builder col(Col col) {
            cols.add(col);
            return this;
        }

        public Builder keys(String... props) {
            keys.addAll(List.of(props));
            return this;
        }

        public Builder noActive() {
            hasActive = false;
            return this;
        }

        public Builder versioned() {
            versioned = true;
            return this;
        }

        public Builder status(String prop, String active, String inactive) {
            statusProp = prop;
            activeStatus = active;
            inactiveStatus = inactive;
            return this;
        }

        public Builder writers(String... roles) {
            writers.addAll(List.of(roles));
            return this;
        }

        public Builder approvers(String... roles) {
            approvers.addAll(List.of(roles));
            return this;
        }

        public Builder usage(String label, String countSql) {
            usages.add(new Usage(label, countSql));
            return this;
        }

        public CatalogSpec build() {
            return new CatalogSpec(this);
        }
    }
}
