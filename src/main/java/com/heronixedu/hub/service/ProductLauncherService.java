package com.heronixedu.hub.service;

import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductLauncherService {

    private final ProductRepository productRepository;

    /**
     * Launch a product by its product code
     */
    public void launchProduct(String productCode) {
        try {
            log.info("Launching product: {}", productCode);

            Product product = productRepository.findByProductCode(productCode)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + productCode));

            // Get executable path
            String executablePath = product.getExecutablePath();
            File jarFile = new File(executablePath);

            // Check if JAR file exists
            if (!jarFile.exists()) {
                log.error("Product JAR not found: {}", executablePath);
                throw new RuntimeException("Product not installed: " + product.getProductName() +
                        "\nExpected location: " + executablePath);
            }

            // Build command to launch the JAR
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-jar",
                    jarFile.getAbsolutePath()
            );

            // Set working directory to JAR location
            processBuilder.directory(jarFile.getParentFile());

            // Redirect output (optional, for debugging)
            processBuilder.inheritIO();

            // Launch the process
            Process process = processBuilder.start();

            // Update last_launched timestamp
            product.setLastLaunched(LocalDateTime.now());
            product.setIsInstalled(true);
            productRepository.save(product);

            log.info("Product launched successfully: {} (PID: {})",
                    product.getProductName(), process.pid());

        } catch (IOException e) {
            log.error("Error launching product: {}", productCode, e);
            throw new RuntimeException("Failed to launch product: " + e.getMessage(), e);
        }
    }

    /**
     * Get all products (for displaying tiles)
     */
    public List<Product> getAllProducts() {
        return productRepository.findAllByOrderByProductNameAsc();
    }

    /**
     * Get only installed products
     */
    public List<Product> getInstalledProducts() {
        return productRepository.findAllByIsInstalledTrue();
    }

    /**
     * Check if a product is installed
     */
    public boolean isProductInstalled(String productCode) {
        return productRepository.findByProductCode(productCode)
                .map(product -> {
                    File jarFile = new File(product.getExecutablePath());
                    boolean exists = jarFile.exists();

                    // Update installation status if it changed
                    if (product.getIsInstalled() != exists) {
                        product.setIsInstalled(exists);
                        productRepository.save(product);
                    }

                    return exists;
                })
                .orElse(false);
    }

    /**
     * Update product installation status
     */
    public void updateProductInstallationStatus() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            File jarFile = new File(product.getExecutablePath());
            boolean exists = jarFile.exists();

            if (product.getIsInstalled() != exists) {
                product.setIsInstalled(exists);
                productRepository.save(product);
                log.info("Updated installation status for {}: {}",
                        product.getProductCode(), exists);
            }
        }
    }
}
