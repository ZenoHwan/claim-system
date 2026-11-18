package com.claims.service.claim;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InformationRequestIntegrationTest extends IntegrationTest {

    @Test
    void requestingInformationOpensARequestAndPausesTheClaim() throws Exception {
        String id = submitMotorClaim("100.00");
        pickUp(OFFICER_1, id);

        requestInformation(OFFICER_1, id, "Please upload the police report")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INFO_REQUESTED"))
                .andExpect(jsonPath("$.informationRequests.length()").value(1))
                .andExpect(jsonPath("$.informationRequests[0].status").value("OPEN"))
                .andExpect(jsonPath("$.informationRequests[0].question").value("Please upload the police report"));
    }

    @Test
    void anEmptyQuestionIsRefused() throws Exception {
        String id = submitMotorClaim("100.00");
        pickUp(OFFICER_1, id);

        requestInformation(OFFICER_1, id, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void informationCanOnlyBeRequestedWhileUnderReview() throws Exception {
        String assessed = assessedClaim("100.00", "90.00");
        requestInformation(OFFICER_1, assessed, "One more thing").andExpect(status().isConflict());

        String waiting = claimAwaitingInformation()[0];
        requestInformation(OFFICER_1, waiting, "And another").andExpect(status().isConflict());
    }

    @Test
    void onlyTheAssignedOfficerCanRequestInformation() throws Exception {
        String id = submitMotorClaim("100.00");
        pickUp(OFFICER_1, id);

        requestInformation(OFFICER_2, id, "Not mine to ask").andExpect(status().isForbidden());
    }

    @Test
    void claimantSeesTheOpenRequestWithWhoAskedAndWhen() throws Exception {
        String id = claimAwaitingInformation()[0];

        getAs("/api/claims/" + id, bearer(AISHA))
                .andExpect(jsonPath("$.informationRequests[0].question").value("Please upload the police report"))
                .andExpect(jsonPath("$.informationRequests[0].requestedByName").value("Tan Wei Ming"))
                .andExpect(jsonPath("$.informationRequests[0].requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.informationRequests[0].status").value("OPEN"));
    }

    @Test
    void respondingAnswersTheRequestAndReturnsTheClaimToTheSameOfficer() throws Exception {
        String[] ids = claimAwaitingInformation();

        respond(AISHA, ids[0], ids[1], "Report attached")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedOfficerName").value("Tan Wei Ming"))
                .andExpect(jsonPath("$.informationRequests[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$.informationRequests[0].responseText").value("Report attached"))
                .andExpect(jsonPath("$.informationRequests[0].answeredAt").isNotEmpty());
    }

    @Test
    void respondingTwiceIsAConflict() throws Exception {
        String[] ids = claimAwaitingInformation();
        respond(AISHA, ids[0], ids[1], "Report attached");

        respond(AISHA, ids[0], ids[1], "Again").andExpect(status().isConflict());
    }

    @Test
    void anotherClaimantCannotAnswer() throws Exception {
        String[] ids = claimAwaitingInformation();

        respond(DANIEL, ids[0], ids[1], "Not my claim").andExpect(status().isNotFound());
    }

    @Test
    void anEmptyResponseIsRefused() throws Exception {
        String[] ids = claimAwaitingInformation();

        postJson("/api/claims/" + ids[0] + "/information-requests/" + ids[1] + "/response", bearer(AISHA), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("responseText"));
    }

    @Test
    void staffCanAskAgainAfterAResponseAndEarlierRequestsStayVisible() throws Exception {
        String[] ids = claimAwaitingInformation();
        respond(AISHA, ids[0], ids[1], "Report attached");

        requestInformation(OFFICER_1, ids[0], "Photos of the damage too, please")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.informationRequests.length()").value(2))
                .andExpect(jsonPath("$.informationRequests[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$.informationRequests[1].status").value("OPEN"));
    }

    @Test
    void aClaimCannotBeAssessedWhileInformationIsOutstanding() throws Exception {
        String id = claimAwaitingInformation()[0];

        assess(OFFICER_1, id, "90.00").andExpect(status().isConflict());
    }

    @Test
    void requestAndResponseEachPublishAnEvent() throws Exception {
        String[] ids = claimAwaitingInformation();
        respond(AISHA, ids[0], ids[1], "Report attached");

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList(
                        "select event_type from outbox_event where aggregate_id = ?::uuid order by created_at", String.class, ids[0]))
                .containsExactly("CLAIM_SUBMITTED", "CLAIM_ASSIGNED", "INFORMATION_REQUESTED", "INFORMATION_PROVIDED");
    }
}
