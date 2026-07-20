package com.pawcare.backend.entity;

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
@Table(name = "payments")
public class Payment {
    @Id
    private String id;
    private String bookingId;
    private Double amount;
    private Double platformFee;
    private Double providerPayout;
    private String status; // PENDING, PAID, FAILED, REFUNDED
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private Boolean payoutRecorded = false;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }
}