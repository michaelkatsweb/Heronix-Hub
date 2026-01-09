package com.heronixedu.hub.service;

import com.heronixedu.hub.model.User;
import com.heronixedu.hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * Authenticate user with username and password
     * @return User object if successful, null otherwise
     */
    public User login(String username, String password) {
        try {
            log.info("Login attempt for user: {}", username);

            // Find user by username
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Invalid username or password"));

            // Check if user is active
            if (!user.getIsActive()) {
                log.warn("Login attempt for inactive user: {}", username);
                throw new RuntimeException("User account is inactive");
            }

            // Verify password
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                log.warn("Invalid password for user: {}", username);
                throw new RuntimeException("Invalid username or password");
            }

            // Generate and save token
            String token = tokenService.generateToken(user);
            tokenService.saveTokenToFile(token);

            log.info("User logged in successfully: {}", username);
            return user;

        } catch (RuntimeException e) {
            log.error("Login failed for user: {}", username, e);
            throw e;
        }
    }

    /**
     * Logout user by deleting token file
     */
    public void logout() {
        try {
            tokenService.deleteTokenFile();
            log.info("User logged out successfully");
        } catch (Exception e) {
            log.error("Error during logout", e);
            throw new RuntimeException("Failed to logout", e);
        }
    }

    /**
     * Check if there's a valid existing token
     */
    public User checkExistingToken() {
        String token = tokenService.readTokenFromFile();
        if (token == null || token.isEmpty()) {
            return null;
        }
        return tokenService.validateToken(token);
    }

    /**
     * Hash password using BCrypt
     */
    public String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }
}
