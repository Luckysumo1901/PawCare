package com.pawcare.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        @NotBlank String bookingId,
        @Min(1) @Max(5) int rating,
        String comment
) {}