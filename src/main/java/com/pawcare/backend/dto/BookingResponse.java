package com.pawcare.backend.dto;

import java.time.Instant;

import com.pawcare.backend.entity.Booking;

public record BookingResponse(
        String id, String ownerId, String providerId, String petId,
        String serviceType, Instant scheduledStart, Instant scheduledEnd, String status,
        String paymentStatus, String address
) {
    public static BookingResponse from(Booking b) {
        return from(b, null);
    }

    public static BookingResponse from(Booking b, String paymentStatus) {
        return new BookingResponse(
                b.getId(), b.getOwnerId(), b.getProviderId(), b.getPetId(),
                b.getServiceType(), b.getScheduledStart(), b.getScheduledEnd(), b.getStatus(),
                paymentStatus, b.getAddress()
        );
    }

    /**
     * Address-redacted variant: hides the address unless the requesting user
     * is the owner, the provider on an ACCEPTED (or later) booking, or an admin.
     */
    public static BookingResponse forViewer(Booking b, String paymentStatus, String viewerId, boolean isAdmin) {
        boolean isOwner = viewerId.equals(b.getOwnerId());
        boolean isProviderWithAccess = viewerId.equals(b.getProviderId())
                && !"PENDING".equals(b.getStatus())
                && !"CANCELLED".equals(b.getStatus());

        String visibleAddress = (isOwner || isProviderWithAccess || isAdmin) ? b.getAddress() : null;

        return new BookingResponse(
                b.getId(), b.getOwnerId(), b.getProviderId(), b.getPetId(),
                b.getServiceType(), b.getScheduledStart(), b.getScheduledEnd(), b.getStatus(),
                paymentStatus, visibleAddress
        );
    }
}