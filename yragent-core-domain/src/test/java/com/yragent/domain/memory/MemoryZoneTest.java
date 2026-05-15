package com.yragent.domain.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryZoneTest {

    @Test
    void shouldMapUserPreferenceToPreferenceZone() {
        assertEquals(MemoryZone.PREFERENCE, MemoryZone.from(MemoryType.USER_PREFERENCE));
    }

    @Test
    void shouldMapProjectPolicyToPreferenceZone() {
        assertEquals(MemoryZone.PREFERENCE, MemoryZone.from(MemoryType.PROJECT_POLICY));
    }

    @Test
    void shouldMapFailurePatternToExperienceZone() {
        assertEquals(MemoryZone.EXPERIENCE, MemoryZone.from(MemoryType.FAILURE_PATTERN));
    }

    @Test
    void shouldMapDecisionToDecisionZone() {
        assertEquals(MemoryZone.DECISION, MemoryZone.from(MemoryType.DECISION));
    }

    @Test
    void shouldMapGateAttemptToDecisionZone() {
        assertEquals(MemoryZone.DECISION, MemoryZone.from(MemoryType.GATE_ATTEMPT));
    }

    @Test
    void shouldMapTaskStateToEntityZone() {
        assertEquals(MemoryZone.ENTITY, MemoryZone.from(MemoryType.TASK_STATE));
    }

    @Test
    void shouldReturnNullForNullType() {
        assertNull(MemoryZone.from(null));
    }
}
