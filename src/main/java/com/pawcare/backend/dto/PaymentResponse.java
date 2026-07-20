package com.pawcare.backend.dto;

import com.pawcare.backend.entity.Payment;

public record PaymentResponse(
        String id, String bookingId, Double amount, Double platformFee,
        Double providerPayout, String status, String razorpayOrderId, String razorpayPaymentId
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getBookingId(), p.getAmount(), p.getPlatformFee(),
                p.getProviderPayout(), p.getStatus(), p.getRazorpayOrderId(), p.getRazorpayPaymentId()
        );
    }
}