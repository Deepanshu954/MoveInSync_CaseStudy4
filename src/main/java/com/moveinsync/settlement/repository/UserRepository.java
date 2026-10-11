package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for user accounts and RBAC security credentials.
 *
 * @author MoveInSync Core Settlement Team
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
