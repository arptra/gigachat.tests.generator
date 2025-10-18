package com.example.tests.generator.introspection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Utility capable of introspecting classes, objects and methods from dependencies
 * and providing structured descriptions that can be embedded into prompts.
 */
public class DependencyIntrospector {

    private final Map<Class<?>, ClassIntrospectionResult> classCache = new ConcurrentHashMap<>();
    private final Map<MethodCacheKey, MethodDescription> methodCache = new ConcurrentHashMap<>();
    private final Map<ConstructorCacheKey, ConstructorDescription> constructorCache = new ConcurrentHashMap<>();

    /**
     * Inspects the runtime class of the provided object.
     */
    public ClassIntrospectionResult introspect(Object instance) {
        Objects.requireNonNull(instance, "instance");
        return introspect(instance.getClass());
    }

    /**
     * Inspects the provided class. Results are cached to avoid repeated expensive lookups.
     */
    public ClassIntrospectionResult introspect(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        return classCache.computeIfAbsent(clazz, this::createClassDescription);
    }

    /**
     * Inspects an individual method.
     */
    public MethodDescription introspect(Method method) {
        Objects.requireNonNull(method, "method");
        return methodCache.computeIfAbsent(MethodCacheKey.from(method), key -> createMethodDescription(method));
    }

    /**
     * Inspects an individual constructor.
     */
    public ConstructorDescription introspect(Constructor<?> constructor) {
        Objects.requireNonNull(constructor, "constructor");
        return constructorCache.computeIfAbsent(ConstructorCacheKey.from(constructor),
                key -> createConstructorDescription(constructor));
    }

    /**
     * Clears all cached results. Mostly useful in tests.
     */
    public void clearCache() {
        classCache.clear();
        methodCache.clear();
        constructorCache.clear();
    }

    private ClassIntrospectionResult createClassDescription(Class<?> clazz) {
        String packageName = clazz.getPackage() == null ? "" : clazz.getPackageName();
        String superClassName = clazz.getSuperclass() == null ? null : clazz.getSuperclass().getName();
        List<String> interfaceNames = Arrays.stream(clazz.getInterfaces())
                .map(Class::getName)
                .sorted()
                .toList();
        List<String> annotations = readAnnotations(clazz.getAnnotations());

        List<ConstructorDescription> constructors = Arrays.stream(clazz.getDeclaredConstructors())
                .map(this::introspect)
                .sorted((left, right) -> left.qualifiedSignature().compareTo(right.qualifiedSignature()))
                .toList();

        List<MethodDescription> methods = collectAllMethods(clazz).stream()
                .sorted((left, right) -> left.qualifiedSignature().compareTo(right.qualifiedSignature()))
                .toList();

        return new ClassIntrospectionResult(
                clazz.getName(),
                packageName,
                superClassName,
                interfaceNames,
                annotations,
                constructors,
                methods
        );
    }

    private Collection<MethodDescription> collectAllMethods(Class<?> clazz) {
        Map<String, MethodDescription> result = new ConcurrentHashMap<>();

        for (Method method : clazz.getDeclaredMethods()) {
            MethodDescription description = introspect(method);
            result.putIfAbsent(description.qualifiedSignature(), description);
        }

        for (Method method : clazz.getMethods()) {
            MethodDescription description = introspect(method);
            result.putIfAbsent(description.qualifiedSignature(), description);
        }

        // Explore interfaces explicitly to catch non-public defaults.
        Deque<Class<?>> queue = new ArrayDeque<>(List.of(clazz));
        Set<Class<?>> visited = ConcurrentHashMap.newKeySet();
        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (Class<?> iface : current.getInterfaces()) {
                queue.addLast(iface);
                for (Method method : iface.getDeclaredMethods()) {
                    MethodDescription description = introspect(method);
                    result.putIfAbsent(description.qualifiedSignature(), description);
                }
            }
        }

        return result.values();
    }

    private MethodDescription createMethodDescription(Method method) {
        List<String> modifiers = modifiers(method.getModifiers());
        List<String> parameterTypes = Arrays.stream(method.getGenericParameterTypes())
                .map(type -> type.getTypeName())
                .toList();
        List<String> parameterNames = readParameterNames(method.getParameters());
        List<String> exceptionTypes = Arrays.stream(method.getExceptionTypes())
                .map(Class::getName)
                .toList();
        List<String> annotations = readAnnotations(method.getAnnotations());
        return new MethodDescription(
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getGenericReturnType().getTypeName(),
                modifiers,
                parameterTypes,
                parameterNames,
                exceptionTypes,
                annotations,
                method.isDefault(),
                method.isVarArgs()
        );
    }

    private ConstructorDescription createConstructorDescription(Constructor<?> constructor) {
        List<String> modifiers = modifiers(constructor.getModifiers());
        List<String> parameterTypes = Arrays.stream(constructor.getGenericParameterTypes())
                .map(type -> type.getTypeName())
                .toList();
        List<String> parameterNames = readParameterNames(constructor.getParameters());
        List<String> exceptionTypes = Arrays.stream(constructor.getExceptionTypes())
                .map(Class::getName)
                .toList();
        List<String> annotations = readAnnotations(constructor.getAnnotations());
        return new ConstructorDescription(
                constructor.getDeclaringClass().getName(),
                constructor.getDeclaringClass().getSimpleName(),
                modifiers,
                parameterTypes,
                parameterNames,
                exceptionTypes,
                annotations,
                constructor.isVarArgs()
        );
    }

    private List<String> readParameterNames(Parameter[] parameters) {
        List<String> names = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String fallback = "arg" + i;
            names.add(parameter.isNamePresent() ? parameter.getName() : fallback);
        }
        return names;
    }

    private List<String> readAnnotations(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .map(annotation -> annotation.annotationType().getName())
                .sorted()
                .toList();
    }

    private List<String> modifiers(int modifiers) {
        String text = Modifier.toString(modifiers);
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split(" "))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private record MethodCacheKey(String declaringClass, String name, List<Class<?>> parameterTypes) {
        static MethodCacheKey from(Method method) {
            return new MethodCacheKey(
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    List.of(method.getParameterTypes())
            );
        }
    }

    private record ConstructorCacheKey(String declaringClass, List<Class<?>> parameterTypes) {
        static ConstructorCacheKey from(Constructor<?> constructor) {
            return new ConstructorCacheKey(
                    constructor.getDeclaringClass().getName(),
                    List.of(constructor.getParameterTypes())
            );
        }
    }
}
