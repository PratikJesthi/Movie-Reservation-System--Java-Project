package com.moviebooking.controller;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.dto.CreateBookingRequest;
import com.moviebooking.dto.UserResponse;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.SessionAuth;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {
    private final BookingService bookingService;
    private final SessionAuth sessionAuth;

    public BookingController(BookingService bookingService, SessionAuth sessionAuth) {
        this.bookingService = bookingService;
        this.sessionAuth = sessionAuth;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request,
                                                  HttpSession session) {
        UserResponse user = sessionAuth.requireUser(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(user.id(), request));
    }

    @GetMapping("/users/{id}/bookings")
    public List<BookingResponse> userBookings(@PathVariable Long id, HttpSession session) {
        UserResponse user = sessionAuth.requireUser(session);
        if (!user.id().equals(id) && !"ADMIN".equals(user.role())) {
            // Do not reveal whether another user's id is valid.
            throw new ResourceNotFoundException("User " + id + " was not found.");
        }
        return bookingService.findForUser(id);
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse booking(@PathVariable Long id, HttpSession session) {
        UserResponse user = sessionAuth.requireUser(session);
        BookingResponse booking = bookingService.findById(id);
        if (!user.id().equals(bookingService.findOwnerId(id)) && !"ADMIN".equals(user.role())) {
            throw new ResourceNotFoundException("Booking " + id + " was not found.");
        }
        return booking;
    }

    @DeleteMapping("/bookings/{id}")
    public BookingResponse cancel(@PathVariable Long id, HttpSession session) {
        UserResponse user = sessionAuth.requireUser(session);
        return bookingService.cancel(id, user.id(), "ADMIN".equals(user.role()));
    }
}
