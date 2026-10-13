package vn.pxvb.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.pxvb.identity.domain.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByDeviceCode(String deviceCode);
}
