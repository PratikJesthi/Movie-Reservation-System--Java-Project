package com.moviebooking.config;

import com.moviebooking.model.User;
import com.moviebooking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a single ADMIN account on startup from environment variables.
 *
 * <p>Runs only when:
 * <ul>
 *   <li>ADMIN_EMAIL and ADMIN_PASSWORD are both set and non-blank, AND</li>
 *   <li>ADMIN_PASSWORD is at least 12 characters, AND</li>
 *   <li>No ADMIN-role user exists yet in the database.</li>
 * </ul>
 *
 * <p>No default or hardcoded credentials are ever used. If the variables are absent
 * or the password is too short, one clear INFO/WARN line is logged and nothing is created.
 *
 * <p>Alternative for local demos (no env vars): run this SQL against the H2 console:
 * <pre>
 *   -- Replace the hash below with the BCrypt hash of your chosen password
 *   INSERT INTO users (name, email, password_hash, role)
 *   VALUES ('Admin', 'admin@example.com', '$2a$10$...', 'ADMIN');
 * </pre>
 *
 * Order(2) runs after DatabaseSeeder (which has no @Order, defaulting to lowest precedence via
 * CommandLineRunner; this ApplicationRunner runs at the same phase but after).
 */
@Component
@Order(2)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean emailSet = adminEmail != null && !adminEmail.isBlank();
        boolean passwordSet = adminPassword != null && !adminPassword.isBlank();

        if (!emailSet || !passwordSet) {
            log.info("Admin bootstrap: ADMIN_EMAIL / ADMIN_PASSWORD not set — skipping admin account creation. " +
                     "Set both env vars to create an admin account on first startup.");
            return;
        }

        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            log.warn("Admin bootstrap: ADMIN_PASSWORD is shorter than {} characters — skipping admin account creation. " +
                     "Use a stronger password.", MIN_PASSWORD_LENGTH);
            return;
        }

        String normalised = adminEmail.trim().toLowerCase();

        boolean adminExists = userRepository.findAll().stream()
                .anyMatch(u -> "ADMIN".equals(u.getRole()));
        if (adminExists) {
            log.info("Admin bootstrap: an ADMIN account already exists — skipping creation.");
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(normalised)) {
            log.warn("Admin bootstrap: email {} is already registered as a non-admin user. " +
                     "Cannot promote via bootstrap — use the H2 console or DB migration.", normalised);
            return;
        }

        User admin = new User();
        admin.setName("Administrator");
        admin.setEmail(normalised);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        log.info("Admin bootstrap: created ADMIN account for {} (email length={}).",
                normalised, normalised.length());
    }
}
