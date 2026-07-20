package com.pawcare.backend.dto;

import java.util.List;

import com.pawcare.backend.entity.ProviderProfile;
import com.pawcare.backend.entity.User;

public record ProviderProfileResponse(
        String id, String userId, String providerName, List<String> serviceTypes,
        Double hourlyRate, Boolean verified, Double averageRating
) {
    public static ProviderProfileResponse from(ProviderProfile p, User user) {
        return new ProviderProfileResponse(
                p.getId(), p.getUserId(), user != null ? user.getName() : "Provider",
                p.getServiceTypes(), p.getHourlyRate(), p.getVerified(), p.getAverageRating()
        );
    }
}