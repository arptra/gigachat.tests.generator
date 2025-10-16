package com.example.tests.orchestration.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodDependencyAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void collectsObjectCreationsAndMethodCalls() throws IOException {
        Path source = writeJavaFile("com.acme", "OrderServiceTest", """
                package com.acme;

                public class OrderServiceTest {
                    void target(String orderId) {
                        Repository repository = new Repository();
                        Service service = new Service(repository, new ClockProvider());
                        service.process(orderId, 42);
                        new Logger().log(service.buildMessage());
                    }
                }
                """);

        MethodDependencyAnalyzer analyzer = new MethodDependencyAnalyzer();
        MethodDependencyGraph graph = analyzer.analyze(source, "com.acme.OrderServiceTest", "target");

        assertEquals("com.acme.OrderServiceTest", graph.getFullyQualifiedClassName());
        assertEquals("target", graph.getMethodName());
        assertEquals(List.of(new MethodParameter("orderId", "String")), graph.getParameters());

        List<DependencyNode> dependencies = graph.getDependencies();
        assertEquals(3, dependencies.size());

        DependencyNode repository = dependencies.get(0);
        assertEquals("Repository", repository.getType());
        assertEquals(DependencyOrigin.LOCAL_VARIABLE, repository.getOrigin());
        assertTrue(repository.getConstructorArguments().isEmpty());
        assertTrue(repository.getMethodInvocations().isEmpty());
        assertTrue(repository.getDependencies().isEmpty());

        DependencyNode service = dependencies.get(1);
        assertEquals("Service", service.getType());
        assertEquals(DependencyOrigin.LOCAL_VARIABLE, service.getOrigin());
        assertEquals(List.of(
                new InvocationArgument("Repository", "repository"),
                new InvocationArgument("ClockProvider", "new ClockProvider()")
        ), service.getConstructorArguments());
        assertEquals(List.of(
                new MethodInvocation("process", List.of(
                        new InvocationArgument("String", "orderId"),
                        new InvocationArgument("int", "42")
                )),
                new MethodInvocation("buildMessage", List.of())
        ), service.getMethodInvocations());
        assertEquals(2, service.getDependencies().size());
        assertSame(repository, service.getDependencies().get(0));

        DependencyNode clockProvider = service.getDependencies().get(1);
        assertEquals("ClockProvider", clockProvider.getType());
        assertEquals(DependencyOrigin.CONSTRUCTOR_ARGUMENT, clockProvider.getOrigin());
        assertTrue(clockProvider.getConstructorArguments().isEmpty());
        assertTrue(clockProvider.getMethodInvocations().isEmpty());

        DependencyNode logger = dependencies.get(2);
        assertEquals("Logger", logger.getType());
        assertEquals(DependencyOrigin.INLINE_TARGET, logger.getOrigin());
        assertEquals(List.of(), logger.getConstructorArguments());
        assertEquals(List.of(new MethodInvocation("log", List.of(
                new InvocationArgument("buildMessage()", "service.buildMessage()")
        ))), logger.getMethodInvocations());

        assertTrue(graph.getUnattachedInvocations().isEmpty());
    }

    @Test
    void recordsNestedDependencyHierarchy() throws IOException {
        Path source = writeJavaFile("com.example", "ConfiguratorTest", """
                package com.example;

                public class ConfiguratorTest {
                    void configure() {
                        Service service = new Service(new Config(new FeatureFlag("beta")));
                        service.configure();
                    }
                }
                """);

        MethodDependencyAnalyzer analyzer = new MethodDependencyAnalyzer();
        MethodDependencyGraph graph = analyzer.analyze(source, "com.example.ConfiguratorTest", "configure");

        List<DependencyNode> dependencies = graph.getDependencies();
        assertEquals(1, dependencies.size());

        DependencyNode service = dependencies.get(0);
        assertEquals("Service", service.getType());
        assertEquals(DependencyOrigin.LOCAL_VARIABLE, service.getOrigin());
        assertEquals(List.of(new InvocationArgument("Config", "new Config(new FeatureFlag(\"beta\"))")),
                service.getConstructorArguments());
        assertEquals(List.of(new MethodInvocation("configure", List.of())), service.getMethodInvocations());
        assertEquals(1, service.getDependencies().size());

        DependencyNode config = service.getDependencies().get(0);
        assertEquals("Config", config.getType());
        assertEquals(DependencyOrigin.CONSTRUCTOR_ARGUMENT, config.getOrigin());
        assertEquals(List.of(new InvocationArgument("FeatureFlag", "new FeatureFlag(\"beta\")")),
                config.getConstructorArguments());
        assertEquals(1, config.getDependencies().size());

        DependencyNode featureFlag = config.getDependencies().get(0);
        assertEquals("FeatureFlag", featureFlag.getType());
        assertEquals(DependencyOrigin.CONSTRUCTOR_ARGUMENT, featureFlag.getOrigin());
        assertEquals(List.of(new InvocationArgument("String", "\"beta\"")), featureFlag.getConstructorArguments());
    }

    private Path writeJavaFile(String packageName, String className, String content) throws IOException {
        Path packageDir = tempDir.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Path source = packageDir.resolve(className + ".java");
        Files.writeString(source, content);
        return source;
    }
}
