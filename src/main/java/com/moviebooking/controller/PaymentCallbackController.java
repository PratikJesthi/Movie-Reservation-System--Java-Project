package com.moviebooking.controller;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.model.Booking;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thymeleaf-backed controller handling the Razorpay Payment Link callback redirect.
 * <p>
 * On callback, it:
 * 1. Verifies the callback HMAC-SHA256 signature using the key secret.
 * 2. Fetches the payment link status directly from the Razorpay API server-side.
 * 3. Only if both checks pass, marks the Payment PAID and the Booking CONFIRMED.
 * 4. Ensures idempotent execution: multiple hits will not double-confirm or error.
 * </p>
 */
@Controller
@RequestMapping("/payments")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    private final PaymentService paymentService;
    private final BookingService bookingService;

    public PaymentCallbackController(PaymentService paymentService, BookingService bookingService) {
        this.paymentService = paymentService;
        this.bookingService = bookingService;
    }

    /**
     * GET /payments/callback
     * Callback URL configured in Razorpay Payment Link.
     */
    @GetMapping("/callback")
    public String handleCallback(
            @RequestParam(value = "razorpay_payment_id", required = false) String paymentId,
            @RequestParam(value = "razorpay_payment_link_id", required = false) String paymentLinkId,
            @RequestParam(value = "razorpay_payment_link_reference_id", required = false) String referenceId,
            @RequestParam(value = "razorpay_payment_link_status", required = false) String status,
            @RequestParam(value = "razorpay_signature", required = false) String signature,
            Model model) {

        log.info("Received Razorpay payment link callback: plinkId={}, refId={}, status={}, paymentId={}",
                paymentLinkId, referenceId, status, paymentId);

        try {
            Booking booking = paymentService.confirmFromCallback(
                    paymentLinkId, referenceId, status, paymentId, signature);

            BookingResponse details = bookingService.findById(booking.getId());

            model.addAttribute("success", true);
            model.addAttribute("booking", details);
            model.addAttribute("movieTitle", details.movieTitle());
            model.addAttribute("showTime", details.showTime());
            model.addAttribute("theatreName", details.theatreName());
            model.addAttribute("screenName", details.screenName());
            model.addAttribute("amount", details.totalAmount());
            model.addAttribute("paymentId", paymentId);
            model.addAttribute("ticketToken", booking.getTicketToken());
            return "payment-callback";

        } catch (Exception ex) {
            log.error("Payment confirmation failed on callback for referenceId={}: {}", referenceId, ex.getMessage(), ex);
            model.addAttribute("success", false);
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("referenceId", referenceId);
            return "payment-callback";
        }
    }
}
