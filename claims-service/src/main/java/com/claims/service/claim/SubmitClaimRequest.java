package com.claims.service.claim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Exactly one of {@code motor} / {@code property} is required, matching the policy's line of business. */
record SubmitClaimRequest(
        @NotNull UUID policyId,
        @NotNull @PastOrPresent LocalDate incidentDate,
        @NotBlank @Size(max = 4000) String incidentDescription,
        @NotNull @Positive BigDecimal claimedAmount,
        @Valid Motor motor,
        @Valid Property property) {

    record Motor(
            @NotBlank @Size(max = 20) String vehicleRegistration,
            @NotBlank @Size(max = 200) String driverName,
            @NotBlank @Size(max = 300) String accidentLocation,
            boolean thirdPartyInvolved) {}

    record Property(@NotBlank @Size(max = 300) String propertyAddress, @NotNull PropertyClaimDetail.LossCause lossCause) {}
}
