package com.ngoctri.flashsale.shared.api;

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
}
