package com.example.tests.generator.metadata;

import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts scanner metadata into richer prompt metadata used by the LLM agent.
 */
public class MetadataTransformer {

    private final Map<String, ClassMetadata> byQualifiedName;
    private final Map<String, List<ClassMetadata>> bySimpleName;

    public MetadataTransformer(List<ClassMetadata> discovered) {
        Objects.requireNonNull(discovered, "discovered");
        this.byQualifiedName = discovered.stream()
                .collect(Collectors.toMap(ClassMetadata::getQualifiedName, metadata -> metadata, (left, right) -> left, LinkedHashMap::new));
        this.bySimpleName = discovered.stream()
                .collect(Collectors.groupingBy(metadata -> extractSimpleName(metadata.getClassName()),
                        LinkedHashMap::new, Collectors.toList()));
    }

    public com.example.tests.generator.metadata.ClassMetadata transform(ClassMetadata source) {
        com.example.tests.generator.metadata.ClassMetadata.Builder builder = com.example.tests.generator.metadata.ClassMetadata.builder()
                .packageName(source.getPackageName())
                .className(source.getClassName())
                .description(source.getDescription())
                .kind(source.getKind())
                .abstractType(source.isAbstractType());

        source.getDependencies().forEach(builder::addDependency);
        source.getEnumConstants().forEach(builder::addEnumConstant);

        for (MethodMetadata method : source.getMethods()) {
            builder.addMethod(toPromptMethod(method, source.getClassName()));
        }

        resolveSupportingTypes(source).stream()
                .map(this::toRelatedType)
                .forEach(builder::addSupportingType);

        return builder.build();
    }

    public Optional<ClassMetadata> findRawMetadata(String fullyQualifiedName) {
        if (fullyQualifiedName == null || fullyQualifiedName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byQualifiedName.get(fullyQualifiedName));
    }

    public Optional<ClassMetadata> resolveRawMetadata(String token, String currentPackage) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        ClassMetadata direct = byQualifiedName.get(token);
        if (direct != null) {
            return Optional.of(direct);
        }
        String simpleName;
        int lastDot = token.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < token.length() - 1) {
            simpleName = token.substring(lastDot + 1);
        } else {
            simpleName = token;
        }
        if (simpleName.isBlank()) {
            return Optional.empty();
        }
        List<ClassMetadata> candidates = bySimpleName.get(simpleName);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        if (currentPackage != null && !currentPackage.isBlank()) {
            for (ClassMetadata candidate : candidates) {
                if (candidate.getPackageName().equals(currentPackage)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.of(candidates.get(0));
    }

    private com.example.tests.generator.metadata.MethodMetadata toPromptMethod(MethodMetadata method, String ownerSimpleName) {
        com.example.tests.generator.metadata.MethodMetadata.Builder builder = com.example.tests.generator.metadata.MethodMetadata.builder()
                .name(method.getName())
                .returnType(method.isConstructor() ? ownerSimpleName : method.getReturnType())
                .description(method.getDescription())
                .staticMethod(method.isStatic())
                .constructor(method.isConstructor());

        method.getParameters().forEach(parameter -> builder.addParameter(
                com.example.tests.generator.metadata.ParameterMetadata.builder()
                        .type(parameter.getType())
                        .name(parameter.getName())
                        .build()));
        return builder.build();
    }

    private RelatedTypeMetadata toRelatedType(ClassMetadata metadata) {
        RelatedTypeMetadata.Builder builder = RelatedTypeMetadata.builder()
                .packageName(metadata.getPackageName())
                .className(metadata.getClassName())
                .qualifiedName(metadata.getQualifiedName())
                .kind(metadata.getKind())
                .abstractType(metadata.isAbstractType());
        metadata.getEnumConstants().forEach(builder::addEnumConstant);
        for (MethodMetadata method : metadata.getMethods()) {
            builder.addMethod(toPromptMethod(method, metadata.getClassName()));
        }
        return builder.build();
    }

    private List<ClassMetadata> resolveSupportingTypes(ClassMetadata source) {
        Set<String> qualifiedNames = new LinkedHashSet<>();
        source.getDependencies().stream()
                .map(this::lookupByImport)
                .filter(Objects::nonNull)
                .map(ClassMetadata::getQualifiedName)
                .forEach(qualifiedNames::add);

        for (MethodMetadata method : source.getMethods()) {
            collectTypeReferences(method.getReturnType(), source.getPackageName(), qualifiedNames);
            method.getParameters().forEach(parameter -> collectTypeReferences(parameter.getType(), source.getPackageName(), qualifiedNames));
        }

        qualifiedNames.remove(source.getQualifiedName());

        return qualifiedNames.stream()
                .map(byQualifiedName::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void collectTypeReferences(String signature, String currentPackage, Set<String> accumulator) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        for (String token : extractTypeCandidates(signature)) {
            ClassMetadata match = lookupType(token, currentPackage);
            if (match != null) {
                accumulator.add(match.getQualifiedName());
            }
        }
    }

    private ClassMetadata lookupByImport(String importName) {
        if (importName == null || importName.isBlank()) {
            return null;
        }
        return byQualifiedName.get(importName);
    }

    private ClassMetadata lookupType(String token, String currentPackage) {
        if (token == null || token.isBlank()) {
            return null;
        }
        ClassMetadata direct = byQualifiedName.get(token);
        if (direct != null) {
            return direct;
        }
        int lastDot = token.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? token.substring(lastDot + 1) : token;
        if (!currentPackage.isEmpty()) {
            ClassMetadata samePackage = byQualifiedName.get(currentPackage + '.' + simpleName);
            if (samePackage != null) {
                return samePackage;
            }
        }
        List<ClassMetadata> candidates = bySimpleName.get(simpleName);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        for (ClassMetadata candidate : candidates) {
            if (candidate.getPackageName().equals(currentPackage)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private String extractSimpleName(String className) {
        if (className == null || className.isBlank()) {
            return className;
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0 || lastDot == className.length() - 1) {
            return className;
        }
        return className.substring(lastDot + 1);
    }

    private List<String> extractTypeCandidates(String signature) {
        String sanitized = signature.replace("...", "");
        String withoutArrays = sanitized.replace("[]", " ");
        String[] parts = withoutArrays.split("[\\s<>,()\\[\\]&?]+");
        List<String> candidates = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String trimmed = part.trim();
            int lastDot = trimmed.lastIndexOf('.');
            String simple = lastDot >= 0 ? trimmed.substring(lastDot + 1) : trimmed;
            if (simple.isEmpty()) {
                continue;
            }
            char first = simple.charAt(0);
            if (Character.isUpperCase(first)) {
                candidates.add(trimmed);
            }
        }
        return candidates;
    }
}
