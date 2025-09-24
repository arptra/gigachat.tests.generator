package com.example.tests.generator.pipeline;

import com.example.tests.generator.build.BuildResult;
import com.example.tests.generator.build.BuildTool;
import com.example.tests.generator.build.ErrorReport;
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
        TestGenerationPipeline pipeline = new TestGenerationPipeline(writer, buildRunner, detector, auditLogger);

        pipeline.logGigachatExchange("req", "res");
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "GeneratedTest", "class GeneratedTest {}");

        GenerationReport report = pipeline.process(List.of(generated));

        Path expected = tempDir.resolve("src/test/java/com/example/GeneratedTest.java");
        assertTrue(Files.exists(expected));
        assertTrue(report.isSuccessful());
        assertTrue(report.getCoverageSummary().isPresent());
        assertEquals(missingClasses, report.getClassesWithoutTests());
        assertEquals(1, report.getAuditLog().size());
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
        TestGenerationPipeline pipeline = new TestGenerationPipeline(writer, buildRunner, detector, new GigachatAuditLogger());

        GenerationReport report = pipeline.process(List.of(new GeneratedTestClass("", "BrokenTest", "class BrokenTest {}")));

        assertFalse(report.isSuccessful());
        assertTrue(report.getErrorReport().isPresent());
        assertEquals("boom", report.getErrorReport().orElseThrow().getErrors().get(0));
    }
}
