package com.claims.service.claim;

import java.util.regex.Pattern;

/** Tidies free text before it is validated and stored: trims, collapses runs of spaces, and blank becomes null. */
final class Clean {

    private static final Pattern PLATE = Pattern.compile("([A-Z]{1,3})(\\d{1,4})([A-Z]?)");

    private Clean() {}

    static String text(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.strip().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** "  wxy1234 " and "W X Y 1234" both become "WXY 1234"; anything else is left for validation to reject. */
    static String plate(String value) {
        String cleaned = text(value);
        if (cleaned == null) {
            return null;
        }
        String upper = cleaned.toUpperCase();
        var parts = PLATE.matcher(upper.replace(" ", ""));
        if (!parts.matches()) {
            return upper;
        }
        return parts.group(1) + " " + parts.group(2) + (parts.group(3).isEmpty() ? "" : " " + parts.group(3));
    }
}
