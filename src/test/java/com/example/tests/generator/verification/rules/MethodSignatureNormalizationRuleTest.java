package com.example.tests.generator.verification.rules;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import com.example.tests.generator.verification.GeneratedTestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodSignatureNormalizationRuleTest {

    private final MethodSignatureNormalizationRule rule = new MethodSignatureNormalizationRule();

    @Test
    void convertsReturnTypeToVoidAndRemovesParameters() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;

                public class SampleTest {

                    @Test
                    public String shouldCallService(String dependency) {
                        dependency.toString();
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("public void shouldCallService()");
        assertThat(updated).doesNotContain("String dependency");
    }

    @Test
    void keepsReturnTypeWhenMethodReturnsValue() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;

                public class SampleTest {

                    @Test
                    public String shouldReturnValue() {
                        return "value";
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source);

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("public String shouldReturnValue()");
    }

    private GeneratedTestContext contextFor(String source) {
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "SampleTest", source);
        return new GeneratedTestContext(generated, null, java.util.List.of());
    }
}
