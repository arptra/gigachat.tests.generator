package com.example.tests.orchestration.gigachat;

import com.example.tests.orchestration.reporting.TestFailureDetail;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sends structured repair requests to Gigachat and records the resulting conversation for follow-up iterations.
 */
public final class GigachatFixGateway {

    private final PromptClient client;
    private final GigachatFixRequestBuilder builder;

    public GigachatFixGateway(PromptClient client, GigachatFixRequestBuilder builder) {
        this.client = Objects.requireNonNull(client, "client");
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    public FixConversationSession startConversation(TestContextSnapshot context,
                                                    TestFailureDetail failure) {
        return startConversation(context, failure, false);
    }

    public FixConversationSession startConversation(TestContextSnapshot context,
                                                    TestFailureDetail failure,
                                                    boolean methodScopedPrompt) {
        return startConversation(context, List.of(failure), methodScopedPrompt);
    }

    public FixConversationSession startConversation(TestContextSnapshot context,
                                                    List<TestFailureDetail> failures) {
        return startConversation(context, failures, false);
    }

    public FixConversationSession startConversation(TestContextSnapshot context,
                                                    List<TestFailureDetail> failures,
                                                    boolean methodScopedPrompt) {
        String prompt = builder.buildFixPrompt(context, failures, methodScopedPrompt);
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
