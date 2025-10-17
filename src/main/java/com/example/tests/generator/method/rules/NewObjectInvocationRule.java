package com.example.tests.generator.method.rules;

import com.example.tests.generator.method.LogicalCodeUnit;
import com.example.tests.generator.method.MethodAnalysisContext;
import com.example.tests.generator.method.MockSnippet;
import com.example.tests.generator.method.MockTemplateRepository;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects new object creation followed by an immediate method call.
 */
public class NewObjectInvocationRule implements MockRule {

    private static final Pattern NEW_OBJECT_CALL = Pattern.compile(
            "^\\s*(?:return\\s+)?new\\s+([\\w$.]+)\\s*\\((.*?)\\)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*?)\\)\\s*;\\s*$",
            Pattern.DOTALL
    );

    @Override
    public Optional<MockSnippet> apply(LogicalCodeUnit unit,
                                       MethodAnalysisContext context,
                                       MockTemplateRepository templates) {
        Matcher matcher = NEW_OBJECT_CALL.matcher(unit.source());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String type = matcher.group(1).trim();
        String method = matcher.group(3).trim();
        String args = matcher.group(4).trim();
        String simpleType = type.contains(".") ? type.substring(type.lastIndexOf('.') + 1) : type;
        String variable = suggestVariableName(simpleType);
        String rendered = templates.render("newObjectInvocation",
                simpleType,
                variable,
                method,
                args,
                "/* TODO: configure return value */");
        return Optional.of(new MockSnippet(id(), unit.source(), rendered));
    }

    private String suggestVariableName(String simpleType) {
        if (simpleType.isEmpty()) {
            return "mock";
        }
        StringBuilder builder = new StringBuilder(simpleType.length());
        builder.append(Character.toLowerCase(simpleType.charAt(0)));
        for (int i = 1; i < simpleType.length(); i++) {
            char ch = simpleType.charAt(i);
            if (Character.isUpperCase(ch)) {
                builder.append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        String candidate = builder.toString();
        if (candidate.isBlank()) {
            return "mock";
        }
        return candidate;
    }

    @Override
    public String id() {
        return "new-object-invocation";
    }
}
