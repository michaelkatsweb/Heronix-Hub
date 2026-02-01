package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.AppAccessPolicy;
import com.heronixedu.hub.model.AppAccessPolicy.PolicyTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppAccessPolicyRepository extends JpaRepository<AppAccessPolicy, Long> {

    // Find all policies for a specific role
    List<AppAccessPolicy> findByRoleName(String roleName);

    // Find all policies for a role and target type
    List<AppAccessPolicy> findByRoleNameAndTargetType(String roleName, PolicyTargetType targetType);

    // Find a specific policy
    Optional<AppAccessPolicy> findByRoleNameAndTargetTypeAndTargetCode(
            String roleName, PolicyTargetType targetType, String targetCode);

    // Check if a specific policy exists
    boolean existsByRoleNameAndTargetTypeAndTargetCode(
            String roleName, PolicyTargetType targetType, String targetCode);

    // Find all granted policies for a role and target type
    List<AppAccessPolicy> findByRoleNameAndTargetTypeAndAccessGrantedTrue(
            String roleName, PolicyTargetType targetType);

    // Find all policies for a target (to see which roles have access)
    List<AppAccessPolicy> findByTargetTypeAndTargetCode(PolicyTargetType targetType, String targetCode);

    // Find all granted product codes for a role
    @Query("SELECT p.targetCode FROM AppAccessPolicy p WHERE p.roleName = :roleName " +
            "AND p.targetType = :targetType AND p.accessGranted = true")
    List<String> findGrantedTargetCodes(String roleName, PolicyTargetType targetType);

    // Count policies per role
    long countByRoleName(String roleName);
}
