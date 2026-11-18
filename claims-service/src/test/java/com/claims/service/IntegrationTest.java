package com.claims.service;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the real application against a real PostgreSQL. One container is shared by every test class. */
@SpringBootTest
public abstract class IntegrationTest {

    public static final String PASSWORD = "password";
    public static final String AISHA = "aisha@claims.test";
    public static final String DANIEL = "daniel@claims.test";
    public static final String SITI_SG = "siti@claims.test";
    public static final String OFFICER_1 = "officer1.my@claims.test";
    public static final String OFFICER_2 = "officer2.my@claims.test";
    public static final String OFFICER_SG = "officer.sg@claims.test";
    public static final String MANAGER = "manager.my@claims.test";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    protected static final Path DOCUMENTS;

    static {
        POSTGRES.start();
        try {
            DOCUMENTS = Files.createTempDirectory("claim-documents");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("claims.documents.dir", () -> DOCUMENTS.toString());
        // tests drive the outbox relay themselves instead of waiting on the scheduler
        registry.add("claims.outbox.relay.enabled", () -> "false");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbc;

    protected final JsonMapper json = JsonMapper.builder().build();
    protected MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected ResultActions login(String email, String password, String door) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password, "door", door))));
    }

    /** "Bearer ..." header value for a seeded user, signing in through the door that matches the account. */
    protected String bearer(String email) throws Exception {
        String door = email.startsWith("officer") || email.startsWith("manager") ? "STAFF" : "CLAIMANT";
        String body = login(email, PASSWORD, door).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asString();
    }

    public static final String AISHA_MOTOR = "40000000-0000-0000-0000-000000000001";
    public static final String AISHA_PROPERTY = "40000000-0000-0000-0000-000000000002";
    public static final String DANIEL_MOTOR = "40000000-0000-0000-0000-000000000003";
    public static final String SITI_SG_MOTOR = "40000000-0000-0000-0000-000000000004";

    protected ResultActions postJson(String url, String auth, Object requestBody) throws Exception {
        return mvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(requestBody)));
    }

    protected ResultActions getAs(String url, String auth) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth));
    }

    protected Map<String, Object> motorClaim(String policyId, String amount) {
        Map<String, Object> claim = new HashMap<>();
        claim.put("policyId", policyId);
        claim.put("incidentDate", LocalDate.now().minusDays(3).toString());
        claim.put("incidentDescription", "Rear-ended at a traffic light");
        claim.put("claimedAmount", amount);
        claim.put("motor", Map.of(
                "vehicleRegistration", "WXY 1234",
                "driverName", "Aisha Rahman",
                "accidentLocation", Map.of(
                        "addressLine1", "Jalan Ampang",
                        "addressLine2", "Kuala Lumpur",
                        "postalCode", "50450"),
                "thirdPartyInvolved", true));
        return claim;
    }

    protected Map<String, Object> singaporeMotorClaim(String policyId, String postalCode) {
        Map<String, Object> claim = motorClaim(policyId, "300.00");
        claim.put("motor", Map.of(
                "vehicleRegistration", "SBA 1234 A",
                "driverName", "Siti Nur",
                "accidentLocation", Map.of("addressLine1", "1 Orchard Road", "postalCode", postalCode),
                "thirdPartyInvolved", false));
        return claim;
    }

    protected Map<String, Object> propertyClaim(String policyId, String amount) {
        Map<String, Object> claim = new HashMap<>();
        claim.put("policyId", policyId);
        claim.put("incidentDate", LocalDate.now().minusDays(3).toString());
        claim.put("incidentDescription", "Burst pipe flooded the kitchen");
        claim.put("claimedAmount", amount);
        claim.put("property", Map.of(
                "propertyAddress", Map.of(
                        "addressLine1", "12 Jalan Bukit",
                        "addressLine2", "Taman Bukit Indah",
                        "addressLine3", "Petaling Jaya",
                        "postalCode", "46000"),
                "lossCause", "WATER"));
        return claim;
    }

    /**
     * A policy may have only one open claim, so a test that submits a claim takes its own copy of a
     * seeded policy (same holder, line, market, currency and sum insured).
     */
    protected String freshPolicy(String seededPolicyId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into policy (id, policy_number, holder_id, line_of_business, market_code, currency,
                    sum_insured, effective_from, effective_to)
                select ?::uuid, ?, holder_id, line_of_business, market_code, currency, sum_insured, effective_from, effective_to
                from policy where id = ?::uuid
                """, id, "TEST-" + id.substring(0, 13), seededPolicyId);
        return id;
    }

    /** Submits a claim and returns its id. */
    protected String submit(String claimantEmail, Map<String, Object> claim) throws Exception {
        return body(postJson("/api/claims", bearer(claimantEmail), claim).andExpect(status().isCreated()))
                .get("id").asString();
    }

    protected String submitMotorClaim(String amount) throws Exception {
        return submit(AISHA, motorClaim(freshPolicy(AISHA_MOTOR), amount));
    }

    protected ResultActions pickUp(String staffEmail, String claimId) throws Exception {
        return postJson("/api/staff/claims/" + claimId + "/assign", bearer(staffEmail), Map.of());
    }

    protected ResultActions assess(String staffEmail, String claimId, String recommendedAmount) throws Exception {
        return postJson("/api/staff/claims/" + claimId + "/assessment", bearer(staffEmail),
                Map.of("findings", "Damage consistent with the report", "recommendedAmount", recommendedAmount));
    }

    protected ResultActions approve(String staffEmail, String claimId, String amount) throws Exception {
        return postJson("/api/staff/claims/" + claimId + "/decision", bearer(staffEmail),
                Map.of("outcome", "APPROVED", "approvedAmount", amount));
    }

    protected ResultActions reject(String staffEmail, String claimId, String reason) throws Exception {
        return postJson("/api/staff/claims/" + claimId + "/decision", bearer(staffEmail),
                Map.of("outcome", "REJECTED", "reason", reason));
    }

    /** A claim picked up and assessed by OFFICER_1, ready for a decision. */
    protected String assessedClaim(String claimed, String recommended) throws Exception {
        String id = submitMotorClaim(claimed);
        pickUp(OFFICER_1, id).andExpect(status().isOk());
        assess(OFFICER_1, id, recommended).andExpect(status().isOk());
        return id;
    }

    protected ResultActions requestInformation(String staffEmail, String claimId, String question) throws Exception {
        return postJson("/api/staff/claims/" + claimId + "/information-requests", bearer(staffEmail), Map.of("question", question));
    }

    /** A claim OFFICER_1 has picked up and asked a question about. Returns {claimId, requestId}. */
    protected String[] claimAwaitingInformation() throws Exception {
        String id = submitMotorClaim("8500.00");
        pickUp(OFFICER_1, id).andExpect(status().isOk());
        JsonNode claim = body(requestInformation(OFFICER_1, id, "Please upload the police report").andExpect(status().isOk()));
        return new String[] {id, claim.get("informationRequests").get(0).get("id").asString()};
    }

    protected ResultActions respond(String claimantEmail, String claimId, String requestId, String text) throws Exception {
        return postJson("/api/claims/" + claimId + "/information-requests/" + requestId + "/response",
                bearer(claimantEmail), Map.of("responseText", text));
    }

    protected ResultActions recordPayment(String staffEmail, String claimId, String reference) throws Exception {
        return postJson("/api/staff/claims/" + claimId + "/payment", bearer(staffEmail),
                Map.of("method", "BANK_TRANSFER", "reference", reference));
    }

    /** A claim approved by OFFICER_1 for the given amount, with its payment still to be recorded. */
    protected String approvedClaim(String amount) throws Exception {
        String id = assessedClaim("8500.00", amount);
        approve(OFFICER_1, id, amount).andExpect(status().isOk());
        return id;
    }

    protected int count(String table, String claimId) {
        String column = table.equals("outbox_event") ? "aggregate_id" : "claim_id";
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?::uuid", Integer.class, claimId);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
