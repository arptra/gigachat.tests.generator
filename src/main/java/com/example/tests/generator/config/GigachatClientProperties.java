package com.example.tests.generator.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

/**
 * Utility that reads Gigachat credentials from environment variables or system properties.
 */
public final class GigachatClientProperties {

    private static final String API_BASE = "GIGACHAT_API_BASE";
    private static final String LEGACY_BASE_URL = "GIGACHAT_BASE_URL";
    private static final String AUTH_URL = "GIGACHAT_AUTH_URL";
    private static final String API_KEY = "GIGACHAT_API_KEY";
    private static final String LEGACY_CLIENT_ID = "GIGACHAT_CLIENT_ID";
    private static final String CLIENT_SECRET = "GIGACHAT_CLIENT_SECRET";
    private static final String SCOPE = "GIGACHAT_SCOPE";
    private static final String MODEL = "GIGACHAT_MODEL";
    private static final String CERT_FILE = "GIGACHAT_CERT_FILE";
    private static final String KEY_FILE = "GIGACHAT_KEY_FILE";
    private static final String CA_FILE = "GIGACHAT_CA_FILE";

    private static final Properties GRADLE_PROPERTIES = loadGradleProperties();

    private GigachatClientProperties() {
    }

    public static GigachatClientConfig load() {
        GigachatClientConfig.Builder builder = GigachatClientConfig.builder()
                .baseUrl(getRequired(API_BASE, LEGACY_BASE_URL))
                .authUrl(getRequired(AUTH_URL))
                .clientId(getRequired(API_KEY, LEGACY_CLIENT_ID));

        getOptional(CLIENT_SECRET).ifPresent(builder::clientSecret);
        getOptional(SCOPE).ifPresent(builder::scope);
        getOptional(MODEL).ifPresent(builder::model);
        getOptional(CERT_FILE).ifPresent(builder::certificateFile);
        getOptional(KEY_FILE).ifPresent(builder::keyFile);
        getOptional(CA_FILE).ifPresent(builder::caFile);

        builder.retryPolicy(new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(60), 2.0));
        builder.connectTimeout(Duration.ofSeconds(10));
        return builder.build();
    }

    private static String getRequired(String key, String... fallbacks) {
        return findValue(key, fallbacks)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing configuration key. Checked: " + joinKeys(key, fallbacks)));
    }

    private static Optional<String> getOptional(String key, String... fallbacks) {
        return findValue(key, fallbacks);
    }

    private static Optional<String> findValue(String key, String... fallbacks) {
        Optional<String> value = resolveValue(key);
        if (value.isPresent()) {
            return value;
        }
        for (String fallback : fallbacks) {
            value = resolveValue(fallback);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveValue(String key) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .or(() -> Optional.ofNullable(GRADLE_PROPERTIES.getProperty(key)))
                .filter(value -> !value.isBlank());
    }

    private static String joinKeys(String key, String... fallbacks) {
        if (fallbacks == null || fallbacks.length == 0) {
            return key;
        }
        String[] keys = new String[fallbacks.length + 1];
        keys[0] = key;
        System.arraycopy(fallbacks, 0, keys, 1, fallbacks.length);
        return String.join(", ", keys);
    }

    private static Properties loadGradleProperties() {
        Properties properties = new Properties();
        Path path = Paths.get("gradle.properties");
        if (!Files.exists(path)) {
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read gradle.properties", e);
        }
        return properties;
    }
}
