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
    }

    public GenerationReport process(List<GeneratedTestClass> generatedTests) throws IOException {
        for (GeneratedTestClass generatedTest : generatedTests) {
            testFileWriter.writeTestFile(
                    generatedTest.getPackageName(),
                    generatedTest.getClassName(),
                    generatedTest.getSourceCode()
            );
        }

        dependencyInstaller.ensureTestDependencies();

        BuildResult buildResult = buildRunner.runBuild();
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
