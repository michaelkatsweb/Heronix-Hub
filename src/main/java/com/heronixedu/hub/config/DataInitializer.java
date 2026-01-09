package com.heronixedu.hub.config;

import com.heronixedu.hub.model.Product;
import com.heronixedu.hub.model.User;
import com.heronixedu.hub.repository.ProductRepository;
import com.heronixedu.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Override
    public void run(String... args) {
        initializeDefaultUser();
        initializeProducts();
    }

    private void initializeDefaultUser() {
        if (!userRepository.existsByUsername("admin")) {
            String hashedPassword = passwordEncoder.encode("admin123");
            User admin = new User("admin", hashedPassword, "Administrator", "ADMIN");
            admin.setIsActive(true);
            userRepository.save(admin);
            log.info("Created default admin user with password: admin123");
            log.info("Generated hash: {}", hashedPassword);
        } else {
            log.info("Admin user already exists");
        }
    }

    private void initializeProducts() {
        createProductIfNotExists("SIS", "Heronix-SIS", "./heronix-sis.jar");
        createProductIfNotExists("SCHEDULER", "Heronix-Scheduler", "./heronix-scheduler.jar");
        createProductIfNotExists("TIME", "Heronix-Time", "./heronix-time.jar");
        createProductIfNotExists("POS", "Heronix-POS", "./heronix-pos.jar");
        log.info("Initialized {} products", productRepository.count());
    }

    private void createProductIfNotExists(String code, String name, String path) {
        if (!productRepository.findByProductCode(code).isPresent()) {
            Product product = new Product(code, name, path);
            product.setIsInstalled(false);
            productRepository.save(product);
            log.info("Created product: {}", name);
        }
    }
}
