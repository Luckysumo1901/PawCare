package com.pawcare.backend.dto;

import java.time.Instant;

import com.pawcare.backend.entity.PayoutLedger;

public record PayoutLedgerResponse(
        String id, String providerId, String bookingId, String paymentId,
        Double amount, String status, Instant createdAt, Instant paidAt
) {
    public static PayoutLedgerResponse from(PayoutLedger p) {
        return new PayoutLedgerResponse(
                p.getId(), p.getProviderId(), p.getBookingId(), p.getPaymentId(),
                p.getAmount(), p.getStatus(), p.getCreatedAt(), p.getPaidAt()
        );
    }
}