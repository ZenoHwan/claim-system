package com.claims.service.claim;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where a motor accident happened or the insured property is. One shape for the request, the
 * stored columns ({@code address_line1..3}, {@code postal_code}) and the response.
 * The postal code's length depends on the policy's market, which {@link ClaimService} checks.
 * {@code postalCode} is null only on claims submitted before addresses were split into lines.
 */
@Embeddable
public record Address(
        @NotBlank @Size(min = 3, max = 300) @Pattern(regexp = LINE, message = LINE_MESSAGE) String addressLine1,
        @Size(max = 300) @Pattern(regexp = LINE, message = LINE_MESSAGE) String addressLine2,
        @Size(max = 300) @Pattern(regexp = LINE, message = LINE_MESSAGE) String addressLine3,
        @NotBlank @Pattern(regexp = "\\d{5,6}", message = "must be 5 or 6 digits") String postalCode) {

    /** Letters, digits and the punctuation addresses use, with at least one letter or digit. */
    static final String LINE = "(?=.*[\\p{L}\\p{N}])[\\p{L}\\p{N} ,.'/#()&-]+";
    static final String LINE_MESSAGE = "may contain only letters, numbers, spaces and , . ' / # ( ) & -";

    public Address {
        addressLine1 = Clean.text(addressLine1);
        addressLine2 = Clean.text(addressLine2);
        addressLine3 = Clean.text(addressLine3);
        postalCode = Clean.text(postalCode);
    }
}
