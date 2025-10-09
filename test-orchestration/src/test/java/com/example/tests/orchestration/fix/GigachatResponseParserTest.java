package com.example.tests.orchestration.fix;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GigachatResponseParserTest {

    private final GigachatResponseParser parser = new GigachatResponseParser();

    @Test
    void extractsCodeBlock() {
        String response = "Here is the fix:\n```java\npublic class Example {}\n```\nHope that helps";
        Optional<String> code = parser.extractJavaCode(response);
        assertTrue(code.isPresent());
        assertEquals("public class Example {}", code.get());
    }

    @Test
    void returnsEmptyWhenNoCodeBlockPresent() {
        Optional<String> code = parser.extractJavaCode("No code here");
        assertTrue(code.isEmpty());
    }
}
