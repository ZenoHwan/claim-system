package com.claims.service.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ClaimLifecycleIntegrationTest extends IntegrationTest {

    @Nested
    class Submission {

        @Test
        void motorClaimIsCreatedSubmittedAndUnassigned() throws Exception {
            postJson("/api/claims", bearer(AISHA), motorClaim(AISHA_MOTOR, "8500.00"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.claimNumber").value(matchesPattern("CLM-\\d{4}-\\d{6}")))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.assignedOfficerId").doesNotExist())
                    .andExpect(jsonPath("$.lineOfBusiness").value("MOTOR"))
                    .andExpect(jsonPath("$.marketCode").value("MY"))
                    .andExpect(jsonPath("$.currency").value("MYR"))
                    .andExpect(jsonPath("$.motor.vehicleRegistration").value("WXY 1234"));
        }

        @Test
        void propertyClaimIsCreated() throws Exception {
            postJson("/api/claims", bearer(AISHA), propertyClaim(AISHA_PROPERTY, "42000.00"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lineOfBusiness").value("PROPERTY"))
                    .andExpect(jsonPath("$.property.lossCause").value("WATER"));
        }

        @Test
        void initialReserveEqualsTheClaimedAmount() throws Exception {
            String id = submitMotorClaim("8500.00");

            getAs("/api/claims/" + id, bearer(OFFICER_1)).andExpect(jsonPath("$.currentReserve").value(8500.00));
            assertThat(jdbc.queryForObject(
                    "select reason from reserve where claim_id = ?::uuid", String.class, id)).isEqualTo("INITIAL");
        }

        @Test
        void theClaimedAmountIsOptionalBecauseClaimantsOftenDoNotKnowItYet() throws Exception {
            Map<String, Object> claim = motorClaim(AISHA_MOTOR, "1.00");
            claim.remove("claimedAmount");

            postJson("/api/claims", bearer(AISHA), claim)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.claimedAmount").doesNotExist());
        }

        @Test
        void withNoClaimedAmountTheInitialReserveIsAShareOfTheSumInsuredForThatLine() throws Exception {
            Map<String, Object> motor = motorClaim(AISHA_MOTOR, "1.00");
            motor.remove("claimedAmount");
            Map<String, Object> property = propertyClaim(AISHA_PROPERTY, "1.00");
            property.remove("claimedAmount");

            // motor: 10% of 80,000; property: 5% of 500,000
            getAs("/api/claims/" + submit(AISHA, motor), bearer(OFFICER_1)).andExpect(jsonPath("$.currentReserve").value(8000.00));
            getAs("/api/claims/" + submit(AISHA, property), bearer(OFFICER_1)).andExpect(jsonPath("$.currentReserve").value(25000.00));
        }

        @Test
        void aClaimWithNoClaimedAmountStillListsAndPublishesItsDefaultReserve() throws Exception {
            Map<String, Object> claim = motorClaim(AISHA_MOTOR, "1.00");
            claim.remove("claimedAmount");
            String id = submit(AISHA, claim);

            getAs("/api/claims", bearer(AISHA)).andExpect(status().isOk());
            assertThat(json.readTree(jdbc.queryForObject(
                            "select payload::text from outbox_event where aggregate_id = ?::uuid", String.class, id))
                    .get("claim").get("currentReserve").decimalValue()).isEqualByComparingTo("8000.00");
        }

        @Test
        void someoneElsesPolicyIsRefusedAndNothingIsCreated() throws Exception {
            int before = jdbc.queryForObject("select count(*) from claim", Integer.class);

            postJson("/api/claims", bearer(DANIEL), motorClaim(AISHA_MOTOR, "100.00")).andExpect(status().isNotFound());

            assertThat(jdbc.queryForObject("select count(*) from claim", Integer.class)).isEqualTo(before);
        }

        @Test
        void incidentOutsideThePolicyPeriodIsRefused() throws Exception {
            Map<String, Object> claim = motorClaim(AISHA_MOTOR, "100.00");
            claim.put("incidentDate", "2025-12-31");

            postJson("/api/claims", bearer(AISHA), claim)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("incidentDate"));
        }

        @Test
        void invalidFieldsAreAllListed() throws Exception {
            Map<String, Object> claim = motorClaim(AISHA_MOTOR, "0");
            claim.put("incidentDate", LocalDate.now().plusDays(1).toString());
            claim.put("incidentDescription", " ");

            postJson("/api/claims", bearer(AISHA), claim)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='claimedAmount')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field=='incidentDate')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field=='incidentDescription')]").exists());
        }

        @Test
        void detailsForThePolicysLineAreRequired() throws Exception {
            // a property-shaped claim against a motor policy
            postJson("/api/claims", bearer(AISHA), propertyClaim(AISHA_MOTOR, "100.00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("motor"));
        }
    }

    @Nested
    class Tracking {

        @Test
        void claimantSeesOnlyTheirOwnClaimsNewestFirst() throws Exception {
            String first = submitMotorClaim("100.00");
            String second = submitMotorClaim("200.00");
            String daniels = submit(DANIEL, motorClaim(DANIEL_MOTOR, "300.00"));

            List<String> ids = ids(body(getAs("/api/claims", bearer(AISHA)).andExpect(status().isOk())));

            assertThat(ids).contains(first, second).doesNotContain(daniels);
            assertThat(ids.indexOf(second)).isLessThan(ids.indexOf(first));
        }

        @Test
        void timelineListsEveryStatusChangeInOrder() throws Exception {
            String id = submitMotorClaim("100.00");
            pickUp(OFFICER_1, id);

            getAs("/api/claims/" + id, bearer(AISHA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.history.length()").value(2))
                    .andExpect(jsonPath("$.history[0].toStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$.history[1].fromStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$.history[1].toStatus").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.history[1].actorName").value("Tan Wei Ming"))
                    .andExpect(jsonPath("$.history[1].changedAt").isNotEmpty());
        }

        @Test
        void anotherClaimantsClaimLooksLikeItDoesNotExist() throws Exception {
            String id = submitMotorClaim("100.00");

            getAs("/api/claims/" + id, bearer(DANIEL)).andExpect(status().isNotFound());
        }

        @Test
        void reserveAndAssessmentAreInternalAndHiddenFromTheClaimant() throws Exception {
            String id = assessedClaim("8500.00", "7200.00");

            getAs("/api/claims/" + id, bearer(AISHA))
                    .andExpect(jsonPath("$.currentReserve").doesNotExist())
                    .andExpect(jsonPath("$.assessment").doesNotExist());
            getAs("/api/claims/" + id, bearer(OFFICER_1))
                    .andExpect(jsonPath("$.currentReserve").value(7200.00))
                    .andExpect(jsonPath("$.assessment.recommendedAmount").value(7200.00));
        }
    }

    @Nested
    class PickUp {

        @Test
        void queueShowsOnlyUnassignedClaimsFromTheOfficersMarketOldestFirst() throws Exception {
            String mine = submitMotorClaim("100.00");
            String later = submitMotorClaim("150.00");
            String taken = submitMotorClaim("200.00");
            pickUp(OFFICER_2, taken);
            String singapore = submit(SITI_SG, motorClaim(SITI_SG_MOTOR, "300.00"));

            JsonNode queue = body(getAs("/api/staff/claims/unassigned", bearer(OFFICER_1)).andExpect(status().isOk()));
            List<String> ids = ids(queue);

            assertThat(ids).contains(mine, later).doesNotContain(taken, singapore);
            assertThat(ids.indexOf(mine)).isLessThan(ids.indexOf(later));
            queue.forEach(claim -> {
                assertThat(claim.get("status").asString()).isEqualTo("SUBMITTED");
                assertThat(claim.get("marketCode").asString()).isEqualTo("MY");
            });
        }

        @Test
        void pickingUpAssignsTheClaimAndPutsItUnderReview() throws Exception {
            String id = submitMotorClaim("100.00");

            pickUp(OFFICER_1, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.assignedOfficerName").value("Tan Wei Ming"));
        }

        @Test
        void whenTwoOfficersPickUpTheSameClaimExactlyOneWins() throws Exception {
            String id = submitMotorClaim("100.00");
            String first = bearer(OFFICER_1);
            String second = bearer(OFFICER_2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> attempts = new ArrayList<>();
                for (String auth : List.of(first, second)) {
                    Callable<Integer> attempt = () -> {
                        go.await();
                        return postJson("/api/staff/claims/" + id + "/assign", auth, Map.of())
                                .andReturn().getResponse().getStatus();
                    };
                    attempts.add(pool.submit(attempt));
                }
                go.countDown();

                assertThat(List.of(attempts.get(0).get(), attempts.get(1).get())).containsExactlyInAnyOrder(200, 409);
            } finally {
                pool.shutdownNow();
            }
            assertThat(count("claim_status_history", id)).isEqualTo(2);
        }

        @Test
        void aClaimFromAnotherMarketCannotBePickedUp() throws Exception {
            String id = submitMotorClaim("100.00");

            pickUp(OFFICER_SG, id).andExpect(status().isNotFound());
        }

        @Test
        void myClaimsListsOnlyClaimsAssignedToMe() throws Exception {
            String mine = submitMotorClaim("100.00");
            String theirs = submitMotorClaim("100.00");
            pickUp(OFFICER_1, mine);
            pickUp(OFFICER_2, theirs);

            assertThat(ids(body(getAs("/api/staff/claims/mine", bearer(OFFICER_1)))))
                    .contains(mine).doesNotContain(theirs);
        }
    }

    @Nested
    class Assessment {

        @Test
        void assessingMovesTheClaimOnAndRevisesTheReserve() throws Exception {
            String id = submitMotorClaim("8500.00");
            pickUp(OFFICER_1, id);

            assess(OFFICER_1, id, "7200.00")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ASSESSED"))
                    .andExpect(jsonPath("$.currentReserve").value(7200.00));

            assertThat(jdbc.queryForList(
                    "select reason from reserve where claim_id = ?::uuid order by created_at", String.class, id))
                    .containsExactly("INITIAL", "ASSESSMENT");
        }

        @Test
        void onlyTheAssignedOfficerCanAssess() throws Exception {
            String id = submitMotorClaim("100.00");
            pickUp(OFFICER_1, id);

            assess(OFFICER_2, id, "90.00").andExpect(status().isForbidden());
        }

        @Test
        void aClaimIsAssessedOnlyOnce() throws Exception {
            String id = assessedClaim("100.00", "90.00");

            assess(OFFICER_1, id, "80.00").andExpect(status().isConflict());
        }

        @Test
        void findingsAndAmountAreRequired() throws Exception {
            String id = submitMotorClaim("100.00");
            pickUp(OFFICER_1, id);

            postJson("/api/staff/claims/" + id + "/assessment", bearer(OFFICER_1), Map.of())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='findings')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field=='recommendedAmount')]").exists());
        }
    }

    @Nested
    class Decision {

        @Test
        void approvalRecordsAmountOfficerAndTime() throws Exception {
            String id = assessedClaim("8500.00", "7200.00");

            approve(OFFICER_1, id, "7200.00")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.decision.outcome").value("APPROVED"))
                    .andExpect(jsonPath("$.decision.approvedAmount").value(7200.00))
                    .andExpect(jsonPath("$.decision.decidedByName").value("Tan Wei Ming"))
                    .andExpect(jsonPath("$.decision.decidedAt").isNotEmpty());
        }

        @Test
        void approvalAboveTheSumInsuredIsRefused() throws Exception {
            String id = assessedClaim("8500.00", "7200.00");

            approve(OFFICER_1, id, "80000.01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("approvedAmount"));
        }

        @Test
        void rejectionReleasesTheReserveAndShowsTheClaimantTheReason() throws Exception {
            String id = assessedClaim("8500.00", "7200.00");

            reject(OFFICER_1, id, "Damage predates the policy")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.currentReserve").value(0));

            getAs("/api/claims/" + id, bearer(AISHA))
                    .andExpect(jsonPath("$.decision.outcome").value("REJECTED"))
                    .andExpect(jsonPath("$.decision.reason").value("Damage predates the policy"));
        }

        @Test
        void rejectionNeedsAReason() throws Exception {
            String id = assessedClaim("100.00", "90.00");

            postJson("/api/staff/claims/" + id + "/decision", bearer(OFFICER_1), Map.of("outcome", "REJECTED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }

        @Test
        void decidingBeforeAssessingIsAConflictAndChangesNothing() throws Exception {
            String id = submitMotorClaim("100.00");
            pickUp(OFFICER_1, id);

            approve(OFFICER_1, id, "90.00").andExpect(status().isConflict());

            getAs("/api/claims/" + id, bearer(OFFICER_1)).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        }

        @Test
        void aClosedClaimAcceptsNoFurtherAction() throws Exception {
            String id = assessedClaim("100.00", "90.00");
            reject(OFFICER_1, id, "Not covered");

            approve(OFFICER_1, id, "90.00").andExpect(status().isConflict());
            assess(OFFICER_1, id, "90.00").andExpect(status().isConflict());
            pickUp(OFFICER_2, id).andExpect(status().isConflict());
        }
    }

    @Nested
    class AuditAndEvents {

        @Test
        void everyTransitionWritesOneHistoryRowAndOneOutboxRow() throws Exception {
            String id = assessedClaim("8500.00", "7200.00");
            approve(OFFICER_1, id, "7200.00");

            assertThat(count("claim_status_history", id)).isEqualTo(4);
            assertThat(jdbc.queryForList(
                    "select event_type from outbox_event where aggregate_id = ?::uuid order by created_at", String.class, id))
                    .containsExactly("CLAIM_SUBMITTED", "CLAIM_ASSIGNED", "CLAIM_ASSESSED", "CLAIM_APPROVED");
        }

        @Test
        void aRefusedActionWritesNeither() throws Exception {
            String id = submitMotorClaim("100.00");

            approve(OFFICER_1, id, "90.00"); // not picked up, so not assigned
            assess(OFFICER_SG, id, "90.00"); // wrong market

            assertThat(count("claim_status_history", id)).isEqualTo(1);
            assertThat(count("outbox_event", id)).isEqualTo(1);
        }

        @Test
        void outboxPayloadIsACompleteSnapshotOfTheClaim() throws Exception {
            String id = assessedClaim("8500.00", "7200.00");

            JsonNode event = json.readTree(jdbc.queryForObject(
                    "select payload::text from outbox_event where aggregate_id = ?::uuid and event_type = 'CLAIM_ASSESSED'",
                    String.class, id));

            assertThat(event.get("type").asString()).isEqualTo("CLAIM_ASSESSED");
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(Instant.parse(event.get("occurredAt").asString())).isBeforeOrEqualTo(Instant.now());
            assertThat(event.get("claimVersion").asLong()).isEqualTo(2);
            JsonNode claim = event.get("claim");
            assertThat(claim.get("claimId").asString()).isEqualTo(id);
            assertThat(claim.get("status").asString()).isEqualTo("ASSESSED");
            assertThat(claim.get("marketCode").asString()).isEqualTo("MY");
            assertThat(claim.get("lineOfBusiness").asString()).isEqualTo("MOTOR");
            assertThat(claim.get("officerName").asString()).isEqualTo("Tan Wei Ming");
            assertThat(claim.get("teamId").asString()).isEqualTo("10000000-0000-0000-0000-000000000001");
            assertThat(claim.get("currentReserve").decimalValue()).isEqualByComparingTo("7200.00");
            assertThat(claim.get("currency").asString()).isEqualTo("MYR");
        }
    }

    @Test
    void aManagerCanDoEverythingAnOfficerCan() throws Exception {
        String id = submitMotorClaim("8500.00");

        pickUp(MANAGER, id).andExpect(status().isOk());
        assess(MANAGER, id, "7200.00").andExpect(status().isOk());
        approve(MANAGER, id, "7200.00").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private static List<String> ids(JsonNode claims) {
        List<String> ids = new ArrayList<>();
        claims.forEach(claim -> ids.add(claim.get("id").asString()));
        return ids;
    }
}
