package com.example.tests.generator.analysis;

import com.example.tests.generator.metadata.MetadataTransformer;
import com.example.tests.generator.project.ProjectLayout;
import com.example.tests.generator.scanner.ProjectScanner;
import com.example.tests.orchestration.analysis.MethodDependencyAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDependencyDocumentationBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void documentsTransitiveDependenciesAndEnums() throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcRoot);
        writeSource("src/main/java/com/example/Target.java", """
                package com.example;

                public class Target {
                    public void run() {
                        Helper helper = new Helper(new Payload("id", Status.ACTIVE));
                        helper.execute();
                        NestedOwner owner = new NestedOwner();
                        NestedOwner.Outcome outcome = owner.build();
                        outcome.message();
                    }
                }
                """);
        writeSource("src/main/java/com/example/Helper.java", """
                package com.example;

                public class Helper {
                    private final Payload payload;

                    public Helper(Payload payload) {
                        this.payload = payload;
                    }

                    public Report execute() {
                        Recommendation recommendation = new Recommendation(payload.id(), Status.ACTIVE);
                        FraudService fraudService = new FraudService();
                        fraudService.check(recommendation);
                        Container container = new Container();
                        container.mapping();
                        return new Report(recommendation);
                    }
                }
                """);
        writeSource("src/main/java/com/example/Payload.java", """
                package com.example;

                public record Payload(String id, Status status) { }
                """);
        writeSource("src/main/java/com/example/Status.java", """
                package com.example;

                public enum Status {
                    ACTIVE,
                    INACTIVE
                }
                """);
        writeSource("src/main/java/com/example/Recommendation.java", """
                package com.example;

                public class Recommendation {
                    private final String id;
                    private final Status status;

                    public Recommendation(String id, Status status) {
                        this.id = id;
                        this.status = status;
                    }

                    public Status status() {
                        return status;
                    }
                }
                """);
        writeSource("src/main/java/com/example/FraudService.java", """
                package com.example;

                public class FraudService {
                    public void check(Recommendation recommendation) {
                        AuditLog log = new AuditLog(recommendation.status());
                        log.append("checked");
                    }
                }
                """);
        writeSource("src/main/java/com/example/AuditLog.java", """
                package com.example;

                public class AuditLog {
                    private final Status status;

                    public AuditLog(Status status) {
                        this.status = status;
                    }

                    public void append(String value) {
                    }
                }
                """);
        writeSource("src/main/java/com/example/Report.java", """
                package com.example;

                public record Report(Recommendation recommendation) { }
                """);
        writeSource("src/main/java/com/example/Bundle.java", """
                package com.example;

                public record Bundle(Status status) { }
                """);
        writeSource("src/main/java/com/example/Container.java", """
                package com.example;

                import java.util.Map;

                public class Container {
                    public Map<String, Bundle> mapping() {
                        return Map.of("default", new Bundle(Status.ACTIVE));
                    }
                }
                """);
        writeSource("src/main/java/com/example/NestedOwner.java", """
                package com.example;

                public class NestedOwner {
                    public record Outcome(String message) { }

                    public Outcome build() {
                        return new Outcome("ok");
                    }
                }
                """);

        ProjectLayout layout = new ProjectLayout("src/main/java", "src/test/java");
        ProjectScanner scanner = new ProjectScanner(tempDir, layout);
        List<com.example.tests.generator.model.ClassMetadata> discovered = scanner.scan();
        MetadataTransformer transformer = new MetadataTransformer(discovered);

        com.example.tests.generator.model.ClassMetadata ownerMetadata = transformer.findRawMetadata("com.example.Target")
                .orElseThrow();
        com.example.tests.generator.metadata.ClassMetadata promptMetadata = transformer.transform(ownerMetadata);

        TestDependencyDocumentationBuilder builder = new TestDependencyDocumentationBuilder(
                new MethodDependencyAnalyzer(), transformer);

        DependencyDocumentation documentation = builder.buildDocumentation(
                promptMetadata,
                ownerMetadata,
                "class TargetTest { void shouldRun() { new Target().run(); } }"
        );

        Map<String, List<String>> dependencyMethods = documentation.dependencyMethods();
        Map<String, List<String>> supportingTypes = documentation.supportingTypes();
        List<String> targetMethods = documentation.targetMethods();

        assertTrue(targetMethods.stream().anyMatch(line -> line.contains("void Target.run()")));

        assertTrue(dependencyMethods.containsKey("com.example.Helper"));
        assertTrue(dependencyMethods.get("com.example.Helper").stream()
                .anyMatch(line -> line.contains("Helper(Payload payload)")));
        assertTrue(dependencyMethods.get("com.example.Helper").stream()
                .anyMatch(line -> line.contains("Report Helper.execute()")));

        assertTrue(supportingTypes.containsKey("com.example.Payload"));
        assertTrue(supportingTypes.get("com.example.Payload").stream()
                .anyMatch(line -> line.contains("Payload(String id, Status status)")));
        assertTrue(supportingTypes.get("com.example.Payload").stream()
                .anyMatch(line -> line.equals("record type")));

        assertTrue(supportingTypes.containsKey("com.example.Status"));
        assertTrue(supportingTypes.get("com.example.Status").stream()
                .anyMatch(line -> line.contains("enum constants: ACTIVE, INACTIVE")));

        assertTrue(dependencyMethods.containsKey("com.example.FraudService"));
        assertTrue(dependencyMethods.get("com.example.FraudService").stream()
                .anyMatch(line -> line.contains("void FraudService.check(Recommendation recommendation)")));

        assertTrue(dependencyMethods.containsKey("com.example.Container"));
        assertTrue(dependencyMethods.get("com.example.Container").stream()
                .anyMatch(line -> line.contains("Map<String, Bundle> Container.mapping()")));

        assertTrue(dependencyMethods.containsKey("com.example.AuditLog"));
        assertTrue(dependencyMethods.get("com.example.AuditLog").stream()
                .anyMatch(line -> line.contains("AuditLog(Status status)")));
        assertTrue(dependencyMethods.get("com.example.AuditLog").stream()
                .anyMatch(line -> line.contains("void AuditLog.append(String value)")));

        assertTrue(supportingTypes.containsKey("com.example.Recommendation"));
        assertTrue(supportingTypes.get("com.example.Recommendation").stream()
                .anyMatch(line -> line.contains("Recommendation(String id, Status status)")));

        assertTrue(supportingTypes.containsKey("com.example.Bundle"));
        assertTrue(supportingTypes.get("com.example.Bundle").stream()
                .anyMatch(line -> line.contains("Bundle(Status status)")));
        assertTrue(supportingTypes.get("com.example.Bundle").stream()
                .anyMatch(line -> line.equals("record type")));

        assertTrue(supportingTypes.containsKey("com.example.NestedOwner.Outcome"));
        assertTrue(supportingTypes.get("com.example.NestedOwner.Outcome").stream()
                .anyMatch(line -> line.contains("NestedOwner.Outcome(String message)")));
        assertTrue(supportingTypes.get("com.example.NestedOwner.Outcome").stream()
                .anyMatch(line -> line.contains("record type")));
    }

    private void writeSource(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

