package com.ngoctri.flashsale.shared.api;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public final class ListParameters {

    private ListParameters() {
    }

    public static int parseInteger(
            String field, String value, int defaultValue, int minimum, int maximum) {
        if (value == null) {
            return defaultValue;
        }
        if (value.isBlank()) {
            throw new InvalidListParameterException(field, "must not be blank");
        }
        try {
            var parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new InvalidListParameterException(
                        field, "must be between " + minimum + " and " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidListParameterException(field, "must be a valid integer");
        }
    }

    public static Long parseOptionalLong(
            String field, String value, long minimum, long maximum) {
        if (value == null) {
            return null;
        }
        rejectBlank(field, value);
        try {
            var parsed = Long.parseLong(value);
            if (parsed < minimum || parsed > maximum) {
                throw new InvalidListParameterException(
                        field, "must be between " + minimum + " and " + maximum);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidListParameterException(field, "must be a valid integer");
        }
    }

    public static UUID parseOptionalUuid(String field, String value) {
        if (value == null) {
            return null;
        }
        rejectBlank(field, value);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidListParameterException(field, "must be a valid UUID");
        }
    }

    public static Instant parseOptionalInstant(String field, String value) {
        if (value == null) {
            return null;
        }
        rejectBlank(field, value);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidListParameterException(field, "must be a valid date-time");
        }
    }

    private static void rejectBlank(String field, String value) {
        if (value.isBlank()) {
            throw new InvalidListParameterException(field, "must not be blank");
        }
    }
}
