package com.example.tests.generator.scanner;

import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;

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
            "node_modules",
            "test",
            "tests"
    );

    private final Path rootDirectory;
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<String> ignoredDirectories;
    private final JavaCompiler compiler;

    public ProjectScanner() {
        this(Paths.get("").toAbsolutePath());
    }

    public ProjectScanner(Path rootDirectory) {
        this(rootDirectory, null);
    }

    public ProjectScanner(Path rootDirectory, Set<String> ignoredDirectories) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory").toAbsolutePath().normalize();
        this.ignoredDirectories = normaliseIgnoredDirectories(ignoredDirectories);
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
                ClassMetadata.Builder builder = ClassMetadata.builder()
                        .packageName(packageName)
                        .className(classTree.getSimpleName().toString())
                        .sourcePath(file)
                        .description("");

                imports.forEach(builder::addImport);
                imports.forEach(builder::addDependency);

                classTree.getModifiers().getAnnotations().stream()
                        .map(annotation -> annotation.getAnnotationType().toString())
                        .forEach(builder::addAnnotation);

                extractMethods(classTree).forEach(builder::addMethod);

                metadataList.add(builder.build());
            }
        }
        return metadataList;
    }

    private List<MethodMetadata> extractMethods(ClassTree classTree) {
        List<MethodMetadata> methods = new ArrayList<>();
        for (Tree member : classTree.getMembers()) {
            if (member instanceof MethodTree methodTree) {
                boolean constructor = methodTree.getName().contentEquals("<init>");
                MethodMetadata.Builder builder = MethodMetadata.builder()
                        .name(constructor ? classTree.getSimpleName().toString() : methodTree.getName().toString())
                        .constructor(constructor)
                        .returnType(constructor || methodTree.getReturnType() == null
                                ? classTree.getSimpleName().toString()
                                : methodTree.getReturnType().toString())
                        .staticMethod(isStatic(methodTree.getModifiers()))
                        .description("");

                for (VariableTree parameter : methodTree.getParameters()) {
                    builder.addParameter(parameter.getType().toString(), parameter.getName().toString());
                }
                methodTree.getModifiers().getAnnotations().stream()
                        .map(annotation -> annotation.getAnnotationType().toString())
                        .forEach(builder::addAnnotation);
                methods.add(builder.build());
            }
        }
        return methods;
    }

    private boolean isStatic(ModifiersTree modifiers) {
        return modifiers.getFlags().contains(Modifier.STATIC);
    }

    /**
     * Clears the internal cache forcing subsequent scans to re-parse all files.
     */
    public void clearCache() {
        cache.clear();
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
