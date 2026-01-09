package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", unique = true, nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "executable_path", nullable = false)
    private String executablePath;

    @Column(name = "is_installed")
    private Boolean isInstalled = false;

    @Column(name = "last_launched")
    private LocalDateTime lastLaunched;

    public Product(String productCode, String productName, String executablePath) {
        this.productCode = productCode;
        this.productName = productName;
        this.executablePath = executablePath;
        this.isInstalled = false;
    }
}
