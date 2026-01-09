package com.heronixedu.hub.repository;

import com.heronixedu.hub.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductCode(String productCode);

    List<Product> findAllByIsInstalledTrue();

    List<Product> findAllByOrderByProductNameAsc();
}
