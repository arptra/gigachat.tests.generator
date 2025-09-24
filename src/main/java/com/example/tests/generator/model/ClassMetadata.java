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
public class ClassMetadata {

    private final String packageName;
    private final String className;
    private final Path sourcePath;
    private final List<String> annotations;
    private final List<MethodMetadata> methods;
    private final Set<String> imports;
    private final Set<String> dependencies;

    public ClassMetadata(String packageName,
                         String className,
                         Path sourcePath,
                         List<String> annotations,
                         List<MethodMetadata> methods,
                         Set<String> imports,
                         Set<String> dependencies) {
        this.packageName = packageName == null ? "" : packageName;
        this.className = Objects.requireNonNull(className, "className");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.annotations = Collections.unmodifiableList(new ArrayList<>(annotations == null ? List.of() : annotations));
        this.methods = Collections.unmodifiableList(new ArrayList<>(methods == null ? List.of() : methods));
        this.imports = Collections.unmodifiableSet(new LinkedHashSet<>(imports == null ? Set.of() : imports));
        this.dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies == null ? Set.of() : dependencies));
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

    public String getQualifiedName() {
        if (packageName == null || packageName.isBlank()) {
            return className;
        }
        return packageName + '.' + className;
    }

    @Override
    public String toString() {
        return "ClassMetadata{" +
                "packageName='" + packageName + '\'' +
                ", className='" + className + '\'' +
                ", sourcePath=" + sourcePath +
                ", methods=" + methods.size() +
                ", imports=" + imports.size() +
                ", dependencies=" + dependencies.size() +
                '}';
    }
}
