package com.moviebooking.repository;

import com.moviebooking.model.Show;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByMovieIdOrderByShowTime(Long movieId);
}
