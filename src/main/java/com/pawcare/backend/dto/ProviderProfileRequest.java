package com.pawcare.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record ProviderProfileRequest(
        @NotEmpty List<String> serviceTypes,
        @Positive Double hourlyRate
) {}