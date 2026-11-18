package com.claims.service.policy;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Reference data, seeded. A claim copies line, market and currency from its policy. */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    private UUID id;

    private String policyNumber;
    private UUID holderId;

    @Enumerated(EnumType.STRING)
    private LineOfBusiness lineOfBusiness;

    private String marketCode;
    private String currency;
    private BigDecimal sumInsured;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    protected Policy() {}

    public boolean covers(LocalDate date) {
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo);
    }

    public UUID getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public UUID getHolderId() {
        return holderId;
    }

    public LineOfBusiness getLineOfBusiness() {
        return lineOfBusiness;
    }

    public String getMarketCode() {
        return marketCode;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }
}
