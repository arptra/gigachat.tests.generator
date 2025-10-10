package com.example.tests.generator.scanner;

import com.example.tests.generator.model.ClassKind;
import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;
import com.example.tests.generator.project.ProjectLayout;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service that scans the working directory for Java classes and collects metadata about them.
 */
public class ProjectScanner {

    private static final Logger LOGGER = Logger.getLogger(ProjectScanner.class.getName());

    private static final Set<String> DEFAULT_IGNORED_DIRECTORIES = Set.of(
            ".git",
            ".gradle",
            ".idea",
            "target",
            "build",
            "out",
            "bin",
            "classes",
            "generated",
            "generated-sources",
            "node_modules"
    );

    private final Path rootDirectory;
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<String> ignoredDirectories;
    private final List<Path> ignoredAbsoluteDirectories;
    private final JavaCompiler compiler;

    public ProjectScanner() {
        this(Paths.get("").toAbsolutePath());
    }

    public ProjectScanner(Path rootDirectory) {
        this(rootDirectory, null, null);
    }

    public ProjectScanner(Path rootDirectory, ProjectLayout layout) {
        this(rootDirectory, layout, null);
    }

    public ProjectScanner(Path rootDirectory, Set<String> ignoredDirectories) {
        this(rootDirectory, null, ignoredDirectories);
    }

    public ProjectScanner(Path rootDirectory, ProjectLayout layout, Set<String> ignoredDirectories) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory").toAbsolutePath().normalize();
        this.ignoredDirectories = normaliseIgnoredDirectories(ignoredDirectories);
        this.ignoredAbsoluteDirectories = determineIgnoredDirectories(layout);
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Java compiler is not available. Ensure a JDK is installed.");
        }
    }

    private Set<String> normaliseIgnoredDirectories(Set<String> directories) {
        LinkedHashSet<String> result = DEFAULT_IGNORED_DIRECTORIES.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (directories != null) {
            directories.stream()
                    .filter(Objects::nonNull)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(result::add);
        }

        return Collections.unmodifiableSet(result);
    }

    public List<ClassMetadata> scan() {
        List<ClassMetadata> discoveredClasses = new ArrayList<>();
        Set<Path> visitedFiles = new HashSet<>();

        try {
            Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (shouldSkipDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && isJavaFile(file)) {
                        Path normalisedFile = file.toAbsolutePath().normalize();
                        visitedFiles.add(normalisedFile);
                        discoveredClasses.addAll(parseJavaFile(normalisedFile));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan project directory " + rootDirectory, e);
        }

        cache.keySet().retainAll(visitedFiles);
        return discoveredClasses;
    }

    private boolean shouldSkipDirectory(Path dir) {
        if (dir == null || rootDirectory.equals(dir)) {
            return false;
        }
        Path absolute = dir.toAbsolutePath().normalize();
        for (Path ignored : ignoredAbsoluteDirectories) {
            if (absolute.startsWith(ignored)) {
                return true;
            }
        }
        Path relative;
        try {
            relative = rootDirectory.relativize(dir);
        } catch (IllegalArgumentException ex) {
            relative = dir;
        }
        for (Path segment : relative) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (ignoredDirectories.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJavaFile(Path file) {
        String fileName = file.getFileName() != null ? file.getFileName().toString() : "";
        return fileName.endsWith(".java");
    }

    private List<Path> determineIgnoredDirectories(ProjectLayout layout) {
        if (layout == null) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        addIfExists(result, layout.testSourceSet());
        return List.copyOf(result);
    }

    private void addIfExists(List<Path> target, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path candidate = rootDirectory.resolve(relativePath).toAbsolutePath().normalize();
        if (Files.exists(candidate)) {
            target.add(candidate);
        }
    }

    private List<ClassMetadata> parseJavaFile(Path file) {
        try {
            CacheEntry entry = cache.get(file);
            FileTime lastModified = Files.getLastModifiedTime(file);
            long size = Files.size(file);

            if (entry != null && entry.isSameVersion(lastModified, size)) {
                return entry.metadata();
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(file.toFile());
                JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, List.of("-proc:none"), null, sources);
                List<CompilationUnitTree> units = new ArrayList<>();
                for (CompilationUnitTree unit : task.parse()) {
                    units.add(unit);
                }
                List<String> errors = diagnostics.getDiagnostics().stream()
                        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                        .map(diagnostic -> String.format(Locale.ENGLISH, "%s:%d %s",
                                diagnostic.getSource() == null ? file : diagnostic.getSource().getName(),
                                diagnostic.getLineNumber(), diagnostic.getMessage(Locale.ENGLISH)))
                        .collect(Collectors.toList());
                if (!errors.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Failed to parse {0}: {1}", new Object[]{file, errors});
                    cache.remove(file);
                    return Collections.emptyList();
                }
                List<ClassMetadata> metadata = extractMetadata(file, units);
                cache.put(file, new CacheEntry(lastModified, size, metadata));
                return metadata;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read Java source file {0}: {1}", new Object[]{file, e.getMessage()});
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to parse Java source file " + file + ':', e);
        }
        return Collections.emptyList();
    }

    private List<ClassMetadata> extractMetadata(Path file, List<CompilationUnitTree> units) {
        List<ClassMetadata> metadataList = new ArrayList<>();
        for (CompilationUnitTree unit : units) {
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            Set<String> imports = unit.getImports().stream()
                    .map(ImportTree::getQualifiedIdentifier)
                    .map(expression -> expression.toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (Tree typeDeclaration : unit.getTypeDecls()) {
                if (!(typeDeclaration instanceof ClassTree classTree)) {
                    continue;
                }
                ClassKind kind = determineKind(classTree);
                boolean abstractType = classTree.getModifiers().getFlags().contains(Modifier.ABSTRACT);
                ClassMetadata.Builder builder = ClassMetadata.builder()
                        .packageName(packageName)
                        .className(classTree.getSimpleName().toString())
                        .sourcePath(file)
                        .description("")
                        .kind(kind)
                        .abstractType(abstractType);

                imports.forEach(builder::addImport);
                imports.forEach(builder::addDependency);

                classTree.getModifiers().getAnnotations().stream()
                        .map(annotation -> annotation.getAnnotationType().toString())
                        .forEach(builder::addAnnotation);

                List<RecordComponentInfo> recordComponents = kind.isRecord()
                        ? extractRecordComponents(classTree)
                        : List.of();

                extractMethods(classTree, recordComponents).forEach(builder::addMethod);

                if (!recordComponents.isEmpty()) {
                    recordComponents.stream()
                            .map(this::toRecordComponentAccessor)
                            .forEach(builder::addMethod);
                }

                if (kind.isEnum()) {
                    classTree.getMembers().stream()
                            .filter(this::isEnumConstant)
                            .map(this::enumConstantName)
                            .filter(Objects::nonNull)
                            .forEach(builder::addEnumConstant);
                }

                metadataList.add(builder.build());
            }
        }
        return metadataList;
    }

    private ClassKind determineKind(ClassTree classTree) {
        return switch (classTree.getKind()) {
            case INTERFACE -> ClassKind.INTERFACE;
            case ENUM -> ClassKind.ENUM;
            case RECORD -> ClassKind.RECORD;
            default -> ClassKind.CLASS;
        };
    }

    private List<MethodMetadata> extractMethods(ClassTree classTree, List<RecordComponentInfo> recordComponents) {
        List<MethodMetadata> methods = new ArrayList<>();
        for (Tree member : classTree.getMembers()) {
            if (member instanceof MethodTree methodTree) {
                if (isPrivate(methodTree.getModifiers())) {
                    continue;
                }
                boolean constructor = methodTree.getName().contentEquals("<init>");
                MethodMetadata.Builder builder = MethodMetadata.builder()
                        .name(constructor ? classTree.getSimpleName().toString() : methodTree.getName().toString())
                        .constructor(constructor)
                        .returnType(constructor || methodTree.getReturnType() == null
                                ? classTree.getSimpleName().toString()
                                : methodTree.getReturnType().toString())
                        .staticMethod(isStatic(methodTree.getModifiers()))
                        .description("");

                List<? extends VariableTree> parameters = methodTree.getParameters();
                if (constructor && parameters.isEmpty() && !recordComponents.isEmpty()) {
                    for (RecordComponentInfo component : recordComponents) {
                        builder.addParameter(component.type(), component.name());
                    }
                } else {
                    for (VariableTree parameter : parameters) {
                        builder.addParameter(parameter.getType().toString(), parameter.getName().toString());
                    }
                }
                methodTree.getModifiers().getAnnotations().stream()
                        .map(annotation -> annotation.getAnnotationType().toString())
                        .forEach(builder::addAnnotation);
                methods.add(builder.build());
            }
        }
        return methods;
    }

    private MethodMetadata toRecordComponentAccessor(RecordComponentInfo component) {
        return MethodMetadata.builder()
                .name(component.name())
                .returnType(component.type())
                .description("Record component accessor")
                .constructor(false)
                .staticMethod(false)
                .build();
    }

    private List<RecordComponentInfo> extractRecordComponents(ClassTree classTree) {
        List<RecordComponentInfo> components = new ArrayList<>();
        for (Tree member : classTree.getMembers()) {
            if (!isRecordComponent(member)) {
                continue;
            }
            String name = invokeToString(member, "getName");
            String type = invokeToString(member, "getType");
            if (name == null || type == null) {
                continue;
            }
            components.add(new RecordComponentInfo(name, type));
        }
        return components;
    }

    private boolean isRecordComponent(Tree member) {
        if (member == null) {
            return false;
        }
        try {
            return member.getKind().name().equals("RECORD_COMPONENT");
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
    }

    private String invokeToString(Tree member, String method) {
        try {
            Object value = member.getClass().getMethod(method).invoke(member);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean isEnumConstant(Tree member) {
        if (member == null) {
            return false;
        }
        if (member instanceof VariableTree variable) {
            try {
                if (variable.getModifiers().getFlags().contains(Modifier.ENUM)) {
                    return true;
                }
            } catch (UnsupportedOperationException ignored) {
                // fall through to the generic check below
            }
        }
        try {
            return member.getKind().name().equals("ENUM_CONSTANT");
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
    }

    private String enumConstantName(Tree member) {
        return invokeToString(member, "getName");
    }

    private boolean isStatic(ModifiersTree modifiers) {
        return modifiers.getFlags().contains(Modifier.STATIC);
    }

    private boolean isPrivate(ModifiersTree modifiers) {
        return modifiers.getFlags().contains(Modifier.PRIVATE);
    }

    /**
     * Clears the internal cache forcing subsequent scans to re-parse all files.
     */
    public void clearCache() {
        cache.clear();
    }

    private record RecordComponentInfo(String name, String type) {
    }

    private static final class CacheEntry {
        private final FileTime lastModified;
        private final long size;
        private final List<ClassMetadata> metadata;

        private CacheEntry(FileTime lastModified, long size, List<ClassMetadata> metadata) {
            this.lastModified = lastModified;
            this.size = size;
            this.metadata = List.copyOf(metadata);
        }

        private boolean isSameVersion(FileTime currentLastModified, long currentSize) {
            return this.lastModified != null
                    && this.lastModified.equals(currentLastModified)
                    && this.size == currentSize;
        }

        private List<ClassMetadata> metadata() {
            return metadata;
        }
    }
}
