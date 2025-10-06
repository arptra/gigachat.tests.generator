package com.example.tests.generator.pipeline;

import com.example.tests.generator.build.BuildResult;
import com.example.tests.generator.build.GradleTestDependencyInstaller;
import com.example.tests.generator.build.ProjectBuildRunner;
import com.example.tests.generator.gigachat.GigachatAuditLogger;
import com.example.tests.generator.output.TestFileWriter;
import com.example.tests.generator.project.ProjectLayout;
import com.example.tests.generator.project.ProjectLayoutResolver;
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
    private final GradleTestDependencyInstaller dependencyInstaller;
    private final ClassWithoutTestsDetector classWithoutTestsDetector;
    private final GigachatAuditLogger auditLogger;
    private boolean dependenciesEnsured;

    public TestGenerationPipeline(Path projectRoot) {
        this(projectRoot, ProjectLayoutResolver.detect(projectRoot));
    }

    public TestGenerationPipeline(Path projectRoot, ProjectLayout layout) {
        this(new TestFileWriter(projectRoot, layout.testSourceSet()),
                new ProjectBuildRunner(projectRoot),
                new GradleTestDependencyInstaller(projectRoot),
                new ClassWithoutTestsDetector(projectRoot, layout.mainSourceSet(), layout.testSourceSet()),
                new GigachatAuditLogger());
    }

    public TestGenerationPipeline(TestFileWriter testFileWriter,
                                  ProjectBuildRunner buildRunner,
                                  GradleTestDependencyInstaller dependencyInstaller,
                                  ClassWithoutTestsDetector classWithoutTestsDetector,
                                  GigachatAuditLogger auditLogger) {
        this.testFileWriter = Objects.requireNonNull(testFileWriter, "testFileWriter");
        this.buildRunner = Objects.requireNonNull(buildRunner, "buildRunner");
        this.dependencyInstaller = Objects.requireNonNull(dependencyInstaller, "dependencyInstaller");
        this.classWithoutTestsDetector = Objects.requireNonNull(classWithoutTestsDetector, "classWithoutTestsDetector");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
        this.dependenciesEnsured = false;
    }

    public GenerationReport process(List<GeneratedTestClass> generatedTests) throws IOException {
        ensureTestDependencies();
        for (GeneratedTestClass generatedTest : generatedTests) {
            testFileWriter.writeTestFile(
                    generatedTest.getPackageName(),
                    generatedTest.getClassName(),
                    generatedTest.getSourceCode()
            );
        }

        BuildResult buildResult = buildRunner.runBuild();
        List<String> classesWithoutTests = classWithoutTestsDetector.detect();
        return new GenerationReport(buildResult, classesWithoutTests, auditLogger.snapshot());
    }

    public void ensureTestDependencies() {
        if (!dependenciesEnsured) {
            dependencyInstaller.ensureTestDependencies();
            dependenciesEnsured = true;
        }
    }

    public TestCompilationResult verifyCompilation(GeneratedTestClass generatedTest) throws IOException {
        Objects.requireNonNull(generatedTest, "generatedTest");
        ensureTestDependencies();

        TestFileWriter.WrittenTestFile writtenTestFile = testFileWriter.writeTestFileWithBackup(
                blankToNull(generatedTest.getPackageName()),
                generatedTest.getClassName(),
                generatedTest.getSourceCode()
        );

        BuildResult buildResult = buildRunner.runBuildForTests(List.of(generatedTest.getFullyQualifiedName()));
        if (buildResult.isSuccess()) {
            LOGGER.info(() -> "Compilation succeeded for " + generatedTest.getFullyQualifiedName());
            return TestCompilationResult.success();
        }

        LOGGER.warning(() -> "Compilation failed for " + generatedTest.getFullyQualifiedName());
        testFileWriter.restorePreviousContent(writtenTestFile);
        List<String> errors = buildResult.getErrors().isEmpty()
                ? List.of("Compilation failed but no diagnostics were reported. Review the build log for details.")
                : buildResult.getErrors();
        return TestCompilationResult.failure(errors);
    }

    public void logGigachatExchange(String request, String response) {
        auditLogger.log(request, response);
    }

    public void resetAudit() {
        auditLogger.reset();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public record TestCompilationResult(boolean successful, List<String> errors) {
        public TestCompilationResult {
            Objects.requireNonNull(errors, "errors");
        }

        public static TestCompilationResult success() {
            return new TestCompilationResult(true, List.of());
        }

        public static TestCompilationResult failure(List<String> errors) {
            return new TestCompilationResult(false, List.copyOf(errors));
        }
    }
}
