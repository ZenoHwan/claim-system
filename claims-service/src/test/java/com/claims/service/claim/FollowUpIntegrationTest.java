package com.claims.service.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class FollowUpIntegrationTest extends IntegrationTest {

    /** The follow-up for one claim in a follow-up list, or null when it is not there. */
    private JsonNode followUpFor(String url, String email, String claimId) throws Exception {
        JsonNode list = body(getAs(url, bearer(email)).andExpect(status().isOk()));
        return StreamSupport.stream(list.spliterator(), false)
                .filter(item -> item.get("claimId").asString().equals(claimId))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aClaimantSeesTheQuestionsWaitingForTheirReply() throws Exception {
        String[] waiting = claimAwaitingInformation();

        JsonNode followUp = followUpFor("/api/claims/follow-ups", AISHA, waiting[0]);

        assertThat(followUp).isNotNull();
        assertThat(followUp.get("requestId").asString()).isEqualTo(waiting[1]);
        assertThat(followUp.get("question").asString()).isEqualTo("Please upload the police report");
        assertThat(followUp.get("status").asString()).isEqualTo("OPEN");
        assertThat(followUp.get("requestedByName").asString()).isNotBlank();
        assertThat(followUpFor("/api/claims/follow-ups", DANIEL, waiting[0])).isNull();
    }

    @Test
    void anOfficerFollowsAQuestionFromSentToReplyUntilTheyActOnIt() throws Exception {
        String[] waiting = claimAwaitingInformation();
        String staff = "/api/staff/claims/follow-ups";

        assertThat(followUpFor(staff, OFFICER_1, waiting[0]).get("status").asString()).isEqualTo("OPEN");
        assertThat(followUpFor(staff, OFFICER_2, waiting[0])).isNull();

        respond(AISHA, waiting[0], waiting[1], "Police report attached").andExpect(status().isOk());

        JsonNode reply = followUpFor(staff, OFFICER_1, waiting[0]);
        assertThat(reply.get("status").asString()).isEqualTo("ANSWERED");
        assertThat(reply.get("responseText").asString()).isEqualTo("Police report attached");
        assertThat(followUpFor("/api/claims/follow-ups", AISHA, waiting[0])).isNull();

        assess(OFFICER_1, waiting[0], "8000.00").andExpect(status().isOk());

        assertThat(followUpFor(staff, OFFICER_1, waiting[0])).isNull();
    }

    @Test
    void aClaimantCannotReadTheStaffFollowUps() throws Exception {
        getAs("/api/staff/claims/follow-ups", bearer(AISHA)).andExpect(status().isForbidden());
    }

    @Test
    void aPolicyShowsItsDetailsAndEveryClaimMadeOnIt() throws Exception {
        String policy = freshPolicy(AISHA_MOTOR);
        String claim = submit(AISHA, motorClaim(policy, "100.00"));

        getAs("/api/policies/" + policy, bearer(AISHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(policy))
                .andExpect(jsonPath("$.lineOfBusiness").value("MOTOR"))
                .andExpect(jsonPath("$.sumInsured").exists())
                .andExpect(jsonPath("$.claims.length()").value(1))
                .andExpect(jsonPath("$.claims[0].id").value(claim))
                .andExpect(jsonPath("$.claims[0].status").value("SUBMITTED"));
    }

    @Test
    void someoneElsesPolicyIsNotFound() throws Exception {
        getAs("/api/policies/" + AISHA_MOTOR, bearer(DANIEL)).andExpect(status().isNotFound());
    }

    // --- cache ---

    @Test
    void followUpsComeFromTheCacheUntilTheClaimChanges() throws Exception {
        String[] waiting = claimAwaitingInformation();
        String staff = "/api/staff/claims/follow-ups";
        JsonNode first = followUpFor("/api/claims/follow-ups", AISHA, waiting[0]);
        followUpFor(staff, OFFICER_1, waiting[0]);

        // Changed behind the application's back: both lists are still answered from Redis, unchanged.
        jdbc.update("UPDATE information_request SET question = 'Changed in the database' WHERE id = ?::uuid", waiting[1]);
        assertThat(followUpFor("/api/claims/follow-ups", AISHA, waiting[0])).isEqualTo(first);
        assertThat(followUpFor(staff, OFFICER_1, waiting[0]).get("question").asString()).isEqualTo("Please upload the police report");

        // A change through the application clears the claimant's and the officer's entries.
        respond(AISHA, waiting[0], waiting[1], "Police report attached").andExpect(status().isOk());

        assertThat(followUpFor("/api/claims/follow-ups", AISHA, waiting[0])).isNull();
        JsonNode reply = followUpFor(staff, OFFICER_1, waiting[0]);
        assertThat(reply.get("status").asString()).isEqualTo("ANSWERED");
        assertThat(reply.get("question").asString()).isEqualTo("Changed in the database");
    }

    @Test
    void followUpsAreStillAnsweredWhenRedisIsDown() throws Exception {
        String[] waiting = claimAwaitingInformation();
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            assertThat(followUpFor("/api/claims/follow-ups", AISHA, waiting[0])).isNotNull();
            respond(AISHA, waiting[0], waiting[1], "Police report attached").andExpect(status().isOk());
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }
}
