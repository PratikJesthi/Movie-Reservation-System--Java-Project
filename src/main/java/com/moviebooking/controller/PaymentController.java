package com.moviebooking.controller;

import com.moviebooking.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Razorpay webhook events.
 * <p>
 * The endpoint reads the RAW request body before JSON deserialization so the
 * HMAC-SHA256 signature can be verified against the exact bytes Razorpay signed.
 * </p>
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/payments/webhook
     * Razorpay calls this after every payment event.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        byte[] rawBody;
        try {
            rawBody = request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            log.error("Failed to read webhook body: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Body read error");
        }

        // 1. Verify signature
        if (signature == null || !paymentService.isValidWebhookSignature(rawBody, signature)) {
            log.warn("Razorpay webhook signature rejected or secret unconfigured.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        // 2. Parse event
        String event;
        String gatewayPaymentId = null;
        String gatewayOrderId = null;
        try {
            JSONObject json = new JSONObject(new String(rawBody));
            event = json.optString("event", "");
            JSONObject payload = json.optJSONObject("payload");
            if (payload != null) {
                JSONObject paymentEntity = payload.optJSONObject("payment");
                if (paymentEntity != null) {
                    JSONObject entity = paymentEntity.optJSONObject("entity");
                    if (entity != null) {
                        gatewayPaymentId = entity.optString("id", null);
                        gatewayOrderId = entity.optString("order_id", null);
                    }
                }
                // For order.paid events the order entity is at payload.order.entity
                if (gatewayOrderId == null) {
                    JSONObject orderEntity = payload.optJSONObject("order");
                    if (orderEntity != null) {
                        JSONObject entity = orderEntity.optJSONObject("entity");
                        if (entity != null) {
                            gatewayOrderId = entity.optString("id", null);
                        }
                    }
                }
            }
        } catch (JSONException ex) {
            log.error("Failed to parse Razorpay webhook JSON: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("Bad JSON");
        }

        // 3. Delegate to service (transactional, idempotent)
        try {
            paymentService.handleWebhookEvent(event, gatewayPaymentId, gatewayOrderId);
        } catch (Exception ex) {
            log.error("Error handling Razorpay webhook event {}: {}", event, ex.getMessage(), ex);
            // Return 200 so Razorpay doesn't retry indefinitely for non-transient errors.
            return ResponseEntity.ok("Processed with error");
        }

        return ResponseEntity.ok("OK");
    }
}
