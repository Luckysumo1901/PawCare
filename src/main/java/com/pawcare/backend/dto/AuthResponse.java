package com.pawcare.backend.dto;

import java.util.List;

public record AuthResponse(String token, String userId, List<String> roles, String name) {}