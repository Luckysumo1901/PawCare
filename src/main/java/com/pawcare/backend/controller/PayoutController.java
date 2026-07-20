package com.pawcare.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.dto.PayoutLedgerResponse;
import com.pawcare.backend.entity.PayoutLedger;
import com.pawcare.backend.repository.PayoutLedgerRepository;

@RestController
@RequestMapping("/providers")
public class PayoutController {

    private final PayoutLedgerRepository payoutLedgerRepository;

    public PayoutController(PayoutLedgerRepository payoutLedgerRepository) {
        this.payoutLedgerRepository = payoutLedgerRepository;
    }

    @PreAuthorize("hasRole('PROVIDER')")
    @GetMapping("/me/payouts")
    public ResponseEntity<?> myPayouts(Authentication auth) {
        List<PayoutLedger> entries = payoutLedgerRepository.findByProviderId(auth.getName());

        double totalEarned = entries.stream().mapToDouble(PayoutLedger::getAmount).sum();
        double totalPending = entries.stream()
                .filter(e -> "PENDING_PAYOUT".equals(e.getStatus()))
                .mapToDouble(PayoutLedger::getAmount).sum();
        double totalPaid = entries.stream()
                .filter(e -> "PAID".equals(e.getStatus()))
                .mapToDouble(PayoutLedger::getAmount).sum();

        return ResponseEntity.ok(Map.of(
                "entries", entries.stream().map(PayoutLedgerResponse::from).toList(),
                "totalEarned", totalEarned,
                "totalPending", totalPending,
                "totalPaid", totalPaid
        ));
    }
}