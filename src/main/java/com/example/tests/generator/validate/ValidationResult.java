package com.example.tests.generator.validate;

import java.util.Collections;
import java.util.List;

/**
 * Result of validating a response from the language model.
 */
public final class ValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final String sanitizedCode;

    private ValidationResult(boolean valid, List<String> errors, String sanitizedCode) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors);
        this.sanitizedCode = sanitizedCode;
    }

    public static ValidationResult success(String sanitizedCode) {
        return new ValidationResult(true, List.of(), sanitizedCode);
    }

    public static ValidationResult failure(List<String> errors) {
        return failure(errors, null);
    }

    public static ValidationResult failure(List<String> errors, String sanitizedCode) {
        return new ValidationResult(false, List.copyOf(errors), sanitizedCode);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public java.util.Optional<String> getSanitizedCode() {
        return java.util.Optional.ofNullable(sanitizedCode);
    }
}
