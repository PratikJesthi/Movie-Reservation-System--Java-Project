package com.moviebooking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.TicketQrService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private TicketQrService qrService;

    @Test
    void verifyUnknownTokenReturnsInvalidState() throws Exception {
        when(bookingRepository.findByTicketToken("unknown-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/verify/unknown-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify"))
                .andExpect(model().attribute("state", "INVALID"));
    }

    @Test
    void verifyPendingBookingReturnsInvalidState() throws Exception {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findByTicketToken("pending-token")).thenReturn(Optional.of(booking));

        mockMvc.perform(get("/verify/pending-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify"))
                .andExpect(model().attribute("state", "INVALID"));
    }

    @Test
    void verifyConfirmedBookingNotCheckedInReturnsValidState() throws Exception {
        Booking booking = new Booking();
        booking.setId(2L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCheckedInAt(null);
        when(bookingRepository.findByTicketToken("valid-token")).thenReturn(Optional.of(booking));
        when(bookingService.findById(2L)).thenReturn(new BookingResponse(
                2L, 10L, "Interstellar", "IMAX", "Hall 1",
                LocalDateTime.now().plusDays(1), List.of("A1"),
                BigDecimal.valueOf(250), "CONFIRMED", null));

        mockMvc.perform(get("/verify/valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify"))
                .andExpect(model().attribute("state", "VALID"))
                .andExpect(model().attributeExists("booking"));
    }

    @Test
    void verifyConfirmedBookingAlreadyCheckedInReturnsUsedState() throws Exception {
        LocalDateTime checkInTime = LocalDateTime.now().minusHours(1);
        Booking booking = new Booking();
        booking.setId(3L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCheckedInAt(checkInTime);
        when(bookingRepository.findByTicketToken("used-token")).thenReturn(Optional.of(booking));
        when(bookingService.findById(3L)).thenReturn(new BookingResponse(
                3L, 10L, "Interstellar", "IMAX", "Hall 1",
                LocalDateTime.now().plusDays(1), List.of("A1"),
                BigDecimal.valueOf(250), "CONFIRMED", null));

        mockMvc.perform(get("/verify/used-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify"))
                .andExpect(model().attribute("state", "USED"))
                .andExpect(model().attribute("checkedInAt", checkInTime));
    }

    @Test
    void qrPngReturns404WhenTokenNotFound() throws Exception {
        when(bookingRepository.findByTicketToken("missing-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/ticket/missing-token/qr.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void qrPngReturnsImageWhenTokenFound() throws Exception {
        Booking booking = new Booking();
        booking.setId(4L);
        when(bookingRepository.findByTicketToken("valid-token")).thenReturn(Optional.of(booking));
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        when(qrService.qrPng("valid-token")).thenReturn(fakePng);

        mockMvc.perform(get("/ticket/valid-token/qr.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(fakePng));
    }
}
