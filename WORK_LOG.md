# Movie Reservation System — Implementation & Work Log

**Timestamp:** 2026-11-16  
**Stack:** Spring Boot 3.3.5 | Java 21 | Maven | Spring Data JPA / Hibernate | Thymeleaf | H2 / PostgreSQL | Razorpay Java SDK 1.4.5  
**Project Root:** `/home/dds/Movie-Reservation-System--Java-Project`

---

## 1. Executive Summary

This log documents all architectural decisions, code changes, root cause analyses, backward-compatibility validations, and test results implemented across the phases of the Movie Reservation System.

Current Test Suite Status: **19 / 19 Tests Passing (`BUILD SUCCESS`)**

---

## 2. Phase-by-Phase Progress & Changes

### Phase 0: Diagnose & Fix Booking Conflict Error (COMPLETE ✅)

#### 1. Problem & Symptom
- Submitting a booking returned HTTP 409: `"The request conflicts with existing data."`
- Client received generic error masking the exact root cause.

#### 2. Diagnostic Instrumentation
- Enhanced `GlobalExceptionHandler.java`:
  - Added SLF4J logger.
  - Updated `@ExceptionHandler(DataIntegrityViolationException.class)` to traverse the exception cause chain.
  - Extracted the exact database constraint name from Hibernate's `ConstraintViolationException` and logged the full stack trace at `ERROR` level while preserving safe client error response.

#### 3. Root Cause Identified
- `Booking.java` had `@Column(name = "created_at", nullable = false, insertable = false)`.
- When JPA executed `INSERT INTO bookings ...`, Hibernate omitted `created_at` from the INSERT statement because `insertable = false`.
- The database schema enforced `created_at TIMESTAMP NOT NULL`, throwing `org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException: NULL not allowed for column "CREATED_AT"`.

#### 4. Resolution
- Modified `Booking.java`:
  - Removed `insertable = false` from `@Column(name = "created_at")`.
  - Added `@PrePersist` lifecycle hook to automatically initialize `createdAt = LocalDateTime.now()` if null.
  - Ensured `BookingSeat` entity enforces the composite unique constraint `@Table(uniqueConstraints = @UniqueConstraint(name = "uq_booking_seats_show_seat", columnNames = {"show_id", "seat_id"}))` so race conditions are stopped by the database engine.
- Created Tests:
  - `BookingPersistenceTest.java`: `@DataJpaTest` asserting `Booking.createdAt` is populated and saved without null constraint errors.
  - `GlobalExceptionHandlerTest.java`: Asserts `DataIntegrityViolationException` returns HTTP 409 with safe API payload.
  - `BookingServiceTest.java`: Added `throwsDataIntegrityViolationWhenDatabaseUniqueConstraintTriggers` to verify double-booking prevention.

---

### Phase 1: Persistent Local Database & Profiles (COMPLETE ✅)

#### 1. Objective
- Eliminate dependence on an external Supabase instance for local development.
- Enable zero-setup local runs while keeping Supabase/PostgreSQL available via Spring profiles.

#### 2. Changes Implemented
- **Default Profile (`application.properties`):**
  - Configured persistent file-based H2 database: `jdbc:h2:file:./data/moviedb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`.
  - Set `spring.jpa.hibernate.ddl-auto=update` to persist schema and data across JVM restarts.
  - Set `spring.sql.init.mode=never` to prevent re-executing raw DDL scripts on startup.
  - Enabled H2 console at `/h2-console`.
- **Supabase Profile (`application-supabase.properties`):**
  - Reads `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD`.
  - Configured `ddl-auto=validate` for production schema safety.
  - Activated via `--spring.profiles.active=supabase` or `SPRING_PROFILES_ACTIVE=supabase`.
- **Database Seeder (`DatabaseSeeder.java`):**
  - Replaced static `data.sql` with a `CommandLineRunner`.
  - Checks `movieRepository.count() == 0` before seeding. If catalogue data exists, seeding is skipped entirely.
- **Git Hygiene:**
  - Added `data/` and `./data/` to `.gitignore`.

#### 3. Verification
- App started against empty directory; `DatabaseSeeder` seeded movies, screens, theatres, and seats.
- Bookings created. App stopped and restarted against `./data/moviedb.mv.db`.
- Seeder reported `Database already contains movie catalogue data. Skipping initial seeding.`
- User accounts and bookings persisted across restarts.

---

### Phase 2: Booking Status Flow & Hold Lifecycle (COMPLETE ✅)

#### 1. Objective
- Introduce explicit lifecycle states for bookings (`PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`).
- Hold seats in `PENDING` state for 10 minutes upon selection.
- Implement an automated background scheduler to release expired holds.
- Guarantee schema backward compatibility against existing Phase 1 database files.

#### 2. Changes Implemented
- **Status Enum (`BookingStatus.java`):**
  - Defined `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.
- **Entity Updates (`Booking.java`):**
  - Replaced legacy `String status` with `@Enumerated(EnumType.STRING) BookingStatus status = BookingStatus.PENDING`.
  - Added nullable column `expires_at` (`@Column(name = "expires_at")`). Nullable design ensures existing database files load without constraint violation errors.
  - `@PrePersist` computes `expiresAt = createdAt.plusMinutes(10)` for `PENDING` bookings.
- **Hold Protection (`BookingSeatRepository.java`):**
  - Updated `findBookedSeatIds(showId)` query to treat seats belonging to both `PENDING` and `CONFIRMED` bookings as unavailable, preventing double-selection during the 10-minute hold.
- **Scheduler & Cleanup (`BookingService.java` & `MovieBookingApplication.java`):**
  - Added `@EnableScheduling` to main application class.
  - Added `@Scheduled(fixedDelayString = "${app.booking.cleanup-interval-ms:60000}")` task `expireStaleBookings()`.
  - Finds all `PENDING` bookings where `expiresAt <= now` (or older than 10 minutes), removes associated `BookingSeat` records (freeing seats for other users), sets status to `EXPIRED`, and clears `expiresAt`.
- **Transitions:**
  - `confirm(bookingId)`: Transitions `PENDING` → `CONFIRMED`, clears `expiresAt`.
  - `cancel(bookingId, userId, isAdmin)`: Validates ownership/admin rights, deletes `BookingSeat` mappings, marks `CANCELLED`.

#### 3. Backward Compatibility Proof
- Rebuilt application and started against the existing `./data/moviedb.mv.db` created during Phase 1.
- Hibernate executed `ALTER TABLE bookings ADD COLUMN expires_at TIMESTAMP` without loss of existing rows.
- Existing User (Bob) logged in; Booking #1 (created with string `"CONFIRMED"` in Phase 1) deserialized cleanly into `BookingStatus.CONFIRMED`.
- New Booking #33 created as `PENDING` with 10-minute expiry window.

---

### Phase 3: Payment Gateway & Razorpay Hosted Payment Links (COMPLETE ✅)

#### 1. Architectural Decisions & Specifications
- **Hosted Payment Links:** Uses Razorpay hosted Payment Links (`plink_xxxx`) created server-side via `razorpayClient.paymentLink.create(...)`. The browser navigates directly (`window.location.href`) to Razorpay's hosted checkout page (`https://rzp.io/i/...`).
- **No checkout.js popup, no new JavaScript:** Eliminates client-side modal scripts, keeping the application frontend lightweight and server-driven.
- **Server-Side Price Authority:** The link `amount` is calculated strictly on the backend from `show.getPrice() * seats.size()` in paise. The client never supplies the price.
- **Booking Reference:** Linked via `reference_id = String.valueOf(booking.getId())`.
- **Confirmation Without Webhook:** Razorpay redirects the user via `GET` to the Thymeleaf-backed callback endpoint `${app.base-url}/payments/callback`.
  1. Verifies HMAC-SHA256 signature using `keySecret` against the official payment link payload format:
     `payment_link_id + "|" + payment_link_reference_id + "|" + payment_link_status + "|" + razorpay_payment_id`
  2. Queries the Razorpay API directly server-side (`paymentLink.fetch(...)`) to ensure gateway status is `paid`.
  3. Only if both checks pass, marks `Payment` as `PAID` and `Booking` as `CONFIRMED`. Query parameters alone are never trusted.
- **Idempotency:** Calling the callback endpoint multiple times (or refreshing the page) checks existing payment state. If already `PAID`/`CONFIRMED`, it safely displays confirmation without double-processing or re-saving.
- **Automatic Expiry of Unpaid Holds:** If a user abandons checkout or payment fails, the booking remains in `PENDING` state. The Phase 2 scheduled job (`expireStaleBookings()`) automatically expires it after 10 minutes and frees the held seats.
- **Existing Page Preservation:** Existing Thymeleaf templates and `app.js` functionality remain intact; only line 120 in `app.js` was updated to navigate to `booking.paymentLinkUrl` when present.

#### 2. Code Changes
- **Entities & Repositories:**
  - `Payment.java`: Fields for `booking`, `paymentLinkId`, `paymentLinkUrl`, `referenceId`, `gatewayPaymentId`, `amount`, `status`, and unique `idempotencyKey`.
  - `PaymentStatus.java`: Enum (`CREATED`, `PAID`, `FAILED`).
  - `PaymentRepository.java`: Queries for `findByPaymentLinkId`, `findByReferenceId`, `findByBookingId`, `findByIdempotencyKey`.
- **Services & Controllers:**
  - `PaymentService.java`:
    - `createPaymentLink(booking)`: Generates Razorpay payment link. Refuses link creation with a clear `IllegalStateException` if keys are missing and simulation is disabled; creates simulated link only when `app.payment.simulation=true`.
    - `validateStartup()`: Halts application startup (`IllegalStateException`) if `app.payment.simulation=true` is set under the `supabase` profile. Logs a loud `WARN` banner at startup whenever simulation is enabled.
    - `isValidPaymentLinkSignature(...)`: Validates HMAC-SHA256 signature against key secret. Rejects `"simulated_valid_signature"` whenever simulation is disabled, even if key secret is empty.
    - `confirmFromCallback(...)`: Double-checks signature + Razorpay API status, updates payment and booking idempotently.
  - `PaymentCallbackController.java`: Thymeleaf controller for `GET /payments/callback`.
  - `BookingService.java`: Injects `PaymentService`, initiates payment link creation upon booking creation, and attaches `paymentLinkUrl` to `BookingResponse`.
  - `BookingResponse.java`: Added `paymentLinkUrl` field with backward-compatible overloaded constructor.
  - `PaymentController.java`: Preserved `POST /api/payments/webhook` for optional webhook intake.
- **Templates:**
  - `payment-callback.html`: Thymeleaf template displaying verified confirmation details or a clear verification failure message.

#### 3. Simulation Safeguards & Startup Validation
- `app.payment.simulation` defaults to `false` in `application.properties` and `application-supabase.properties`.
- Startup check strictly prohibits simulation under the `supabase` profile.
- A prominent startup banner warns the administrator whenever simulation is intentionally enabled:
  ```
  *******************************************************************************
  WARNING: PAYMENT SIMULATION MODE IS ACTIVE (app.payment.simulation=true)!
  Real Razorpay payments are NOT enabled. All links bypass the payment gateway.
  DO NOT ENABLE THIS IN PRODUCTION ENVIRONMENTS!
  *******************************************************************************
  ```
- Missing Razorpay keys with simulation disabled fail fast with a clear exception message.
- The literal string `"simulated_valid_signature"` is rejected unless simulation is explicitly enabled.

#### 4. Later Work / Deferred Refinements
- **Webhook Integration & Tunneling:** The webhook endpoint (`POST /api/payments/webhook`) is implemented and preserved in code. In later staging/production work, register the webhook URL with Razorpay (using an ngrok tunnel `https://<ngrok-id>.ngrok-free.app/api/payments/webhook`), configure `RAZORPAY_WEBHOOK_SECRET`, and perform end-to-end webhook event testing.
- **Late Payment Handling on Expired Holds:** If a payment completes after the 10-minute hold has expired and seats were released, add logic to detect `booking.status == EXPIRED`, reject confirmation, and initiate an automatic refund or flag for customer support.
- **Link Expiry Synchronization (`expire_by`):** Set Razorpay Payment Link `expire_by` unix timestamp to match `booking.expiresAt` (10 minutes) so the payment link automatically expires in Razorpay when the hold ends.
- **Strict Amount & Link ID Comparison in Callback:** In `confirmFromCallback`, add explicit assertions verifying `link.get("amount") == booking.totalAmount * 100` and `link.get("id").equals(payment.getPaymentLinkId())`.

#### 5. Automated Tests Added
- `PaymentServiceTest.java`:
  - `validPaymentLinkSignaturePassesVerification`: Asserts HMAC-SHA256 signature check passes for valid payloads.
  - `invalidPaymentLinkSignatureFailsVerification`: Asserts tampered signatures are rejected.
  - `rejectsConfirmationWhenSignatureIsInvalid`: Asserts confirmation aborts with `ConflictException` when signature check fails.
  - `simulatedSignatureIsRejectedWhenSimulationIsOffEvenIfKeySecretIsBlank`: Verifies simulated signature is rejected when simulation is off and no booking or payment is confirmed.
  - `simulatedSignatureIsAcceptedWhenSimulationIsExplicitlyOn`: Verifies simulated signature is allowed only when simulation is explicitly enabled.
  - `createPaymentLinkFailsClearlyWhenKeysMissingAndSimulationOff`: Verifies clear `IllegalStateException` on missing credentials without silent fallback.
  - `createPaymentLinkSucceedsInSimulationModeWhenExplicitlyEnabled`: Verifies simulation payment link creation when explicitly enabled.
  - `startupFailsIfSimulationAndSupabaseProfileBothActive`: Asserts startup failure if simulation is enabled in `supabase` profile.
  - `confirmsPaymentFromCallbackIdempotently`: Verifies first callback confirms payment and booking, while repeated callback execution does not re-save or duplicate operations.
- `PaymentCallbackControllerTest.java`:
  - `callbackReturnsSuccessViewWhenConfirmationSucceeds`: Tests HTTP 200 and success model attributes on valid callback.
  - `callbackReturnsErrorViewWhenConfirmationFails`: Tests HTTP 200 and error view with message on invalid/failed payment.
- `BookingServiceTest.java`:
  - `expireStaleBookingsFreesSeatsAndMarksExpired`: Verifies unpaid pending bookings are expired and seats freed after hold window.

---

### Phase 4: QR Ticket & Async Notification (UPCOMING 📋)

1. Add `ticketToken` (UUID) and `checkedIn` (boolean) to `Booking`.
2. Add ZXing dependency (`com.google.zxing:core:3.5.3` and `javase:3.5.3`) for server-side QR generation encoding `{app.base-url}/verify/{ticketToken}`.
3. Protected `/verify/{token}` endpoint with admin-only check-in action.
4. Asynchronous email dispatch via `@TransactionalEventListener(phase = AFTER_COMMIT)` and `@Async` using `spring-boot-starter-mail`.

---

## 3. Environment Variables Reference

| Variable | Purpose | Default / Fallback |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | Default (H2 local file) |
| `SUPABASE_DB_URL` | Supabase JDBC connection URL | Used with `supabase` profile |
| `SUPABASE_DB_USER` | Supabase database user | Used with `supabase` profile |
| `SUPABASE_DB_PASSWORD` | Supabase database password | Used with `supabase` profile |
| `RAZORPAY_KEY_ID` | Razorpay public test key ID | Empty |
| `RAZORPAY_KEY_SECRET` | Razorpay API secret | Empty |
| `RAZORPAY_WEBHOOK_SECRET` | Secret configured on Razorpay dashboard | Empty (Dev bypass if unset) |
| `APP_BASE_URL` | Base application URL | `http://localhost:8080` |
| `APP_PAYMENT_SIMULATION` | Explicitly enable local payment simulation | `false` |
| `MAIL_USER` | SMTP username for tickets (Phase 4) | Unset |
| `MAIL_APP_PASSWORD` | SMTP password/app key (Phase 4) | Unset |

---

## 4. Phase 4: QR Ticket & Asynchronous Email Delivery

### Architecture & Implementation Details

#### 1. Database Schema Additions (`Booking.java`)
Three nullable columns were added so the application remains backwards-compatible with existing databases:
- `ticket_token`: Unique 36-character UUID string generated upon payment confirmation.
- `checked_in_at`: Timestamp indicating when the ticket was checked in at the cinema entrance (null until checked in).
- `ticket_email_sent_at`: Timestamp indicating when the confirmation email was successfully dispatched (ensures idempotency / single-delivery).

#### 2. QR Code Generation (`TicketQrService.java`)
- Powered by ZXing 3.5.3 (`com.google.zxing:core` and `com.google.zxing:javase`).
- Generates a 300x300 PNG QR code containing the verification URL `${app.base-url}/verify/{ticketToken}`.
- Exposed via `GET /ticket/{token}/qr.png` (returns 404 if token not found).

#### 3. Verification Endpoint (`TicketController.java` & `verify.html`)
- `GET /verify/{token}`:
  - If token does not exist or booking is not `CONFIRMED`: Renders `INVALID TICKET` page.
  - If booking is `CONFIRMED` and `checkedInAt == null`: Renders `VALID TICKET` with showtime, movie, screen, seats, and booking ID.
  - If booking is `CONFIRMED` and `checkedInAt != null`: Renders `ALREADY USED` with the check-in timestamp.
  - Read-only: Scanning the QR code never mutates state.

#### 4. Asynchronous Ticket Emailing (`TicketMailService.java` & `BookingConfirmedEvent.java`)
- `BookingConfirmedEvent` is published by `PaymentService` inside `confirmFromCallback` after booking is saved as `CONFIRMED`.
- `TicketMailService` handles this event asynchronously (`@Async`) via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
- Dispatches a rich HTML email with the QR code embedded as an inline attachment (`cid:ticketqr`) and the verification link.
- Gracefully skips email dispatch if `MAIL_USER` / `MAIL_APP_PASSWORD` are unset or placeholder (`"..."`), ensuring payment confirmation never fails due to email issues.

#### 5. Callback Page QR Display (`payment-callback.html`)
- Displays the ticket QR image directly on the confirmation screen once payment is confirmed, with a note informing the customer that their ticket has also been emailed.

---

## 5. Current Test Verification Report

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.moviebooking.exception.GlobalExceptionHandlerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moviebooking.controller.PaymentCallbackControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moviebooking.controller.TicketControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moviebooking.service.TicketQrServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moviebooking.service.PaymentServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moviebooking.service.BookingServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.moviebooking.repository.BookingPersistenceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
