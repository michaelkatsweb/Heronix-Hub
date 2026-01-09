package com.heronixedu.hub.util;

import com.heronixedu.hub.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * TokenReader Utility Class
 *
 * This class is designed to be used by other Heronix products (SIS, Scheduler, Time, POS)
 * to read and validate the JWT token created by Heronix-Hub for Single Sign-On.
 *
 * Usage in other products:
 *
 * public void start(Stage stage) {
 *     if (TokenReader.hasValidToken()) {
 *         User user = TokenReader.getUserFromToken();
 *         showMainScreen(user); // Skip login screen!
 *     } else {
 *         showLoginScreen(); // Normal login
 *     }
 * }
 *
 * CRITICAL: All products must use the same SECRET_KEY file location for JWT validation.
 */
@Slf4j
public class TokenReader {

    private static final String TOKEN_FILE = System.getProperty("user.home") + "/.heronix/auth/token.jwt";
    private static final String SECRET_KEY_FILE = System.getProperty("user.home") + "/.heronix/config/secret.key";

    /**
     * Check if a valid token exists
     * @return true if a valid token exists, false otherwise
     */
    public static boolean hasValidToken() {
        try {
            String token = readTokenFromFile();
            if (token == null || token.isEmpty()) {
                return false;
            }

            User user = validateToken(token);
            return user != null;

        } catch (Exception e) {
            log.error("Error checking token validity", e);
            return false;
        }
    }

    /**
     * Get user information from token
     * @return User object if token is valid, null otherwise
     */
    public static User getUserFromToken() {
        try {
            String token = readTokenFromFile();
            if (token == null || token.isEmpty()) {
                return null;
            }

            return validateToken(token);

        } catch (Exception e) {
            log.error("Error getting user from token", e);
            return null;
        }
    }

    /**
     * Read token from file
     */
    private static String readTokenFromFile() {
        try {
            Path path = Paths.get(TOKEN_FILE);
            if (!Files.exists(path)) {
                log.debug("Token file does not exist: {}", TOKEN_FILE);
                return null;
            }
            return Files.readString(path).trim();
        } catch (IOException e) {
            log.error("Error reading token from file", e);
            return null;
        }
    }

    /**
     * Validate token and extract user information
     */
    private static User validateToken(String token) {
        try {
            SecretKey key = loadSecretKey();
            if (key == null) {
                log.error("Secret key not found");
                return null;
            }

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

            // Extract user information
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
     * Load secret key from file
     */
    private static SecretKey loadSecretKey() {
        try {
            Path keyPath = Paths.get(SECRET_KEY_FILE);
            if (!Files.exists(keyPath)) {
                log.error("Secret key file not found: {}", SECRET_KEY_FILE);
                return null;
            }

            byte[] keyBytes = Files.readAllBytes(keyPath);
            return Keys.hmacShaKeyFor(keyBytes);

        } catch (IOException e) {
            log.error("Error loading secret key", e);
            return null;
        }
    }
}
