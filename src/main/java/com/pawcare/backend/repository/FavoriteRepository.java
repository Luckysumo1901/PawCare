package com.pawcare.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pawcare.backend.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    List<Favorite> findByOwnerId(String ownerId);
    Optional<Favorite> findByOwnerIdAndProviderId(String ownerId, String providerId);
}