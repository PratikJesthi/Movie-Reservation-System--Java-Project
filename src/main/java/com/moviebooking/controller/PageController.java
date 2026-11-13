package com.moviebooking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PageController {
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

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
