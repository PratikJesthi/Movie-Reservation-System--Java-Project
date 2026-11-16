# CHALCHITRA — Movie Reservation System

A movie booking system built with Java 21, Spring Boot, Maven, Thymeleaf, and PostgreSQL/H2. It includes a JSON REST API and a responsive server-rendered frontend that uses the Fetch API.

## Requirements

- Java 21 JDK
- Maven 3.8 or newer (or use the Maven wrapper if one is added later)
- Visual Studio Code

## VS Code setup

Open this folder in VS Code. Install the recommended extensions from **Extensions: Show Recommended Extensions**:

- Extension Pack for Java (`vscjava.vscode-java-pack`)
- Spring Boot Extension Pack (`vmware.vscode-spring-boot`)
- XML (`redhat.vscode-xml`)

The project uses Maven, so VS Code should import `pom.xml` automatically. Select a Java 21 JDK if prompted.

## Local database

The default profile uses a persistent H2 database at `./data/moviedb`. No database server or cloud account is needed. On first startup, the application creates the JPA schema and seeds sample movies, a theatre, screens, seats, and showtimes.

The H2 console is available at [http://localhost:8080/h2-console](http://localhost:8080/h2-console). Use JDBC URL `jdbc:h2:file:./data/moviedb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, username `sa`, and a blank password.

## Run the application

Use Java 21 and Maven. For a complete local booking demonstration, enable the clearly simulated payment flow:

```bash
APP_PAYMENT_SIMULATION=true mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080), register an account, choose a movie and showtime, select available seats, and complete the simulated callback. It is not a real Razorpay transaction. Without the simulation variable or Razorpay credentials, payment-link creation is disabled.

The app can also use Razorpay test credentials from environment variables. Never commit credentials.

## PostgreSQL / Supabase

An optional `supabase` profile reads `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD`, and expects the database schema to already match all JPA entities. **The checked-in `schema.sql` is currently behind the application model** and is missing payment and ticket/hold columns. Do not use it to create a new Supabase database until it has been updated and verified.

### Administrator account

Public registration always creates a regular `USER`. To use the admin page in the local H2 demo, register normally, open the H2 console, and promote that account (replace the email):

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

Log out and log back in so the session picks up the new role. The admin page at `/admin` can add movies. Movie updates and deletion are available through the API.

## API endpoints

All request and response bodies use JSON. Authenticated calls use the browser's HTTP session cookie; browser fetch calls are same-origin. A `401` means log in, and a `403` means the current account lacks the required role.

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create a user account and log in |
| POST | `/api/auth/login` | Public | Log in and create a session |
| GET | `/api/auth/me` | User | Return the signed-in account |
| POST | `/api/auth/logout` | User | End the current session |
| GET | `/api/movies` | Public | List movies |
| GET | `/api/movies/{id}` | Public | Get movie details |
| POST | `/api/movies` | Admin | Add a movie |
| PUT | `/api/movies/{id}` | Admin | Update a movie |
| DELETE | `/api/movies/{id}` | Admin | Delete a movie |
| GET | `/api/movies/{id}/shows` | Public | List a movie's showtimes |
| GET | `/api/shows/{id}/seats` | Public | List seats with `AVAILABLE` or `BOOKED` status |
| POST | `/api/bookings` | User | Reserve selected seats for a show |
| GET | `/api/users/{id}/bookings` | Owner or admin | List that user's bookings |
| GET | `/api/bookings/{id}` | Owner or admin | Get a booking (used by confirmation page) |
| DELETE | `/api/bookings/{id}` | Owner or admin | Cancel a booking and free its seats |

Example booking request:

```json
{
  "showId": 1,
  "seatIds": [1, 2]
}
```

Booking is transactional. The service checks seat availability and seat/screen consistency; the database's unique `(show_id, seat_id)` constraint rejects concurrent attempts to reserve the same seat. Validation and API errors return JSON with a timestamp, status, message, and optional field details.

## Project structure

```text
src/main/java/com/moviebooking/
├── MovieBookingApplication.java
├── config/              # BCrypt password encoder
├── controller/          # REST endpoints and page routes
├── dto/                 # Validated request and response records
├── exception/           # Domain exceptions and JSON error handler
├── model/               # JPA entities
├── repository/          # Spring Data repositories
└── service/             # Business logic and session access
src/main/resources/
├── application.properties
├── static/css/          # Responsive styles
├── static/js/           # Fetch API and page behavior
└── templates/           # Thymeleaf pages
src/test/java/           # Service, repository, controller, and ticket tests
```

## Tests

The repository currently contains 31 JUnit test methods covering booking, payment callbacks, ticket QR/verification, persistence, and error handling. Run them with:

```bash
mvn test
```
