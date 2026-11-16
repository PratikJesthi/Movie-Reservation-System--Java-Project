package com.moviebooking.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.model.Movie;
import com.moviebooking.model.Screen;
import com.moviebooking.model.Show;
import com.moviebooking.model.Theatre;
import com.moviebooking.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class BookingPersistenceTest {
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesBookingWithPopulatedCreatedAt() {
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@test.com");
        user.setPasswordHash("hash");
        user = entityManager.persist(user);

        Movie movie = new Movie();
        movie.setTitle("Test Movie");
        movie.setDescription("Test Desc");
        movie.setGenre("Sci-Fi");
        movie.setDurationMinutes(120);
        movie.setLanguage("English");
        movie = entityManager.persist(movie);

        Theatre theatre = new Theatre();
        theatre.setName("Test Theatre");
        theatre.setCity("Test City");
        theatre = entityManager.persist(theatre);

        Screen screen = new Screen();
        screen.setName("Screen 1");
        screen.setTheatre(theatre);
        screen.setTotalSeats(20);
        screen = entityManager.persist(screen);

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setShowTime(LocalDateTime.now().plusDays(1));
        show.setPrice(new BigDecimal("200.00"));
        show = entityManager.persist(show);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShow(show);
        booking.setTotalAmount(new BigDecimal("200.00"));
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking saved = bookingRepository.saveAndFlush(booking);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }
}
