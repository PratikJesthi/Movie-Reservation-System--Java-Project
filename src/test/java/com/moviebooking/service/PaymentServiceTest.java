package com.moviebooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moviebooking.exception.ConflictException;
import com.moviebooking.model.Booking;
import com.moviebooking.model.BookingSeat;
import com.moviebooking.model.BookingStatus;
import com.moviebooking.model.Payment;
import com.moviebooking.model.PaymentStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.BookingSeatRepository;
import com.moviebooking.repository.PaymentRepository;
import com.razorpay.PaymentLink;
import com.razorpay.PaymentLinkClient;
import com.razorpay.RazorpayClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingSeatRepository bookingSeatRepository;

    @Mock
    private Environment environment;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private PaymentLinkClient paymentLinkClient;

    private PaymentService paymentService;

    private static final String TEST_SECRET = "secret_key_xyz_987654";
    private static final String TEST_KEY_ID = "rzp_test_123456";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                bookingRepository, paymentRepository, bookingSeatRepository, Optional.empty(), environment, eventPublisher);
        paymentService.setKeyId(TEST_KEY_ID);
        paymentService.setKeySecret(TEST_SECRET);
        paymentService.setSimulationEnabled(false);
    }

    private String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Hex.encodeHexString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validPaymentLinkSignaturePassesVerification() throws Exception {
        String paymentLinkId = "plink_test_001";
        String referenceId = "42";
        String status = "paid";
        String paymentId = "pay_test_999";

        String payload = paymentLinkId + "|" + referenceId + "|" + status + "|" + paymentId;
        String validSignature = computeHmacSha256(payload, TEST_SECRET);

        boolean isValid = paymentService.isValidPaymentLinkSignature(
                paymentLinkId, referenceId, status, paymentId, validSignature);

        assertTrue(isValid, "Valid signature must pass verification");
    }

    @Test
    void invalidPaymentLinkSignatureFailsVerification() {
        String paymentLinkId = "plink_test_001";
        String referenceId = "42";
        String status = "paid";
        String paymentId = "pay_test_999";

        String tamperedSignature = "bad_signature_that_does_not_match";

        boolean isValid = paymentService.isValidPaymentLinkSignature(
                paymentLinkId, referenceId, status, paymentId, tamperedSignature);

        assertFalse(isValid, "Tampered signature must be rejected");
    }

    @Test
    void rejectsConfirmationWhenSignatureIsInvalid() {
        String paymentLinkId = "plink_test_001";
        String referenceId = "42";
        String status = "paid";
        String paymentId = "pay_test_999";
        String invalidSignature = "invalid_signature_hex";

        assertThrows(ConflictException.class, () ->
                paymentService.confirmFromCallback(paymentLinkId, referenceId, status, paymentId, invalidSignature));

        verify(bookingRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void simulatedSignatureIsRejectedWhenSimulationIsOffEvenIfKeySecretIsBlank() {
        paymentService.setSimulationEnabled(false);
        paymentService.setKeySecret(""); // Blank key secret

        String paymentLinkId = "plink_sim_123";
        String referenceId = "55";
        String status = "paid";
        String paymentId = "pay_sim_999";
        String simulatedSig = "simulated_valid_signature";

        // Signature validation must reject
        boolean isValid = paymentService.isValidPaymentLinkSignature(
                paymentLinkId, referenceId, status, paymentId, simulatedSig);
        assertFalse(isValid, "Simulated signature must be rejected whenever simulation is off");

        // Callback confirmation must reject and save nothing
        assertThrows(ConflictException.class, () ->
                paymentService.confirmFromCallback(paymentLinkId, referenceId, status, paymentId, simulatedSig));

        verify(bookingRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void simulatedSignatureIsAcceptedWhenSimulationIsExplicitlyOn() {
        paymentService.setSimulationEnabled(true);
        paymentService.setKeySecret(""); // In simulation mode, no secret needed

        boolean isValid = paymentService.isValidPaymentLinkSignature(
                "plink_sim_123", "55", "paid", "pay_sim_999", "simulated_valid_signature");

        assertTrue(isValid, "Simulated signature must be accepted when simulation is explicitly enabled");
    }

    @Test
    void createPaymentLinkFailsClearlyWhenKeysMissingAndSimulationOff() {
        paymentService.setSimulationEnabled(false);
        paymentService.setKeyId("");
        paymentService.setKeySecret("");

        Booking booking = new Booking();
        booking.setId(99L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("200.00"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.createPaymentLink(booking));

        assertTrue(ex.getMessage().contains("missing and payment simulation is disabled"),
                "Error message must clearly state missing keys and disabled simulation");
    }

    @Test
    void createPaymentLinkSucceedsInSimulationModeWhenExplicitlyEnabled() {
        paymentService.setSimulationEnabled(true);
        paymentService.setKeyId("");
        paymentService.setKeySecret("");

        Booking booking = new Booking();
        booking.setId(77L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("250.00"));

        when(paymentRepository.findByBookingId(77L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.createPaymentLink(booking);

        assertTrue(payment.getPaymentLinkId().startsWith("plink_sim_"));
        assertTrue(payment.getPaymentLinkUrl().contains("simulated_valid_signature"));
        assertEquals("77", payment.getReferenceId());
    }

    @Test
    void startupFailsIfSimulationAndSupabaseProfileBothActive() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"supabase"});
        paymentService.setSimulationEnabled(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.validateStartup());

        assertTrue(ex.getMessage().contains("Payment simulation must never be active in the 'supabase' profile"),
                "Startup validation must prevent simulation in supabase profile");
    }

    @Test
    void confirmsPaymentFromCallbackIdempotently() throws Exception {
        String paymentLinkId = "plink_test_002";
        String referenceId = "88";
        String status = "paid";
        String paymentId = "pay_test_555";

        String payload = paymentLinkId + "|" + referenceId + "|" + status + "|" + paymentId;
        String signature = computeHmacSha256(payload, TEST_SECRET);

        Booking booking = new Booking();
        booking.setId(88L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setTotalAmount(new BigDecimal("350.00"));

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setBooking(booking);
        payment.setPaymentLinkId(paymentLinkId);
        payment.setReferenceId(referenceId);
        payment.setStatus(PaymentStatus.CREATED);

        when(bookingRepository.findById(88L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByReferenceId(referenceId)).thenReturn(Optional.of(payment));
        when(bookingSeatRepository.findByBookingId(88L)).thenReturn(List.of(new BookingSeat()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.setSimulationEnabled(true);

        // First callback hit -> should confirm booking and mark payment PAID
        Booking confirmedFirst = paymentService.confirmFromCallback(
                paymentLinkId, referenceId, status, paymentId, signature);

        assertEquals(BookingStatus.CONFIRMED, confirmedFirst.getStatus());
        assertNull(confirmedFirst.getExpiresAt());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(paymentId, payment.getGatewayPaymentId());
        verify(bookingRepository, times(1)).save(booking);
        verify(paymentRepository, times(1)).save(payment);

        // Second callback hit with same parameters -> idempotent, should return without double-saving
        Booking confirmedSecond = paymentService.confirmFromCallback(
                paymentLinkId, referenceId, status, paymentId, signature);

        assertEquals(BookingStatus.CONFIRMED, confirmedSecond.getStatus());
        verify(bookingRepository, times(1)).save(booking);
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    void paymentAfterExpiryIsNotConfirmedAndMarksPaidAfterExpiry() throws Exception {
        String paymentLinkId = "plink_test_expired";
        String referenceId = "66";
        String status = "paid";
        String paymentId = "pay_test_late";

        String payload = paymentLinkId + "|" + referenceId + "|" + status + "|" + paymentId;
        String signature = computeHmacSha256(payload, TEST_SECRET);

        Booking booking = new Booking();
        booking.setId(66L);
        booking.setStatus(BookingStatus.EXPIRED); // Hold already expired!
        booking.setTotalAmount(new BigDecimal("300.00"));

        Payment payment = new Payment();
        payment.setId(25L);
        payment.setBooking(booking);
        payment.setPaymentLinkId(paymentLinkId);
        payment.setReferenceId(referenceId);
        payment.setStatus(PaymentStatus.CREATED);

        when(bookingRepository.findById(66L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByReferenceId(referenceId)).thenReturn(Optional.of(payment));
        when(bookingSeatRepository.findByBookingId(66L)).thenReturn(List.of()); // Seats were already released
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.setSimulationEnabled(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                paymentService.confirmFromCallback(paymentLinkId, referenceId, status, paymentId, signature));

        assertTrue(ex.getMessage().contains("Your payment was received but your seat hold had expired"),
                "Must inform customer that hold expired and to contact support for a refund");

        // Verify Payment was saved as PAID_AFTER_EXPIRY
        assertEquals(PaymentStatus.PAID_AFTER_EXPIRY, payment.getStatus());
        verify(paymentRepository).save(payment);

        // Verify Booking was NOT marked CONFIRMED
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }

    @Test
    void paymentLinkIdMismatchIsRejected() throws Exception {
        String paymentLinkId = "plink_tampered_diff";
        String referenceId = "40";
        String status = "paid";
        String paymentId = "pay_test_id";

        String payload = paymentLinkId + "|" + referenceId + "|" + status + "|" + paymentId;
        String signature = computeHmacSha256(payload, TEST_SECRET);

        Booking booking = new Booking();
        booking.setId(40L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("250.00"));

        Payment payment = new Payment();
        payment.setId(15L);
        payment.setBooking(booking);
        payment.setPaymentLinkId("plink_original_123"); // Original link ID
        payment.setReferenceId(referenceId);

        when(bookingRepository.findById(40L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByReferenceId(referenceId)).thenReturn(Optional.of(payment));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                paymentService.confirmFromCallback(paymentLinkId, referenceId, status, paymentId, signature));

        assertTrue(ex.getMessage().contains("Payment link ID mismatch"),
                "Mismatched payment link ID must be rejected");
        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void amountMismatchIsRejected() throws Exception {
        razorpayClient.paymentLink = paymentLinkClient;
        PaymentService livePaymentService = new PaymentService(
                bookingRepository, paymentRepository, bookingSeatRepository, Optional.of(razorpayClient), environment, eventPublisher);
        livePaymentService.setKeyId(TEST_KEY_ID);
        livePaymentService.setKeySecret(TEST_SECRET);

        String paymentLinkId = "plink_amount_test";
        String referenceId = "30";
        String status = "paid";
        String paymentId = "pay_test_amount";

        String payload = paymentLinkId + "|" + referenceId + "|" + status + "|" + paymentId;
        String signature = computeHmacSha256(payload, TEST_SECRET);

        Booking booking = new Booking();
        booking.setId(30L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("500.00")); // Expected 50000 paise

        Payment payment = new Payment();
        payment.setId(8L);
        payment.setBooking(booking);
        payment.setPaymentLinkId(paymentLinkId);
        payment.setReferenceId(referenceId);
        payment.setStatus(PaymentStatus.CREATED);

        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByReferenceId(referenceId)).thenReturn(Optional.of(payment));

        // Mock Razorpay returning a payment link with mismatched amount: 20000 paise instead of 50000 paise
        JSONObject mockJson = new JSONObject();
        mockJson.put("id", paymentLinkId);
        mockJson.put("status", "paid");
        mockJson.put("amount_paid", 20000);
        PaymentLink mockLink = new PaymentLink(mockJson);
        when(paymentLinkClient.fetch(paymentLinkId)).thenReturn(mockLink);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                livePaymentService.confirmFromCallback(paymentLinkId, referenceId, status, paymentId, signature));

        assertTrue(ex.getMessage().contains("Payment amount mismatch"),
                "Amount mismatch must throw ConflictException");
        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void webhookSignatureRejectsWhenSecretIsBlankOrPlaceholder() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        // Blank secret
        paymentService.setWebhookSecret("");
        assertFalse(paymentService.isValidWebhookSignature(body, "any_sig"));

        paymentService.setWebhookSecret(null);
        assertFalse(paymentService.isValidWebhookSignature(body, "any_sig"));

        // Placeholder "..."
        paymentService.setWebhookSecret("...");
        assertFalse(paymentService.isValidWebhookSignature(body, "any_sig"));

        paymentService.setWebhookSecret("   ...   ");
        assertFalse(paymentService.isValidWebhookSignature(body, "any_sig"));

        // Missing signature header
        paymentService.setWebhookSecret("valid_secret_key");
        assertFalse(paymentService.isValidWebhookSignature(body, null));
        assertFalse(paymentService.isValidWebhookSignature(body, ""));
    }
}
