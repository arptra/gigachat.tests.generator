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

        ProjectLayout layout = new ProjectLayout("src/main/java", "src/test/java");
        ProjectScanner scanner = new ProjectScanner(tempDir, layout);
        List<com.example.tests.generator.model.ClassMetadata> discovered = scanner.scan();
        MetadataTransformer transformer = new MetadataTransformer(discovered);

        com.example.tests.generator.model.ClassMetadata ownerMetadata = transformer.findRawMetadata("com.example.Target")
                .orElseThrow();
        com.example.tests.generator.metadata.ClassMetadata promptMetadata = transformer.transform(ownerMetadata);

        TestDependencyDocumentationBuilder builder = new TestDependencyDocumentationBuilder(
                new MethodDependencyAnalyzer(), transformer);

        Map<String, List<String>> documentation = builder.buildDocumentation(
                promptMetadata,
                ownerMetadata,
                "class TargetTest { void shouldRun() { new Target().run(); } }"
        );

        assertTrue(documentation.containsKey("com.example.Helper"));
        assertTrue(documentation.get("com.example.Helper").stream()
                .anyMatch(line -> line.contains("Helper(Payload payload)")));
        assertTrue(documentation.get("com.example.Helper").stream()
                .anyMatch(line -> line.contains("Report Helper.execute()")));

        assertTrue(documentation.containsKey("com.example.Payload"));
        assertTrue(documentation.get("com.example.Payload").stream()
                .anyMatch(line -> line.contains("Payload(String id, Status status)")));
        assertTrue(documentation.get("com.example.Payload").stream()
                .anyMatch(line -> line.equals("record type")));

        assertTrue(documentation.containsKey("com.example.Status"));
        assertTrue(documentation.get("com.example.Status").stream()
                .anyMatch(line -> line.contains("enum constants: ACTIVE, INACTIVE")));

        assertTrue(documentation.containsKey("com.example.FraudService"));
        assertTrue(documentation.get("com.example.FraudService").stream()
                .anyMatch(line -> line.contains("void FraudService.check(Recommendation recommendation)")));

        assertTrue(documentation.containsKey("com.example.AuditLog"));
        assertTrue(documentation.get("com.example.AuditLog").stream()
                .anyMatch(line -> line.contains("AuditLog(Status status)")));
        assertTrue(documentation.get("com.example.AuditLog").stream()
                .anyMatch(line -> line.contains("void AuditLog.append(String value)")));
    }

    private void writeSource(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

