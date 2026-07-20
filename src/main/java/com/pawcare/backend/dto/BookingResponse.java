package com.pawcare.backend.dto;

import java.time.Instant;

import com.pawcare.backend.entity.Booking;

public record BookingResponse(
        String id, String ownerId, String providerId, String petId,
        String serviceType, Instant scheduledStart, Instant scheduledEnd, String status,
        String paymentStatus
) {
    public static BookingResponse from(Booking b) {
        return from(b, null);
    }

    public static BookingResponse from(Booking b, String paymentStatus) {
        return new BookingResponse(
                b.getId(), b.getOwnerId(), b.getProviderId(), b.getPetId(),
                b.getServiceType(), b.getScheduledStart(), b.getScheduledEnd(), b.getStatus(),
                paymentStatus
        );
    }
}