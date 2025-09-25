package com.example.tests.generator.project;

import java.util.Objects;

/**
 * Describes the resolved source set layout of a target project.
 */
public record ProjectLayout(String mainSourceSet, String testSourceSet) {

    public ProjectLayout {
        Objects.requireNonNull(mainSourceSet, "mainSourceSet");
        Objects.requireNonNull(testSourceSet, "testSourceSet");
    }
}
