package com.moviebooking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviebooking.dto.UserResponse;
import com.moviebooking.exception.UnauthorizedException;
import com.moviebooking.service.AdminLoginThrottleService;
import com.moviebooking.service.AuthService;
import com.moviebooking.service.SessionAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    private MockMvc mockMvc;

    @Mock private AuthService authService;
    @Mock private SessionAuth sessionAuth;

    private AdminLoginThrottleService throttle;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        throttle = new AdminLoginThrottleService();
        // Inject @Value defaults manually since we're not in a Spring context
        ReflectionTestUtils.setField(throttle, "maxAttempts", 5);
        ReflectionTestUtils.setField(throttle, "windowMinutes", 15L);

        controller = new AuthController(authService, sessionAuth, throttle);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── Success ──────────────────────────────────────────────────────────────

    @Test
    void adminLogin_success_returnsAdminUser() throws Exception {
        UserResponse admin = new UserResponse(1L, "Admin", "admin@test.com", "ADMIN");
        when(authService.login(any())).thenReturn(admin);

        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"secret123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("admin@test.com"));
    }

    @Test
    void adminLogin_success_clearsThrottleCounter() throws Exception {
        UserResponse admin = new UserResponse(1L, "Admin", "admin@test.com", "ADMIN");
        when(authService.login(any())).thenReturn(admin);

        // Record 3 failures first directly on the throttle service
        throttle.recordFailure("admin@test.com", "127.0.0.1");
        throttle.recordFailure("admin@test.com", "127.0.0.1");
        throttle.recordFailure("admin@test.com", "127.0.0.1");
        assertThat(throttle.isLocked("admin@test.com", "127.0.0.1")).isFalse(); // 3 < 5

        // Successful login should clear them
        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"secret123456\"}"))
                .andExpect(status().isOk());

        // Counter cleared — not locked
        assertThat(throttle.isLocked("admin@test.com", "127.0.0.1")).isFalse();
    }

    // ── Generic 401 for all failure cases ───────────────────────────────────

    @Test
    void adminLogin_wrongPassword_returns401WithGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("bad"));

        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid administrator credentials."));
    }

    @Test
    void adminLogin_unknownEmail_returns401WithGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("bad"));

        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.com\",\"password\":\"pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid administrator credentials."));
    }

    @Test
    void adminLogin_nonAdminUser_returns401WithSameGenericMessage() throws Exception {
        // A valid USER account — must be indistinguishable from wrong credentials
        UserResponse normalUser = new UserResponse(2L, "Bob", "bob@test.com", "USER");
        when(authService.login(any())).thenReturn(normalUser);

        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@test.com\",\"password\":\"pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid administrator credentials."));
    }

    // ── Throttle behaviour ───────────────────────────────────────────────────

    @Test
    void adminLogin_throttle_lockoutAfter5Failures() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("bad"));
        String body = "{\"email\":\"hacker@test.com\",\"password\":\"wrong\"}";

        // 5 failures → all 401
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/admin-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt → 429 (throttle isLocked check fires before authService)
        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many attempts. Try again later."));
    }

    @Test
    void adminLogin_throttle_successResetsCounter() throws Exception {
        // Pre-load 4 failures directly
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("admin2@test.com", "127.0.0.1");
        }
        // Not yet locked
        assertThat(throttle.isLocked("admin2@test.com", "127.0.0.1")).isFalse();

        // Successful login
        UserResponse admin = new UserResponse(1L, "Admin", "admin2@test.com", "ADMIN");
        when(authService.login(any())).thenReturn(admin);
        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin2@test.com\",\"password\":\"correct123456\"}"))
                .andExpect(status().isOk());

        // Counter cleared — need 5 new failures to lock
        assertThat(throttle.isLocked("admin2@test.com", "127.0.0.1")).isFalse();
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("admin2@test.com", "127.0.0.1");
        }
        assertThat(throttle.isLocked("admin2@test.com", "127.0.0.1")).isFalse(); // 4 < 5
        throttle.recordFailure("admin2@test.com", "127.0.0.1"); // 5th
        assertThat(throttle.isLocked("admin2@test.com", "127.0.0.1")).isTrue();  // now locked
    }

    @Test
    void adminLogin_throttle_lockedReturns429BeforeCredentialCheck() throws Exception {
        // Lock the counter directly
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("locked@test.com", "127.0.0.1");
        }
        assertThat(throttle.isLocked("locked@test.com", "127.0.0.1")).isTrue();

        // authService should NOT be called — throttle fires first
        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"locked@test.com\",\"password\":\"any\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
