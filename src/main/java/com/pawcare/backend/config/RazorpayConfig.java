package com.pawcare.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.razorpay.RazorpayClient;

@Configuration
public class RazorpayConfig {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Bean
    public RazorpayClient razorpayClient() throws Exception {
        if (keyId == null || keyId.isBlank() || keyId.contains("your_key")) {
            throw new IllegalStateException(
                "RAZORPAY_KEY_ID is missing or a placeholder. " +
                "Set it before starting the app (see set-env.ps1 / set-env.sh).");
        }
        if (keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException(
                "RAZORPAY_KEY_SECRET is missing. " +
                "Set it before starting the app (see set-env.ps1 / set-env.sh).");
        }
        return new RazorpayClient(keyId, keySecret);
    }
}