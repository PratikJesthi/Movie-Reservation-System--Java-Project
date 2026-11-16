package com.moviebooking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.model.Movie;
import com.moviebooking.model.Screen;
import com.moviebooking.model.Show;
import com.moviebooking.model.Theatre;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private PaymentCallbackController paymentCallbackController;

    private Booking booking;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentCallbackController).build();

        Movie movie = new Movie();
        movie.setTitle("Inception");

        Theatre theatre = new Theatre();
        theatre.setName("PVR Cinemas");

        Screen screen = new Screen();
        screen.setName("Audi 1");
        screen.setTheatre(theatre);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setShowTime(LocalDateTime.of(2026, 11, 20, 20, 0));

        booking = new Booking();
        booking.setId(10L);
        booking.setShow(show);
        booking.setTotalAmount(new BigDecimal("300.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
    }

    @Test
    void callbackReturnsSuccessViewWhenConfirmationSucceeds() throws Exception {
        when(paymentService.confirmFromCallback(
                eq("plink_123"), eq("10"), eq("paid"), eq("pay_abc"), eq("sig_valid")))
                .thenReturn(booking);

        BookingResponse responseDto = new BookingResponse(
                10L, 1L, "Inception", "PVR Cinemas", "Audi 1",
                LocalDateTime.of(2026, 11, 20, 20, 0), List.of("A1"),
                new BigDecimal("300.00"), "CONFIRMED", LocalDateTime.now(), null);

        when(bookingService.findById(10L)).thenReturn(responseDto);

        mockMvc.perform(get("/payments/callback")
                        .param("razorpay_payment_id", "pay_abc")
                        .param("razorpay_payment_link_id", "plink_123")
                        .param("razorpay_payment_link_reference_id", "10")
                        .param("razorpay_payment_link_status", "paid")
                        .param("razorpay_signature", "sig_valid"))
                .andExpect(status().isOk())
                .andExpect(view().name("payment-callback"))
                .andExpect(model().attribute("success", true))
                .andExpect(model().attribute("movieTitle", "Inception"))
                .andExpect(model().attribute("paymentId", "pay_abc"));
    }

    @Test
    void callbackReturnsErrorViewWhenConfirmationFails() throws Exception {
        when(paymentService.confirmFromCallback(any(), any(), any(), any(), any()))
                .thenThrow(new ConflictException("Invalid signature"));

        mockMvc.perform(get("/payments/callback")
                        .param("razorpay_payment_id", "pay_abc")
                        .param("razorpay_payment_link_id", "plink_123")
                        .param("razorpay_payment_link_reference_id", "10")
                        .param("razorpay_payment_link_status", "paid")
                        .param("razorpay_signature", "sig_bad"))
                .andExpect(status().isOk())
                .andExpect(view().name("payment-callback"))
                .andExpect(model().attribute("success", false))
                .andExpect(model().attribute("errorMessage", "Invalid signature"));
    }
}
