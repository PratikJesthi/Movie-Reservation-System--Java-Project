package com.moviebooking.controller;

import com.moviebooking.dto.MovieRequest;
import com.moviebooking.dto.MovieResponse;
import com.moviebooking.dto.ShowResponse;
import com.moviebooking.service.MovieService;
import com.moviebooking.service.SessionAuth;
import com.moviebooking.service.ShowService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movies")
public class MovieController {
    private final MovieService movieService;
    private final ShowService showService;
    private final SessionAuth sessionAuth;

    public MovieController(MovieService movieService, ShowService showService, SessionAuth sessionAuth) {
        this.movieService = movieService;
        this.showService = showService;
        this.sessionAuth = sessionAuth;
    }

    @GetMapping
    public List<MovieResponse> allMovies() {
        return movieService.findAll();
    }

    @GetMapping("/{id}")
    public MovieResponse movie(@PathVariable Long id) {
        return movieService.findById(id);
    }

    @GetMapping("/{id}/shows")
    public List<ShowResponse> shows(@PathVariable Long id) {
        movieService.findById(id); // Return 404 for an unknown movie rather than an empty list.
        return showService.findByMovie(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse create(@Valid @RequestBody MovieRequest request, HttpSession session) {
        sessionAuth.requireAdmin(session);
        return movieService.create(request);
    }

    @PutMapping("/{id}")
    public MovieResponse update(@PathVariable Long id, @Valid @RequestBody MovieRequest request,
                                HttpSession session) {
        sessionAuth.requireAdmin(session);
        return movieService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpSession session) {
        sessionAuth.requireAdmin(session);
        movieService.delete(id);
    }
}
