package com.example.tests.generator.metadata;

import com.example.tests.generator.method.MethodMockPlan;
import com.example.tests.generator.method.MockSnippet;
import com.example.tests.generator.model.ClassKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Describes the production class that needs tests.
 */
public final class ClassMetadata {

    private final String packageName;
    private final String className;
    private final String description;
    private final List<MethodMetadata> methods;
    private final List<String> dependencies;
    private final CoverageRequirements coverageRequirements;
    private final List<String> mockingRestrictions;
    private final List<String> exampleScenarios;
    private final ClassKind kind;
    private final boolean abstractType;
    private final List<String> enumConstants;
    private final List<RelatedTypeMetadata> supportingTypes;
    private final Map<String, List<String>> dependencyMethods;
    private final Map<String, List<String>> supportingTypeMembers;
    private final List<String> targetMethods;
    private final boolean dependencyFocusEnabled;
    private final Map<String, List<String>> methodMockPlans;

    private ClassMetadata(Builder builder) {
        this.packageName = Objects.requireNonNull(builder.packageName, "packageName");
        this.className = Objects.requireNonNull(builder.className, "className");
        this.description = builder.description == null ? "" : builder.description;
        this.methods = Collections.unmodifiableList(new ArrayList<>(builder.methods));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
        this.coverageRequirements = builder.coverageRequirements;
        this.mockingRestrictions = Collections.unmodifiableList(new ArrayList<>(builder.mockingRestrictions));
        this.exampleScenarios = Collections.unmodifiableList(new ArrayList<>(builder.exampleScenarios));
        this.kind = builder.kind;
        this.abstractType = builder.abstractType;
        this.enumConstants = Collections.unmodifiableList(new ArrayList<>(builder.enumConstants));
        this.supportingTypes = Collections.unmodifiableList(new ArrayList<>(builder.supportingTypes));
        this.dependencyMethods = immutableCopy(builder.dependencyMethods);
        this.supportingTypeMembers = immutableCopy(builder.supportingTypeMembers);
        this.targetMethods = List.copyOf(new ArrayList<>(builder.targetMethods));
        this.dependencyFocusEnabled = builder.dependencyFocusEnabled;
        this.methodMockPlans = immutableCopy(builder.methodMockPlans);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getDescription() {
        return description;
    }

    public List<MethodMetadata> getMethods() {
        return methods;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public CoverageRequirements getCoverageRequirements() {
        return coverageRequirements;
    }

    public List<String> getMockingRestrictions() {
        return mockingRestrictions;
    }

    public List<String> getExampleScenarios() {
        return exampleScenarios;
    }

    public ClassKind getKind() {
        return kind;
    }

    public boolean isEnumType() {
        return kind != null && kind.isEnum();
    }

    public boolean isInterface() {
        return kind != null && kind.isInterface();
    }

    public boolean isAbstractType() {
        return abstractType;
    }

    public List<String> getEnumConstants() {
        return enumConstants;
    }

    public List<RelatedTypeMetadata> getSupportingTypes() {
        return supportingTypes;
    }

    public Map<String, List<String>> getDependencyMethods() {
        return dependencyMethods;
    }

    public Map<String, List<String>> getSupportingTypeMembers() {
        return supportingTypeMembers;
    }

    public Map<String, List<String>> getMethodMockPlans() {
        return methodMockPlans;
    }

    public List<String> getTargetMethods() {
        return targetMethods;
    }

    public boolean isDependencyFocusEnabled() {
        return dependencyFocusEnabled;
    }

    public String getFullyQualifiedName() {
        return packageName + "." + className;
    }

    public ClassMetadata withDependencyDocumentation(Map<String, List<String>> dependencyMethods,
                                                     Map<String, List<String>> supportingTypeMembers,
                                                     List<String> targetMethods,
                                                     boolean dependencyFocusEnabled) {
        return new ClassMetadata(this, dependencyMethods, supportingTypeMembers, targetMethods,
                dependencyFocusEnabled, methodMockPlans);
    }

    public ClassMetadata withMethodMockPlans(List<MethodMockPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return this;
        }
        Map<String, List<String>> mergedMockPlans = new LinkedHashMap<>(this.methodMockPlans);
        for (MethodMockPlan plan : plans) {
            if (plan == null || plan.isEmpty()) {
                continue;
            }
            List<String> suggestions = new ArrayList<>();
            for (MockSnippet snippet : plan.snippets()) {
                if (snippet == null) {
                    continue;
                }
                String formatted = String.format("Rule %s:%n  Original: %s%n  Mock:%n%s",
                        snippet.ruleId(),
                        snippet.originalCode(),
                        indent(snippet.mockCode()));
                suggestions.add(formatted);
            }
            if (!suggestions.isEmpty()) {
                mergedMockPlans.merge(plan.methodName(), suggestions, (left, right) -> {
                    List<String> combined = new ArrayList<>(left);
                    LinkedHashSet<String> seen = new LinkedHashSet<>(left);
                    for (String entry : right) {
                        if (seen.add(entry)) {
                            combined.add(entry);
                        }
                    }
                    return combined;
                });
            }
        }
        if (mergedMockPlans.equals(this.methodMockPlans)) {
            return this;
        }
        return new ClassMetadata(this, this.dependencyMethods, this.supportingTypeMembers, this.targetMethods,
                this.dependencyFocusEnabled, mergedMockPlans);
    }

    public static Builder builder() {
        return new Builder();
    }

    private ClassMetadata(ClassMetadata source,
                          Map<String, List<String>> dependencyMethods,
                          Map<String, List<String>> supportingTypeMembers,
                          List<String> targetMethods,
                          boolean dependencyFocusEnabled,
                          Map<String, List<String>> methodMockPlans) {
        this.packageName = source.packageName;
        this.className = source.className;
        this.description = source.description;
        this.methods = source.methods;
        this.dependencies = source.dependencies;
        this.coverageRequirements = source.coverageRequirements;
        this.mockingRestrictions = source.mockingRestrictions;
        this.exampleScenarios = source.exampleScenarios;
        this.kind = source.kind;
        this.abstractType = source.abstractType;
        this.enumConstants = source.enumConstants;
        this.supportingTypes = source.supportingTypes;
        this.dependencyMethods = immutableCopy(dependencyMethods);
        this.supportingTypeMembers = immutableCopy(supportingTypeMembers);
        this.targetMethods = List.copyOf(new ArrayList<>(targetMethods));
        this.dependencyFocusEnabled = dependencyFocusEnabled;
        this.methodMockPlans = immutableCopy(methodMockPlans);
    }

    private static Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "source");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            List<String> members = value == null ? List.of() : List.copyOf(new ArrayList<>(value));
            copy.put(key, members);
        });
        return Map.copyOf(copy);
    }

    private static String indent(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text.trim();
        }
        String normalized = text.replace("\r\n", "\n");
        return normalized.lines()
                .map(line -> "    " + line)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public static final class Builder {
        private String packageName;
        private String className;
        private String description;
        private final List<MethodMetadata> methods = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();
        private CoverageRequirements coverageRequirements;
        private final List<String> mockingRestrictions = new ArrayList<>();
        private final List<String> exampleScenarios = new ArrayList<>();
        private ClassKind kind = ClassKind.CLASS;
        private boolean abstractType;
        private final List<String> enumConstants = new ArrayList<>();
        private final List<RelatedTypeMetadata> supportingTypes = new ArrayList<>();
        private final Map<String, List<String>> dependencyMethods = new LinkedHashMap<>();
        private final Map<String, List<String>> supportingTypeMembers = new LinkedHashMap<>();
        private final List<String> targetMethods = new ArrayList<>();
        private boolean dependencyFocusEnabled;
        private final Map<String, List<String>> methodMockPlans = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addMethod(MethodMetadata method) {
            this.methods.add(Objects.requireNonNull(method, "method"));
            return this;
        }

        public Builder addDependency(String dependency) {
            this.dependencies.add(Objects.requireNonNull(dependency, "dependency"));
            return this;
        }

        public Builder kind(ClassKind kind) {
            this.kind = kind == null ? ClassKind.CLASS : kind;
            return this;
        }

        public Builder abstractType(boolean abstractType) {
            this.abstractType = abstractType;
            return this;
        }

        public Builder coverageRequirements(CoverageRequirements coverageRequirements) {
            this.coverageRequirements = coverageRequirements;
            return this;
        }

        public Builder addMockingRestriction(String restriction) {
            this.mockingRestrictions.add(Objects.requireNonNull(restriction, "restriction"));
            return this;
        }

        public Builder addExampleScenario(String scenario) {
            this.exampleScenarios.add(Objects.requireNonNull(scenario, "scenario"));
            return this;
        }

        public Builder addEnumConstant(String constant) {
            this.enumConstants.add(Objects.requireNonNull(constant, "constant"));
            return this;
        }

        public Builder addSupportingType(RelatedTypeMetadata supportingType) {
            this.supportingTypes.add(Objects.requireNonNull(supportingType, "supportingType"));
            return this;
        }

        public Builder addMethodMockPlan(String methodName, List<String> suggestions) {
            if (methodName == null || methodName.isBlank() || suggestions == null || suggestions.isEmpty()) {
                return this;
            }
            List<String> cleaned = new ArrayList<>();
            for (String suggestion : suggestions) {
                if (suggestion != null && !suggestion.isBlank()) {
                    cleaned.add(suggestion);
                }
            }
            if (!cleaned.isEmpty()) {
                this.methodMockPlans.put(methodName, List.copyOf(cleaned));
            }
            return this;
        }

        public Builder putDependencyMethods(String qualifiedName, List<String> methods) {
            if (qualifiedName != null && !qualifiedName.isBlank() && methods != null) {
                this.dependencyMethods.put(qualifiedName, new ArrayList<>(methods));
            }
            return this;
        }

        public Builder putSupportingTypeMembers(String qualifiedName, List<String> members) {
            if (qualifiedName != null && !qualifiedName.isBlank() && members != null) {
                this.supportingTypeMembers.put(qualifiedName, new ArrayList<>(members));
            }
            return this;
        }

        public Builder addTargetMethod(String method) {
            if (method != null && !method.isBlank()) {
                this.targetMethods.add(method);
            }
            return this;
        }

        public Builder dependencyFocusEnabled(boolean dependencyFocusEnabled) {
            this.dependencyFocusEnabled = dependencyFocusEnabled;
            return this;
        }

        public ClassMetadata build() {
            return new ClassMetadata(this);
        }
    }
}
