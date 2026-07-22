package com.pawcare.backend.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pawcare.backend.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, String> {
    Optional<Review> findByBookingId(String bookingId);
    boolean existsByBookingId(String bookingId);
    List<Review> findByBookingIdIn(List<String> bookingIds);
}