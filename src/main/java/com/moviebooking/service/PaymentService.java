package com.moviebooking.service;

import com.moviebooking.event.BookingConfirmedEvent;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.model.Payment;
import com.moviebooking.model.PaymentStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.BookingSeatRepository;
import com.moviebooking.repository.PaymentRepository;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles Razorpay hosted Payment Links and payment verification:
 * - Creating hosted Payment Links with server-calculated amounts
 * - Verifying callback HMAC-SHA256 signatures with the key secret
 * - Server-side verification via Razorpay fetch API
 * - Idempotent payment confirmation
 * - Strict simulation controls (off by default; forbidden in supabase profile)
 * - Correctness guards: hold expiry check, link ID and amount validation
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final Optional<RazorpayClient> razorpayClient;
    private final Environment environment;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${app.payment.simulation:false}")
    private boolean simulationEnabled;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          BookingSeatRepository bookingSeatRepository,
                          Optional<RazorpayClient> razorpayClient,
                          Environment environment,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.razorpayClient = razorpayClient;
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void validateStartup() {
        if (simulationEnabled) {
            if (environment != null && Arrays.asList(environment.getActiveProfiles()).contains("supabase")) {
                throw new IllegalStateException("Payment simulation must never be active in the 'supabase' profile! Halting startup.");
            }
            log.warn("*******************************************************************************");
            log.warn("WARNING: PAYMENT SIMULATION MODE IS ACTIVE (app.payment.simulation=true)!");
            log.warn("Real Razorpay payments are NOT enabled. All links bypass the payment gateway.");
            log.warn("DO NOT ENABLE THIS IN PRODUCTION ENVIRONMENTS!");
            log.warn("*******************************************************************************");
        }
    }

    /**
     * Creates a hosted Razorpay Payment Link for a pending booking.
     * The amount is calculated strictly from server-side booking/show seat prices.
     * The booking ID is attached as reference_id.
     */
    @Transactional
    public Payment createPaymentLink(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Cannot initiate payment for a booking that is "
                    + booking.getStatus().name().toLowerCase() + ".");
        }

        boolean hasKeys = keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
        if (!hasKeys && !simulationEnabled) {
            throw new IllegalStateException(
                    "Payment gateway credentials (RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET) are missing and payment simulation is disabled. Cannot create payment link.");
        }

        // Return existing payment if already generated for this booking.
        Optional<Payment> existing = paymentRepository.findByBookingId(booking.getId());
        if (existing.isPresent() && existing.get().getPaymentLinkUrl() != null) {
            return existing.get();
        }

        String idempotencyKey = UUID.randomUUID().toString();
        String referenceId = String.valueOf(booking.getId());

        // Amount in paise (1 INR = 100 paise)
        int amountInPaise = booking.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue();

        String paymentLinkId;
        String shortUrl;

        if (hasKeys && razorpayClient.isPresent()) {
            try {
                JSONObject request = new JSONObject();
                request.put("amount", amountInPaise);
                request.put("currency", "INR");
                request.put("accept_partial", false);
                request.put("reference_id", referenceId);
                request.put("description", "ReelReserve Booking #" + booking.getId());
                request.put("callback_url", appBaseUrl + "/payments/callback");
                request.put("callback_method", "get");

                if (booking.getUser() != null) {
                    JSONObject customer = new JSONObject();
                    customer.put("name", booking.getUser().getName());
                    customer.put("email", booking.getUser().getEmail());
                    request.put("customer", customer);
                }

                PaymentLink link = razorpayClient.get().paymentLink.create(request);
                paymentLinkId = link.get("id");
                shortUrl = link.get("short_url");
                log.info("Created Razorpay Payment Link {} for booking {}", paymentLinkId, booking.getId());
            } catch (RazorpayException ex) {
                log.error("Failed to create Razorpay Payment Link for booking {}: {}", booking.getId(), ex.getMessage(), ex);
                throw new IllegalStateException("Payment gateway communication failed: " + ex.getMessage(), ex);
            }
        } else if (simulationEnabled) {
            // Simulated local mode (only active when app.payment.simulation=true)
            paymentLinkId = "plink_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            shortUrl = appBaseUrl + "/payments/callback"
                    + "?razorpay_payment_id=pay_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                    + "&razorpay_payment_link_id=" + paymentLinkId
                    + "&razorpay_payment_link_reference_id=" + referenceId
                    + "&razorpay_payment_link_status=paid"
                    + "&razorpay_signature=simulated_valid_signature";
            log.info("Simulated Razorpay Payment Link for booking {}: {}", booking.getId(), shortUrl);
        } else {
            throw new IllegalStateException("Payment gateway is not available.");
        }

        Payment payment = existing.orElseGet(Payment::new);
        payment.setBooking(booking);
        payment.setPaymentLinkId(paymentLinkId);
        payment.setPaymentLinkUrl(shortUrl);
        payment.setReferenceId(referenceId);
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(idempotencyKey);
        return paymentRepository.save(payment);
    }

    /**
     * Verifies the Razorpay Payment Link callback HMAC-SHA256 signature.
     * Payload format: payment_link_id|payment_link_reference_id|payment_link_status|razorpay_payment_id
     */
    public boolean isValidPaymentLinkSignature(String paymentLinkId, String referenceId,
                                               String paymentLinkStatus, String razorpayPaymentId,
                                               String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        // Support simulated dev/test signature ONLY when simulation is explicitly enabled
        if ("simulated_valid_signature".equals(signature)) {
            if (!simulationEnabled) {
                log.warn("Rejected simulated signature 'simulated_valid_signature' because app.payment.simulation is false.");
                return false;
            }
            return true;
        }

        if (keySecret == null || keySecret.isBlank()) {
            log.warn("Razorpay key secret not configured; rejecting signature verification.");
            return false;
        }

        try {
            String payload = (paymentLinkId != null ? paymentLinkId : "") + "|"
                    + (referenceId != null ? referenceId : "") + "|"
                    + (paymentLinkStatus != null ? paymentLinkStatus : "") + "|"
                    + (razorpayPaymentId != null ? razorpayPaymentId : "");

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = Hex.encodeHexString(hash);
            return expected.equalsIgnoreCase(signature.trim());
        } catch (Exception ex) {
            log.error("Error computing payment link HMAC signature: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Confirms payment and booking upon callback:
     * 1. Verifies the callback signature with the key secret.
     * 2. Fetches the payment link status and amount from Razorpay API server-side.
     * 3. Validates that paymentLinkId matches stored record and paid amount matches booking amount.
     * 4. If booking is EXPIRED, CANCELLED, or seats released: marks Payment PAID_AFTER_EXPIRY and rejects confirmation.
     * 5. Only if valid and active, marks Payment as PAID and Booking as CONFIRMED.
     * 6. Idempotent: hitting callback repeatedly will not double-confirm or alter state.
     *
     * @return Confirmed Booking entity
     */
    @Transactional
    public Booking confirmFromCallback(String paymentLinkId, String referenceId,
                                       String paymentLinkStatus, String razorpayPaymentId,
                                       String signature) {
        // Step 1: Verify HMAC signature
        boolean signatureValid = isValidPaymentLinkSignature(paymentLinkId, referenceId,
                paymentLinkStatus, razorpayPaymentId, signature);
        if (!signatureValid) {
            log.warn("Payment link callback signature verification FAILED for paymentLinkId={} ref={}",
                    paymentLinkId, referenceId);
            throw new ConflictException("Payment signature verification failed.");
        }

        // Step 2: Find Booking & Payment
        Long bookingId;
        try {
            bookingId = Long.parseLong(referenceId);
        } catch (NumberFormatException ex) {
            throw new ResourceNotFoundException("Invalid booking reference: " + referenceId);
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found."));

        Payment payment = paymentRepository.findByReferenceId(referenceId)
                .or(() -> paymentRepository.findByPaymentLinkId(paymentLinkId))
                .or(() -> paymentRepository.findByBookingId(bookingId))
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setBooking(booking);
                    p.setPaymentLinkId(paymentLinkId);
                    p.setReferenceId(referenceId);
                    p.setAmount(booking.getTotalAmount());
                    p.setIdempotencyKey(UUID.randomUUID().toString());
                    return p;
                });

        // Step 3: Link ID Validation
        if (payment.getPaymentLinkId() != null && !payment.getPaymentLinkId().equals(paymentLinkId)) {
            log.error("Payment link ID mismatch for booking {}: expected {} but received {}",
                    bookingId, payment.getPaymentLinkId(), paymentLinkId);
            throw new ConflictException("Payment link ID mismatch.");
        }

        boolean hasKeys = keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();

        // Step 4: Fetch status and amount from Razorpay API server-side (do NOT trust query params alone)
        if (hasKeys && razorpayClient.isPresent()) {
            try {
                PaymentLink link = razorpayClient.get().paymentLink.fetch(paymentLinkId);
                String apiStatus = link.get("status");
                if (!"paid".equalsIgnoreCase(apiStatus)) {
                    log.warn("Razorpay API returned status '{}' for payment link {}, not 'paid'.",
                            apiStatus, paymentLinkId);
                    throw new ConflictException("Payment link is not paid (gateway status: " + apiStatus + ").");
                }

                // Amount verification
                Object amountPaidObj = link.has("amount_paid") && link.get("amount_paid") != null
                        ? link.get("amount_paid") : link.get("amount");
                long amountPaidPaise = ((Number) amountPaidObj).longValue();
                long expectedPaise = booking.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValue();
                if (amountPaidPaise != expectedPaise) {
                    log.error("Payment amount mismatch for booking {}: expected {} paise but received {} paise.",
                            booking.getId(), expectedPaise, amountPaidPaise);
                    throw new ConflictException("Payment amount mismatch: expected " + expectedPaise
                            + " paise but received " + amountPaidPaise + " paise.");
                }
            } catch (RazorpayException ex) {
                log.error("Failed to query Razorpay API for payment link {}: {}", paymentLinkId, ex.getMessage(), ex);
                throw new IllegalStateException("Failed to verify payment status with gateway: " + ex.getMessage(), ex);
            }
        } else if (simulationEnabled) {
            // Simulation mode check
            if (!"paid".equalsIgnoreCase(paymentLinkStatus)) {
                throw new ConflictException("Payment link status is not paid.");
            }
        } else {
            throw new ConflictException("Payment gateway is not configured and simulation is disabled.");
        }

        // Step 5: Check Hold Expiry & Released Seats (Correctness Guard)
        boolean seatsReleased = bookingSeatRepository.findByBookingId(booking.getId()).isEmpty();
        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.CANCELLED || seatsReleased) {
            log.warn("Payment arrived for booking {} but hold expired or seats were released (status={}, seatsReleased={}).",
                    booking.getId(), booking.getStatus(), seatsReleased);
            payment.setStatus(PaymentStatus.PAID_AFTER_EXPIRY);
            payment.setGatewayPaymentId(razorpayPaymentId);
            paymentRepository.save(payment);
            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.EXPIRED);
                booking.setExpiresAt(null);
                bookingRepository.save(booking);
            }
            throw new ConflictException("Your payment was received but your seat hold had expired. Please contact support for a refund.");
        }

        // Step 6: Idempotent confirmation check
        if (payment.getStatus() == PaymentStatus.PAID && booking.getStatus() == BookingStatus.CONFIRMED) {
            log.info("Booking {} and payment {} already confirmed. Idempotent return.", bookingId, payment.getId());
            return booking;
        }

        // Apply confirmation
        payment.setStatus(PaymentStatus.PAID);
        payment.setGatewayPaymentId(razorpayPaymentId);
        paymentRepository.save(payment);

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        if (booking.getTicketToken() == null) {
            booking.setTicketToken(UUID.randomUUID().toString());
        }
        Booking savedBooking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingConfirmedEvent(savedBooking.getId()));
        log.info("Successfully confirmed booking {} via payment link callback (paymentId={})",
                bookingId, razorpayPaymentId);

        return savedBooking;
    }

    /**
     * Verifies webhook HMAC-SHA256 signature.
     * Rejects unconditionally if webhook secret is blank, null, or placeholder "...".
     */
    public boolean isValidWebhookSignature(byte[] rawBody, String razorpaySignatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank() || "...".equals(webhookSecret.trim())) {
            log.warn("Razorpay webhook secret is missing or placeholder ('{}'); rejecting webhook.", webhookSecret);
            return false;
        }
        if (razorpaySignatureHeader == null || razorpaySignatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody);
            String computedHex = Hex.encodeHexString(computed);
            return computedHex.equalsIgnoreCase(razorpaySignatureHeader);
        } catch (Exception ex) {
            log.error("HMAC signature verification error: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /** Idempotent webhook handler (kept for future/deferred testing). */
    @Transactional
    public void handleWebhookEvent(String event, String gatewayPaymentId, String gatewayOrderId) {
        log.info("Razorpay webhook event={} orderId={} paymentId={}", event, gatewayOrderId, gatewayPaymentId);
        if ("payment.captured".equals(event) || "order.paid".equals(event)) {
            Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
            if (payment == null) return;
            if (payment.getStatus() == PaymentStatus.PAID) return;
            payment.setStatus(PaymentStatus.PAID);
            if (gatewayPaymentId != null) payment.setGatewayPaymentId(gatewayPaymentId);
            paymentRepository.save(payment);
            Booking booking = payment.getBooking();
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setExpiresAt(null);
                bookingRepository.save(booking);
            }
        }
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isSimulationEnabled() {
        return simulationEnabled;
    }

    public void setSimulationEnabled(boolean simulationEnabled) {
        this.simulationEnabled = simulationEnabled;
    }
}
