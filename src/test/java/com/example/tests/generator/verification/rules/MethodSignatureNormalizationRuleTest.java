package com.example.tests.generator.verification.rules;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.ParameterMetadata;
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
        GeneratedTestContext context = contextFor(source, metadata(builder -> builder
                .addMethod(MethodMetadata.builder()
                        .name("shouldCallService")
                        .returnType("void")
                        .build())));

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
        GeneratedTestContext context = contextFor(source, metadata(builder -> builder
                .addMethod(MethodMetadata.builder()
                        .name("shouldReturnValue")
                        .returnType("java.lang.String")
                        .build())));

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("public String shouldReturnValue()");
    }

    @Test
    void stripsSignatureCopiedFromProductionMethod() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;

                public class SampleTest {

                    @Test
                    public Response process(Request request) {
                        subject.process(request);
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source, metadata(builder -> builder
                .addMethod(MethodMetadata.builder()
                        .name("process")
                        .returnType("com.example.Response")
                        .addParameter(ParameterMetadata.builder()
                                .name("request")
                                .type("com.example.Request")
                                .build())
                        .build())));

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("public void process()");
        assertThat(updated).doesNotContain("Request request");
        assertThat(updated).doesNotContain("Response process");
    }

    @Test
    void normalizesReturnTypeForMethodsWithVoidProductionSignature() {
        String source = """
                package com.example;

                import org.junit.jupiter.api.Test;

                public class SampleTest {

                    @Test
                    public Response execute() {
                        subject.execute();
                    }
                }
                """;
        GeneratedTestContext context = contextFor(source, metadata(builder -> builder
                .addMethod(MethodMetadata.builder()
                        .name("execute")
                        .returnType("void")
                        .build())));

        rule.apply(context);

        String updated = context.getSourceCode();
        assertThat(updated).contains("public void execute()");
    }

    private GeneratedTestContext contextFor(String source, ClassMetadata metadata) {
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "SampleTest", source);
        return new GeneratedTestContext(generated, metadata, java.util.List.of());
    }

    private ClassMetadata metadata(java.util.function.UnaryOperator<ClassMetadata.Builder> customiser) {
        ClassMetadata.Builder builder = ClassMetadata.builder()
                .packageName("com.example")
                .className("Subject");
        return customiser.apply(builder).build();
    }
}
