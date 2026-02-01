package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.NetworkConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NetworkConfigRepository extends JpaRepository<NetworkConfig, Long> {
    Optional<NetworkConfig> findByConfigName(String configName);
    Optional<NetworkConfig> findByIsActiveTrue();
}
