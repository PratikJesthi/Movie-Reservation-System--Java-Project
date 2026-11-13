package com.moviebooking.service;

import com.moviebooking.dto.SeatResponse;
import com.moviebooking.dto.ShowResponse;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.model.Seat;
import com.moviebooking.model.Show;
import com.moviebooking.repository.BookingSeatRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.ShowRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowService {
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository,
                       BookingSeatRepository bookingSeatRepository) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> findByMovie(Long movieId) {
        return showRepository.findByMovieIdOrderByShowTime(movieId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> seatsForShow(Long showId) {
        Show show = getShow(showId);
        Set<Long> bookedIds = Set.copyOf(bookingSeatRepository.findBookedSeatIds(showId));
        return seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(show.getScreen().getId())
                .stream().map(seat -> toSeatResponse(seat, bookedIds.contains(seat.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Show getShow(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show " + id + " was not found."));
    }

    private ShowResponse toResponse(Show show) {
        return new ShowResponse(show.getId(), show.getMovie().getId(), show.getScreen().getId(),
                show.getScreen().getTheatre().getName(), show.getScreen().getTheatre().getCity(),
                show.getScreen().getName(), show.getShowTime(), show.getPrice());
    }

    private SeatResponse toSeatResponse(Seat seat, boolean booked) {
        return new SeatResponse(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                booked ? "BOOKED" : "AVAILABLE");
    }
}
