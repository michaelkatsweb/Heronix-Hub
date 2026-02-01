package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.InstallationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstallationLogRepository extends JpaRepository<InstallationLog, Long> {
    List<InstallationLog> findByProductIdOrderByStartedAtDesc(Long productId);
    List<InstallationLog> findByUserIdOrderByStartedAtDesc(Long userId);
    List<InstallationLog> findByStatus(String status);
    List<InstallationLog> findTop10ByOrderByStartedAtDesc();
}
