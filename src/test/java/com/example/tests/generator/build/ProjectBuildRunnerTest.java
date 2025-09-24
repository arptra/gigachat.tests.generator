package com.example.tests.generator.build;

import com.example.tests.generator.reporting.CoverageAnalyzer;
import com.example.tests.generator.reporting.CoverageMetric;
import com.example.tests.generator.reporting.CoverageSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectBuildRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void usesGradleWrapperWhenPresent() throws IOException {
        Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
        RecordingExecutor executor = new RecordingExecutor(new CommandResult(0, "BUILD SUCCESS"));
        CoverageSummary summary = new CoverageSummary(tempDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml"), Map.of(
                "LINE", new CoverageMetric("LINE", 1, 9)
        ));
        CoverageAnalyzer analyzer = new CoverageAnalyzer() {
            @Override
            public Optional<CoverageSummary> analyze(Path projectRoot) {
                return Optional.of(summary);
            }
        };
        ProjectBuildRunner runner = new ProjectBuildRunner(tempDir, executor, analyzer);

        BuildResult result = runner.runBuild();

        assertTrue(result.isSuccess());
        assertEquals(BuildTool.GRADLE, result.getBuildTool());
        assertEquals(List.of("./gradlew", "test"), executor.lastCommand);
        assertTrue(result.getCoverageSummary().isPresent());
    }

    @Test
    void reportsErrorsWhenBuildFails() throws IOException {
        Files.writeString(tempDir.resolve("gradlew"), "#!/bin/sh\nexit 1\n");
        RecordingExecutor executor = new RecordingExecutor(new CommandResult(1, "Compilation ERROR: something"));
        CoverageAnalyzer analyzer = new CoverageAnalyzer() {
            @Override
            public Optional<CoverageSummary> analyze(Path projectRoot) {
                return Optional.empty();
            }
        };
        ProjectBuildRunner runner = new ProjectBuildRunner(tempDir, executor, analyzer);

        BuildResult result = runner.runBuild();

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(line -> line.contains("ERROR")));
        assertTrue(result.getErrorReport().isPresent());
        ErrorReport report = result.getErrorReport().orElseThrow();
        assertEquals(BuildTool.GRADLE, report.getBuildTool());
        assertTrue(report.getErrors().get(0).contains("ERROR"));
    }

    @Test
    void reportsUnknownWhenWrapperMissing() {
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        ProjectBuildRunner runner = new ProjectBuildRunner(tempDir, (command, workingDirectory) -> new CommandResult(0, ""), analyzer);

        BuildResult result = runner.runBuild();

        assertFalse(result.isSuccess());
        assertEquals(BuildTool.UNKNOWN, result.getBuildTool());
        assertTrue(result.getErrors().get(0).contains("Gradle wrapper"));
    }

    private static class RecordingExecutor implements CommandExecutor {

        private final CommandResult commandResult;
        private List<String> lastCommand;

        RecordingExecutor(CommandResult commandResult) {
            this.commandResult = commandResult;
        }

        @Override
        public CommandResult execute(List<String> command, Path workingDirectory) {
            this.lastCommand = List.copyOf(command);
            return commandResult;
        }
    }
}
