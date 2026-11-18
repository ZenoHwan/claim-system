package com.claims.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

class ClaimEventJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private static ClaimSnapshot snapshot() {
        return new ClaimSnapshot(
                UUID.randomUUID(), "CLM-2026-000123", "MY", "MOTOR", "ASSESSED",
                UUID.randomUUID(), UUID.randomUUID(), "Tan Wei Ming", UUID.randomUUID(),
                new BigDecimal("7200.00"), "MYR",
                Instant.parse("2026-11-18T01:15:00Z"), null, null);
    }

    @ParameterizedTest
    @EnumSource(ClaimEventType.class)
    void everyEventTypeSurvivesAJsonRoundTrip(ClaimEventType type) {
        ClaimEvent event = new ClaimEvent(UUID.randomUUID(), type, Instant.parse("2026-11-18T02:00:00Z"), 3, snapshot());

        ClaimEvent read = json.readValue(json.writeValueAsString(event), ClaimEvent.class);

        assertThat(read).isEqualTo(event);
    }

    @Test
    void anUnknownFieldAddedByANewerProducerIsIgnored() {
        ClaimEvent event = new ClaimEvent(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, Instant.now(), 0, snapshot());
        String withExtra = json.writeValueAsString(event).replaceFirst("\\{", "{\"addedLater\":\"x\",");

        assertThat(json.readValue(withExtra, ClaimEvent.class).eventId()).isEqualTo(event.eventId());
    }

    @Test
    void thereAreExactlyEightEventTypes() {
        assertThat(ClaimEventType.values()).hasSize(8);
    }
}
