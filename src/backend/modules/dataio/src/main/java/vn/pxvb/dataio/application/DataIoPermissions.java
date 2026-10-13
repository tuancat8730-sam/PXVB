package vn.pxvb.dataio.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.pxvb.common.security.PermissionContributor;

@Component
public class DataIoPermissions implements PermissionContributor {

    @Override
    public List<PermissionDef> permissions() {
        return List.of(new PermissionDef("dataio.import.view", "dataio",
                "Xem mọi lần nhập liệu Excel của người khác", Set.of("SYS_ADMIN")));
    }
}
