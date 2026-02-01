package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.KioskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KioskConfigRepository extends JpaRepository<KioskConfig, Long> {

    /**
     * Get the first (and typically only) kiosk configuration.
     */
    default Optional<KioskConfig> getConfig() {
        return findAll().stream().findFirst();
    }
}
