package com.example.tests.generator.method;

import com.example.tests.generator.method.rules.MockRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates method analysis, rule execution and optional validation.
 */
public class MethodMockGenerator {

    private final TargetClassAnalyzer analyzer;
    private final List<MockRule> rules;
    private final MockTemplateRepository templates;
    private final MethodMockValidationService validationService;

    public MethodMockGenerator(TargetClassAnalyzer analyzer,
                               List<MockRule> rules,
                               MockTemplateRepository templates,
                               MethodMockValidationService validationService) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.rules = List.copyOf(rules);
        this.templates = Objects.requireNonNull(templates, "templates");
        this.validationService = validationService;
    }

    public List<MethodMockPlan> generate(String sourceCode, boolean validateWithGigachat) {
        List<MethodAnalysis> analyses = analyzer.analyze(sourceCode);
        if (analyses.isEmpty()) {
            return List.of();
        }
        List<MethodMockPlan> plans = new ArrayList<>();
        for (MethodAnalysis analysis : analyses) {
            MethodAnalysisContext context = new MethodAnalysisContext(analysis.className(), analysis.methodName());
            List<MockSnippet> snippets = new ArrayList<>();
            for (LogicalCodeUnit unit : analysis.codeUnits()) {
                for (MockRule rule : rules) {
                    Optional<MockSnippet> snippet = rule.apply(unit, context, templates);
                    if (snippet.isPresent()) {
                        snippets.add(snippet.get());
                        break;
                    }
                }
            }
            Optional<String> feedback = Optional.empty();
            if (validateWithGigachat && validationService != null) {
                feedback = validationService.validate(analysis, Collections.unmodifiableList(snippets));
            }
            plans.add(new MethodMockPlan(analysis.className(),
                    analysis.methodName(),
                    analysis.methodSource(),
                    snippets,
                    feedback));
        }
        return List.copyOf(plans);
    }
}
