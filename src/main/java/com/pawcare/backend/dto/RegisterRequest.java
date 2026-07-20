package com.pawcare.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @Email @NotBlank String email,
        @Size(min = 8) String password,
        @NotBlank @Pattern(regexp = "OWNER|PROVIDER", message = "role must be OWNER or PROVIDER") String role
) {}