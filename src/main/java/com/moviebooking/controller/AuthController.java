package com.moviebooking.controller;

import com.moviebooking.dto.LoginRequest;
import com.moviebooking.dto.RegisterRequest;
import com.moviebooking.dto.UserResponse;
import com.moviebooking.service.AdminLoginThrottleService;
import com.moviebooking.service.AuthService;
import com.moviebooking.service.SessionAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String GENERIC_ADMIN_FAIL = "Invalid administrator credentials.";
    private static final String THROTTLE_MSG = "Too many attempts. Try again later.";

    private final AuthService authService;
    private final SessionAuth sessionAuth;
    private final AdminLoginThrottleService throttle;

    public AuthController(AuthService authService, SessionAuth sessionAuth,
                          AdminLoginThrottleService throttle) {
        this.authService = authService;
        this.sessionAuth = sessionAuth;
        this.throttle = throttle;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        UserResponse user = authService.register(request);
        session.setAttribute(SessionAuth.USER_SESSION_KEY, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        HttpSession oldSession = servletRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate(); // Replace any previous login and renew the session id.
        }
        UserResponse user = authService.login(request);
        servletRequest.getSession(true).setAttribute(SessionAuth.USER_SESSION_KEY, user);
        return user;
    }

    @GetMapping("/me")
    public UserResponse me(HttpSession session) {
        return sessionAuth.requireUser(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    /**
     * Admin-only login. All failure cases (wrong password, unknown email, non-admin account)
     * return the identical 401 body so the endpoint never reveals whether an account exists.
     *
     * <p>Rate-limited: after {@code app.admin.login.max-attempts} failures within
     * {@code app.admin.login.window-minutes} minutes the endpoint returns 429.
     */
    @PostMapping("/admin-login")
    public ResponseEntity<?> adminLogin(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest servletRequest) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        String ip = extractIp(servletRequest);

        // 1. Throttle check
        if (throttle.isLocked(email, ip)) {
            log.warn("Admin login LOCKED_OUT email={} ip={}", email, ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", THROTTLE_MSG));
        }

        // 2. Credential + role check — all failures get the same generic message
        UserResponse user = null;
        try {
            user = authService.login(request);
        } catch (Exception ignored) {
            // fall through to failure handling
        }

        if (user == null || !"ADMIN".equals(user.role())) {
            int failures = throttle.recordFailure(email, ip);
            if (failures >= throttle.getMaxAttempts()) {
                log.warn("Admin login LOCKOUT_TRIGGERED email={} ip={} attempts={}", email, ip, failures);
            } else {
                log.warn("Admin login FAILED email={} ip={} attempts={}", email, ip, failures);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", GENERIC_ADMIN_FAIL));
        }

        // 3. Success — session fixation prevention: invalidate old session, start fresh
        HttpSession old = servletRequest.getSession(false);
        if (old != null) old.invalidate();
        servletRequest.getSession(true).setAttribute(SessionAuth.USER_SESSION_KEY, user);
        throttle.clearOnSuccess(email, ip);
        log.info("Admin login SUCCESS email={} ip={}", email, ip);
        return ResponseEntity.ok(user);
    }

    private static String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
