package com.example.tests.generator.verification.rules;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.verification.GeneratedTestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockitoCompilationFixRuleTest {

    private final MockitoCompilationFixRule rule = new MockitoCompilationFixRule();

    @Test
    void addsExtendWithAnnotationAndMockitoImportsWhenMocksArePresent() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;

                public class SampleTest {

                    @Mock
                    private Dependency dependency;

                    @Test
                    void usesMockito() {
                        Mockito.when(dependency.call()).thenReturn("value");
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("@ExtendWith(MockitoExtension.class)");
        assertThat(updated).contains("import org.mockito.Mock;");
        assertThat(updated).contains("import org.mockito.Mockito;");
        assertThat(updated).contains("import org.junit.jupiter.api.extension.ExtendWith;");
        assertThat(updated).contains("import org.mockito.junit.jupiter.MockitoExtension;");
    }

    @Test
    void doesNotDuplicateExtendWithAnnotationWhenAlreadyPresent() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.junit.jupiter.MockitoExtension;
                import org.mockito.Mock;

                @ExtendWith(MockitoExtension.class)
                public class SampleTest {

                    @Mock
                    private Dependency dependency;

                    @Test
                    void usesMockito() {
                        dependency.toString();
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).containsOnlyOnce("@ExtendWith(MockitoExtension.class)");
        assertThat(updated).contains("import org.mockito.Mock;");
    }

    private GeneratedTestContext contextFor(String source) {
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "SampleTest", source);
        return new GeneratedTestContext(generated, null, java.util.List.of());
    }
}
