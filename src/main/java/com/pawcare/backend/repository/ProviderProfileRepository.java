package com.pawcare.backend.repository;

import com.pawcare.backend.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, String> {
    Optional<ProviderProfile> findByUserId(String userId);
    List<ProviderProfile> findByServiceTypesContaining(String serviceType);
}