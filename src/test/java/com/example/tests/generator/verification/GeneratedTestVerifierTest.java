package com.example.tests.generator.verification;

import com.example.tests.generator.metadata.ClassMetadata;
import com.example.tests.generator.metadata.MethodMetadata;
import com.example.tests.generator.metadata.RelatedTypeMetadata;
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

    @Test
    void canonicalisesMalformedImportsUsingMetadata() {
        String originalSource = """
                import FlashSaleCoordinator;
                import DiscountEngine;
                import InventoryGateway;
                import NotificationGateway;
                import ArrayList;
                import List;
                import LocalDate;
                import org.junit.jupiter.api.Test;

                public class FinalGeneratedTest {

                    @Test
                    void compilesWithCanonicalImports() {
                        FlashSaleCoordinator coordinator = new com.acme.discount.complex.FlashSaleCoordinator(
                                new com.acme.discount.DiscountEngine(java.util.List.of()),
                                new com.acme.discount.complex.InventoryGateway(),
                                new com.acme.discount.complex.NotificationGateway());
                        List<String> values = new ArrayList<>();
                        LocalDate today = LocalDate.now();
                        if (coordinator == null || values == null || today == null) {
                            throw new AssertionError("Expected objects to be initialised");
                        }
                    }
                }
                """;
        GeneratedTestClass generated = new GeneratedTestClass("", "FinalGeneratedTest", originalSource);
        RelatedTypeMetadata inventoryGateway = RelatedTypeMetadata.builder()
                .packageName("com.acme.discount.complex")
                .className("InventoryGateway")
                .build();
        RelatedTypeMetadata notificationGateway = RelatedTypeMetadata.builder()
                .packageName("com.acme.discount.complex")
                .className("NotificationGateway")
                .build();

        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount.complex")
                .className("FlashSaleCoordinator")
                .addDependency("com.acme.discount.DiscountEngine")
                .addSupportingType(inventoryGateway)
                .addSupportingType(notificationGateway)
                .putSupportingTypeMembers("com.acme.discount.complex.InventoryGateway", List.of("reserve"))
                .putSupportingTypeMembers("com.acme.discount.complex.NotificationGateway", List.of("send"))
                .build();

        GeneratedTestClass verified = verifier.verify(generated, metadata);
        String verifiedSource = verified.getSourceCode();

        assertThat(verifiedSource).contains("import com.acme.discount.complex.FlashSaleCoordinator;");
        assertThat(verifiedSource).contains("import com.acme.discount.DiscountEngine;");
        assertThat(verifiedSource).contains("import com.acme.discount.complex.InventoryGateway;");
        assertThat(verifiedSource).contains("import com.acme.discount.complex.NotificationGateway;");
        assertThat(verifiedSource).contains("import java.util.ArrayList;");
        assertThat(verifiedSource).contains("import java.util.List;");
        assertThat(verifiedSource).contains("import java.time.LocalDate;");
        assertThat(verifiedSource).doesNotContain("import FlashSaleCoordinator;");
        assertThat(verifiedSource).doesNotContain("import InventoryGateway;");
        assertThat(verifiedSource).doesNotContain("import ArrayList;");
    }

    @Test
    void addsMissingImportsForStandardAndDomainTypes() {
        String originalSource = """
                import org.junit.jupiter.api.Test;

                public class FlashSaleCoordinatorTest {

                    @Test
                    void addsCanonicalImportsForCollections() {
                        HashSet<ProductCategory> categories = new HashSet<>(Arrays.asList(ProductCategory.ELECTRONICS));
                        if (categories.isEmpty()) {
                            throw new AssertionError("Expected non-empty categories");
                        }
                    }
                }
                """;
        RelatedTypeMetadata productCategory = RelatedTypeMetadata.builder()
                .packageName("com.acme.discount")
                .className("ProductCategory")
                .build();
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount.complex")
                .className("FlashSaleCoordinator")
                .addSupportingType(productCategory)
                .build();

        GeneratedTestClass generated = new GeneratedTestClass("", "FlashSaleCoordinatorTest", originalSource);
        GeneratedTestClass verified = verifier.verify(generated, metadata);

        String verifiedSource = verified.getSourceCode();
        assertThat(verifiedSource).contains("import java.util.HashSet;");
        assertThat(verifiedSource).contains("import java.util.Arrays;");
        assertThat(verifiedSource).contains("import com.acme.discount.ProductCategory;");
        assertThat(verifiedSource).contains("HashSet<ProductCategory> categories = new HashSet<>(Arrays.asList(ProductCategory.ELECTRONICS));");
    }

    @Test
    void canonicalisesStaticImportsForStandardLibraryAndDomainTypes() {
        String originalSource = """
                import static Collections.singletonList;
                import static ProductCategory.ELECTRONICS;
                import org.junit.jupiter.api.Test;

                public class OrderTest {

                    @Test
                    void usesStaticHelpers() {
                        java.util.List<com.acme.discount.ProductCategory> categories = singletonList(ELECTRONICS);
                        if (categories.isEmpty()) {
                            throw new AssertionError("Expected non-empty categories");
                        }
                    }
                }
                """;
        RelatedTypeMetadata productCategory = RelatedTypeMetadata.builder()
                .packageName("com.acme.discount")
                .className("ProductCategory")
                .build();
        ClassMetadata metadata = ClassMetadata.builder()
                .packageName("com.acme.discount")
                .className("Order")
                .addSupportingType(productCategory)
                .build();

        GeneratedTestClass generated = new GeneratedTestClass("", "OrderTest", originalSource);
        GeneratedTestClass verified = verifier.verify(generated, metadata);

        String verifiedSource = verified.getSourceCode();
        assertThat(verifiedSource).doesNotContain("import static");
        assertThat(verifiedSource).contains("import java.util.Collections;");
        assertThat(verifiedSource).contains("import com.acme.discount.ProductCategory;");
        assertThat(verifiedSource)
                .contains("Collections.singletonList(ProductCategory.ELECTRONICS)");
    }
}
