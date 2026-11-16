package com.moviebooking.service;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.dto.CreateBookingRequest;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingSeat;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.model.Payment;
import com.moviebooking.model.Seat;
import com.moviebooking.model.Show;
import com.moviebooking.model.User;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.BookingSeatRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final ShowService showService;
    private final PaymentService paymentService;

    public BookingService(BookingRepository bookingRepository, BookingSeatRepository bookingSeatRepository,
                          SeatRepository seatRepository, UserRepository userRepository,
                          ShowService showService, PaymentService paymentService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.showService = showService;
        this.paymentService = paymentService;
    }

    @Transactional
    public BookingResponse create(Long userId, CreateBookingRequest request) {
        Show show = showService.getShow(request.showId());
        List<Long> requestedIds = request.seatIds();
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new ConflictException("A seat was selected more than once.");
        }

        List<Seat> seats = seatRepository.findByIdIn(requestedIds);
        if (seats.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more selected seats were not found.");
        }
        if (seats.stream().anyMatch(seat -> !seat.getScreen().getId().equals(show.getScreen().getId()))) {
            throw new ConflictException("All selected seats must belong to the selected show's screen.");
        }

        Set<Long> bookedIds = Set.copyOf(bookingSeatRepository.findBookedSeatIds(show.getId()));
        if (uniqueIds.stream().anyMatch(bookedIds::contains)) {
            throw new ConflictException("One or more selected seats have already been booked.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " was not found."));
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShow(show);
        booking.setTotalAmount(show.getPrice().multiply(BigDecimal.valueOf(seats.size())));
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking = bookingRepository.saveAndFlush(booking);

        Booking finalBooking = booking;
        List<BookingSeat> bookingSeats = seats.stream().map(seat -> {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(finalBooking);
            bookingSeat.setShow(show);
            bookingSeat.setSeat(seat);
            return bookingSeat;
        }).toList();
        // The UNIQUE(show_id, seat_id) constraint protects simultaneous race condition requests.
        bookingSeatRepository.saveAllAndFlush(bookingSeats);

        // Generate hosted Razorpay Payment Link (server-side calculated amount, booking ID as reference_id)
        Payment payment = paymentService.createPaymentLink(booking);
        String paymentLinkUrl = payment != null ? payment.getPaymentLinkUrl() : null;

        List<String> seatLabels = bookingSeats.stream()
                .map(bs -> bs.getSeat().getRowLabel() + bs.getSeat().getSeatNumber())
                .sorted().toList();

        return toResponse(booking, seatLabels, paymentLinkUrl);
    }

    @Transactional
    public BookingResponse confirm(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new ConflictException("Cannot confirm a booking that is " + booking.getStatus().name().toLowerCase() + ".");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findForUser(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public Long findOwnerId(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .map(booking -> booking.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));
    }

    @Transactional
    public BookingResponse cancel(Long bookingId, Long requestingUserId, boolean administrator) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " was not found."));
        if (!administrator && !booking.getUser().getId().equals(requestingUserId)) {
            throw new ResourceNotFoundException("Booking " + bookingId + " was not found.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new ConflictException("This booking has already been " + booking.getStatus().name().toLowerCase() + ".");
        }
        List<String> seats = bookingSeatRepository.findByBookingId(bookingId).stream()
                .map(bookingSeat -> bookingSeat.getSeat().getRowLabel() + bookingSeat.getSeat().getSeatNumber())
                .sorted().toList();
        bookingSeatRepository.deleteByBookingId(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        return toResponse(booking, seats);
    }

    @Scheduled(fixedDelayString = "${app.booking.cleanup-interval-ms:60000}")
    @Transactional
    public void expireStaleBookings() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(10);
        List<Booking> staleBookings = bookingRepository.findStaleBookings(BookingStatus.PENDING, now, cutoff);
        for (Booking booking : staleBookings) {
            bookingSeatRepository.deleteByBookingId(booking.getId());
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setExpiresAt(null);
            bookingRepository.save(booking);
            log.info("Expired stale pending booking {} and released its seats.", booking.getId());
        }
    }

    private BookingResponse toResponse(Booking booking) {
        List<String> seats = bookingSeatRepository.findByBookingId(booking.getId()).stream()
                .map(bookingSeat -> bookingSeat.getSeat().getRowLabel() + bookingSeat.getSeat().getSeatNumber())
                .sorted().toList();
        return toResponse(booking, seats);
    }

    private BookingResponse toResponse(Booking booking, List<String> seats) {
        return toResponse(booking, seats, null);
    }

    private BookingResponse toResponse(Booking booking, List<String> seats, String paymentLinkUrl) {
        Show show = booking.getShow();
        return new BookingResponse(booking.getId(), show.getId(), show.getMovie().getTitle(),
                show.getScreen().getTheatre().getName(), show.getScreen().getName(), show.getShowTime(),
                seats, booking.getTotalAmount(), booking.getStatus().name(), booking.getCreatedAt(),
                paymentLinkUrl);
    }
}
