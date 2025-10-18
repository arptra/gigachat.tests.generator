package com.example.tests.generator.introspection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyIntrospectorTest {

    private final DependencyIntrospector introspector = new DependencyIntrospector();

    @AfterEach
    void tearDown() {
        introspector.clearCache();
    }

    @Test
    void cachesClassDescriptions() {
        ClassIntrospectionResult first = introspector.introspect(SampleComponent.class);
        ClassIntrospectionResult second = introspector.introspect(SampleComponent.class);

        assertThat(second).isSameAs(first);

        SampleComponent component = new SampleComponent();
        ClassIntrospectionResult fromObject = introspector.introspect(component);
        assertThat(fromObject).isSameAs(first);
    }

    @Test
    void cachesMethodAndConstructorDescriptions() throws Exception {
        Method joinMethod = SampleComponent.class.getMethod("join", String.class);
        MethodDescription methodFirst = introspector.introspect(joinMethod);
        MethodDescription methodSecond = introspector.introspect(joinMethod);
        assertThat(methodSecond).isSameAs(methodFirst);

        Constructor<SampleComponent> ctor = SampleComponent.class.getConstructor(int.class);
        ConstructorDescription ctorFirst = introspector.introspect(ctor);
        ConstructorDescription ctorSecond = introspector.introspect(ctor);
        assertThat(ctorSecond).isSameAs(ctorFirst);
    }

    @Test
    void collectsInformationFromJdkClass() {
        ClassIntrospectionResult result = introspector.introspect(LocalDate.class);

        assertThat(result.className()).isEqualTo(LocalDate.class.getName());
        assertThat(result.packageName()).isEqualTo("java.time");
        assertThat(result.methods())
                .as("LocalDate should contain parse factory method")
                .anyMatch(method -> method.name().equals("parse")
                        && method.returnType().equals(LocalDate.class.getName()));
    }

    @Test
    void collectsInheritedMethodsFromDependencies() {
        ClassIntrospectionResult result = introspector.introspect(SampleComponent.class);

        assertThat(result.methods())
                .anyMatch(method -> method.name().equals("add")
                        && method.declaringClass().equals(ArrayList.class.getName()));

        assertThat(result.methods())
                .anyMatch(method -> method.name().equals("join")
                        && method.declaringClass().equals(SampleComponent.class.getName())
                        && method.signature().contains("java.lang.String delimiter"));
    }

    @Test
    void methodIntrospectionReturnsDetailedSignature() throws Exception {
        Method method = List.class.getMethod("stream");
        MethodDescription description = introspector.introspect(method);

        assertThat(description.returnType()).isEqualTo("java.util.stream.Stream<E>");
        assertThat(description.signature()).contains("Stream<E> stream()");
    }

    @Test
    void formattedOutputContainsConstructorsAndMethods() {
        ClassIntrospectionResult result = introspector.introspect(SampleComponent.class);
        String formatted = result.formatAsText();

        assertThat(formatted).contains("Class: " + SampleComponent.class.getName());
        assertThat(formatted).contains("Constructors:");
        assertThat(formatted).contains("Methods:");
        assertThat(formatted).contains("join(java.lang.String delimiter)");
    }

    static class SampleComponent extends ArrayList<String> {

        SampleComponent() {
            super();
        }

        SampleComponent(int initialCapacity) {
            super(initialCapacity);
        }

        public String join(String delimiter) {
            return String.join(delimiter, this);
        }

        @Deprecated
        public void legacyMethod() {
            // no-op
        }
    }
}
