package com.heronixedu.hub.service;

import com.heronixedu.hub.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class TokenService {

    @Value("${heronix.jwt.secret-key-file}")
    private String secretKeyFile;

    @Value("${heronix.jwt.token-file}")
    private String tokenFile;

    @Value("${heronix.jwt.expiration-hours}")
    private int expirationHours;

    private SecretKey secretKey;

    /**
     * Generate JWT token for authenticated user
     */
    public String generateToken(User user) {
        try {
            SecretKey key = getOrCreateSecretKey();
            Instant now = Instant.now();
            Instant expiration = now.plus(expirationHours, ChronoUnit.HOURS);

            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .claim("userId", user.getId())
                    .claim("fullName", user.getFullName())
                    .claim("role", user.getRole())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiration))
                    .signWith(key)
                    .compact();

            log.info("Generated JWT token for user: {}", user.getUsername());
            return token;
        } catch (Exception e) {
            log.error("Error generating token", e);
            throw new RuntimeException("Failed to generate authentication token", e);
        }
    }

    /**
     * Save token to file for SSO
     */
    public void saveTokenToFile(String token) {
        try {
            Path path = Paths.get(tokenFile);
            Files.createDirectories(path.getParent());
            Files.writeString(path, token);

            // Set file permissions to rw------- (600) on Unix/Linux
            if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
                try {
                    Set<PosixFilePermission> perms = new HashSet<>();
                    perms.add(PosixFilePermission.OWNER_READ);
                    perms.add(PosixFilePermission.OWNER_WRITE);
                    Files.setPosixFilePermissions(path, perms);
                } catch (UnsupportedOperationException e) {
                    log.warn("POSIX file permissions not supported on this system");
                }
            }

            log.info("Token saved to file: {}", tokenFile);
        } catch (IOException e) {
            log.error("Error saving token to file", e);
            throw new RuntimeException("Failed to save authentication token", e);
        }
    }

    /**
     * Read token from file
     */
    public String readTokenFromFile() {
        try {
            Path path = Paths.get(tokenFile);
            if (!Files.exists(path)) {
                log.debug("Token file does not exist: {}", tokenFile);
                return null;
            }
            String token = Files.readString(path).trim();
            log.debug("Token read from file");
            return token;
        } catch (IOException e) {
            log.error("Error reading token from file", e);
            return null;
        }
    }

    /**
     * Validate token and extract user information
     */
    public User validateToken(String token) {
        try {
            SecretKey key = getOrCreateSecretKey();
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Check if token is expired
            if (claims.getExpiration().before(new Date())) {
                log.warn("Token has expired");
                return null;
            }

            User user = new User();
            user.setId(claims.get("userId", Long.class));
            user.setUsername(claims.getSubject());
            user.setFullName(claims.get("fullName", String.class));
            user.setRole(claims.get("role", String.class));

            log.info("Token validated for user: {}", user.getUsername());
            return user;
        } catch (Exception e) {
            log.error("Error validating token", e);
            return null;
        }
    }

    /**
     * Delete token file (logout)
     */
    public void deleteTokenFile() {
        try {
            Path path = Paths.get(tokenFile);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Token file deleted");
            }
        } catch (IOException e) {
            log.error("Error deleting token file", e);
            throw new RuntimeException("Failed to delete authentication token", e);
        }
    }

    /**
     * Get or create secret key for JWT signing
     */
    private SecretKey getOrCreateSecretKey() {
        if (secretKey != null) {
            return secretKey;
        }

        try {
            Path keyPath = Paths.get(secretKeyFile);

            if (Files.exists(keyPath)) {
                // Read existing key
                byte[] keyBytes = Files.readAllBytes(keyPath);
                secretKey = Keys.hmacShaKeyFor(keyBytes);
                log.info("Loaded existing secret key from: {}", secretKeyFile);
            } else {
                // Generate new key
                secretKey = Jwts.SIG.HS256.key().build();
                Files.createDirectories(keyPath.getParent());
                Files.write(keyPath, secretKey.getEncoded());

                // Set file permissions on Unix/Linux
                if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
                    try {
                        Set<PosixFilePermission> perms = new HashSet<>();
                        perms.add(PosixFilePermission.OWNER_READ);
                        perms.add(PosixFilePermission.OWNER_WRITE);
                        Files.setPosixFilePermissions(keyPath, perms);
                    } catch (UnsupportedOperationException e) {
                        log.warn("POSIX file permissions not supported on this system");
                    }
                }

                log.info("Generated new secret key and saved to: {}", secretKeyFile);
            }

            return secretKey;
        } catch (IOException e) {
            log.error("Error managing secret key", e);
            throw new RuntimeException("Failed to manage JWT secret key", e);
        }
    }
}
