package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.DownloadSourcePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DownloadSourcePolicyRepository extends JpaRepository<DownloadSourcePolicy, Long> {

    List<DownloadSourcePolicy> findByIsActiveTrueOrderByPriorityAsc();

    List<DownloadSourcePolicy> findByPolicyTypeAndIsActiveTrueOrderByPriorityAsc(
            DownloadSourcePolicy.PolicyType policyType);

    @Query("SELECT p FROM DownloadSourcePolicy p WHERE p.isActive = true ORDER BY p.priority ASC")
    List<DownloadSourcePolicy> findAllActivePolicies();

    boolean existsByPatternIgnoreCase(String pattern);

    List<DownloadSourcePolicy> findAllByOrderByPriorityAsc();
}
