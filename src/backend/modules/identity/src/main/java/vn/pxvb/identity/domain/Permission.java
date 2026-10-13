package vn.pxvb.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Quyền dạng {@code <mod>.<resource>.<action>} (sys_permission). Seed bằng code, không sửa qua UI. */
@Entity
@Table(name = "sys_permission")
public class Permission {

    @Id
    @Column(length = 100)
    private String code;

    @Column(nullable = false, length = 30)
    private String module;

    private String description;

    protected Permission() {
    }

    public Permission(String code, String module, String description) {
        this.code = code;
        this.module = module;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}
