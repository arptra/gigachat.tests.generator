package com.example.tests.generator.verification.rules;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.verification.GeneratedTestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyFieldCleanupRuleTest {

    private final DependencyFieldCleanupRule rule = new DependencyFieldCleanupRule();

    @Test
    void removesUnusedMockFields() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.mockito.Mock;

                public class SampleTest {

                    @Mock
                    private Dependency unused;

                    @Mock
                    private Dependency used;

                    @Test
                    void executes() {
                        used.toString();
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).doesNotContain("unused");
        assertThat(updated).contains("private Dependency used;");
    }

    @Test
    void keepsFieldsReferencedInAnnotationsOrCode() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;
                import org.mockito.InjectMocks;
                import org.mockito.Mock;

                public class SampleTest {

                    @Mock
                    private Dependency dependency;

                    @InjectMocks
                    private Service service;

                    @Test
                    void executes() {
                        service.run();
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("private Dependency dependency;");
        assertThat(updated).contains("private Service service;");
    }

    private GeneratedTestContext contextFor(String source) {
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "SampleTest", source);
        return new GeneratedTestContext(generated, null, java.util.List.of());
    }
}
