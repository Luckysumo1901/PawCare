package com.pawcare.backend.dto;

import com.pawcare.backend.entity.Review;

public record ReviewResponse(
        String id,
        String bookingId,
        String reviewerId,
        Integer rating,
        String comment
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getReviewerId(), r.getRating(), r.getComment());
    }
}