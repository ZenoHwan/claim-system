package com.claims.service.claim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code claimedAmount} is the claimant's own estimate and may be left out.
 * Exactly one of {@code motor} / {@code property} is required, matching the policy's line of business.
 * Free text is trimmed and its spaces collapsed before validation.
 */
record SubmitClaimRequest(
        @NotNull UUID policyId,
        @NotNull @PastOrPresent LocalDate incidentDate,
        @NotBlank @Size(min = 20, max = 4000, message = "must be between 20 and 4000 characters") String incidentDescription,
        @Positive @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places") BigDecimal claimedAmount,
        @Valid Motor motor,
        @Valid Property property) {

    SubmitClaimRequest {
        incidentDescription = Clean.text(incidentDescription);
    }

    record Motor(
            // ponytail: standard MY / SG plates only; vanity plates (e.g. "PATRIOT 1") need a looser rule
            @NotBlank @Pattern(regexp = "[A-Z]{1,3} \\d{1,4}( [A-Z])?", message = "must look like WXY 1234 or SBA 1234 A")
                    String vehicleRegistration,
            @NotBlank @Size(min = 2, max = 200)
                    @Pattern(regexp = "\\p{L}[\\p{L} .'/@-]*", message = "may contain only letters, spaces and . ' / @ -")
                    String driverName,
            @NotNull @Valid Address accidentLocation,
            boolean thirdPartyInvolved) {

        Motor {
            vehicleRegistration = Clean.plate(vehicleRegistration);
            driverName = Clean.text(driverName);
        }
    }

    record Property(@NotNull @Valid Address propertyAddress, @NotNull PropertyClaimDetail.LossCause lossCause) {}
}
