package com.example.tests.orchestration.execution;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable request describing how the orchestrator should execute the project tests.
 */
public final class TestRunRequest {

    private final Path projectDir;
    private final Path gradleExecutable;
    private final List<String> tasks;
    private final List<String> additionalArguments;
    private final Map<String, String> environment;
    private final Duration timeout;

    private TestRunRequest(Builder builder) {
        this.projectDir = builder.projectDir;
        this.gradleExecutable = builder.gradleExecutable;
        this.tasks = List.copyOf(builder.tasks);
        this.additionalArguments = List.copyOf(builder.additionalArguments);
        this.environment = Map.copyOf(builder.environment);
        this.timeout = builder.timeout;
    }

    public Path getProjectDir() {
        return projectDir;
    }

    public Path getGradleExecutable() {
        return gradleExecutable;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public List<String> getAdditionalArguments() {
        return additionalArguments;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public static Builder builder(Path projectDir) {
        return new Builder(projectDir);
    }

    public static final class Builder {
        private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

        private final Path projectDir;
        private Path gradleExecutable;
        private final List<String> tasks = new ArrayList<>();
        private final List<String> additionalArguments = new ArrayList<>();
        private final Map<String, String> environment = new HashMap<>();
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder(Path projectDir) {
            this.projectDir = Objects.requireNonNull(projectDir, "projectDir");
            this.gradleExecutable = defaultGradleExecutable(projectDir);
            this.tasks.add("test");
        }

        public Builder gradleExecutable(Path executable) {
            this.gradleExecutable = Objects.requireNonNull(executable, "executable");
            return this;
        }

        public Builder tasks(List<String> tasks) {
            this.tasks.clear();
            this.tasks.addAll(Objects.requireNonNull(tasks, "tasks"));
            return this;
        }

        public Builder addTask(String task) {
            this.tasks.add(Objects.requireNonNull(task, "task"));
            return this;
        }

        public Builder addArgument(String argument) {
            this.additionalArguments.add(Objects.requireNonNull(argument, "argument"));
            return this;
        }

        public Builder addEnvironmentVariable(String key, String value) {
            this.environment.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public TestRunRequest build() {
            return new TestRunRequest(this);
        }

        private static Path defaultGradleExecutable(Path projectDir) {
            String os = System.getProperty("os.name", "").toLowerCase();
            String wrapperName = os.contains("win") ? "gradlew.bat" : "gradlew";
            return projectDir.resolve(wrapperName);
        }
    }
}
