package com.pawcare.backend.controller;

import java.time.Instant;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.dto.BookingResponse;
import com.pawcare.backend.dto.PaymentResponse;
import com.pawcare.backend.dto.PayoutLedgerResponse;
import com.pawcare.backend.dto.UserResponse;
import com.pawcare.backend.entity.PayoutLedger;
import com.pawcare.backend.repository.BookingRepository;
import com.pawcare.backend.repository.PaymentRepository;
import com.pawcare.backend.repository.PayoutLedgerRepository;
import com.pawcare.backend.repository.UserRepository;
import com.pawcare.backend.service.AuditLogService;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PayoutLedgerRepository payoutLedgerRepository;
    private final AuditLogService auditLogService;

    public AdminController(BookingRepository bookingRepository, PaymentRepository paymentRepository,
                            UserRepository userRepository, PayoutLedgerRepository payoutLedgerRepository,
                            AuditLogService auditLogService) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.payoutLedgerRepository = payoutLedgerRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> allUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(UserResponse::from).toList());
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> allBookings() {
        return ResponseEntity.ok(bookingRepository.findAll().stream().map(BookingResponse::from).toList());
    }

    @GetMapping("/payments")
    public ResponseEntity<?> allPayments() {
        return ResponseEntity.ok(paymentRepository.findAll().stream().map(PaymentResponse::from).toList());
    }

    @GetMapping("/payouts")
    public ResponseEntity<?> allPayouts() {
        return ResponseEntity.ok(payoutLedgerRepository.findAll().stream().map(PayoutLedgerResponse::from).toList());
    }

    @PatchMapping("/payouts/{id}/mark-paid")
    public ResponseEntity<?> markPayoutPaid(@PathVariable String id, Authentication auth) {
        Optional<PayoutLedger> entryOpt = payoutLedgerRepository.findById(id);
        if (entryOpt.isEmpty()) return ResponseEntity.status(404).body("Payout entry not found");

        PayoutLedger entry = entryOpt.get();
        entry.setStatus("PAID");
        entry.setPaidAt(Instant.now());
        payoutLedgerRepository.save(entry);

        auditLogService.log(auth.getName(), "PAYOUT_MARKED_PAID", "PayoutLedger", id);

        return ResponseEntity.ok(PayoutLedgerResponse.from(entry));
    }
}