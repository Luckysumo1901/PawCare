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
import com.pawcare.backend.entity.User;
import com.pawcare.backend.repository.BookingRepository;
import com.pawcare.backend.repository.PaymentRepository;
import com.pawcare.backend.repository.ProviderProfileRepository;
import com.pawcare.backend.repository.ReviewRepository;
import com.pawcare.backend.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final UserRepository userRepository;

    public ReviewController(ReviewRepository reviewRepository,
                            BookingRepository bookingRepository,
                            PaymentRepository paymentRepository,
                            ProviderProfileRepository providerProfileRepository,
                            UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.userRepository = userRepository;
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

        Optional<Booking> bookingOpt = bookingRepository.findById(req.bookingId());
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Booking not found");
        }
        Booking booking = bookingOpt.get();

        if (!booking.getOwnerId().equals(reviewerId)) {
            return ResponseEntity.status(403).body("Not authorized to review this booking");
        }

        if (!"COMPLETED".equals(booking.getStatus())) {
            return ResponseEntity.status(400).body("Booking is not completed");
        }

        Optional<Payment> paymentOpt = paymentRepository.findByBookingId(req.bookingId());
        if (paymentOpt.isEmpty() || !"PAID".equals(paymentOpt.get().getStatus())) {
            return ResponseEntity.status(400).body("Booking must be paid before leaving a review");
        }

        Review review = new Review(null, req.bookingId(), reviewerId, req.rating(), req.comment(), null);
        reviewRepository.save(review);

        recalculateProviderAverage(booking.getProviderId());

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

    // All reviews for a given provider, with reviewer names + running average.
    // This is the missing "place to see customer reviews" for a provider profile/search card.
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getForProvider(@PathVariable String providerId) {
        List<Booking> providerBookings = bookingRepository.findByProviderId(providerId);
        List<String> bookingIds = providerBookings.stream().map(Booking::getId).toList();

        if (bookingIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("reviews", List.of(), "averageRating", 0.0, "count", 0));
        }

        List<Review> reviews = reviewRepository.findByBookingIdIn(bookingIds);

        Map<String, User> reviewers = userRepository.findAllById(
                reviews.stream().map(Review::getReviewerId).distinct().toList()
        ).stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<ReviewResponse> response = reviews.stream()
                .map(r -> ReviewResponse.from(r, reviewers.containsKey(r.getReviewerId())
                        ? reviewers.get(r.getReviewerId()).getName() : "Pet Owner"))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();

        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);

        return ResponseEntity.ok(Map.of(
                "reviews", response,
                "averageRating", Math.round(avg * 10.0) / 10.0,
                "count", reviews.size()
        ));
    }

    private void recalculateProviderAverage(String providerId) {
        providerProfileRepository.findByUserId(providerId).ifPresent(profile -> {
            List<Booking> providerBookings = bookingRepository.findByProviderId(providerId);
            List<String> bookingIds = providerBookings.stream().map(Booking::getId).toList();
            List<Review> reviews = reviewRepository.findByBookingIdIn(bookingIds);
            double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            profile.setAverageRating(Math.round(avg * 10.0) / 10.0);
            providerProfileRepository.save(profile);
        });
    }
}