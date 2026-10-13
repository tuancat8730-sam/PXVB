package vn.pxvb.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.pxvb.identity.domain.OrgUnit;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    Optional<OrgUnit> findByCode(String code);
}
