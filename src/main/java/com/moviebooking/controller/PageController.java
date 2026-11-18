package com.moviebooking.controller;

import com.moviebooking.dto.UserResponse;
import com.moviebooking.service.SessionAuth;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class PageController {

    private final SessionAuth sessionAuth;

    public PageController(SessionAuth sessionAuth) {
        this.sessionAuth = sessionAuth;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/movies/{id}")
    public ModelAndView movie(@PathVariable Long id) {
        ModelAndView page = new ModelAndView("movie");
        page.addObject("movieId", id);
        return page;
    }

    @GetMapping("/shows/{id}/seats")
    public ModelAndView seats(@PathVariable Long id) {
        ModelAndView page = new ModelAndView("seats");
        page.addObject("showId", id);
        return page;
    }

    @GetMapping("/bookings/confirmation/{id}")
    public ModelAndView confirmation(@PathVariable Long id) {
        ModelAndView page = new ModelAndView("confirmation");
        page.addObject("bookingId", id);
        return page;
    }

    @GetMapping("/bookings")
    public String bookings() {
        return "bookings";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    /**
     * Server-side guard for the admin panel.
     * - Anonymous  → redirect to /admin/login
     * - Logged in as USER → 403 (requireAdmin throws ForbiddenException → error page)
     * - Logged in as ADMIN → serve admin.html
     */
    @GetMapping("/admin")
    public Object admin(HttpSession session) {
        Object stored = session.getAttribute(SessionAuth.USER_SESSION_KEY);
        if (!(stored instanceof UserResponse)) {
            return new RedirectView("/admin/login");
        }
        // Will throw ForbiddenException for non-admin, caught by GlobalExceptionHandler → error page
        sessionAuth.requireAdmin(session);
        return new ModelAndView("admin");
    }

    /** Admin login page — reachable by URL; not in public nav. */
    @GetMapping("/admin/login")
    public String adminLogin() {
        return "admin-login";
    }
}
