package com.moviebooking.service;

import com.moviebooking.dto.BookingResponse;
import com.moviebooking.event.BookingConfirmedEvent;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.repository.BookingRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

@Service
public class TicketMailService {
    private static final Logger log = LoggerFactory.getLogger(TicketMailService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final JavaMailSender mailSender;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final TicketQrService qrService;

    @Value("${spring.mail.username:}")
    private String mailUser;
    @Value("${spring.mail.password:}")
    private String mailPassword;

    public TicketMailService(JavaMailSender mailSender, BookingRepository bookingRepository,
                             BookingService bookingService, TicketQrService qrService) {
        this.mailSender = mailSender;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.qrService = qrService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            sendTicket(event.bookingId());
        } catch (Exception ex) {
            // A mail failure must never affect the booking.
            log.error("Ticket email failed for booking {}: {}", event.bookingId(), ex.getMessage(), ex);
        }
    }

    private void sendTicket(Long bookingId) throws Exception {
        if (unset(mailUser) || unset(mailPassword)) {
            log.warn("Mail not configured (MAIL_USER / MAIL_APP_PASSWORD); skipping ticket email for booking {}", bookingId);
            return;
        }
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED
                || booking.getTicketToken() == null || booking.getTicketEmailSentAt() != null) {
            return;
        }
        BookingResponse d = bookingService.findById(bookingId);
        String token = booking.getTicketToken();

        String html = "<h2>Your ticket is confirmed 🎬</h2>"
                + "<p><b>" + HtmlUtils.htmlEscape(d.movieTitle()) + "</b><br>"
                + d.showTime().format(WHEN) + "<br>"
                + HtmlUtils.htmlEscape(d.theatreName() + " · " + d.screenName()) + "<br>"
                + "Seats: " + HtmlUtils.htmlEscape(String.join(", ", d.seats())) + "<br>"
                + "Booking #" + d.id() + " · Total paid ₹" + d.totalAmount() + "</p>"
                + "<p>Show this QR at the entrance:</p>"
                + "<img src='cid:ticketqr' width='220' height='220' alt='Ticket QR'>"
                + "<p style='color:#666;font-size:12px'>" + qrService.verifyUrl(token) + "</p>";

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
        h.setFrom(mailUser);
        h.setTo(booking.getUser().getEmail());
        h.setSubject("Your ticket: " + d.movieTitle());
        h.setText(html, true);
        h.addInline("ticketqr", new ByteArrayResource(qrService.qrPng(token)), "image/png");
        mailSender.send(msg);

        booking.setTicketEmailSentAt(LocalDateTime.now());
        bookingRepository.save(booking);
        log.info("Ticket email sent for booking {}", bookingId);
    }

    private boolean unset(String v) {
        return v == null || v.isBlank() || v.trim().equals("...");
    }
}