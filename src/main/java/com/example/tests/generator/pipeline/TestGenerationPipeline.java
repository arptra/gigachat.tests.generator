package com.example.tests.generator.pipeline;

import com.example.tests.generator.build.BuildResult;
import com.example.tests.generator.build.ProjectBuildRunner;
import com.example.tests.generator.gigachat.GigachatAuditLogger;
import com.example.tests.generator.output.TestFileWriter;
import com.example.tests.generator.reporting.ClassWithoutTestsDetector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Coordinates writing generated tests to disk and validating them via the project build.
 */
public class TestGenerationPipeline {

    private static final Logger LOGGER = Logger.getLogger(TestGenerationPipeline.class.getName());

    private final TestFileWriter testFileWriter;
    private final ProjectBuildRunner buildRunner;
    private final ClassWithoutTestsDetector classWithoutTestsDetector;
    private final GigachatAuditLogger auditLogger;

    public TestGenerationPipeline(Path projectRoot) {
        this(new TestFileWriter(projectRoot),
                new ProjectBuildRunner(projectRoot),
                new ClassWithoutTestsDetector(projectRoot),
                new GigachatAuditLogger());
    }

    public TestGenerationPipeline(TestFileWriter testFileWriter,
                                  ProjectBuildRunner buildRunner,
                                  ClassWithoutTestsDetector classWithoutTestsDetector,
                                  GigachatAuditLogger auditLogger) {
        this.testFileWriter = Objects.requireNonNull(testFileWriter, "testFileWriter");
        this.buildRunner = Objects.requireNonNull(buildRunner, "buildRunner");
        this.classWithoutTestsDetector = Objects.requireNonNull(classWithoutTestsDetector, "classWithoutTestsDetector");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    public GenerationReport process(List<GeneratedTestClass> generatedTests) throws IOException {
        LOGGER.info("Writing generated test sources to disk");
        for (GeneratedTestClass generatedTest : generatedTests) {
            LOGGER.info(() -> "Writing test class "
                    + (generatedTest.getPackageName() == null || generatedTest.getPackageName().isBlank()
                    ? generatedTest.getClassName()
                    : generatedTest.getPackageName() + '.' + generatedTest.getClassName()));
            testFileWriter.writeTestFile(
                    generatedTest.getPackageName(),
                    generatedTest.getClassName(),
                    generatedTest.getSourceCode()
            );
        }

        LOGGER.info("Running project build to validate generated tests");
        BuildResult buildResult = buildRunner.runBuild();
        LOGGER.info(() -> "Build finished with status: " + (buildResult.isSuccessful() ? "SUCCESS" : "FAILURE"));
        LOGGER.info("Checking for classes without generated tests");
        List<String> classesWithoutTests = classWithoutTestsDetector.detect();
        return new GenerationReport(buildResult, classesWithoutTests, auditLogger.snapshot());
    }

    public void logGigachatExchange(String request, String response) {
        auditLogger.log(request, response);
    }

    public void resetAudit() {
        auditLogger.reset();
    }
}
