package com.pawcare.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pawcare.backend.entity.Booking;

public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findByOwnerId(String ownerId);
    List<Booking> findByProviderId(String providerId);
}