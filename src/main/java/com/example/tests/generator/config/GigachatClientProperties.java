package com.example.tests.generator.config;

import java.time.Duration;
import java.util.Optional;

/**
 * Utility that reads Gigachat credentials from environment variables or system properties.
 */
public final class GigachatClientProperties {

    private static final String BASE_URL = "GIGACHAT_BASE_URL";
    private static final String AUTH_URL = "GIGACHAT_AUTH_URL";
    private static final String CLIENT_ID = "GIGACHAT_CLIENT_ID";
    private static final String CLIENT_SECRET = "GIGACHAT_CLIENT_SECRET";
    private static final String SCOPE = "GIGACHAT_SCOPE";
    private static final String MODEL = "GIGACHAT_MODEL";

    private GigachatClientProperties() {
    }

    public static GigachatClientConfig load() {
        GigachatClientConfig.Builder builder = GigachatClientConfig.builder()
                .baseUrl(getRequired(BASE_URL))
                .authUrl(getRequired(AUTH_URL))
                .clientId(getRequired(CLIENT_ID))
                .clientSecret(getRequired(CLIENT_SECRET));

        getOptional(SCOPE).ifPresent(builder::scope);
        getOptional(MODEL).ifPresent(builder::model);

        builder.retryPolicy(new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(60), 2.0));
        builder.connectTimeout(Duration.ofSeconds(10));
        return builder.build();
    }

    private static String getRequired(String key) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .orElseThrow(() -> new IllegalStateException("Missing configuration key: " + key));
    }

    private static Optional<String> getOptional(String key) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)));
    }
}
