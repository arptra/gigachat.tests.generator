package com.example.tests.generator.method;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads mock templates from the classpath resources.
 */
public class MockTemplateRepository {

    private final Map<String, String> templates;

    public MockTemplateRepository() {
        this("method-mocks/templates.properties");
    }

    public MockTemplateRepository(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Properties properties = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Mock templates resource not found: " + resourcePath);
            }
            properties.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mock templates: " + e.getMessage(), e);
        }
        this.templates = properties.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                ));
    }

    public String render(String key, Object... args) {
        String template = templates.get(key);
        if (template == null) {
            throw new IllegalArgumentException("Unknown mock template: " + key);
        }
        return String.format(template, args);
    }
}
