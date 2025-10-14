package com.example.tests.generator.verification.rules;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.verification.GeneratedTestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportSanitizerRuleTest {

    private final ImportSanitizerRule rule = new ImportSanitizerRule();

    @Test
    void addsMissingImportsAndDeduplicatesExistingOnes() {
        String source = """
                package com.example;

                import foo.Bar;
                import foo.Bar;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class SampleTest {

                    @Test
                    void usesAssertions() {
                        assertEquals(1, 1);
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("import foo.Bar;\n");
        assertThat(updated).containsOnlyOnce("import org.junit.jupiter.api.Test;");
        assertThat(updated).containsOnlyOnce("import org.junit.jupiter.api.Assertions;");
        assertThat(updated).containsOnlyOnce("import static org.junit.jupiter.api.Assertions.assertEquals;");
    }

    @Test
    void insertsImportBlockWhenNonePresent() {
        String source = """
                package com.example;

                public class SampleTest {

                    @BeforeEach
                    void setUp() {
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("import org.junit.jupiter.api.BeforeEach;\n");
        assertThat(updated).contains("package com.example;\n\nimport org.junit.jupiter.api.BeforeEach;\n\npublic class SampleTest");
    }

    private GeneratedTestContext contextFor(String source) {
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "SampleTest", source);
        return new GeneratedTestContext(generated, null, java.util.List.of());
    }
}
