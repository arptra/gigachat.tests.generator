package com.example.tests.generator.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Describes a Java class discovered by the {@code ProjectScanner}.
 */
public final class ClassMetadata {

    private final String packageName;
    private final String className;
    private final Path sourcePath;
    private final String description;
    private final List<String> annotations;
    private final List<MethodMetadata> methods;
    private final Set<String> imports;
    private final Set<String> dependencies;
    private final ClassKind kind;
    private final boolean abstractType;
    private final List<String> enumConstants;

    private ClassMetadata(Builder builder) {
        this.packageName = builder.packageName == null ? "" : builder.packageName;
        this.className = Objects.requireNonNull(builder.className, "className");
        this.sourcePath = Objects.requireNonNull(builder.sourcePath, "sourcePath");
        this.description = builder.description == null ? "" : builder.description;
        this.annotations = Collections.unmodifiableList(new ArrayList<>(builder.annotations));
        this.methods = Collections.unmodifiableList(new ArrayList<>(builder.methods));
        this.imports = Collections.unmodifiableSet(new LinkedHashSet<>(builder.imports));
        this.dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dependencies));
        this.kind = builder.kind;
        this.abstractType = builder.abstractType;
        this.enumConstants = Collections.unmodifiableList(new ArrayList<>(builder.enumConstants));
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public List<MethodMetadata> getMethods() {
        return methods;
    }

    public Set<String> getImports() {
        return imports;
    }

    public Set<String> getDependencies() {
        return dependencies;
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

    public boolean isRecord() {
        return kind != null && kind.isRecord();
    }

    public boolean isAbstractType() {
        return abstractType;
    }

    public List<String> getEnumConstants() {
        return enumConstants;
    }

    public String getQualifiedName() {
        if (packageName == null || packageName.isBlank()) {
            return className;
        }
        return packageName + '.' + className;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String packageName;
        private String className;
        private Path sourcePath;
        private String description;
        private final List<String> annotations = new ArrayList<>();
        private final List<MethodMetadata> methods = new ArrayList<>();
        private final Set<String> imports = new LinkedHashSet<>();
        private final Set<String> dependencies = new LinkedHashSet<>();
        private ClassKind kind = ClassKind.CLASS;
        private boolean abstractType;
        private final List<String> enumConstants = new ArrayList<>();

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

        public Builder sourcePath(Path sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addAnnotation(String annotation) {
            if (annotation != null && !annotation.isBlank()) {
                this.annotations.add(annotation);
            }
            return this;
        }

        public Builder addMethod(MethodMetadata method) {
            this.methods.add(Objects.requireNonNull(method, "method"));
            return this;
        }

        public Builder addImport(String importName) {
            if (importName != null && !importName.isBlank()) {
                this.imports.add(importName);
            }
            return this;
        }

        public Builder addDependency(String dependency) {
            if (dependency != null && !dependency.isBlank()) {
                this.dependencies.add(dependency);
            }
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

        public Builder addEnumConstant(String constant) {
            if (constant != null && !constant.isBlank()) {
                this.enumConstants.add(constant);
            }
            return this;
        }

        public ClassMetadata build() {
            return new ClassMetadata(this);
        }
    }
}
