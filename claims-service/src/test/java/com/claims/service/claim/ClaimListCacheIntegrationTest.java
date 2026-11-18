package com.claims.service.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ClaimListCacheIntegrationTest extends IntegrationTest {

    private static final String MY_CLAIMS = "/api/claims";
    private static final String QUEUE = "/api/staff/claims/unassigned";
    private static final String ASSIGNED = "/api/staff/claims/mine";

    /** The row for one claim in a list, or null when it is not there. */
    private JsonNode row(String url, String email, String claimId) throws Exception {
        JsonNode list = body(getAs(url, bearer(email)).andExpect(status().isOk()));
        return StreamSupport.stream(list.spliterator(), false)
                .filter(item -> item.get("id").asString().equals(claimId))
                .findFirst()
                .orElse(null);
    }

    @Test
    void theListsAndTheQueueComeFromTheCacheUntilAClaimChanges() throws Exception {
        String claim = submit(AISHA, motorClaim(freshPolicy(AISHA_MOTOR), "100.00"));
        JsonNode first = row(MY_CLAIMS, AISHA, claim);
        assertThat(first.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(row(QUEUE, OFFICER_1, claim)).isNotNull();
        assertThat(row(ASSIGNED, OFFICER_1, claim)).isNull();

        // Changed behind the application's back: the lists are still answered from Redis, unchanged.
        jdbc.update("UPDATE claim SET claimed_amount = 999.00 WHERE id = ?::uuid", claim);
        assertThat(row(MY_CLAIMS, AISHA, claim)).isEqualTo(first);
        assertThat(row(QUEUE, OFFICER_1, claim).get("claimedAmount").decimalValue()).isEqualByComparingTo("100.00");

        // A change through the application clears the claimant's list, the officer's list and the market's queue.
        pickUp(OFFICER_1, claim).andExpect(status().isOk());

        JsonNode after = row(MY_CLAIMS, AISHA, claim);
        assertThat(after.get("status").asString()).isEqualTo("UNDER_REVIEW");
        assertThat(after.get("claimedAmount").decimalValue()).isEqualByComparingTo("999.00");
        assertThat(row(QUEUE, OFFICER_1, claim)).isNull();
        assertThat(row(ASSIGNED, OFFICER_1, claim)).isNotNull();
    }
}
