package com.moviebooking.service;

import com.moviebooking.dto.UserResponse;
import com.moviebooking.exception.ForbiddenException;
import com.moviebooking.exception.UnauthorizedException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class SessionAuth {
    public static final String USER_SESSION_KEY = "loggedInUser";

    public UserResponse requireUser(HttpSession session) {
        Object user = session.getAttribute(USER_SESSION_KEY);
        if (user instanceof UserResponse userResponse) {
            return userResponse;
        }
        throw new UnauthorizedException("Please log in to continue.");
    }

    public UserResponse requireAdmin(HttpSession session) {
        UserResponse user = requireUser(session);
        if (!"ADMIN".equals(user.role())) {
            throw new ForbiddenException("Administrator access is required.");
        }
        return user;
    }
}
