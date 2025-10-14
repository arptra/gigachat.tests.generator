package com.example.tests.generator.cli;

import com.example.tests.generator.model.ClassMetadata;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGeneratorCliTargetMatchingTest {

    private Method matchesTarget;
    private TestGeneratorCli cli;

    @BeforeEach
    void setUp() throws Exception {
        matchesTarget = TestGeneratorCli.class.getDeclaredMethod("matchesTarget",
                ClassMetadata.class, List.class);
        matchesTarget.setAccessible(true);
        cli = new TestGeneratorCli();
    }

    @Test
    void matchesOnlyExactFullyQualifiedNamesWhenPackageProvided() throws Exception {
        ClassMetadata expected = metadata("mtd.baks", "LIB", "src/main/java/mtd/baks/LIB.java");
        ClassMetadata otherPackage = metadata("mtd.other", "LIB", "src/main/java/mtd/other/LIB.java");

        boolean matched = invokeMatches(expected, List.of("mtd.baks.LIB"));
        boolean notMatched = invokeMatches(otherPackage, List.of("mtd.baks.LIB"));

        assertTrue(matched, "Expected fully-qualified target to match class in the same package");
        assertFalse(notMatched, "Classes in other packages must not match the fully-qualified target");
    }

    @Test
    void stillMatchesSimpleClassNames() throws Exception {
        ClassMetadata metadata = metadata("mtd.baks", "LIB", "src/main/java/mtd/baks/LIB.java");

        boolean matched = invokeMatches(metadata, List.of("LIB"));

        assertTrue(matched, "Simple class name targets should continue to work");
    }

    private boolean invokeMatches(ClassMetadata metadata, List<String> targets) throws Exception {
        return (boolean) matchesTarget.invoke(cli, metadata, targets);
    }

    private ClassMetadata metadata(String packageName, String className, String path) {
        return ClassMetadata.builder()
                .packageName(packageName)
                .className(className)
                .sourcePath(Path.of(path))
                .build();
    }
}
