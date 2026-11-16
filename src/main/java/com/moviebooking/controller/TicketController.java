package com.moviebooking.controller;

import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.TicketQrService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class TicketController {
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final TicketQrService qrService;

    public TicketController(BookingRepository bookingRepository, BookingService bookingService,
                            TicketQrService qrService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.qrService = qrService;
    }

    /** Read-only: scanning never changes state. */
    @GetMapping("/verify/{token}")
    public String verify(@PathVariable String token, Model model) {
        Booking booking = bookingRepository.findByTicketToken(token).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED) {
            model.addAttribute("state", "INVALID");
            return "verify";
        }
        model.addAttribute("state", booking.getCheckedInAt() == null ? "VALID" : "USED");
        model.addAttribute("checkedInAt", booking.getCheckedInAt());
        model.addAttribute("booking", bookingService.findById(booking.getId()));
        return "verify";
    }

    @GetMapping("/ticket/{token}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable String token) {
        if (bookingRepository.findByTicketToken(token).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(qrService.qrPng(token));
    }
}