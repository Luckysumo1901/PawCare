package com.pawcare.backend.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotBlank String providerId,
        @NotBlank String petId,
        @NotBlank String serviceType,
        @NotNull @Future Instant scheduledStart,
        @NotNull @Future Instant scheduledEnd,
        @NotBlank String address
) {}