# CHALCHITRA — Project Review and Lab Demo Notes

Reviewed on 16 November 2026. This is a code and configuration review; the test suite was not run as part of this review.

## Project at a glance

CHALCHITRA is a Java 21 / Spring Boot 3.3.5 movie reservation application. The backend uses Spring MVC REST controllers, Spring Data JPA/Hibernate, and service classes for the booking rules. The web interface uses Thymeleaf templates, JavaScript Fetch calls, and CSS. The default database is persistent, file-based H2. A PostgreSQL/Supabase profile is also configured.

```text
Browser (Thymeleaf + JavaScript)
              │ HTTP / JSON
Spring MVC controllers → services → Spring Data repositories / JPA
                                             │
                                      H2 or PostgreSQL
```

## What is implemented

### Backend

- Account registration and login with BCrypt password hashes and session-based user/admin roles.
- Movie listing, details, and admin-only movie create/update/delete API routes.
- Movie showtime and seat availability APIs.
- Booking creation with server-calculated totals, ten-minute pending holds, duplicate-seat checks, a database uniqueness constraint, and a scheduled cleanup job for expired holds.
- Booking history and cancellation endpoints.
- Razorpay hosted payment links, callback signature verification, and a webhook endpoint.
- QR ticket generation, a public ticket verification page, and asynchronous confirmation email support.
- DTO validation, domain exceptions, and consistent API error responses.

### Frontend

- Responsive catalogue, movie details, showtimes, seat selection, login/register, booking history, admin movie form, payment result, and ticket pages.
- The JavaScript client calls the same-origin JSON API and escapes server-provided values before inserting them into HTML.
- The admin page currently adds movies only. It does not provide showtime, theatre, screen, or seat management.

### Database

The relational model includes users, movies, theatres, screens, seats, shows, bookings, booking-seat rows, and payments. Foreign keys, unique constraints, and indexes support basic data integrity and booking lookups. The local seeder creates sample movies, a theatre, screens, seats, and showtimes when the movie catalogue is empty.

There are 31 JUnit test methods in the repository. The work log reports a successful run, but that result was not re-verified during this review.

## Required work before the professor demo

### Priority 1 — make the demo path reliable

1. **Enable local payment simulation for the demo.** Payment simulation defaults to `false`, so a local booking currently fails to produce a payment link unless Razorpay credentials are set. Start the local demo with `APP_PAYMENT_SIMULATION=true` (commands below). Keep this local-only; the Supabase profile intentionally rejects simulation.
2. **Fix cancellation/payment consistency.** The UI offers cancellation for confirmed, paid bookings. The service releases the seats but does not refund the payment. Until refunds exist, limit cancellation to unpaid pending bookings or hide/disable confirmed-booking cancellation. If paid cancellation is required, add refund handling and a clear refund status.
3. **Bring `schema.sql` up to date before using PostgreSQL/Supabase.** It does not currently define the `payments` table or the newer `bookings` fields (`expires_at`, `ticket_token`, `checked_in_at`, and `ticket_email_sent_at`). The Supabase profile uses Hibernate `validate`, so the documented SQL setup will not match the current entities. Add a versioned migration and verify a fresh PostgreSQL database from an empty state.
4. **Describe payment simulation honestly in the demo.** Simulation confirms the booking through a local callback; it is not a Razorpay transaction. Do not enter or present real card details as part of this flow.
5. **Repair late-payment recording.** The callback path sets `PAID_AFTER_EXPIRY` and then throws a runtime exception inside a transactional method. Spring rolls back that transaction by default, so the status may not be saved. Persist the late-payment/refund-needed state in a committed transaction before returning the error to the user.

### Priority 2 — close functional gaps

- Add an authenticated admin check-in action that atomically changes a confirmed ticket to used. The current `/verify/{token}` page is read-only, so repeated scans remain valid until a check-in timestamp is set elsewhere.
- Verify or finish the payment-link webhook integration before presenting it as working. The endpoint currently looks payments up by gateway order ID, while the normal purchase flow creates payment links and does not set that order ID. Add event-specific matching, amount/status checks, and retry behavior for transient failures.
- Add admin screens and APIs for theatres, screens, showtimes, and seat layouts. Without these, the app can display seeded showtimes but an administrator cannot operate a full cinema schedule from the UI.
- Add a booking/payment status view for pending, confirmed, cancelled, expired, and paid-after-expiry cases. Define refunds/support handling for late payments and paid cancellations.
- Add search/filtering for movies and showtimes (genre, language, date, city) if time permits; it improves the presentation but is not needed to show the core Java/database flow.
- Decide whether email is part of the demo. Ticket email needs SMTP credentials; the QR shown in the payment callback is enough for an offline demonstration.

### Priority 3 — polish and reliability

- Replace manual schema drift with Flyway or Liquibase migrations; keep one authoritative seed path rather than maintaining SQL seed data and Java seed data independently.
- Review session security and enable CSRF protection for state-changing requests. Keep H2 Console local-only and do not expose it in a deployed environment.
- Add API documentation (for example, OpenAPI), and update the README/work log so setup steps, Java version, payment behavior, and test counts agree with the current code.
- Add/retain regression coverage for cancellation/refund behavior, expired payment callbacks, check-in, and a real PostgreSQL schema startup. Run the existing test suite and one full browser flow before presenting.

## Database recommendation and local setup

**For a classroom presentation, use the default local H2 file database.** It is persistent across restarts, needs no internet or account, and is seeded automatically. This is the least fragile setup for a live demo. The H2 console is enabled at `/h2-console`; use JDBC URL `jdbc:h2:file:./data/moviedb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, user `sa`, and a blank password. The generated `data/` folder is local application data and should not be committed.

From the project directory on macOS/Linux:

```bash
export APP_PAYMENT_SIMULATION=true
mvn spring-boot:run
```

Open `http://localhost:8080`. The demo mode enables the app's simulated payment callback so the booking can reach `CONFIRMED` and display its QR ticket. Without this variable or valid Razorpay credentials, payment-link creation is intentionally unavailable.

**For a database-focused submission or multi-user deployment, PostgreSQL is the better target.** It is a full relational database and matches the production-style profile and JDBC dependency. Supabase is a hosted PostgreSQL option, but this repository's `schema.sql` must first be migrated to match the current JPA model. For a professor demo, local PostgreSQL is also an option if it has already been installed and configured; avoid depending on a new cloud account or network connection on presentation day.

The Supabase profile reads `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD` and uses `ddl-auto=validate`. Do not use it with the checked-in `schema.sql` until the schema mismatch above is fixed. Use environment variables for secrets; do not put database or payment credentials in source files.

## Suggested 5–7 minute presentation flow

1. Introduce **CHALCHITRA — Movie Reservation System** and the stack: Java 21, Spring Boot, JPA, Thymeleaf, JavaScript, and H2.
2. Show the catalogue and a movie's seeded showtimes. Explain the controller → service → repository → database layers.
3. Register/log in, select a show and seats, and create a booking. Show the total is computed by the backend and the seats become unavailable while held.
4. Complete the clearly labelled local simulated payment. Show the confirmed booking and QR ticket.
5. Open My Bookings and then the H2 Console. Show `bookings`, `booking_seats`, and `payments`, and explain primary/foreign keys and the unique `(show_id, seat_id)` constraint that prevents double booking.
6. If time permits, try to select a held seat from a second account and explain the conflict response and ten-minute expiry behavior.

For now, avoid demonstrating cancellation of a paid booking until refund behavior is implemented. Also, the QR verification endpoint checks a ticket but does not yet perform staff check-in.

## Project identity

The visible site branding, page titles, Maven project name, Spring application name, payment-link description, and README now use **CHALCHITRA**. The Maven artifact ID remains `movie-reservation-system` so existing build/run commands and tooling stay stable.
