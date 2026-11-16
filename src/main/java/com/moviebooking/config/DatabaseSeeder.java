package com.moviebooking.config;

import com.moviebooking.model.Movie;
import com.moviebooking.model.Screen;
import com.moviebooking.model.Seat;
import com.moviebooking.model.Show;
import com.moviebooking.model.Theatre;
import com.moviebooking.repository.MovieRepository;
import com.moviebooking.repository.ScreenRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.TheatreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final MovieRepository movieRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;

    public DatabaseSeeder(MovieRepository movieRepository,
                          TheatreRepository theatreRepository,
                          ScreenRepository screenRepository,
                          SeatRepository seatRepository,
                          ShowRepository showRepository) {
        this.movieRepository = movieRepository;
        this.theatreRepository = theatreRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (movieRepository.count() > 0) {
            log.info("Database already contains movie catalogue data. Skipping initial seeding.");
            return;
        }

        log.info("Empty database detected. Seeding sample catalogue data...");

        Movie movie1 = createMovie("The Last Signal",
                "A radio astronomer discovers a message hidden in a distant transmission.",
                "Science Fiction", 124, "English",
                "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=900&q=80");
        Movie movie2 = createMovie("Monsoon Letters",
                "Two childhood friends reconnect while restoring an old cinema in their hometown.",
                "Drama", 111, "Hindi",
                "https://images.unsplash.com/photo-1478720568477-152d9b164e26?auto=format&fit=crop&w=900&q=80");
        Movie movie3 = createMovie("Midnight Runway",
                "A rookie pilot and her crew race to deliver a vital package before sunrise.",
                "Adventure", 103, "English",
                "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=900&q=80");
        movieRepository.saveAll(List.of(movie1, movie2, movie3));

        Theatre theatre = new Theatre();
        theatre.setName("Grand Screen Cinemas");
        theatre.setCity("Bengaluru");
        theatre = theatreRepository.save(theatre);

        Screen screen1 = new Screen();
        screen1.setName("Screen 1");
        screen1.setTheatre(theatre);
        screen1.setTotalSeats(24);

        Screen screen2 = new Screen();
        screen2.setName("Screen 2");
        screen2.setTheatre(theatre);
        screen2.setTotalSeats(24);

        screen1 = screenRepository.save(screen1);
        screen2 = screenRepository.save(screen2);

        List<Seat> seats = new ArrayList<>();
        seedSeatsForScreen(screen1, seats);
        seedSeatsForScreen(screen2, seats);
        seatRepository.saveAll(seats);

        LocalDateTime now = LocalDateTime.now();
        Show show1 = createShow(movie1, screen1, now.plusDays(1).plusHours(10), new BigDecimal("250.00"));
        Show show2 = createShow(movie1, screen1, now.plusDays(1).plusHours(18), new BigDecimal("300.00"));
        Show show3 = createShow(movie2, screen2, now.plusDays(1).plusHours(13), new BigDecimal("220.00"));
        Show show4 = createShow(movie2, screen2, now.plusDays(2).plusHours(17), new BigDecimal("260.00"));
        Show show5 = createShow(movie3, screen1, now.plusDays(2).plusHours(11), new BigDecimal("240.00"));
        Show show6 = createShow(movie3, screen2, now.plusDays(2).plusHours(20), new BigDecimal("300.00"));

        showRepository.saveAll(List.of(show1, show2, show3, show4, show5, show6));
        log.info("Sample catalogue seeded successfully.");
    }

    private void seedSeatsForScreen(Screen screen, List<Seat> list) {
        String[] rows = {"A", "B", "C"};
        for (String row : rows) {
            for (int num = 1; num <= 8; num++) {
                Seat seat = new Seat();
                seat.setScreen(screen);
                seat.setRowLabel(row);
                seat.setSeatNumber(num);
                list.add(seat);
            }
        }
    }

    private Movie createMovie(String title, String desc, String genre, int duration, String lang, String poster) {
        Movie m = new Movie();
        m.setTitle(title);
        m.setDescription(desc);
        m.setGenre(genre);
        m.setDurationMinutes(duration);
        m.setLanguage(lang);
        m.setPosterUrl(poster);
        return m;
    }

    private Show createShow(Movie movie, Screen screen, LocalDateTime time, BigDecimal price) {
        Show s = new Show();
        s.setMovie(movie);
        s.setScreen(screen);
        s.setShowTime(time);
        s.setPrice(price);
        return s;
    }
}
