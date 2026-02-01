package com.heronixedu.hub.model;

import com.heronixedu.hub.model.enums.ServerType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", unique = true, nullable = false, length = 100)
    private String configName;

    // Server Settings
    @Enumerated(EnumType.STRING)
    @Column(name = "server_type", length = 20)
    @Builder.Default
    private ServerType serverType = ServerType.AUTO;

    @Column(name = "local_server_path", length = 500)
    private String localServerPath;

    @Column(name = "cloud_server_url", length = 500)
    private String cloudServerUrl;

    // Proxy Settings
    @Column(name = "proxy_enabled")
    @Builder.Default
    private Boolean proxyEnabled = false;

    @Column(name = "proxy_host", length = 255)
    private String proxyHost;

    @Column(name = "proxy_port")
    private Integer proxyPort;

    @Column(name = "proxy_username", length = 100)
    private String proxyUsername;

    @Column(name = "proxy_password", length = 255)
    private String proxyPassword;

    @Column(name = "proxy_type", length = 20)
    @Builder.Default
    private String proxyType = "HTTP";

    // DNS Settings
    @Column(name = "custom_dns_enabled")
    @Builder.Default
    private Boolean customDnsEnabled = false;

    @Column(name = "primary_dns", length = 45)
    private String primaryDns;

    @Column(name = "secondary_dns", length = 45)
    private String secondaryDns;

    // Port Configuration
    @Column(name = "api_port")
    @Builder.Default
    private Integer apiPort = 8080;

    @Column(name = "websocket_port")
    @Builder.Default
    private Integer websocketPort = 8081;

    // SSL Settings
    @Column(name = "ssl_enabled")
    @Builder.Default
    private Boolean sslEnabled = false;

    @Column(name = "ssl_cert_path", length = 500)
    private String sslCertPath;

    @Column(name = "ssl_key_path", length = 500)
    private String sslKeyPath;

    @Column(name = "ssl_verify_hostname")
    @Builder.Default
    private Boolean sslVerifyHostname = true;

    // Firewall Info (display only)
    @Column(name = "required_outbound_ports", length = 255)
    @Builder.Default
    private String requiredOutboundPorts = "80,443,8080";

    // Status
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_connection_test")
    private LocalDateTime lastConnectionTest;

    @Column(name = "connection_status", length = 50)
    private String connectionStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
