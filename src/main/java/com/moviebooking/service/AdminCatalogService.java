package com.moviebooking.service;

import com.moviebooking.dto.ScreenOption;
import com.moviebooking.dto.ShowCreateRequest;
import com.moviebooking.dto.ShowResponse;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.model.Movie;
import com.moviebooking.model.Screen;
import com.moviebooking.model.Show;
import com.moviebooking.repository.MovieRepository;
import com.moviebooking.repository.ScreenRepository;
import com.moviebooking.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCatalogService {
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final ShowRepository showRepository;

    public AdminCatalogService(MovieRepository movieRepository, ScreenRepository screenRepository,
                               ShowRepository showRepository) {
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
        this.showRepository = showRepository;
    }

    @Transactional(readOnly = true)
    public List<ScreenOption> screens() {
        return screenRepository.findAll().stream()
                .map(s -> new ScreenOption(s.getId(), s.getTheatre().getName() + " · "
                        + s.getTheatre().getCity() + " · " + s.getName()))
                .toList();
    }

    @Transactional
    public ShowResponse createShow(ShowCreateRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie " + request.movieId() + " was not found."));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new ResourceNotFoundException("Screen " + request.screenId() + " was not found."));
        if (!request.showTime().isAfter(LocalDateTime.now())) {
            throw new ConflictException("The showtime must be in the future.");
        }
        // Approximate overlap rule: no other show on this screen within (duration + 15 min) either side.
        int gap = movie.getDurationMinutes() + 15;
        if (!showRepository.findByScreenIdAndShowTimeBetween(screen.getId(),
                request.showTime().minusMinutes(gap), request.showTime().plusMinutes(gap)).isEmpty()) {
            throw new ConflictException("That screen already has a show around this time.");
        }
        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setShowTime(request.showTime());
        show.setPrice(request.price());
        show = showRepository.save(show);
        return new ShowResponse(show.getId(), movie.getId(), screen.getId(),
                screen.getTheatre().getName(), screen.getTheatre().getCity(),
                screen.getName(), show.getShowTime(), show.getPrice());
    }
}
