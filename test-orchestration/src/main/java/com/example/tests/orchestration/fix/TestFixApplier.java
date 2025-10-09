package com.example.tests.orchestration.fix;

import com.example.tests.orchestration.gigachat.TestContextSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Applies the model response by overwriting the affected test source file.
 */
public final class TestFixApplier {

    public void applyFix(TestContextSnapshot context, String updatedSource) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(updatedSource, "updatedSource");
        Files.writeString(context.getSourceFile(), updatedSource, StandardCharsets.UTF_8);
    }
}
