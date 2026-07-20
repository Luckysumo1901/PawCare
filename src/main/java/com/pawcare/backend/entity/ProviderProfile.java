package com.pawcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "provider_profiles")
public class ProviderProfile {
    @Id
    private String id;
    private String userId;

    @ElementCollection
    @CollectionTable(name = "provider_service_types", joinColumns = @JoinColumn(name = "provider_profile_id"))
    @Column(name = "service_type")
    private List<String> serviceTypes;

    private Double hourlyRate;
    private String razorpayAccountId;
    private Boolean verified = false;
    private Double averageRating;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}