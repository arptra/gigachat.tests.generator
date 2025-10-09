package com.example.tests.orchestration.gigachat;

import com.example.agent.providers.LLMClient;
import com.example.tests.orchestration.reporting.TestFailureDetail;

import java.util.Map;
import java.util.Objects;

/**
 * Sends structured repair requests to Gigachat and records the resulting conversation for follow-up iterations.
 */
public final class GigachatFixGateway {

    private final LLMClient client;
    private final GigachatFixRequestBuilder builder;

    public GigachatFixGateway(LLMClient client, GigachatFixRequestBuilder builder) {
        this.client = Objects.requireNonNull(client, "client");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    public FixConversationSession startConversation(TestContextSnapshot context, TestFailureDetail failure) {
        String prompt = builder.buildFixPrompt(context, failure);
        FixConversationSession session = new FixConversationSession(prompt);
        String response = client.sendPrompt(prompt, Map.of());
        session.recordModelResponse(response);
        return session;
    }

    public String sendFollowUp(FixConversationSession session, String followUpPrompt) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(followUpPrompt, "followUpPrompt");
        String response = client.sendPrompt(followUpPrompt, Map.of());
        session.recordModelResponse(response);
        return response;
    }
}
