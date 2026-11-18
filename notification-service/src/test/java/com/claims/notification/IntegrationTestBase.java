package com.claims.notification;

import static org.awaitility.Awaitility.await;

import com.claims.events.ClaimEvent;
import com.claims.events.ClaimEventType;
import com.claims.events.ClaimSnapshot;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the real service against real PostgreSQL and Kafka, and talks to it only over HTTP and Kafka. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "claims.kafka.retry-interval=20ms")
@Import(IntegrationTestBase.Containers.class)
abstract class IntegrationTestBase {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17");
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.0.0");
        }
    }

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Value("${claims.jwt.secret}")
    String jwtSecret;

    @Autowired
    KafkaTemplate<String, String> kafka;

    // ---- tokens -------------------------------------------------------------------------------

    String tokenFor(UUID userId, String role) {
        return sign(userId, role, jwtSecret);
    }

    static String sign(UUID userId, String role, String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("market", "MY")
                .claim("name", "Test User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    // ---- http ---------------------------------------------------------------------------------

    HttpRequest.Builder request(String path, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    HttpResponse<String> get(String path, String token) throws Exception {
        return HTTP.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String token) throws Exception {
        return HTTP.send(request(path, token).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode inboxOf(UUID userId) throws Exception {
        return JSON.readTree(get("/api/notifications", tokenFor(userId, "CLAIMANT")).body());
    }

    /** Waits until the user's inbox holds exactly {@code count} notifications and returns it. */
    JsonNode awaitInbox(UUID userId, int count) {
        JsonNode[] inbox = new JsonNode[1];
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            inbox[0] = inboxOf(userId);
            org.assertj.core.api.Assertions.assertThat(inbox[0].get("items").size()).isEqualTo(count);
        });
        return inbox[0];
    }

    // ---- events -------------------------------------------------------------------------------

    static ClaimSnapshot claim(UUID claimantId, UUID officerId) {
        return new ClaimSnapshot(
                UUID.randomUUID(), "CLM-2026-" + String.format("%06d", (int) (Math.random() * 999_999)), "MY", "MOTOR",
                "UNDER_REVIEW", claimantId, officerId, "Tan Wei Ming", UUID.randomUUID(),
                new BigDecimal("8500.00"), "MYR", Instant.now(), null, null);
    }

    static ClaimEvent event(ClaimEventType type, ClaimSnapshot claim) {
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), 1, claim);
    }

    void publish(ClaimEvent event) {
        kafka.send(ClaimEvent.TOPIC, event.claim().claimId().toString(), JSON.writeValueAsString(event));
    }
}
