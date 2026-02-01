package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVersionRepository extends JpaRepository<ProductVersion, Long> {
    List<ProductVersion> findByProductIdOrderByReleaseDateDesc(Long productId);
    Optional<ProductVersion> findByProductIdAndIsLatestTrue(Long productId);
    Optional<ProductVersion> findByProductIdAndVersion(Long productId, String version);
}
