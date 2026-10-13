package vn.pxvb.identity.domain;

/** Nguồn xác thực của tài khoản (FR-CC-004). LDAP chỉ bật bằng cấu hình ở epic sau. */
public enum AuthSource {
    LOCAL,
    LDAP
}
