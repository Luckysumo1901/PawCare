package com.pawcare.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    private String id;
    private String ownerId;
    private String providerId;
    private String petId;
    private String serviceType;
    private Instant scheduledStart;
    private Instant scheduledEnd;
    private String status; // PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}