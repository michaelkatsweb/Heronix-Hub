package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.ThirdPartyApp;
import com.heronixedu.hub.model.enums.ThirdPartyAppCategory;
import com.heronixedu.hub.model.enums.UpdatePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThirdPartyAppRepository extends JpaRepository<ThirdPartyApp, Long> {

    Optional<ThirdPartyApp> findByAppCode(String appCode);

    boolean existsByAppCode(String appCode);

    List<ThirdPartyApp> findAllByOrderByAppNameAsc();

    List<ThirdPartyApp> findByIsApprovedTrueOrderByAppNameAsc();

    List<ThirdPartyApp> findByIsInstalledTrueOrderByAppNameAsc();

    List<ThirdPartyApp> findByCategoryOrderByAppNameAsc(ThirdPartyAppCategory category);

    List<ThirdPartyApp> findByIsApprovedTrueAndCategoryOrderByAppNameAsc(ThirdPartyAppCategory category);

    @Query("SELECT t FROM ThirdPartyApp t WHERE t.isApproved = true AND " +
           "(LOWER(t.appName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.publisher) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<ThirdPartyApp> searchApprovedApps(String searchTerm);

    @Query("SELECT t FROM ThirdPartyApp t WHERE " +
           "LOWER(t.appName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.publisher) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<ThirdPartyApp> searchAllApps(String searchTerm);

    List<ThirdPartyApp> findByIsApprovedFalseOrderByCreatedAtDesc();

    @Query("SELECT COUNT(t) FROM ThirdPartyApp t WHERE t.isApproved = true")
    long countApproved();

    @Query("SELECT COUNT(t) FROM ThirdPartyApp t WHERE t.isInstalled = true")
    long countInstalled();

    // Update-related queries
    @Query("SELECT t FROM ThirdPartyApp t WHERE t.updateAvailable = true ORDER BY t.appName")
    List<ThirdPartyApp> findAppsWithUpdatesAvailable();

    @Query("SELECT t FROM ThirdPartyApp t WHERE t.updateAvailable = true AND t.updateApproved = false ORDER BY t.appName")
    List<ThirdPartyApp> findAppsWithPendingUpdateApproval();

    @Query("SELECT t FROM ThirdPartyApp t WHERE t.updateAvailable = true AND t.updateApproved = true ORDER BY t.appName")
    List<ThirdPartyApp> findAppsWithApprovedUpdates();

    List<ThirdPartyApp> findByUpdatePolicyOrderByAppNameAsc(UpdatePolicy policy);

    @Query("SELECT t FROM ThirdPartyApp t WHERE t.isInstalled = true AND t.updatePolicy != 'DISABLED' ORDER BY t.appName")
    List<ThirdPartyApp> findAppsEligibleForUpdateCheck();

    @Query("SELECT COUNT(t) FROM ThirdPartyApp t WHERE t.updateAvailable = true")
    long countWithUpdatesAvailable();

    @Query("SELECT COUNT(t) FROM ThirdPartyApp t WHERE t.updateAvailable = true AND t.updateApproved = false")
    long countWithPendingUpdateApproval();

    // Rollback-related queries
    @Query("SELECT t FROM ThirdPartyApp t WHERE t.rollbackAvailable = true ORDER BY t.appName")
    List<ThirdPartyApp> findAppsWithRollbackAvailable();

    // Security-related queries
    @Query("SELECT t FROM ThirdPartyApp t WHERE t.requireSignature = true ORDER BY t.appName")
    List<ThirdPartyApp> findAppsRequiringSignature();

    // Expired approvals query
    @Query("SELECT t FROM ThirdPartyApp t WHERE t.updateApproved = true AND t.updateApprovalExpiresAt < CURRENT_TIMESTAMP")
    List<ThirdPartyApp> findAppsWithExpiredApprovals();
}
