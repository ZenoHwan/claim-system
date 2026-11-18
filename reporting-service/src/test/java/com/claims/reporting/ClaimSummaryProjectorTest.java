package com.claims.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.claims.events.ClaimEvent;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ClaimSummaryProjectorTest extends IntegrationTest {

    @Autowired
    ClaimSummaryProjector projector;

    private Map<String, Object> row(TestClaim claim) {
        return jdbc.sql("SELECT * FROM claim_summary WHERE claim_id = ?").param(claim.claimId).query().singleRow();
    }

    private long rows(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void aFullLifecycleLeavesOneRowInItsFinalState() {
        var claim = TestClaim.motorMy(5);
        projector.apply(claim.submitted("8500.00"));
        projector.apply(claim.assigned(TestClaim.TAN, "Tan Wei Ming"));
        projector.apply(claim.informationRequested());
        projector.apply(claim.assessed("7200.00"));
        projector.apply(claim.approved(4));
        projector.apply(claim.settled());

        assertThat(rows("claim_summary")).isEqualTo(1);
        assertThat(rows("processed_event")).isEqualTo(6);
        var row = row(claim);
        assertThat(row.get("status")).isEqualTo("SETTLED");
        assertThat(row.get("market_code")).isEqualTo("MY");
        assertThat(row.get("line_of_business")).isEqualTo("MOTOR");
        assertThat(row.get("officer_id")).isEqualTo(TestClaim.TAN);
        assertThat(row.get("officer_name")).isEqualTo("Tan Wei Ming");
        assertThat((BigDecimal) row.get("current_reserve")).isEqualByComparingTo("0");
        assertThat(row.get("claim_version")).isEqualTo(5L);
        assertThat(row.get("decided_at")).isNotNull();
        assertThat(row.get("settled_at")).isNotNull();
    }

    @Test
    void eachEventTypeUpdatesTheRow() {
        var claim = TestClaim.motorMy(1);
        projector.apply(claim.submitted("8500.00"));
        assertThat(row(claim).get("status")).isEqualTo("SUBMITTED");
        assertThat(row(claim).get("officer_id")).isNull();
        assertThat((BigDecimal) row(claim).get("current_reserve")).isEqualByComparingTo("8500");

        projector.apply(claim.assigned(TestClaim.KUMAR, "Kumar"));
        assertThat(row(claim).get("status")).isEqualTo("UNDER_REVIEW");
        assertThat(row(claim).get("officer_id")).isEqualTo(TestClaim.KUMAR);

        projector.apply(claim.assessed("7200.00"));
        assertThat(row(claim).get("status")).isEqualTo("ASSESSED");
        assertThat((BigDecimal) row(claim).get("current_reserve")).isEqualByComparingTo("7200");

        projector.apply(claim.rejected(2));
        assertThat(row(claim).get("status")).isEqualTo("REJECTED");
        assertThat((BigDecimal) row(claim).get("current_reserve")).isEqualByComparingTo("0");
    }

    @Test
    void aDuplicateEventChangesNothing() {
        var claim = TestClaim.motorMy(1);
        projector.apply(claim.submitted("8500.00"));
        ClaimEvent assessed = claim.assessed("7200.00");
        projector.apply(assessed);
        var before = row(claim);

        projector.apply(assessed);

        assertThat(row(claim)).isEqualTo(before);
        assertThat(rows("processed_event")).isEqualTo(2);
    }

    @Test
    void anEventOlderThanTheStoredVersionIsIgnored() {
        var claim = TestClaim.motorMy(1);
        ClaimEvent submitted = claim.submitted("8500.00");
        ClaimEvent assessed = claim.assessed("7200.00");

        projector.apply(assessed);
        projector.apply(submitted); // arrives late

        assertThat(row(claim).get("status")).isEqualTo("ASSESSED");
        assertThat((BigDecimal) row(claim).get("current_reserve")).isEqualByComparingTo("7200");
        assertThat(row(claim).get("claim_version")).isEqualTo(1L);
    }
}
