package com.pawcare.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payout_ledger")
public class PayoutLedger {
    @Id
    private String id;
    private String providerId;
    private String bookingId;
    private String paymentId;
    private Double amount;
    private String status; // PENDING_PAYOUT, PAID
    private Instant createdAt;
    private Instant paidAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}