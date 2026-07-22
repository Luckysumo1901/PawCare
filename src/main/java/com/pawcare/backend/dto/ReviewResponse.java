package com.pawcare.backend.dto;

import java.time.Instant;
import com.pawcare.backend.entity.Review;

public record ReviewResponse(
        String id,
        String bookingId,
        String reviewerId,
        String reviewerName,
        Integer rating,
        String comment,
        Instant createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getReviewerId(), null,
                r.getRating(), r.getComment(), r.getCreatedAt());
    }

    public static ReviewResponse from(Review r, String reviewerName) {
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getReviewerId(), reviewerName,
                r.getRating(), r.getComment(), r.getCreatedAt());
    }
}