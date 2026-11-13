package com.moviebooking.controller;

import com.moviebooking.dto.LoginRequest;
import com.moviebooking.dto.RegisterRequest;
import com.moviebooking.dto.UserResponse;
import com.moviebooking.service.AuthService;
import com.moviebooking.service.SessionAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
    private final AuthService authService;
    private final SessionAuth sessionAuth;

    public AuthController(AuthService authService, SessionAuth sessionAuth) {
        this.authService = authService;
        this.sessionAuth = sessionAuth;
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
}
