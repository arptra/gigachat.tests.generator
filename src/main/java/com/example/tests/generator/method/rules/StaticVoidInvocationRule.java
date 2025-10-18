package com.example.tests.generator.method.rules;

import com.example.tests.generator.method.LogicalCodeUnit;
import com.example.tests.generator.method.MethodAnalysisContext;
import com.example.tests.generator.method.MockSnippet;
import com.example.tests.generator.method.MockTemplateRepository;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects static void method invocations and produces Mockito static mock suggestions.
 */
public class StaticVoidInvocationRule implements MockRule {

    private static final Pattern STATIC_CALL = Pattern.compile(
            "^\\s*([\\w$.]+)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*;\\s*$"
    );

    @Override
    public Optional<MockSnippet> apply(LogicalCodeUnit unit,
                                       MethodAnalysisContext context,
                                       MockTemplateRepository templates) {
        Matcher matcher = STATIC_CALL.matcher(unit.source());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String owner = matcher.group(1).trim();
        if (isJavaUtilObjects(owner)) {
            return Optional.empty();
        }
        String method = matcher.group(2).trim();
        String arguments = matcher.group(3).trim();
        String simpleOwner = owner.contains(".") ? owner.substring(owner.lastIndexOf('.') + 1) : owner;
        String rendered = templates.render("staticVoidCall",
                simpleOwner,
                owner,
                method,
                arguments);
        return Optional.of(new MockSnippet(id(), unit.source(), rendered));
    }

    @Override
    public String id() {
        return "static-void-invocation";
    }

    private boolean isJavaUtilObjects(String owner) {
        if (owner == null || owner.isEmpty()) {
            return false;
        }
        return "Objects".equals(owner) || "java.util.Objects".equals(owner);
    }
}
