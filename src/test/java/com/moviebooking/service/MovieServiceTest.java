package com.moviebooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.moviebooking.dto.MovieResponse;
import com.moviebooking.model.Movie;
import com.moviebooking.repository.MovieRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    private PosterSvgService posterSvgService;
    private MovieService movieService;

    @BeforeEach
    void setUp() {
        posterSvgService = new PosterSvgService();
        movieService = new MovieService(movieRepository, posterSvgService);
    }

    @Test
    void findById_withExternalPoster_preservesOriginalUrl() {
        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("The Last Signal");
        movie.setGenre("Science Fiction");
        movie.setLanguage("English");
        movie.setPosterUrl("https://images.unsplash.com/photo-test");
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));

        MovieResponse res = movieService.findById(1L);
        assertThat(res.posterUrl()).isEqualTo("https://images.unsplash.com/photo-test");
    }

    @Test
    void findById_withNullPoster_generatesSvgPosterEndpoint() {
        Movie movie = new Movie();
        movie.setId(4L);
        movie.setTitle("Ghost Frequency");
        movie.setGenre("Horror");
        movie.setLanguage("English");
        movie.setPosterUrl(null);
        when(movieRepository.findById(4L)).thenReturn(Optional.of(movie));

        MovieResponse res = movieService.findById(4L);
        assertThat(res.posterUrl()).isEqualTo("/api/movies/4/poster");
    }

    @Test
    void getPosterSvg_rendersSvgForMovie() {
        Movie movie = new Movie();
        movie.setId(5L);
        movie.setTitle("Kabaddi Kings");
        movie.setGenre("Sports");
        movie.setLanguage("Hindi");
        movie.setCertificate("UA");
        when(movieRepository.findById(5L)).thenReturn(Optional.of(movie));

        String svg = movieService.getPosterSvg(5L);
        assertThat(svg).contains("<svg").contains("Kabaddi Kings").contains(">UA<");
    }
}
