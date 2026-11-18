package com.claims.service;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the real application against a real PostgreSQL. One container is shared by every test class. */
@SpringBootTest
public abstract class IntegrationTest {

    public static final String PASSWORD = "Passw0rd!";
    public static final String AISHA = "aisha@claims.test";
    public static final String DANIEL = "daniel@claims.test";
    public static final String SITI_SG = "siti@claims.test";
    public static final String OFFICER_1 = "officer1.my@claims.test";
    public static final String OFFICER_2 = "officer2.my@claims.test";
    public static final String OFFICER_SG = "officer.sg@claims.test";
    public static final String MANAGER = "manager.my@claims.test";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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

    protected org.springframework.test.web.servlet.ResultActions login(String email, String password, String door) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password, "door", door))));
    }

    /** "Bearer ..." header value for a seeded user, signing in through the door that matches the account. */
    protected String bearer(String email) throws Exception {
        String door = email.startsWith("officer") || email.startsWith("manager") ? "STAFF" : "CLAIMANT";
        String body = login(email, PASSWORD, door).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asString();
    }

    protected JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
