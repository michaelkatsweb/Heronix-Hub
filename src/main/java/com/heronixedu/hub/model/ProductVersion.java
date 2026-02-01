package com.heronixedu.hub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_versions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "version"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "is_latest")
    @Builder.Default
    private Boolean isLatest = false;

    @Column(name = "min_hub_version", length = 50)
    private String minHubVersion;
}
