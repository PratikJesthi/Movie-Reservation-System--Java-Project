package com.moviebooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.dto.CreateBookingRequest;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingSeat;
import com.moviebooking.model.Movie;
import com.moviebooking.model.Screen;
import com.moviebooking.model.Seat;
import com.moviebooking.model.Show;
import com.moviebooking.model.Theatre;
import com.moviebooking.model.User;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.BookingSeatRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShowService showService;
    @InjectMocks private BookingService bookingService;

    private User user;
    private Show show;
    private Seat seatA1;
    private Seat seatA2;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setName("Sam");

        Movie movie = new Movie();
        movie.setId(4L);
        movie.setTitle("Sample Movie");

        Theatre theatre = new Theatre();
        theatre.setId(2L);
        theatre.setName("Central Cinema");

        Screen screen = new Screen();
        screen.setId(3L);
        screen.setName("Screen 1");
        screen.setTheatre(theatre);

        show = new Show();
        show.setId(9L);
        show.setMovie(movie);
        show.setScreen(screen);
        show.setShowTime(LocalDateTime.of(2026, 11, 20, 19, 30));
        show.setPrice(new BigDecimal("250.00"));

        seatA1 = seat(101L, screen, 1);
        seatA2 = seat(102L, screen, 2);
    }

    @Test
    void createsBookingAndTotalsSelectedSeats() {
        when(showService.getShow(9L)).thenReturn(show);
        when(seatRepository.findByIdIn(List.of(101L, 102L))).thenReturn(List.of(seatA1, seatA2));
        when(bookingSeatRepository.findBookedSeatIds(9L)).thenReturn(List.of());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setId(51L);
            return saved;
        });

        BookingResponse response = bookingService.create(7L, new CreateBookingRequest(9L, List.of(101L, 102L)));

        assertEquals(51L, response.id());
        assertEquals(new BigDecimal("500.00"), response.totalAmount());
        assertEquals(List.of("A1", "A2"), response.seats());
        assertEquals("CONFIRMED", response.status());
        verify(bookingSeatRepository).saveAllAndFlush(any());
    }

    @Test
    void rejectsSeatThatWasAlreadyBooked() {
        when(showService.getShow(9L)).thenReturn(show);
        when(seatRepository.findByIdIn(List.of(101L))).thenReturn(List.of(seatA1));
        when(bookingSeatRepository.findBookedSeatIds(9L)).thenReturn(List.of(101L));

        assertThrows(ConflictException.class,
                () -> bookingService.create(7L, new CreateBookingRequest(9L, List.of(101L))));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancellationRemovesSeatReservationsAndMarksBookingCancelled() {
        Booking booking = new Booking();
        booking.setId(51L);
        booking.setUser(user);
        booking.setShow(show);
        booking.setTotalAmount(new BigDecimal("250.00"));
        booking.setStatus("CONFIRMED");

        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBooking(booking);
        bookingSeat.setShow(show);
        bookingSeat.setSeat(seatA1);
        when(bookingRepository.findById(51L)).thenReturn(Optional.of(booking));
        when(bookingSeatRepository.findByBookingId(51L)).thenReturn(List.of(bookingSeat));

        BookingResponse response = bookingService.cancel(51L, 7L, false);

        assertEquals("CANCELLED", response.status());
        assertEquals(List.of("A1"), response.seats());
        verify(bookingSeatRepository).deleteByBookingId(51L);
        verify(bookingRepository).findById(51L);
        assertEquals("CANCELLED", booking.getStatus());
    }

    private Seat seat(Long id, Screen screen, int number) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setScreen(screen);
        seat.setRowLabel("A");
        seat.setSeatNumber(number);
        return seat;
    }
}
