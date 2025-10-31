package com.example.tests.generator.verification;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.pipeline.GeneratedTestClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedTestVerifierTest {

    private final GeneratedTestVerifier verifier = new GeneratedTestVerifier();

    @Test
    void appliesDefaultRulesToGeneratedClass() {
        String originalSource = """
                package com.example;

                import foo.bar.Bar;

                public class SampleServiceTest {

                    @Mock
                    private Bar unused;

                    @Mock
                    private Bar used;

                    @Test
                    public Bar shouldCallService(Bar dependency) {
                        Mockito.when(used.toString()).thenReturn("value");
                        String result = used.toString();
                        Assertions.assertEquals("value", result);
                    }
                }
                """;
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "SampleServiceTest", originalSource);

        GeneratedTestClass verified = verifier.verify(generated, null);
        String verifiedSource = verified.getSourceCode();

        assertThat(verifiedSource).contains("@ExtendWith(MockitoExtension.class)");
        assertThat(verifiedSource).contains("import org.junit.jupiter.api.Test;");
        assertThat(verifiedSource).contains("import org.junit.jupiter.api.Assertions;");
        assertThat(verifiedSource).contains("import org.mockito.Mock;");
        assertThat(verifiedSource).contains("import org.mockito.Mockito;");
        assertThat(verifiedSource).contains("import org.mockito.junit.jupiter.MockitoExtension;");
        assertThat(verifiedSource).contains("import foo.bar.Bar;");
        assertThat(verifiedSource).doesNotContain("unused;");
        assertThat(verifiedSource).contains("void shouldCallService()");
        assertThat(verifiedSource).doesNotContain("Bar dependency");
    }

    @Test
    void correctsReturnTypeAssignmentsWhenCompilationReportsIncompatibleTypes() {
        String originalSource = """
                package com.example;

                import org.junit.jupiter.api.Test;

                public class CalculatorTest {

                    private final Calculator calculator = new Calculator();

                    @Test
                    void delegatesToCalculator() {
                        DiscountResult result = (DiscountResult) calculator.calculate();
                    }
                }
                """;
        GeneratedTestClass generated = new GeneratedTestClass("com.example", "CalculatorTest", originalSource);
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.example")
                .className("Calculator")
                .addMethod(MethodMetadata.builder()
                        .name("calculate")
                        .returnType("Result")
                        .build())
                .build();

        GeneratedTestClass verified = verifier.verify(
                generated,
                metadata,
                List.of("incompatible types: Result cannot be converted to DiscountResult")
        );

        String verifiedSource = verified.getSourceCode();
        assertThat(verifiedSource)
                .contains("com.example.Calculator.Result result = calculator.calculate();");
        assertThat(verifiedSource).doesNotContain("(DiscountResult)");
    }

    @Test
    void rewritesFullyQualifiedTypesToCanonicalImports() {
        String originalSource = """
                import org.junit.jupiter.api.Test;

                public class FinalGeneratedTest {

                    @Test
                    void shouldUseDomainTypes() {
                        com.acme.discount.complex.FlashSaleCoordinator coordinator =
                                new com.acme.discount.complex.FlashSaleCoordinator(null, null, null);
                        java.time.LocalDate today = java.time.LocalDate.now();
                        if (coordinator == null || today == null) {
                            throw new AssertionError("Expected non-null objects");
                        }
                    }
                }
                """;
        GeneratedTestClass generated = new GeneratedTestClass("", "FinalGeneratedTest", originalSource);
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount.complex")
                .className("FlashSaleCoordinator")
                .addDependency("com.acme.discount.complex.InventoryGateway")
                .addDependency("com.acme.discount.complex.NotificationGateway")
                .addDependency("com.acme.discount.CustomerProfile")
                .build();

        GeneratedTestClass verified = verifier.verify(generated, metadata);
        String verifiedSource = verified.getSourceCode();

        assertThat(verifiedSource).contains("import com.acme.discount.complex.FlashSaleCoordinator;");
        assertThat(verifiedSource).contains("import java.time.LocalDate;");
        assertThat(verifiedSource).contains("FlashSaleCoordinator coordinator = new FlashSaleCoordinator");
        assertThat(verifiedSource).contains("LocalDate today = LocalDate.now();");
        assertThat(verifiedSource).doesNotContain("new com.acme.discount.complex.FlashSaleCoordinator");
        assertThat(verifiedSource).doesNotContain("com.acme.discount.complex.FlashSaleCoordinator coordinator");
        assertThat(verifiedSource).doesNotContain("java.time.LocalDate.now");
    }
}
