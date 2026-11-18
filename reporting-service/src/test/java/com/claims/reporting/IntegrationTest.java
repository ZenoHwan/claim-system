package com.claims.reporting;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Real PostgreSQL, Kafka and Redis, started once for the whole test run. */
@SpringBootTest
abstract class IntegrationTest {

    static final String JWT_SECRET = "test-secret-that-is-at-least-32-bytes-long";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("claims.jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    JdbcClient jdbc;

    @Autowired
    RedisConnectionFactory redis;

    @BeforeEach
    void emptyReadModel() {
        jdbc.sql("TRUNCATE claim_summary, processed_event").update();
        redis.getConnection().serverCommands().flushAll();
    }

    static String bearer(String role, String market) {
        var key = new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .claim("market", market)
                .claim("name", "Test " + role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
