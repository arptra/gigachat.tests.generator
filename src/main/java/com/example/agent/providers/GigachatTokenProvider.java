package com.example.agent.providers;

import com.example.tests.generator.config.GigachatClientConfig;
import com.example.tests.generator.util.JsonUtils;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Retrieves OAuth tokens for Gigachat API requests and caches them until expiration.
 */
public class GigachatTokenProvider {

    private final HttpClient httpClient;
    private final GigachatClientConfig config;
    private final AtomicReference<GigachatToken> cache = new AtomicReference<>();

    public GigachatTokenProvider(HttpClient httpClient, GigachatClientConfig config) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.config = Objects.requireNonNull(config, "config");
    }

    public String getAccessToken() {
        GigachatToken current = cache.get();
        if (current != null && !current.isExpired()) {
            return current.getAccessToken();
        }
        synchronized (this) {
            current = cache.get();
            if (current != null && !current.isExpired()) {
                return current.getAccessToken();
            }
            GigachatToken newToken = fetchToken();
            cache.set(newToken);
            return newToken.getAccessToken();
        }
    }

    public void invalidate() {
        cache.set(null);
    }

    private GigachatToken fetchToken() {
        StringBuilder payload = new StringBuilder();
        payload.append("scope=").append(encode(config.getScope()))
                .append("&client_id=").append(encode(config.getClientId()))
                .append("&grant_type=client_credentials");
        String secret = config.getClientSecret();
        if (secret != null && !secret.isBlank()) {
            payload.append("&client_secret=").append(encode(secret));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(config.getAuthUrl())
                .header("Authorization", "Basic " + config.getClientId())
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("RqUID", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RateLimitException("Failed to obtain access token: " + response.statusCode() +
                        " - " + response.body());
            }
            return parseToken(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RateLimitException("Failed to obtain access token", e);
        }
    }

    private GigachatToken parseToken(String body) {
        try {
            String token = Optional.ofNullable(JsonUtils.findString(body, "access_token"))
                    .orElseThrow(() -> new IllegalStateException("access_token is missing in response"));
            Instant expiresAt = Optional.ofNullable(JsonUtils.findString(body, "expires_at"))
                    .map(OffsetDateTime::parse)
                    .map(OffsetDateTime::toInstant)
                    .orElseGet(() -> Optional.ofNullable(JsonUtils.findLong(body, "expires_in"))
                            .map(seconds -> Instant.now().plusSeconds(seconds))
                            .orElse(Instant.now().plusSeconds(1800)));
            return new GigachatToken(token, expiresAt);
        } catch (Exception e) {
            throw new RateLimitException("Unable to parse OAuth response", e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
