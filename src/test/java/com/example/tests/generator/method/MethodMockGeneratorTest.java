package com.example.tests.generator.method;

import com.example.tests.generator.method.MockSnippet;
import com.example.tests.generator.method.rules.MockRule;
import com.example.tests.generator.method.rules.NewObjectInvocationRule;
import com.example.tests.generator.method.rules.StaticVoidInvocationRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodMockGeneratorTest {

    private final TestMethodAnalyzer analyzer = new TestMethodAnalyzer();
    private final List<MockRule> rules = List.of(
            new StaticVoidInvocationRule(),
            new NewObjectInvocationRule()
    );
    private final MockTemplateRepository templates = new MockTemplateRepository();

    @Test
    void generatesMocksForStaticAndNewObjectCalls() {
        String code = "package com.example;\n\n" +
                "import org.junit.jupiter.api.Test;\n\n" +
                "class SampleServiceTest {\n" +
                "    @Test void usesDependencies() {\n" +
                "        Helper.cleanup();\n" +
                "        new RemoteClient().execute();\n" +
                "    }\n" +
                "}\n";

        MethodMockGenerator generator = new MethodMockGenerator(analyzer, rules, templates, null);
        List<MethodMockPlan> plans = generator.generate(code, false);

        assertFalse(plans.isEmpty(), "Plans should be generated");
        MethodMockPlan plan = plans.get(0);
        assertEquals("SampleServiceTest", plan.className().substring(plan.className().lastIndexOf('.') + 1));
        assertEquals("usesDependencies", plan.methodName());
        assertEquals(2, plan.snippets().size());
        MockSnippet first = plan.snippets().get(0);
        assertEquals("static-void-invocation", first.ruleId());
        assertTrue(first.mockCode().contains("mockStatic(Helper.class)"));
        MockSnippet second = plan.snippets().get(1);
        assertEquals("new-object-invocation", second.ruleId());
        assertTrue(second.mockCode().contains("Mockito.when"));
    }

    @Test
    void invokesValidationWhenEnabled() {
        String code = "package com.example;\n\n" +
                "import org.junit.jupiter.api.Test;\n\n" +
                "class SampleServiceTest {\n" +
                "    @Test void usesDependencies() {\n" +
                "        Helper.cleanup();\n" +
                "    }\n" +
                "}\n";

        AtomicBoolean validated = new AtomicBoolean(false);
        MethodMockValidationService validationService = (analysis, snippets) -> {
            validated.set(true);
            return Optional.of("ok");
        };
        MethodMockGenerator generator = new MethodMockGenerator(analyzer, rules, templates, validationService);

        List<MethodMockPlan> plans = generator.generate(code, true);

        assertTrue(validated.get(), "Validation should be invoked when enabled");
        assertFalse(plans.isEmpty());
        assertTrue(plans.get(0).validationFeedback().isPresent());
    }
}
