package com.pawcare.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pawcare.backend.entity.PayoutLedger;

public interface PayoutLedgerRepository extends JpaRepository<PayoutLedger, String> {
    List<PayoutLedger> findByProviderId(String providerId);
}