package com.example.tests.generator.verification;

import com.example.tests.generator.pipeline.GeneratedTestClass;
import org.junit.jupiter.api.Test;

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
}
