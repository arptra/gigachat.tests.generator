package com.example.agent.providers;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Base contract for invoking a Large Language Model provider.
 */
public interface LLMClient {

    /**
     * Sends a prompt to the LLM and returns the full textual response.
     *
     * @param prompt the message delivered to the model
     * @param options provider specific options (temperature, topP, etc.)
     * @return the textual answer returned by the provider
     */
    String sendPrompt(String prompt, Map<String, Object> options);

    /**
     * Sends a prompt and returns a lazy {@link Stream} of chunks representing
     * the streamed model output. The caller is responsible for closing the stream.
     *
     * @param prompt the prompt to execute
     * @param options provider specific options
     * @return stream of response chunks
     */
    Stream<String> streamResponses(String prompt, Map<String, Object> options);

    /**
     * Handles a rate limit situation and decides whether the call should be retried.
     * Implementations may throw a {@link RateLimitException} if the request cannot be retried.
     *
     * @param statusCode HTTP status code received from the provider
     * @param responseBody textual payload received from the provider
     */
    void handleRateLimits(int statusCode, String responseBody);
}
