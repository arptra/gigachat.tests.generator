package com.example.agent.providers;

import com.example.tests.generator.config.GigachatClientConfig;
import com.example.tests.generator.config.RetryPolicy;
import com.example.tests.generator.util.JsonUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gigachat-based implementation of {@link LLMClient}.
 */
public class GigachatLLMClient implements LLMClient {

    private static final Logger log = Logger.getLogger(GigachatLLMClient.class.getName());
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final HttpClient httpClient;
    private final GigachatClientConfig config;
    private final GigachatTokenProvider tokenProvider;

    public GigachatLLMClient(GigachatClientConfig config) {
        this(config, createHttpClient(config));
    }

    public GigachatLLMClient(GigachatClientConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.tokenProvider = new GigachatTokenProvider(httpClient, config);
    }

    @Override
    public String sendPrompt(String prompt, Map<String, Object> options) {
        Objects.requireNonNull(prompt, "prompt");
        HttpResponse<String> response = executeWithRetry(() -> buildRequest(prompt, options, false),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            handleRateLimits(response.statusCode(), response.body());
        }
        return parseMessage(response.body());
    }

    @Override
    public Stream<String> streamResponses(String prompt, Map<String, Object> options) {
        Objects.requireNonNull(prompt, "prompt");
        HttpResponse<InputStream> response = executeWithRetry(
                () -> buildRequest(prompt, options, true),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            handleRateLimits(response.statusCode(), "");
            return Stream.empty();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Unable to close Gigachat stream", e);
            }
        }).filter(line -> !line.isBlank())
                .map(this::stripStreamPrefix);
    }

    @Override
    public void handleRateLimits(int statusCode, String responseBody) {
        if (statusCode == 429) {
            throw new RateLimitException("Gigachat rate limit exceeded: " + responseBody);
        }
        if (statusCode >= 500) {
            throw new RateLimitException("Gigachat service is unavailable: " + statusCode + " - " + responseBody);
        }
    }

    private HttpRequest buildRequest(String prompt, Map<String, Object> options, boolean stream) {
        String payload = buildPayload(prompt, options, stream);
        return HttpRequest.newBuilder()
                .uri(buildEndpoint())
                .timeout(config.getConnectTimeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    private URI buildEndpoint() {
        String base = config.getBaseUrl().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + CHAT_COMPLETIONS_PATH);
    }

    private String buildPayload(String prompt, Map<String, Object> options, boolean stream) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"model\":\"").append(JsonUtils.escape(config.getModel())).append('\"');
        builder.append(',').append("\"stream\":").append(stream);
        if (options != null) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                String key = entry.getKey();
                if ("model".equalsIgnoreCase(key) || "messages".equalsIgnoreCase(key)
                        || "stream".equalsIgnoreCase(key)) {
                    continue;
                }
                builder.append(',').append('"').append(JsonUtils.escape(entry.getKey())).append('"').append(':');
                JsonUtils.appendJsonValue(builder, entry.getValue());
            }
        }
        builder.append(',').append("\"messages\":");
        JsonUtils.appendJsonValue(builder, buildMessages(prompt));
        builder.append('}');
        return builder.toString();
    }

    private List<Map<String, String>> buildMessages(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", "You are a helpful assistant that writes Java tests.");
        messages.add(system);

        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", prompt);
        messages.add(user);
        return messages;
    }

    private <T> HttpResponse<T> executeWithRetry(Supplier<HttpRequest> requestSupplier,
            HttpResponse.BodyHandler<T> handler) {
        RetryPolicy retryPolicy = config.getRetryPolicy();
        int attempt = 0;
        Duration backoff = retryPolicy.getInitialBackoff();
        while (true) {
            attempt++;
            HttpRequest request = requestSupplier.get();
            try {
                HttpResponse<T> response = httpClient.send(request, handler);
                if (response.statusCode() == 401) {
                    // force token refresh
                    tokenProvider.invalidate();
                    tokenProvider.getAccessToken();
                }
                if (response.statusCode() >= 400) {
                    if (attempt >= retryPolicy.getMaxAttempts()) {
                        return response;
                    }
                    try {
                        handleRateLimits(response.statusCode(), extractBody(response.body()));
                    } catch (RateLimitException exception) {
                        log.log(Level.WARNING, "Rate limit encountered on attempt {0}: {1}",
                                new Object[]{attempt, exception.getMessage()});
                    }
                    sleep(backoff);
                    backoff = nextBackoff(backoff, retryPolicy);
                    continue;
                }
                return response;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt >= retryPolicy.getMaxAttempts()) {
                    throw new RateLimitException("Gigachat request failed after retries", e);
                }
                log.log(Level.WARNING, "Gigachat call failed on attempt {0}: {1}", new Object[]{attempt, e.getMessage()});
                sleep(backoff);
                backoff = nextBackoff(backoff, retryPolicy);
            }
        }
    }

    private void sleep(Duration backoff) {
        try {
            long jitter = ThreadLocalRandom.current().nextLong(100, 300);
            Thread.sleep(backoff.toMillis() + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RateLimitException("Retry interrupted", e);
        }
    }

    private Duration nextBackoff(Duration current, RetryPolicy policy) {
        long next = (long) (current.toMillis() * policy.getMultiplier());
        if (next > policy.getMaxBackoff().toMillis()) {
            next = policy.getMaxBackoff().toMillis();
        }
        return Duration.ofMillis(next);
    }

    private String extractBody(Object body) {
        if (body instanceof String text) {
            return text;
        }
        if (body instanceof InputStream stream) {
            try (InputStream in = stream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.log(Level.FINE, "Unable to read stream body", e);
                return "";
            }
        }
        return String.valueOf(body);
    }

    private String parseMessage(String body) {
        String choiceContent = JsonUtils.extractChoiceContent(body);
        if (choiceContent != null && !choiceContent.isBlank()) {
            return choiceContent;
        }
        String message = JsonUtils.findString(body, "message");
        return message != null ? message : body;
    }

    private String stripStreamPrefix(String rawLine) {
        String line = rawLine.trim();
        if (line.startsWith("data:")) {
            line = line.substring(5).trim();
        }
        if ("[DONE]".equalsIgnoreCase(line)) {
            return "";
        }
        String content = JsonUtils.extractStreamContent(line);
        if (content != null && !content.isEmpty()) {
            return content;
        }
        return line;
    }

    private static HttpClient createHttpClient(GigachatClientConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout());
        GigachatSslContextFactory.create(config).ifPresent(builder::sslContext);
        return builder.build();
    }
}
