package com.pawcare.backend.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pawcare.backend.entity.PetProfile;

public interface PetProfileRepository extends JpaRepository<PetProfile, String> {
    List<PetProfile> findByOwnerId(String ownerId);
    Optional<PetProfile> findByIdAndOwnerId(String id, String ownerId);
}