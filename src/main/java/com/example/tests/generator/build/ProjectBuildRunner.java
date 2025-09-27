package com.example.tests.generator.build;

import com.example.tests.generator.reporting.CoverageAnalyzer;
import com.example.tests.generator.reporting.CoverageSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Runs the project build using the Gradle wrapper to validate generated tests.
 */
public class ProjectBuildRunner {

    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i)(error|failure|exception)");
    private static final Logger LOGGER = Logger.getLogger(ProjectBuildRunner.class.getName());

    private final Path projectRoot;
    private final CommandExecutor commandExecutor;
    private final CoverageAnalyzer coverageAnalyzer;

    public ProjectBuildRunner(Path projectRoot) {
        this(projectRoot, new DefaultCommandExecutor(), new CoverageAnalyzer());
    }

    public ProjectBuildRunner(Path projectRoot, CommandExecutor commandExecutor, CoverageAnalyzer coverageAnalyzer) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.coverageAnalyzer = Objects.requireNonNull(coverageAnalyzer, "coverageAnalyzer");
    }

    public BuildResult runBuild() {
        LOGGER.fine("Determining build command");
        BuildCommand buildCommand = determineCommand();
        if (buildCommand == null) {
            LOGGER.warning("Gradle wrapper not found. Cannot execute build.");
            List<String> errors = List.of("Не удалось найти Gradle wrapper (gradlew). Добавьте wrapper в проект, чтобы запускать тесты.");
            ErrorReport errorReport = new ErrorReport(Instant.now(), BuildTool.UNKNOWN, errors, "");
            return new BuildResult(BuildTool.UNKNOWN, false, "", errors, null, errorReport);
        }

        CommandResult commandResult;
        try {
            LOGGER.fine(() -> "Executing build command: " + String.join(" ", buildCommand.command()));
            commandResult = commandExecutor.execute(buildCommand.command(), projectRoot);
            LOGGER.fine(() -> "Build command finished with exit code " + commandResult.exitCode());
        } catch (IOException e) {
            List<String> errors = List.of("Ошибка ввода-вывода при запуске сборки: " + e.getMessage());
            ErrorReport errorReport = new ErrorReport(Instant.now(), buildCommand.tool(), errors, "");
            return new BuildResult(buildCommand.tool(), false, "", errors, null, errorReport);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            List<String> errors = List.of("Сборка была прервана: " + e.getMessage());
            ErrorReport errorReport = new ErrorReport(Instant.now(), buildCommand.tool(), errors, "");
            return new BuildResult(buildCommand.tool(), false, "", errors, null, errorReport);
        }

        boolean success = commandResult.isSuccessful();
        LOGGER.fine(() -> "Build success: " + success);
        List<String> errors = success ? Collections.emptyList() : extractErrors(commandResult.output());
        CoverageSummary coverageSummary = success ? coverageAnalyzer.analyze(projectRoot).orElse(null) : null;
        ErrorReport errorReport = success ? null : new ErrorReport(Instant.now(), buildCommand.tool(), errors, commandResult.output());

        return new BuildResult(buildCommand.tool(), success, commandResult.output(), errors, coverageSummary, errorReport);
    }

    private BuildCommand determineCommand() {
        Path gradlew = projectRoot.resolve("gradlew");
        if (Files.exists(gradlew)) {
            gradlew.toFile().setExecutable(true);
            LOGGER.fine("Using Unix Gradle wrapper");
            return new BuildCommand(List.of("./gradlew", "test"), BuildTool.GRADLE);
        }
        Path gradlewBat = projectRoot.resolve("gradlew.bat");
        if (Files.exists(gradlewBat)) {
            LOGGER.fine("Using Windows Gradle wrapper");
            return new BuildCommand(List.of("gradlew.bat", "test"), BuildTool.GRADLE);
        }
        return null;
    }

    private List<String> extractErrors(String output) {
        if (output == null || output.isBlank()) {
            return List.of("Сборка завершилась с ошибкой, но вывод пуст.");
        }
        String[] lines = output.split("\\R");
        List<String> matches = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (ERROR_PATTERN.matcher(trimmed).find()) {
                matches.add(trimmed);
            }
        }
        if (!matches.isEmpty()) {
            return matches;
        }
        int take = Math.min(15, lines.length);
        List<String> tail = new ArrayList<>(take);
        for (int i = Math.max(0, lines.length - take); i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isBlank()) {
                tail.add(trimmed);
            }
        }
        return tail.isEmpty() ? List.of("Сборка завершилась с ошибкой. См. полный лог для деталей.") : tail;
    }

    private record BuildCommand(List<String> command, BuildTool tool) {}
}
