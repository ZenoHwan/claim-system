package com.claims.service.claim;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where a motor accident happened or the insured property is. One shape for the request, the
 * stored columns ({@code address_line1..3}, {@code postal_code}) and the response.
 * {@code postalCode} is null only on claims submitted before addresses were split into lines.
 */
@Embeddable
public record Address(
        @NotBlank @Size(max = 300) String addressLine1,
        @Size(max = 300) String addressLine2,
        @Size(max = 300) String addressLine3,
        // ponytail: 5 digits (MY) or 6 (SG) for any market; check against the policy's market if more markets join
        @NotBlank @Pattern(regexp = "\\d{5,6}", message = "must be a 5 or 6 digit postal code") String postalCode) {}
