package vn.pxvb.common.security;

import java.util.List;

/**
 * Phạm vi dữ liệu của một vai trò (FR-CC-003/006): toàn PX / theo nhóm dây chuyền / theo đơn vị.
 */
public record DataScope(Type type, String value) {

    public enum Type { ALL, LINE, UNIT }

    public static DataScope all() {
        return new DataScope(Type.ALL, null);
    }

    /** {@code true} nếu một trong các phạm vi cho phép truy cập đối tượng thuộc (type,value). */
    public static boolean permits(List<DataScope> scopes, Type type, String value) {
        return scopes.stream().anyMatch(s ->
                s.type() == Type.ALL || (s.type() == type && s.value() != null && s.value().equals(value)));
    }
}
