package com.pawcare.backend.dto;

import com.pawcare.backend.entity.PetProfile;

public record PetProfileResponse(
        String id,
        String ownerId,
        String name,
        String species,
        String breed,
        Integer age,
        String notes
) {
    public static PetProfileResponse from(PetProfile p) {
        return new PetProfileResponse(
                p.getId(), p.getOwnerId(), p.getName(), p.getSpecies(),
                p.getBreed(), p.getAge(), p.getNotes()
        );
    }
}