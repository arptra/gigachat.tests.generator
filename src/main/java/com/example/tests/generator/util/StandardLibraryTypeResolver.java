package com.example.tests.generator.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility that resolves common simple type names used in prompts into their
 * canonical Java standard-library counterparts. This keeps the mapping in one
 * place so both prompt builders and verifiers can stay in sync when deciding
 * which imports should be generated automatically.
 */
public final class StandardLibraryTypeResolver {

    private static final Map<String, String> SIMPLE_NAME_ALIASES = buildAliases();

    private StandardLibraryTypeResolver() {
    }

    /**
     * Attempts to resolve a simple type name into a fully qualified standard
     * library type. The lookup is case-sensitive and ignores trailing array
     * markers.
     */
    public static Optional<String> resolve(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Optional.empty();
        }
        String candidate = normalise(simpleName);
        if (candidate.contains(".")) {
            return isStandardLibrary(candidate) ? Optional.of(candidate) : Optional.empty();
        }
        String resolved = SIMPLE_NAME_ALIASES.get(candidate);
        return resolved == null ? Optional.empty() : Optional.of(resolved);
    }

    /**
     * Returns an immutable view of the known simple-name aliases.
     */
    public static Map<String, String> aliases() {
        return SIMPLE_NAME_ALIASES;
    }

    /**
     * Determines whether the provided qualified name belongs to the Java
     * standard library.
     */
    public static boolean isStandardLibrary(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return false;
        }
        String normalised = qualifiedName.trim();
        return normalised.startsWith("java.")
                || normalised.startsWith("javax.")
                || normalised.startsWith("jakarta.");
    }

    private static String normalise(String simpleName) {
        String trimmed = simpleName.trim();
        while (trimmed.endsWith("[]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        if (trimmed.endsWith("...")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        register(aliases, "ArrayDeque", "java.util.ArrayDeque");
        register(aliases, "ArrayList", "java.util.ArrayList");
        register(aliases, "Arrays", "java.util.Arrays");
        register(aliases, "BigDecimal", "java.math.BigDecimal");
        register(aliases, "BigInteger", "java.math.BigInteger");
        register(aliases, "Collection", "java.util.Collection");
        register(aliases, "Collections", "java.util.Collections");
        register(aliases, "Comparator", "java.util.Comparator");
        register(aliases, "Deque", "java.util.Deque");
        register(aliases, "EnumMap", "java.util.EnumMap");
        register(aliases, "EnumSet", "java.util.EnumSet");
        register(aliases, "HashMap", "java.util.HashMap");
        register(aliases, "HashSet", "java.util.HashSet");
        register(aliases, "Instant", "java.time.Instant");
        register(aliases, "LinkedHashMap", "java.util.LinkedHashMap");
        register(aliases, "LinkedHashSet", "java.util.LinkedHashSet");
        register(aliases, "LinkedList", "java.util.LinkedList");
        register(aliases, "List", "java.util.List");
        register(aliases, "LocalDate", "java.time.LocalDate");
        register(aliases, "LocalDateTime", "java.time.LocalDateTime");
        register(aliases, "LocalTime", "java.time.LocalTime");
        register(aliases, "Map", "java.util.Map");
        register(aliases, "Map.Entry", "java.util.Map.Entry");
        register(aliases, "Month", "java.time.Month");
        register(aliases, "Objects", "java.util.Objects");
        register(aliases, "Optional", "java.util.Optional");
        register(aliases, "Queue", "java.util.Queue");
        register(aliases, "Set", "java.util.Set");
        register(aliases, "Stream", "java.util.stream.Stream");
        register(aliases, "Collectors", "java.util.stream.Collectors");
        register(aliases, "Supplier", "java.util.function.Supplier");
        register(aliases, "Function", "java.util.function.Function");
        register(aliases, "Consumer", "java.util.function.Consumer");
        register(aliases, "Predicate", "java.util.function.Predicate");
        register(aliases, "Duration", "java.time.Duration");
        register(aliases, "UUID", "java.util.UUID");
        register(aliases, "TreeMap", "java.util.TreeMap");
        register(aliases, "TreeSet", "java.util.TreeSet");
        register(aliases, "NavigableMap", "java.util.NavigableMap");
        register(aliases, "NavigableSet", "java.util.NavigableSet");
        register(aliases, "SortedMap", "java.util.SortedMap");
        register(aliases, "SortedSet", "java.util.SortedSet");
        register(aliases, "AtomicInteger", "java.util.concurrent.atomic.AtomicInteger");
        register(aliases, "AtomicLong", "java.util.concurrent.atomic.AtomicLong");
        register(aliases, "ConcurrentHashMap", "java.util.concurrent.ConcurrentHashMap");
        register(aliases, "CopyOnWriteArrayList", "java.util.concurrent.CopyOnWriteArrayList");
        register(aliases, "CopyOnWriteArraySet", "java.util.concurrent.CopyOnWriteArraySet");
        return Collections.unmodifiableMap(aliases);
    }

    private static void register(Map<String, String> aliases, String simple, String qualified) {
        Objects.requireNonNull(simple, "simple");
        Objects.requireNonNull(qualified, "qualified");
        aliases.putIfAbsent(simple, qualified);
    }
}
