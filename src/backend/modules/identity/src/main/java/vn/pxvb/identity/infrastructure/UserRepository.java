package vn.pxvb.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.pxvb.identity.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmployeeCode(String employeeCode);

    Optional<User> findByBadgeNo(String badgeNo);

    boolean existsByUsername(String username);

    Page<User> findByUsernameContainingOrFullNameContaining(String username, String fullName, Pageable pageable);
}
