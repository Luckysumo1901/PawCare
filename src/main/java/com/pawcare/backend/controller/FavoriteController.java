package com.pawcare.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.entity.Favorite;
import com.pawcare.backend.repository.FavoriteRepository;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;

    public FavoriteController(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping
    public ResponseEntity<?> myFavorites(Authentication auth) {
        List<String> providerIds = favoriteRepository.findByOwnerId(auth.getName())
                .stream().map(Favorite::getProviderId).toList();
        return ResponseEntity.ok(providerIds);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/{providerId}")
    public ResponseEntity<?> toggleFavorite(@PathVariable String providerId, Authentication auth) {
        Optional<Favorite> existing = favoriteRepository.findByOwnerIdAndProviderId(auth.getName(), providerId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("favorited", false));
        }
        favoriteRepository.save(new Favorite(null, auth.getName(), providerId));
        return ResponseEntity.ok(Map.of("favorited", true));
    }
}