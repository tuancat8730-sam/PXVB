package vn.pxvb.masterdata.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Dùng trong {@code @PreAuthorize("@mdAccess.can(#resource, 'view')")} để kiểm quyền theo danh mục động. */
@Component("mdAccess")
public class MdAccess {

    public boolean can(String resource, String action) {
        CatalogSpec spec = Catalogs.byResource(resource);
        if (spec == null) {
            return true; // để controller trả 404 thay vì 403 cho tài nguyên không tồn tại
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String required = "md." + spec.permKey() + "." + action;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (required.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
