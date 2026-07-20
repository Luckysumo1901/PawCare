package com.pawcare.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PetProfileRequest(
        @NotBlank String name,
        @NotBlank String species,
        String breed,
        Integer age,
        String notes
) {}