package com.example.tests.generator.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardLibraryTypeResolverTest {

    @Test
    void resolvesSimpleAlias() {
        assertEquals("java.util.HashSet", StandardLibraryTypeResolver.resolve("HashSet").orElseThrow());
    }

    @Test
    void resolvesKnownEnumMapAlias() {
        assertEquals("java.util.EnumMap", StandardLibraryTypeResolver.resolve("EnumMap").orElseThrow());
    }

    @Test
    void exposesAliasesAsUnmodifiableMap() {
        Map<String, String> aliases = StandardLibraryTypeResolver.aliases();
        assertEquals("java.util.List", aliases.get("List"));
        assertTrue(aliases.containsKey("Collectors"));
    }

    @Test
    void recognisesStandardQualifiedName() {
        assertTrue(StandardLibraryTypeResolver.isStandardLibrary("java.util.List"));
    }
}
