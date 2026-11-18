package com.claims.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.claims.events.ClaimEvent;
import com.claims.events.ClaimEventType;
import com.claims.events.ClaimSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationRulesTest {

    private static final UUID CLAIMANT = UUID.randomUUID();
    private static final UUID OFFICER = UUID.randomUUID();

    private static ClaimEvent event(ClaimEventType type, UUID officerId) {
        ClaimSnapshot claim = new ClaimSnapshot(
                UUID.randomUUID(), "CLM-2026-000123", "MY", "MOTOR", "UNDER_REVIEW",
                CLAIMANT, officerId, "Tan Wei Ming", UUID.randomUUID(),
                new BigDecimal("8500.00"), "MYR", Instant.now(), null, null);
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), 1, claim);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimEventType.class, names = {
        "CLAIM_ASSIGNED", "INFORMATION_REQUESTED", "CLAIM_APPROVED", "CLAIM_REJECTED", "CLAIM_SETTLED"
    })
    void progressOnAClaimIsToldToTheClaimant(ClaimEventType type) {
        var draft = NotificationRules.forEvent(event(type, OFFICER));

        assertThat(draft).isPresent();
        assertThat(draft.get().recipientId()).isEqualTo(CLAIMANT);
        assertThat(draft.get().message()).contains("CLM-2026-000123");
    }

    @Test
    void aClaimantResponseIsToldToTheAssignedStaffMember() {
        var draft = NotificationRules.forEvent(event(ClaimEventType.INFORMATION_PROVIDED, OFFICER));

        assertThat(draft).isPresent();
        assertThat(draft.get().recipientId()).isEqualTo(OFFICER);
        assertThat(draft.get().message()).contains("CLM-2026-000123").containsIgnoringCase("responded");
    }

    @ParameterizedTest
    @EnumSource(value = ClaimEventType.class, names = {"CLAIM_SUBMITTED", "CLAIM_ASSESSED"})
    void submissionAndAssessmentNotifyNobody(ClaimEventType type) {
        assertThat(NotificationRules.forEvent(event(type, OFFICER))).isEmpty();
    }

    @Test
    void aResponseOnAnUnassignedClaimHasNobodyToNotify() {
        assertThat(NotificationRules.forEvent(event(ClaimEventType.INFORMATION_PROVIDED, null))).isEmpty();
    }

    @Test
    void theMessageSaysWhatHappenedInPlainWords() {
        assertThat(NotificationRules.forEvent(event(ClaimEventType.INFORMATION_REQUESTED, OFFICER)).get().message())
                .containsIgnoringCase("more information");
        assertThat(NotificationRules.forEvent(event(ClaimEventType.CLAIM_APPROVED, OFFICER)).get().message())
                .containsIgnoringCase("approved");
        assertThat(NotificationRules.forEvent(event(ClaimEventType.CLAIM_REJECTED, OFFICER)).get().message())
                .containsIgnoringCase("rejected");
    }
}
