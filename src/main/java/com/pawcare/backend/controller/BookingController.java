package com.pawcare.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.dto.BookingRequest;
import com.pawcare.backend.dto.BookingResponse;
import com.pawcare.backend.entity.Booking;
import com.pawcare.backend.repository.BookingRepository;
import com.pawcare.backend.service.AuditLogService;
import com.pawcare.backend.service.BookingStateMachine;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final BookingStateMachine stateMachine;
    private final AuditLogService auditLogService;
    private final com.pawcare.backend.repository.PaymentRepository paymentRepository;

    public BookingController(BookingRepository bookingRepository, BookingStateMachine stateMachine,
                            AuditLogService auditLogService,
                            com.pawcare.backend.repository.PaymentRepository paymentRepository) {
        this.bookingRepository = bookingRepository;
        this.stateMachine = stateMachine;
        this.auditLogService = auditLogService;
        this.paymentRepository = paymentRepository;
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BookingRequest req, Authentication auth) {
        if (req.scheduledStart().isAfter(req.scheduledEnd()) || req.scheduledStart().equals(req.scheduledEnd())) {
            return ResponseEntity.badRequest().body("Scheduled start must be before scheduled end");
        }

        List<Booking> providerBookings = bookingRepository.findByProviderId(req.providerId());
        boolean hasOverlap = providerBookings.stream()
                .filter(b -> !b.getStatus().equals("CANCELLED"))
                .anyMatch(b -> req.scheduledStart().isBefore(b.getScheduledEnd()) && b.getScheduledStart().isBefore(req.scheduledEnd()));

        if (hasOverlap) {
            return ResponseEntity.status(409).body("Provider is already booked for this slot");
        }

        Booking booking = new Booking(null, auth.getName(), req.providerId(), req.petId(),
                req.serviceType(), req.scheduledStart(), req.scheduledEnd(), "PENDING", req.address());
        bookingRepository.save(booking);

        // Owner always sees their own address back in the create response
        return ResponseEntity.ok(BookingResponse.from(booking));
    }

    @PreAuthorize("hasAnyRole('OWNER','PROVIDER','ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String userId = auth.getName();
        List<Booking> bookings;
        if (isAdmin) {
            bookings = bookingRepository.findAll();
        } else {
            List<Booking> asOwner = bookingRepository.findByOwnerId(userId);
            List<Booking> asProvider = bookingRepository.findByProviderId(userId);
            bookings = new java.util.ArrayList<>(asOwner);
            bookings.addAll(asProvider);
        }

        Map<String, String> paymentStatusByBooking = paymentRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.pawcare.backend.entity.Payment::getBookingId,
                        com.pawcare.backend.entity.Payment::getStatus,
                        (a, b) -> b // if duplicates, keep latest
                ));

        return ResponseEntity.ok(bookings.stream()
                .map(b -> BookingResponse.forViewer(b, paymentStatusByBooking.get(b.getId()), userId, isAdmin))
                .toList());
    }

    @PreAuthorize("hasAnyRole('OWNER','PROVIDER')")
    @PatchMapping("/{id}/{action}")
    public ResponseEntity<?> transition(@PathVariable String id, @PathVariable String action, Authentication auth) {
        Optional<Booking> opt = bookingRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body("Booking not found");

        Booking booking = opt.get();
        String userId = auth.getName();
        boolean isOwner = userId.equals(booking.getOwnerId());
        boolean isProvider = userId.equals(booking.getProviderId());
        if (!isOwner && !isProvider) return ResponseEntity.status(403).body("Not part of this booking");

        String targetStatus = switch (action.toLowerCase()) {
            case "accept" -> "ACCEPTED";
            case "start" -> "IN_PROGRESS";
            case "complete" -> "COMPLETED";
            case "cancel" -> "CANCELLED";
            default -> null;
        };
        if (targetStatus == null) return ResponseEntity.badRequest().body("Unknown action: " + action);

        if (!action.equalsIgnoreCase("cancel") && !isProvider) {
            return ResponseEntity.status(403).body("Only the provider can perform this action");
        }

        if (!stateMachine.canTransition(booking.getStatus(), targetStatus)) {
            return ResponseEntity.status(409).body(
                    "Cannot transition from " + booking.getStatus() + " to " + targetStatus);
        }

        booking.setStatus(targetStatus);
        bookingRepository.save(booking);

        auditLogService.log(userId, "BOOKING_" + targetStatus, "Booking", booking.getId());

        return ResponseEntity.ok(BookingResponse.forViewer(booking, null, userId, false));
    }
}