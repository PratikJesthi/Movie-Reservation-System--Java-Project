package com.moviebooking.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a RazorpayClient bean only when razorpay.key-id is configured.
 * When running tests (or without env vars) the bean is absent and PaymentService
 * falls back to a no-op / simulation mode.
 */
@Configuration
public class RazorpayConfig {

    @Bean
    @ConditionalOnProperty(name = "razorpay.key-id", matchIfMissing = false)
    public RazorpayClient razorpayClient(
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret) throws RazorpayException {
        return new RazorpayClient(keyId, keySecret);
    }
}
