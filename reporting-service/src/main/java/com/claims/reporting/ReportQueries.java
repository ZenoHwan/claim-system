package com.claims.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Every figure is an aggregate over claim_summary, computed when asked and then cached until the next event (see CacheConfig). */
@Repository
class ReportQueries {

    /** A claim counts toward workload and exposure until it is settled or rejected. */
    private static final String OPEN = "status NOT IN ('SETTLED', 'REJECTED')";

    record Workload(Instant asOf, String market, long openClaims, long unassigned, List<OfficerWorkload> officers, Ageing ageing) {}

    record OfficerWorkload(UUID officerId, String officerName, long open, Map<String, Long> byStatus) {}

    record Ageing(long days0to2, long days3to7, long days8plus) {}

    record Performance(Instant asOf, String market, long decidedLast30Days, Double averageDaysToDecision) {}

    record Exposure(Instant asOf, List<ExposureRow> rows) {}

    record ExposureRow(String marketCode, String lineOfBusiness, String currency, long openClaims, BigDecimal outstandingReserve) {}

    private final JdbcClient jdbc;

    ReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(cacheNames = CacheConfig.WORKLOAD, key = "#market")
    public Workload workload(String market) {
        Map<String, Object> totals = jdbc.sql("""
                SELECT count(*)                                       AS open,
                       count(*) FILTER (WHERE officer_id IS NULL)     AS unassigned,
                       count(*) FILTER (WHERE age_days <= 2)          AS days_0_2,
                       count(*) FILTER (WHERE age_days BETWEEN 3 AND 7) AS days_3_7,
                       count(*) FILTER (WHERE age_days >= 8)          AS days_8_plus
                FROM (SELECT officer_id, floor(extract(epoch FROM now() - submitted_at) / 86400) AS age_days
                      FROM claim_summary WHERE market_code = :market AND %s) open_claims
                """.formatted(OPEN))
                .param("market", market)
                .query().singleRow();

        Map<UUID, OfficerWorkload> officers = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT officer_id, officer_name, status, count(*) AS claims
                FROM claim_summary
                WHERE market_code = :market AND officer_id IS NOT NULL AND %s
                GROUP BY officer_id, officer_name, status
                ORDER BY officer_name, status
                """.formatted(OPEN))
                .param("market", market)
                .query((rs, n) -> {
                    UUID id = rs.getObject("officer_id", UUID.class);
                    long claims = rs.getLong("claims");
                    officers.merge(id,
                            new OfficerWorkload(id, rs.getString("officer_name"), claims, new LinkedHashMap<>(Map.of(rs.getString("status"), claims))),
                            (soFar, next) -> {
                                soFar.byStatus().putAll(next.byStatus());
                                return new OfficerWorkload(id, soFar.officerName(), soFar.open() + next.open(), soFar.byStatus());
                            });
                    return id;
                })
                .list();

        return new Workload(asOf(), market, (long) totals.get("open"), (long) totals.get("unassigned"),
                new ArrayList<>(officers.values()),
                new Ageing((long) totals.get("days_0_2"), (long) totals.get("days_3_7"), (long) totals.get("days_8_plus")));
    }

    @Cacheable(cacheNames = CacheConfig.PERFORMANCE, key = "#market")
    public Performance performance(String market) {
        Map<String, Object> row = jdbc.sql("""
                SELECT count(*) AS decided,
                       avg(extract(epoch FROM decided_at - submitted_at) / 86400)::float8 AS avg_days
                FROM claim_summary
                WHERE market_code = :market AND decided_at >= now() - interval '30 days'
                """)
                .param("market", market)
                .query().singleRow();
        return new Performance(asOf(), market, (long) row.get("decided"), (Double) row.get("avg_days"));
    }

    /** Grouped by currency as well, so amounts in different currencies can never be added together. */
    @Cacheable(cacheNames = CacheConfig.EXPOSURE, key = "'all'")
    public Exposure exposure() {
        List<ExposureRow> rows = jdbc.sql("""
                SELECT market_code, line_of_business, currency, count(*) AS open_claims, sum(current_reserve) AS reserve
                FROM claim_summary
                WHERE %s
                GROUP BY market_code, line_of_business, currency
                ORDER BY market_code, line_of_business
                """.formatted(OPEN))
                .query((rs, n) -> new ExposureRow(rs.getString("market_code"), rs.getString("line_of_business"),
                        rs.getString("currency"), rs.getLong("open_claims"), rs.getBigDecimal("reserve")))
                .list();
        return new Exposure(asOf(), rows);
    }

    /** Time of the newest event applied; null before the first one. Tells the reader how current the figures are. */
    private Instant asOf() {
        Timestamp latest = jdbc.sql("SELECT max(last_event_at) FROM claim_summary").query(Timestamp.class).optional().orElse(null);
        return latest == null ? null : latest.toInstant();
    }
}
