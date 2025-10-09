package com.example.tests.orchestration.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Executes Gradle test tasks in a separate process and captures the console output for analysis.
 */
public class GradleTestSuiteRunner implements TestSuiteRunner {

    @Override
    public TestRunResult runAllTests(TestRunRequest request) {
        Objects.requireNonNull(request, "request");

        List<String> command = buildCommand(request);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(request.getProjectDir().toFile());
        builder.redirectErrorStream(true);
        builder.environment().putAll(request.getEnvironment());

        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        boolean timedOut = false;
        Instant start = Instant.now();

        try {
            ensureExecutable(request.getGradleExecutable());
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            if (!process.waitFor(request.getTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                timedOut = true;
                process.destroyForcibly();
            } else {
                exitCode = process.exitValue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.append("Gradle execution interrupted: ").append(e.getMessage()).append(System.lineSeparator());
        } catch (IOException e) {
            output.append("Failed to execute Gradle: ").append(e.getMessage()).append(System.lineSeparator());
        }

        Duration elapsed = Duration.between(start, Instant.now());
        return new TestRunResult(exitCode, timedOut, elapsed, output.toString());
    }

    private static List<String> buildCommand(TestRunRequest request) {
        List<String> command = new ArrayList<>();
        Path executable = request.getGradleExecutable();
        command.add(executable.toAbsolutePath().toString());
        command.addAll(request.getTasks());
        command.addAll(request.getAdditionalArguments());
        return command;
    }

    private static void ensureExecutable(Path executable) throws IOException {
        if (!Files.exists(executable)) {
            throw new IOException("Gradle wrapper not found at " + executable);
        }
        if (!Files.isExecutable(executable)) {
            executable.toFile().setExecutable(true);
        }
    }
}
