package com.example.tests.generator.method;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodMockMergerTest {

    private final MethodMockMerger merger = new MethodMockMerger();

    @Test
    void replacesOriginalCodeWithMockSnippet() {
        String method = "void usesDependencies() {\n" +
                "        Helper.cleanup();\n" +
                "        finalizeCall();\n" +
                "    }";
        MockSnippet snippet = new MockSnippet(
                "static-void-invocation",
                "Helper.cleanup();",
                "try (MockedStatic<Helper> mocked = Mockito.mockStatic(Helper.class)) {\n" +
                        "    mocked.when(Helper::cleanup);\n" +
                        "}"
        );

        String merged = merger.merge(method, List.of(snippet));

        assertTrue(merged.contains("Mockito.mockStatic(Helper.class)"));
        assertTrue(merged.contains("        try (MockedStatic<Helper> mocked"));
        assertTrue(merged.contains("            mocked.when(Helper::cleanup);"));
        assertTrue(merged.contains("finalizeCall();"));
        assertFalse(merged.contains("Helper.cleanup();"));
    }

    @Test
    void mergesMultipleSnippetsSequentially() {
        String method = "void orchestrate() {\n" +
                "        Helper.cleanup();\n" +
                "        new RemoteClient().execute();\n" +
                "    }";
        MockSnippet first = new MockSnippet(
                "static-void-invocation",
                "Helper.cleanup();",
                "try (MockedStatic<Helper> mocked = Mockito.mockStatic(Helper.class)) {\n" +
                        "    mocked.when(Helper::cleanup);\n" +
                        "}"
        );
        MockSnippet second = new MockSnippet(
                "new-object-invocation",
                "new RemoteClient().execute();",
                "RemoteClient remoteClient = Mockito.mock(RemoteClient.class);\n" +
                        "Mockito.when(remoteClient.execute()).thenReturn(null);"
        );

        String merged = merger.merge(method, List.of(first, second));

        assertTrue(merged.contains("RemoteClient remoteClient = Mockito.mock"));
        assertFalse(merged.contains("new RemoteClient().execute();"));
    }

    @Test
    void mergesMultipleSnippetsSequentiallyWithArguments() {
        String method = "String orchestrate(String orderId, int retries) {\n" +
                "        Helper.cleanup(orderId);\n" +
                "        return new RemoteClient(orderId).execute(retries);\n" +
                "    }";
        MockSnippet first = new MockSnippet(
                "static-void-invocation",
                "Helper.cleanup(orderId);",
                "try (MockedStatic<Helper> mocked = Mockito.mockStatic(Helper.class)) {\n" +
                        "    mocked.when(() -> Helper.cleanup(orderId));\n" +
                        "}"
        );
        MockSnippet second = new MockSnippet(
                "new-object-invocation",
                "return new RemoteClient(orderId).execute(retries);",
                "RemoteClient remoteClient = Mockito.mock(RemoteClient.class);\n" +
                        "Mockito.when(remoteClient.execute(retries)).thenReturn(null);\n" +
                        "return remoteClient.execute(retries);"
        );

        String merged = merger.merge(method, List.of(first, second));

        assertTrue(merged.startsWith("String orchestrate(String orderId, int retries)"));
        assertTrue(merged.contains("Mockito.mockStatic(Helper.class)"));
        assertFalse(merged.contains("Helper.cleanup(orderId);"));
        assertFalse(merged.contains("return new RemoteClient(orderId).execute(retries);"));
    }

    @Test
    void keepsMethodUntouchedWhenSnippetNotFound() {
        String method = "void orchestrate() {\n" +
                "        Helper.cleanup();\n" +
                "    }";
        MockSnippet snippet = new MockSnippet(
                "new-object-invocation",
                "new RemoteClient().execute();",
                "RemoteClient remoteClient = Mockito.mock(RemoteClient.class);\n" +
                        "Mockito.when(remoteClient.execute()).thenReturn(null);"
        );

        String merged = merger.merge(method, List.of(snippet));

        assertEquals(method, merged);
    }
}
