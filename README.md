# Movie Reservation System

A beginner-friendly movie booking prototype built with Java 17, Spring Boot, Maven, Thymeleaf, and Supabase PostgreSQL. It includes a JSON REST API and a responsive server-rendered frontend that uses the Fetch API.

## Requirements

- Java 17 JDK
- Maven 3.8 or newer (or use the Maven wrapper if one is added later)
- A Supabase project with a PostgreSQL database
- Visual Studio Code

## VS Code setup

Open this folder in VS Code. Install the recommended extensions from **Extensions: Show Recommended Extensions**:

- Extension Pack for Java (`vscjava.vscode-java-pack`)
- Spring Boot Extension Pack (`vmware.vscode-spring-boot`)
- XML (`redhat.vscode-xml`)

The project uses Maven, so VS Code should import `pom.xml` automatically. Select a Java 17 JDK if prompted.

## Supabase database setup

1. In the Supabase dashboard, open **SQL Editor** and create a new query.
2. Paste the contents of [`schema.sql`](./schema.sql) and run it. It creates the tables and sample catalogue data.
3. In **Project Settings → Database**, copy the connection details. The JDBC URL should look like `jdbc:postgresql://<host>:5432/postgres?sslmode=require`. Use the database username and password shown by Supabase. If your project uses a pooler, select its PostgreSQL connection string and make sure it uses the JDBC prefix.
4. Set the environment variables below in the shell that will run Spring Boot. Do not commit credentials.

macOS/Linux (current terminal):

```bash
export SUPABASE_DB_URL='jdbc:postgresql://<host>:5432/postgres?sslmode=require'
export SUPABASE_DB_USER='postgres'
export SUPABASE_DB_PASSWORD='your-database-password'
```

Windows PowerShell (current terminal):

```powershell
$env:SUPABASE_DB_URL = 'jdbc:postgresql://<host>:5432/postgres?sslmode=require'
$env:SUPABASE_DB_USER = 'postgres'
$env:SUPABASE_DB_PASSWORD = 'your-database-password'
```

Replace the placeholders with your Supabase connection values. `application.properties` reads these variables and sets `spring.jpa.hibernate.ddl-auto=validate`, so the schema must exist before the application starts. Spring SQL initialization is disabled because the schema is run manually in Supabase.

## Run the application

From the project folder, with the environment variables set:

```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Register an account, choose a movie and showtime, select available seats, and confirm the booking.

### Administrator account

Public registration always creates a regular `USER`. To use the admin page, register normally, then promote that account from the Supabase SQL Editor (replace the email):

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
src/test/java/           # Booking service unit tests
```

## Tests

The booking service tests cover successful booking, rejecting an already-booked seat, and cancellation. Run them with:

```bash
mvn test
```
