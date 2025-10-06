package com.example.tests.generator.pipeline;

import com.example.tests.generator.build.BuildResult;
import com.example.tests.generator.build.BuildTool;
import com.example.tests.generator.build.ErrorReport;
import com.example.tests.generator.build.GradleTestDependencyInstaller;
import com.example.tests.generator.build.ProjectBuildRunner;
import com.example.tests.generator.gigachat.GigachatAuditLogger;
import com.example.tests.generator.output.TestFileWriter;
import com.example.tests.generator.reporting.ClassWithoutTestsDetector;
import com.example.tests.generator.reporting.CoverageSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGenerationPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void aggregatesReportsAndWritesFiles() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        CoverageSummary coverageSummary = new CoverageSummary(tempDir.resolve("jacoco.xml"), Map.of());
        BuildResult buildResult = new BuildResult(BuildTool.GRADLE, true, "ok", List.of(), coverageSummary, null);
        ProjectBuildRunner buildRunner = new ProjectBuildRunner(tempDir) {
            @Override
            public BuildResult runBuild() {
                return buildResult;
            }
        };
        List<String> missingClasses = List.of("com.example.Service");
        ClassWithoutTestsDetector detector = new ClassWithoutTestsDetector(tempDir) {
            @Override
            public List<String> detect() {
                return missingClasses;
            }
        };
        GigachatAuditLogger auditLogger = new GigachatAuditLogger();
        AtomicBoolean dependencyApplied = new AtomicBoolean(false);
        GradleTestDependencyInstaller dependencyInstaller = new GradleTestDependencyInstaller(tempDir) {
            @Override
            public void ensureTestDependencies() {
                dependencyApplied.set(true);
            }
        };
        TestGenerationPipeline pipeline = new TestGenerationPipeline(writer, buildRunner, dependencyInstaller, detector, auditLogger);

        pipeline.logGigachatExchange("req", "res");
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "GeneratedTest", "class GeneratedTest {}");

        GenerationReport report = pipeline.process(List.of(generated));

        Path expected = tempDir.resolve("src/test/java/com/example/GeneratedTest.java");
        assertTrue(Files.exists(expected));
        assertTrue(report.isSuccessful());
        assertTrue(report.getCoverageSummary().isPresent());
        assertEquals(missingClasses, report.getClassesWithoutTests());
        assertEquals(1, report.getAuditLog().size());
        assertTrue(dependencyApplied.get());
    }

    @Test
    void returnsErrorReportOnFailure() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        ErrorReport errorReport = new ErrorReport(Instant.now(), BuildTool.GRADLE, List.of("boom"), "log");
        BuildResult failureResult = new BuildResult(BuildTool.GRADLE, false, "log", List.of("boom"), null, errorReport);
        ProjectBuildRunner buildRunner = new ProjectBuildRunner(tempDir) {
            @Override
            public BuildResult runBuild() {
                return failureResult;
            }
        };
        ClassWithoutTestsDetector detector = new ClassWithoutTestsDetector(tempDir) {
            @Override
            public List<String> detect() {
                return List.of();
            }
        };
        GradleTestDependencyInstaller dependencyInstaller = new GradleTestDependencyInstaller(tempDir) {
            @Override
            public void ensureTestDependencies() {
                // noop for tests
            }
        };
        TestGenerationPipeline pipeline = new TestGenerationPipeline(writer, buildRunner, dependencyInstaller, detector, new GigachatAuditLogger());

        GenerationReport report = pipeline.process(List.of(new GeneratedTestClass("", "BrokenTest", "class BrokenTest {}")));

        assertFalse(report.isSuccessful());
        assertTrue(report.getErrorReport().isPresent());
        assertEquals("boom", report.getErrorReport().orElseThrow().getErrors().get(0));
    }

    @Test
    void verifyCompilationRestoresFileOnFailure() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        GradleTestDependencyInstaller dependencyInstaller = new GradleTestDependencyInstaller(tempDir) {
            @Override
            public void ensureTestDependencies() {
                // no-op
            }
        };
        BuildResult failure = new BuildResult(BuildTool.GRADLE, false, "log", List.of("error: boom"), null, null);
        ProjectBuildRunner buildRunner = new ProjectBuildRunner(tempDir) {
            @Override
            public BuildResult runBuildForTests(List<String> testClassNames) {
                return failure;
            }
        };
        ClassWithoutTestsDetector detector = new ClassWithoutTestsDetector(tempDir);
        TestGenerationPipeline pipeline = new TestGenerationPipeline(writer, buildRunner, dependencyInstaller, detector, new GigachatAuditLogger());

        TestGenerationPipeline.TestCompilationResult result = pipeline.verifyCompilation(new GeneratedTestClass("com.example", "BrokenTest", "package com.example; class BrokenTest {}"));

        assertFalse(result.successful());
        assertTrue(result.errors().contains("error: boom"));
        Path expected = tempDir.resolve("src/test/java/com/example/BrokenTest.java");
        assertFalse(Files.exists(expected));
    }

    @Test
    void verifyCompilationKeepsFileOnSuccess() throws IOException {
        TestFileWriter writer = new TestFileWriter(tempDir);
        GradleTestDependencyInstaller dependencyInstaller = new GradleTestDependencyInstaller(tempDir) {
            @Override
            public void ensureTestDependencies() {
                // no-op
            }
        };
        BuildResult successResult = new BuildResult(BuildTool.GRADLE, true, "ok", List.of(), null, null);
        ProjectBuildRunner buildRunner = new ProjectBuildRunner(tempDir) {
            @Override
            public BuildResult runBuildForTests(List<String> testClassNames) {
                return successResult;
            }
        };
        ClassWithoutTestsDetector detector = new ClassWithoutTestsDetector(tempDir);
        TestGenerationPipeline pipeline = new TestGenerationPipeline(writer, buildRunner, dependencyInstaller, detector, new GigachatAuditLogger());

        TestGenerationPipeline.TestCompilationResult result = pipeline.verifyCompilation(new GeneratedTestClass("", "WorkingTest", "class WorkingTest {}"));

        assertTrue(result.successful());
        Path expected = tempDir.resolve("src/test/java/WorkingTest.java");
        assertTrue(Files.exists(expected));
    }
}
