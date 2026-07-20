package com.pawcare.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.dto.ReviewRequest;
import com.pawcare.backend.dto.ReviewResponse;
import com.pawcare.backend.entity.Booking;
import com.pawcare.backend.entity.Payment;
import com.pawcare.backend.entity.Review;
import com.pawcare.backend.repository.BookingRepository;
import com.pawcare.backend.repository.PaymentRepository;
import com.pawcare.backend.repository.ReviewRepository;

import java.util.Optional;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public ReviewController(ReviewRepository reviewRepository,
                            BookingRepository bookingRepository,
                            PaymentRepository paymentRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    // RBAC: only OWNER can leave a review (they're the one who booked the service)
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ReviewRequest req, Authentication auth) {
        String reviewerId = auth.getName();

        // Rule: at most one review per booking
        if (reviewRepository.existsByBookingId(req.bookingId())) {
            return ResponseEntity.status(409).body("This booking already has a review");
        }

        // 1. Load the Booking by req.bookingId() and confirm it exists
        Optional<Booking> bookingOpt = bookingRepository.findById(req.bookingId());
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Booking not found");
        }
        Booking booking = bookingOpt.get();

        // 2. Confirm booking.getOwnerId().equals(reviewerId)
        if (!booking.getOwnerId().equals(reviewerId)) {
            return ResponseEntity.status(403).body("Not authorized to review this booking");
        }

        // 3. Confirm booking.getStatus() == COMPLETED
        if (!"COMPLETED".equals(booking.getStatus())) {
            return ResponseEntity.status(400).body("Booking is not completed");
        }

        // 4. Load the Payment for this booking and confirm status == PAID
        Optional<Payment> paymentOpt = paymentRepository.findByBookingId(req.bookingId());
        if (paymentOpt.isEmpty() || !"PAID".equals(paymentOpt.get().getStatus())) {
            return ResponseEntity.status(400).body("Booking must be paid before leaving a review");
        }

        Review review = new Review(null, req.bookingId(), reviewerId, req.rating(), req.comment(), null);
        reviewRepository.save(review);

        return ResponseEntity.ok(ReviewResponse.from(review));
    }

    // Anyone authenticated can view a booking's review (owner checking back,
    // provider checking their feedback, admin auditing)
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<?> getForBooking(@PathVariable String bookingId) {
        return reviewRepository.findByBookingId(bookingId)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(ReviewResponse.from(r)))
                .orElse(ResponseEntity.status(404).body("No review for this booking yet"));
    }
}