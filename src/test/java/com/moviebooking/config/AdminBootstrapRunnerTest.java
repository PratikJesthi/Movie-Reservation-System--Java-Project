package com.moviebooking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moviebooking.model.User;
import com.moviebooking.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock private UserRepository userRepository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AdminBootstrapRunner(userRepository, encoder);
    }

    private void setVars(String email, String password) {
        ReflectionTestUtils.setField(runner, "adminEmail", email);
        ReflectionTestUtils.setField(runner, "adminPassword", password);
    }

    private void noAdminExists() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void creates_adminAccount_whenBothVarsSetAndNoAdminExists() throws Exception {
        setVars("admin@example.com", "strongpassword1");
        noAdminExists();

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        User saved = cap.getValue();
        assertThat(saved.getRole()).isEqualTo("ADMIN");
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        // BCrypt hash — verify it matches the original password
        assertThat(encoder.matches("strongpassword1", saved.getPasswordHash())).isTrue();
    }

    @Test
    void normalises_email_toLowercase() throws Exception {
        setVars("Admin@EXAMPLE.COM", "strongpassword1");
        noAdminExists();

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isEqualTo("admin@example.com");
    }

    // ── Does nothing cases ───────────────────────────────────────────────────

    @Test
    void doesNothing_whenEmailMissing() throws Exception {
        setVars("", "strongpassword1");
        runner.run(new DefaultApplicationArguments());
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothing_whenPasswordMissing() throws Exception {
        setVars("admin@example.com", "");
        runner.run(new DefaultApplicationArguments());
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothing_whenPasswordTooShort() throws Exception {
        setVars("admin@example.com", "short");   // < 12 chars
        runner.run(new DefaultApplicationArguments());
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothing_whenPasswordExactly11Chars() throws Exception {
        setVars("admin@example.com", "12characters"); // 12 chars — wait, let me count: 12chars=8... "12characters" = 12 chars → should work
        // Use an 11-char password to test the boundary
        setVars("admin@example.com", "elevenchar1"); // 11 chars
        runner.run(new DefaultApplicationArguments());
        verify(userRepository, never()).save(any());
    }

    @Test
    void creates_whenPasswordExactly12Chars() throws Exception {
        setVars("admin@example.com", "twelve_chars"); // 12 chars exactly
        noAdminExists();
        runner.run(new DefaultApplicationArguments());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void doesNothing_whenAdminAlreadyExists() throws Exception {
        setVars("admin@example.com", "strongpassword1");
        User existingAdmin = new User();
        existingAdmin.setRole("ADMIN");
        when(userRepository.findAll()).thenReturn(List.of(existingAdmin));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothing_whenEmailAlreadyRegisteredAsNonAdmin() throws Exception {
        setVars("admin@example.com", "strongpassword1");
        // No ADMIN exists, but the email is taken by a USER
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        when(userRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(true);

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNotCreate_duplicateAdmin_onRestart() throws Exception {
        setVars("admin@example.com", "strongpassword1");
        // Simulate second startup: an ADMIN already exists
        User existing = new User();
        existing.setRole("ADMIN");
        when(userRepository.findAll()).thenReturn(List.of(existing));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }
}
