package com.pawcare.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RazorpayLinkedAccountRequest(
        @NotBlank String businessName,
        @Email @NotBlank String email,
        @NotBlank String phone,
        @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Enter a valid PAN") String pan
) {}