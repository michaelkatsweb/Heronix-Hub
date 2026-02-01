package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByPermissionName(String permissionName);
    List<Permission> findByCategory(String category);
    boolean existsByPermissionName(String permissionName);
}
