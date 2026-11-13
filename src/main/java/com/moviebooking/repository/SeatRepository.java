package com.moviebooking.repository;

import com.moviebooking.model.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScreenIdOrderByRowLabelAscSeatNumberAsc(Long screenId);
    List<Seat> findByIdIn(List<Long> ids);
}
