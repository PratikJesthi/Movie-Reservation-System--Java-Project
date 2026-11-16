package com.moviebooking.repository;

import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<Booking> findByTicketToken(String ticketToken);

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND (b.expiresAt <= :now OR (b.expiresAt IS NULL AND b.createdAt <= :cutoff))")
    List<Booking> findStaleBookings(@Param("status") BookingStatus status,
                                   @Param("now") LocalDateTime now,
                                   @Param("cutoff") LocalDateTime cutoff);
}
