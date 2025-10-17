package com.example.tests.generator.method;

import com.example.agent.providers.LLMClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * Delegates mock validation to Gigachat when requested by the user.
 */
public class GigachatMethodMockValidationService implements MethodMockValidationService {

    private final LLMClient llmClient;
    private final Supplier<Map<String, Object>> optionsSupplier;

    public GigachatMethodMockValidationService(LLMClient llmClient,
                                               Supplier<Map<String, Object>> optionsSupplier) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.optionsSupplier = Objects.requireNonNull(optionsSupplier, "optionsSupplier");
    }

    @Override
    public Optional<String> validate(MethodAnalysis analysis, List<MockSnippet> snippets) {
        if (snippets.isEmpty()) {
            return Optional.empty();
        }
        String prompt = buildPrompt(analysis, snippets);
        String response = llmClient.sendPrompt(prompt, optionsSupplier.get());
        return Optional.ofNullable(response);
    }

    private String buildPrompt(MethodAnalysis analysis, List<MockSnippet> snippets) {
        StringJoiner joiner = new StringJoiner("\n\n");
        joiner.add("Проанализируй предложенные мок объекты для тестового метода и подтверди что они корректны." +
                " Если есть проблемы, перечисли их.");
        joiner.add("Класс: " + analysis.className());
        joiner.add("Метод:\n" + analysis.methodSource());
        StringBuilder mocksBuilder = new StringBuilder("Моки:\n");
        for (MockSnippet snippet : snippets) {
            mocksBuilder.append("Правило: ").append(snippet.ruleId()).append('\n');
            mocksBuilder.append("Оригинал: ").append(snippet.originalCode()).append('\n');
            mocksBuilder.append("Шаблон:\n").append(snippet.mockCode()).append('\n');
        }
        joiner.add(mocksBuilder.toString());
        return joiner.toString();
    }
}
