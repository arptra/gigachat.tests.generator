package com.example.tests.generator.scanner;

import com.example.tests.generator.model.ClassMetadata;
import com.example.tests.generator.model.MethodMetadata;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Providers;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final JavaParser javaParser;
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<String> ignoredDirectories;

    public ProjectScanner() {
        this(Paths.get("").toAbsolutePath());
    }

    public ProjectScanner(Path rootDirectory) {
        this(rootDirectory, new JavaParser(), null);
    }

    public ProjectScanner(Path rootDirectory, JavaParser javaParser, Set<String> ignoredDirectories) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory").toAbsolutePath().normalize();
        this.javaParser = Objects.requireNonNull(javaParser, "javaParser");
        this.ignoredDirectories = normaliseIgnoredDirectories(ignoredDirectories);
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

    /**
     * Performs a recursive scan of the configured root directory, collecting metadata for Java classes.
     *
     * @return metadata for each discovered class
     */
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

            String content = Files.readString(file, StandardCharsets.UTF_8);
            String hash = computeHash(content);

            if (entry != null && entry.hasSameHash(hash)) {
                cache.put(file, entry.updated(lastModified, size));
                return entry.metadata();
            }

            ParseResult<CompilationUnit> result = javaParser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(content));
            if (result.getResult().isEmpty()) {
                logProblems(file, result);
                cache.remove(file);
                return Collections.emptyList();
            }

            CompilationUnit compilationUnit = result.getResult().get();
            List<ClassMetadata> metadata = extractMetadata(file, compilationUnit);
            cache.put(file, new CacheEntry(lastModified, size, hash, metadata));
            return metadata;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read Java source file {0}: {1}", new Object[]{file, e.getMessage()});
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to parse Java source file " + file + ':', e);
        }
        return Collections.emptyList();
    }

    private void logProblems(Path file, ParseResult<CompilationUnit> result) {
        if (result.getProblems().isEmpty()) {
            return;
        }
        for (Problem problem : result.getProblems()) {
            LOGGER.log(Level.WARNING, "Problem while parsing {0}: {1}", new Object[]{file, problem.getMessage()});
        }
    }

    private List<ClassMetadata> extractMetadata(Path file, CompilationUnit compilationUnit) {
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        Set<String> imports = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ClassMetadata> metadataList = new ArrayList<>();
        for (TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
            if (typeDeclaration.isAnnotationDeclaration()) {
                continue;
            }
            List<String> classAnnotations = typeDeclaration.getAnnotations().stream()
                    .map(annotation -> annotation.getName().asString())
                    .collect(Collectors.toCollection(ArrayList::new));
            List<MethodMetadata> methods = extractMethodMetadata(typeDeclaration);
            Set<String> dependencies = collectDependencies(typeDeclaration);

            metadataList.add(new ClassMetadata(
                    packageName,
                    typeDeclaration.getNameAsString(),
                    file,
                    classAnnotations,
                    methods,
                    imports,
                    dependencies
            ));
        }
        return metadataList;
    }

    private List<MethodMetadata> extractMethodMetadata(TypeDeclaration<?> typeDeclaration) {
        List<MethodMetadata> methods = new ArrayList<>();
        for (BodyDeclaration<?> member : typeDeclaration.getMembers()) {
            if (member instanceof MethodDeclaration methodDeclaration) {
                methods.add(new MethodMetadata(
                        methodDeclaration.getNameAsString(),
                        methodDeclaration.getDeclarationAsString(true, true, true),
                        false,
                        methodDeclaration.getAnnotations().stream()
                                .map(annotation -> annotation.getName().asString())
                                .collect(Collectors.toCollection(ArrayList::new))
                ));
            } else if (member instanceof ConstructorDeclaration constructorDeclaration) {
                methods.add(new MethodMetadata(
                        constructorDeclaration.getNameAsString(),
                        constructorDeclaration.getDeclarationAsString(true, true, true),
                        true,
                        constructorDeclaration.getAnnotations().stream()
                                .map(annotation -> annotation.getName().asString())
                                .collect(Collectors.toCollection(ArrayList::new))
                ));
            }
        }
        return methods;
    }

    private Set<String> collectDependencies(TypeDeclaration<?> typeDeclaration) {
        LinkedHashSet<String> dependencies = typeDeclaration.findAll(ClassOrInterfaceType.class).stream()
                .map(ClassOrInterfaceType::getNameWithScope)
                .map(name -> name.replace('$', '.'))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        dependencies.remove(typeDeclaration.getNameAsString());
        dependencies.removeIf(String::isBlank);
        return dependencies;
    }

    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
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
        private final String hash;
        private final List<ClassMetadata> metadata;

        private CacheEntry(FileTime lastModified, long size, String hash, List<ClassMetadata> metadata) {
            this.lastModified = lastModified;
            this.size = size;
            this.hash = hash;
            this.metadata = List.copyOf(metadata);
        }

        private boolean isSameVersion(FileTime currentLastModified, long currentSize) {
            return this.lastModified != null
                    && this.lastModified.equals(currentLastModified)
                    && this.size == currentSize;
        }

        private boolean hasSameHash(String otherHash) {
            return Objects.equals(this.hash, otherHash);
        }

        private CacheEntry updated(FileTime currentLastModified, long currentSize) {
            return new CacheEntry(currentLastModified, currentSize, this.hash, this.metadata);
        }

        private List<ClassMetadata> metadata() {
            return metadata;
        }
    }
}
