package com.pawcare.backend.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class BookingStateMachine {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "PENDING",     Set.of("ACCEPTED", "CANCELLED"),
            "ACCEPTED",    Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("COMPLETED"),
            "COMPLETED",   Set.of(),
            "CANCELLED",   Set.of()
    );

    public boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}