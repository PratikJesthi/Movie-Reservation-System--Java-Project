package com.moviebooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records a Razorpay payment attempt for a Booking.
 * Tracks payment links, orders, and payment IDs.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The booking this payment covers. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Razorpay payment link id (e.g. plink_XXXX). */
    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    /** Razorpay hosted payment link URL (e.g. https://rzp.io/i/XXXX). */
    @Column(name = "payment_link_url", length = 255)
    private String paymentLinkUrl;

    /** Razorpay order id (e.g. order_XXXX), if orders API was used. */
    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    /** Razorpay payment id (e.g. pay_XXXX). Null until payment is captured/paid. */
    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    /** Reference ID sent to Razorpay (e.g. booking ID). */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /** Amount in rupees (matches booking.totalAmount). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    /**
     * Unique key guaranteeing idempotency: duplicate webhooks or multiple callback hits
     * will not double-confirm or double-charge.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
