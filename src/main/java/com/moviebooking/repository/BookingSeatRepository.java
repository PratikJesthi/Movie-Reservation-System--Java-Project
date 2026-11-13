package com.moviebooking.repository;

import com.moviebooking.model.BookingSeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    @Query("select bs.seat.id from BookingSeat bs where bs.show.id = :showId "
            + "and bs.booking.status = 'CONFIRMED'")
    List<Long> findBookedSeatIds(@Param("showId") Long showId);

    @Query("select bs from BookingSeat bs where bs.booking.id = :bookingId order by bs.seat.rowLabel, bs.seat.seatNumber")
    List<BookingSeat> findByBookingId(@Param("bookingId") Long bookingId);

    @Modifying
    @Query("delete from BookingSeat bs where bs.booking.id = :bookingId")
    void deleteByBookingId(@Param("bookingId") Long bookingId);
}
