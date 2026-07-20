package com.pawcare.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BookingStateMachineTest {

    private final BookingStateMachine sm = new BookingStateMachine();

    @Test
    void pendingCanMoveToAccepted() {
        assertTrue(sm.canTransition("PENDING", "ACCEPTED"));
    }

    @Test
    void pendingCannotMoveToCompleted() {
        assertFalse(sm.canTransition("PENDING", "COMPLETED"));
    }

    @Test
    void completedIsTerminal() {
        assertFalse(sm.canTransition("COMPLETED", "CANCELLED"));
    }
}