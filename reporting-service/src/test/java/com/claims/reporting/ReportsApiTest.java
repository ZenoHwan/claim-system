package com.claims.reporting;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.events.ClaimEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ReportsApiTest extends IntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    ClaimSummaryProjector projector;

    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions asManager(String market, String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("MANAGER", market)));
    }

    private void apply(ClaimEvent... events) {
        for (ClaimEvent event : events) {
            projector.apply(event);
        }
    }

    // --- access ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/reports/workload", "/api/reports/performance", "/api/reports/exposure"})
    void aRequestWithoutATokenIsUnauthorised(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTamperedTokenIsUnauthorised() throws Exception {
        String token = bearer("MANAGER", "MY");
        mvc.perform(get("/api/reports/exposure").header(HttpHeaders.AUTHORIZATION, token.substring(0, token.length() - 3) + "abc"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFFICER", "CLAIMANT"})
    void onlyManagersMaySeeReports(String role) throws Exception {
        for (String path : new String[] {"/api/reports/workload", "/api/reports/performance", "/api/reports/exposure"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(role, "MY"))).andExpect(status().isForbidden());
        }
    }

    // --- workload ---

    @Test
    void workloadListsOpenClaimsPerOfficerByStatus() throws Exception {
        var a = TestClaim.motorMy(0);
        var b = TestClaim.motorMy(0);
        var c = TestClaim.motorMy(0);
        var d = TestClaim.motorMy(0);
        apply(a.submitted("100"), a.assigned(TestClaim.TAN, "Tan"));
        apply(b.submitted("100"), b.assigned(TestClaim.TAN, "Tan"), b.informationRequested());
        apply(c.submitted("100"), c.assigned(TestClaim.TAN, "Tan"));
        apply(d.submitted("100"), d.assigned(TestClaim.KUMAR, "Kumar"), d.assessed("90"));

        asManager("MY", "/api/reports/workload")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openClaims").value(4))
                .andExpect(jsonPath("$.unassigned").value(0))
                .andExpect(jsonPath("$.officers", hasSize(2)))
                .andExpect(jsonPath("$.officers[?(@.officerName == 'Tan')].open").value(3))
                .andExpect(jsonPath("$.officers[?(@.officerName == 'Tan')].byStatus.UNDER_REVIEW").value(2))
                .andExpect(jsonPath("$.officers[?(@.officerName == 'Tan')].byStatus.INFO_REQUESTED").value(1))
                .andExpect(jsonPath("$.officers[?(@.officerName == 'Kumar')].byStatus.ASSESSED").value(1));
    }

    @Test
    void workloadCountsClaimsNobodyHasPickedUp() throws Exception {
        apply(TestClaim.motorMy(0).submitted("100"), TestClaim.motorMy(0).submitted("100"), TestClaim.motorMy(0).submitted("100"));

        asManager("MY", "/api/reports/workload")
                .andExpect(jsonPath("$.unassigned").value(3))
                .andExpect(jsonPath("$.officers", hasSize(0)));
    }

    @Test
    void workloadGroupsOpenClaimsByAgeSinceSubmission() throws Exception {
        apply(TestClaim.motorMy(0).submitted("1"), TestClaim.motorMy(2).submitted("1"));
        apply(TestClaim.motorMy(3).submitted("1"), TestClaim.motorMy(7).submitted("1"), TestClaim.motorMy(5).submitted("1"));
        apply(TestClaim.motorMy(8).submitted("1"));

        asManager("MY", "/api/reports/workload")
                .andExpect(jsonPath("$.ageing.days0to2").value(2))
                .andExpect(jsonPath("$.ageing.days3to7").value(3))
                .andExpect(jsonPath("$.ageing.days8plus").value(1));
    }

    @Test
    void closedClaimsAndOtherMarketsAreLeftOutOfWorkload() throws Exception {
        var settled = TestClaim.motorMy(1);
        var rejected = TestClaim.motorMy(1);
        var singapore = TestClaim.of("SG", "MOTOR", "SGD");
        apply(settled.submitted("100"), settled.assigned(TestClaim.TAN, "Tan"), settled.assessed("90"), settled.approved(1), settled.settled());
        apply(rejected.submitted("100"), rejected.assigned(TestClaim.TAN, "Tan"), rejected.assessed("90"), rejected.rejected(1));
        apply(singapore.submitted("100"));

        asManager("MY", "/api/reports/workload")
                .andExpect(jsonPath("$.openClaims").value(0))
                .andExpect(jsonPath("$.unassigned").value(0))
                .andExpect(jsonPath("$.officers", hasSize(0)))
                .andExpect(jsonPath("$.ageing.days0to2").value(0));
    }

    // --- performance ---

    @Test
    void performanceAveragesDaysFromSubmissionToDecision() throws Exception {
        var four = TestClaim.motorMy(10);
        var eight = TestClaim.motorMy(10);
        apply(four.submitted("100"), four.assigned(TestClaim.TAN, "Tan"), four.assessed("90"), four.approved(4));
        apply(eight.submitted("100"), eight.assigned(TestClaim.TAN, "Tan"), eight.assessed("90"), eight.rejected(8));

        asManager("MY", "/api/reports/performance")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decidedLast30Days").value(2))
                .andExpect(jsonPath("$.averageDaysToDecision").value(6.0));
    }

    @Test
    void performanceReportsNoAverageWhenNothingWasDecided() throws Exception {
        apply(TestClaim.motorMy(1).submitted("100"));

        asManager("MY", "/api/reports/performance")
                .andExpect(jsonPath("$.decidedLast30Days").value(0))
                .andExpect(jsonPath("$.averageDaysToDecision").value(nullValue()));
    }

    @Test
    void performanceIgnoresDecisionsOlderThanThirtyDays() throws Exception {
        var old = TestClaim.motorMy(60);
        apply(old.submitted("100"), old.assigned(TestClaim.TAN, "Tan"), old.assessed("90"), old.approved(5));

        asManager("MY", "/api/reports/performance").andExpect(jsonPath("$.decidedLast30Days").value(0));
    }

    @Test
    void performanceBreaksDownWhatEachOfficerDecidedAndSettledToday() throws Exception {
        var approvedAndSettled = TestClaim.motorMy(0);
        var approved = TestClaim.motorMy(0);
        var rejected = TestClaim.motorMy(0);
        var decidedLastWeek = TestClaim.motorMy(10);
        var stillOpen = TestClaim.motorMy(0);
        apply(approvedAndSettled.submitted("100"), approvedAndSettled.assigned(TestClaim.TAN, "Tan"), approvedAndSettled.assessed("90"),
                approvedAndSettled.approved(0), approvedAndSettled.settled());
        apply(approved.submitted("100"), approved.assigned(TestClaim.TAN, "Tan"), approved.assessed("90"), approved.approved(0));
        apply(rejected.submitted("100"), rejected.assigned(TestClaim.KUMAR, "Kumar"), rejected.assessed("90"), rejected.rejected(0));
        apply(decidedLastWeek.submitted("100"), decidedLastWeek.assigned(TestClaim.KUMAR, "Kumar"), decidedLastWeek.assessed("90"),
                decidedLastWeek.approved(3));
        apply(stillOpen.submitted("100"), stillOpen.assigned(TestClaim.KUMAR, "Kumar"));

        asManager("MY", "/api/reports/performance")
                .andExpect(jsonPath("$.today", hasSize(2)))
                .andExpect(jsonPath("$.today[?(@.officerName == 'Tan')].approved").value(2))
                .andExpect(jsonPath("$.today[?(@.officerName == 'Tan')].settled").value(1))
                .andExpect(jsonPath("$.today[?(@.officerName == 'Tan')].rejected").value(0))
                .andExpect(jsonPath("$.today[?(@.officerName == 'Kumar')].rejected").value(1))
                .andExpect(jsonPath("$.today[?(@.officerName == 'Kumar')].approved").value(0));
    }

    // --- exposure ---

    @Test
    void exposureFollowsTheReserveThroughTheLifecycle() throws Exception {
        var claim = TestClaim.motorMy(1);
        String myMotor = "$.rows[?(@.marketCode == 'MY' && @.lineOfBusiness == 'MOTOR')].outstandingReserve";

        apply(claim.submitted("8500.00"));
        asManager("MY", "/api/reports/exposure").andExpect(jsonPath(myMotor).value(8500.00));

        apply(claim.assigned(TestClaim.TAN, "Tan"), claim.assessed("7200.00"));
        asManager("MY", "/api/reports/exposure").andExpect(jsonPath(myMotor).value(7200.00));

        apply(claim.approved(1), claim.settled());
        asManager("MY", "/api/reports/exposure").andExpect(jsonPath("$.rows", hasSize(0)));
    }

    @Test
    void exposureIsGroupedByMarketAndLineAndNeverAddsCurrenciesTogether() throws Exception {
        apply(TestClaim.of("MY", "MOTOR", "MYR").submitted("1000"), TestClaim.of("MY", "MOTOR", "MYR").submitted("500"));
        apply(TestClaim.of("MY", "PROPERTY", "MYR").submitted("40000"));
        apply(TestClaim.of("SG", "MOTOR", "SGD").submitted("300"));

        asManager("MY", "/api/reports/exposure")
                .andExpect(jsonPath("$.rows", hasSize(3)))
                .andExpect(jsonPath("$.rows[?(@.marketCode == 'MY' && @.lineOfBusiness == 'MOTOR')].outstandingReserve").value(1500.00))
                .andExpect(jsonPath("$.rows[?(@.marketCode == 'MY' && @.lineOfBusiness == 'MOTOR')].currency").value("MYR"))
                .andExpect(jsonPath("$.rows[?(@.marketCode == 'MY' && @.lineOfBusiness == 'MOTOR')].openClaims").value(2))
                .andExpect(jsonPath("$.rows[?(@.marketCode == 'MY' && @.lineOfBusiness == 'PROPERTY')].outstandingReserve").value(40000.00))
                .andExpect(jsonPath("$.rows[?(@.marketCode == 'SG' && @.lineOfBusiness == 'MOTOR')].currency").value("SGD"))
                .andExpect(jsonPath("$.rows[?(@.marketCode == 'SG' && @.lineOfBusiness == 'MOTOR')].outstandingReserve").value(300.00));
    }

    @Test
    void aReplayedEventDoesNotChangeTheFigures() throws Exception {
        var claim = TestClaim.motorMy(1);
        ClaimEvent submitted = claim.submitted("8500.00");
        ClaimEvent assigned = claim.assigned(TestClaim.TAN, "Tan");
        ClaimEvent assessed = claim.assessed("7200.00");
        apply(submitted, assigned, assessed);

        apply(submitted, assigned, assessed, assessed); // stream replayed from the start, plus a duplicate

        asManager("MY", "/api/reports/exposure")
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].outstandingReserve").value(7200.00));
        asManager("MY", "/api/reports/workload")
                .andExpect(jsonPath("$.openClaims").value(1))
                .andExpect(jsonPath("$.officers[0].byStatus.ASSESSED").value(1));
    }

    // --- as-of and contract ---

    @Test
    void everyViewStatesTheTimeOfTheLastEventApplied() throws Exception {
        ClaimEvent last = TestClaim.motorMy(0).submitted("100");
        apply(last);

        for (String path : new String[] {"/api/reports/workload", "/api/reports/performance", "/api/reports/exposure"}) {
            asManager("MY", path).andExpect(jsonPath("$.asOf").value(org.hamcrest.Matchers.startsWith(last.occurredAt().toString().substring(0, 19))));
        }
    }

    @Test
    void asOfIsAbsentBeforeAnyEventHasBeenApplied() throws Exception {
        asManager("MY", "/api/reports/exposure").andExpect(jsonPath("$.asOf").value(nullValue()));
    }

    @Test
    void theOpenApiDocumentListsTheThreeViews() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/reports/workload'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/performance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/exposure'].get").exists());
    }

    // --- cache ---

    @Test
    void aReportComesFromTheCacheUntilAnEventForItsMarketIsApplied() throws Exception {
        var a = TestClaim.motorMy(0);
        apply(a.submitted("100"));
        asManager("MY", "/api/reports/workload").andExpect(jsonPath("$.openClaims").value(1));
        asManager("MY", "/api/reports/exposure").andExpect(jsonPath("$.rows", hasSize(1)));

        // Changed behind the projector's back: still answered from Redis.
        jdbc.sql("UPDATE claim_summary SET status = 'SETTLED'").update();
        asManager("MY", "/api/reports/workload").andExpect(jsonPath("$.openClaims").value(1));

        // A Singapore event leaves the Malaysian workload cached, but exposure covers every market.
        apply(TestClaim.of("SG", "MOTOR", "SGD").submitted("100"));
        asManager("MY", "/api/reports/workload").andExpect(jsonPath("$.openClaims").value(1));
        asManager("MY", "/api/reports/exposure")
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].marketCode").value("SG"));

        // A Malaysian event clears the Malaysian reports.
        apply(TestClaim.motorMy(0).submitted("100"), TestClaim.motorMy(0).submitted("100"));
        asManager("MY", "/api/reports/workload").andExpect(jsonPath("$.openClaims").value(2));
    }

    @Test
    void reportsAreStillAnsweredWhenRedisIsDown() throws Exception {
        apply(TestClaim.motorMy(0).submitted("100"));
        whileRedisIsDown(() -> {
            asManager("MY", "/api/reports/workload").andExpect(status().isOk()).andExpect(jsonPath("$.openClaims").value(1));
            apply(TestClaim.motorMy(0).submitted("100"));
        });
        asManager("MY", "/api/reports/workload").andExpect(jsonPath("$.openClaims").value(2));
    }
}
