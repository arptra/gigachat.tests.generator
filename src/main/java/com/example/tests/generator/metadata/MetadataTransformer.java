package com.example.tests.generator.metadata;

import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;

/**
 * Converts scanner metadata into richer prompt metadata used by the LLM agent.
 */
public class MetadataTransformer {

    public com.example.tests.generator.metadata.ClassMetadata transform(ClassMetadata source) {
        com.example.tests.generator.metadata.ClassMetadata.Builder builder = com.example.tests.generator.metadata.ClassMetadata.builder()
                .packageName(source.getPackageName())
                .className(source.getClassName())
                .description(source.getDescription())
                .enumType(source.isEnumType());

        source.getDependencies().forEach(builder::addDependency);
        source.getEnumConstants().forEach(builder::addEnumConstant);

        for (MethodMetadata method : source.getMethods()) {
            com.example.tests.generator.metadata.MethodMetadata.Builder methodBuilder = com.example.tests.generator.metadata.MethodMetadata.builder()
                    .name(method.getName())
                    .returnType(method.isConstructor() ? source.getClassName() : method.getReturnType())
                    .description(method.getDescription())
                    .staticMethod(method.isStatic());

            method.getParameters().forEach(parameter -> methodBuilder.addParameter(
                    com.example.tests.generator.metadata.ParameterMetadata.builder()
                            .type(parameter.getType())
                            .name(parameter.getName())
                            .build()));

            builder.addMethod(methodBuilder.build());
        }

        return builder.build();
    }
}
