package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
    Optional<Role> findByName(RoleName name);
}