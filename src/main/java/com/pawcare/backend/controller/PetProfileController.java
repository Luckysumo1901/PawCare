package com.pawcare.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.pawcare.backend.dto.PetProfileRequest;
import com.pawcare.backend.dto.PetProfileResponse;
import com.pawcare.backend.entity.PetProfile;
import com.pawcare.backend.repository.PetProfileRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/pets")
public class PetProfileController {

    private final PetProfileRepository petProfileRepository;

    public PetProfileController(PetProfileRepository petProfileRepository) {
        this.petProfileRepository = petProfileRepository;
    }

    // RBAC: only users with OWNER role can hold pets. ADMIN can view everything for support/audit.
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody PetProfileRequest req, Authentication auth) {
        String ownerId = auth.getName(); // JWT subject == User.id, set by JwtAuthFilter

        PetProfile pet = new PetProfile(null, ownerId, req.name(), req.species(), req.breed(), req.age(), req.notes());
        petProfileRepository.save(pet);

        return ResponseEntity.ok(PetProfileResponse.from(pet));
    }

    // RBAC: OWNER sees their own pets, ADMIN can see any owner's pets via ?ownerId=
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String ownerId, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        String targetOwnerId = (isAdmin && ownerId != null) ? ownerId : auth.getName();

        List<PetProfileResponse> pets = petProfileRepository.findByOwnerId(targetOwnerId).stream()
                .map(PetProfileResponse::from)
                .toList();

        return ResponseEntity.ok(pets);
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable String id, Authentication auth) {
        return findOwnedOrForbid(id, auth)
                .<ResponseEntity<?>>map(pet -> ResponseEntity.ok(PetProfileResponse.from(pet)))
                .orElse(ResponseEntity.status(404).body("Pet profile not found"));
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody PetProfileRequest req, Authentication auth) {
        var existing = findOwnedOrForbid(id, auth);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body("Pet profile not found");
        }

        PetProfile pet = existing.get();
        pet.setName(req.name());
        pet.setSpecies(req.species());
        pet.setBreed(req.breed());
        pet.setAge(req.age());
        pet.setNotes(req.notes());
        petProfileRepository.save(pet);

        return ResponseEntity.ok(PetProfileResponse.from(pet));
    }

    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id, Authentication auth) {
        var existing = findOwnedOrForbid(id, auth);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body("Pet profile not found");
        }
        petProfileRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Ownership check: an OWNER can only ever touch their own pets, regardless of
    // what id they pass in the URL — this is the ABAC-style guard for this resource.
    // ADMIN bypasses the ownership restriction for support/audit purposes.
    private java.util.Optional<PetProfile> findOwnedOrForbid(String id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return petProfileRepository.findById(id);
        }
        return petProfileRepository.findByIdAndOwnerId(id, auth.getName());
    }
}