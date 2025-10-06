package com.example.tests.generator.metadata;

import com.example.tests.generator.model.ClassKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes supporting domain types that the tests may need to instantiate or mock.
 */
public final class RelatedTypeMetadata {

    private final String packageName;
    private final String className;
    private final String qualifiedName;
    private final ClassKind kind;
    private final boolean abstractType;
    private final List<MethodMetadata> methods;
    private final List<String> enumConstants;

    private RelatedTypeMetadata(Builder builder) {
        this.packageName = builder.packageName == null ? "" : builder.packageName;
        this.className = Objects.requireNonNull(builder.className, "className");
        this.qualifiedName = builder.qualifiedName == null
                ? (this.packageName.isEmpty() ? this.className : this.packageName + '.' + this.className)
                : builder.qualifiedName;
        this.kind = builder.kind == null ? ClassKind.CLASS : builder.kind;
        this.abstractType = builder.abstractType;
        this.methods = Collections.unmodifiableList(new ArrayList<>(builder.methods));
        this.enumConstants = Collections.unmodifiableList(new ArrayList<>(builder.enumConstants));
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public ClassKind getKind() {
        return kind;
    }

    public boolean isAbstractType() {
        return abstractType;
    }

    public boolean isEnumType() {
        return kind != null && kind.isEnum();
    }

    public List<MethodMetadata> getMethods() {
        return methods;
    }

    public List<String> getEnumConstants() {
        return enumConstants;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String packageName;
        private String className;
        private String qualifiedName;
        private ClassKind kind;
        private boolean abstractType;
        private final List<MethodMetadata> methods = new ArrayList<>();
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

        public Builder qualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
            return this;
        }

        public Builder kind(ClassKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder abstractType(boolean abstractType) {
            this.abstractType = abstractType;
            return this;
        }

        public Builder addMethod(MethodMetadata method) {
            this.methods.add(Objects.requireNonNull(method, "method"));
            return this;
        }

        public Builder addEnumConstant(String constant) {
            this.enumConstants.add(Objects.requireNonNull(constant, "constant"));
            return this;
        }

        public RelatedTypeMetadata build() {
            return new RelatedTypeMetadata(this);
        }
    }
}
