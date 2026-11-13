package com.moviebooking.service;

import com.moviebooking.dto.MovieRequest;
import com.moviebooking.dto.MovieResponse;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.model.Movie;
import com.moviebooking.repository.MovieRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovieService {
    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> findAll() {
        return movieRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MovieResponse findById(Long id) {
        return toResponse(getMovie(id));
    }

    @Transactional
    public MovieResponse create(MovieRequest request) {
        return toResponse(movieRepository.save(copyToMovie(request, new Movie())));
    }

    @Transactional
    public MovieResponse update(Long id, MovieRequest request) {
        return toResponse(movieRepository.save(copyToMovie(request, getMovie(id))));
    }

    @Transactional
    public void delete(Long id) {
        movieRepository.delete(getMovie(id));
    }

    private Movie getMovie(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie " + id + " was not found."));
    }

    private Movie copyToMovie(MovieRequest request, Movie movie) {
        movie.setTitle(request.title().trim());
        movie.setDescription(request.description().trim());
        movie.setGenre(request.genre().trim());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setLanguage(request.language().trim());
        movie.setPosterUrl(request.posterUrl() == null ? null : request.posterUrl().trim());
        return movie;
    }

    private MovieResponse toResponse(Movie movie) {
        return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDescription(), movie.getGenre(),
                movie.getDurationMinutes(), movie.getLanguage(), movie.getPosterUrl());
    }
}
