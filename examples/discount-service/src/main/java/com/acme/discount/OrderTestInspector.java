package com.acme.discount;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.mockito.Mockito;

/**
 * Utility that inspects {@link Order} and describes the collaborators that
 * should be mocked when generating tests.
 */
public final class OrderTestInspector {

    private OrderTestInspector() {
    }

    /**
     * Analyses the provided method name and returns a list of fully qualified
     * dependency type names that should be mocked.
     *
     * @param methodName the Order method to inspect
     * @return ordered list of dependency type names
     */
    public static List<String> inspectDependenciesFor(String methodName) {
        Set<String> dependencies = new LinkedHashSet<>();
        Class<Order> clazz = Order.class;

        addFieldDependencies(clazz, dependencies);
        addConstructorDependencies(clazz, dependencies);

        if (methodName == null || methodName.isBlank()) {
            return new ArrayList<>(dependencies);
        }

        if ("<init>".equals(methodName) || Order.class.getSimpleName().equals(methodName)) {
            return new ArrayList<>(dependencies);
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                addType(dependencies, method.getGenericReturnType());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    addType(dependencies, parameterType);
                }
            }
        }

        dependencies.remove(clazz.getName());
        return new ArrayList<>(dependencies);
    }

    /**
     * Creates an example snippet that shows how to mock dependencies for the
     * requested method.
     *
     * @param methodName Order method that will be exercised
     * @return code sample containing Mockito-based mocks
     */
    public static String generateMockConstructor(String methodName) {
        List<String> dependencies = inspectDependenciesFor(methodName);
        StringBuilder builder = new StringBuilder();
        builder.append("// Example mock constructor for method ")
                .append(methodName == null ? "<unknown>" : methodName)
                .append('\n');

        for (String dependency : dependencies) {
            if (dependency.equals(Order.class.getName())) {
                continue;
            }
            String simpleName = simpleName(dependency);
            builder.append(dependency)
                    .append(' ')
                    .append(decapitalize(simpleName) + "Mock")
                    .append(" = Mockito.mock(")
                    .append(dependency)
                    .append(".class);\n");
        }

        builder.append("java.util.List<")
                .append(OrderLine.class.getName())
                .append("> orderLines = new java.util.ArrayList<>();\n");
        builder.append("orderLines.add(Mockito.mock(")
                .append(OrderLine.class.getName())
                .append(".class));\n");
        builder.append(Order.class.getName())
                .append(" order = new ")
                .append(Order.class.getName())
                .append("(\"order-123\", java.time.LocalDate.now(), orderLines);\n");
        builder.append("return order;\n");
        return builder.toString();
    }

    private static void addFieldDependencies(Class<?> clazz, Set<String> dependencies) {
        for (Field field : clazz.getDeclaredFields()) {
            addType(dependencies, field.getGenericType());
        }
    }

    private static void addConstructorDependencies(Class<?> clazz, Set<String> dependencies) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            for (Type parameterType : constructor.getGenericParameterTypes()) {
                addType(dependencies, parameterType);
            }
        }
    }

    private static void addType(Set<String> dependencies, Type type) {
        if (type == null) {
            return;
        }
        if (type instanceof Class<?> clazz) {
            addClass(dependencies, clazz);
        } else if (type instanceof ParameterizedType parameterizedType) {
            addType(dependencies, parameterizedType.getRawType());
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                addType(dependencies, argument);
            }
        } else if (type instanceof GenericArrayType arrayType) {
            addType(dependencies, arrayType.getGenericComponentType());
        }
    }

    private static void addClass(Set<String> dependencies, Class<?> clazz) {
        if (clazz == null || clazz.isPrimitive()) {
            return;
        }
        dependencies.add(clazz.getName());
        Class<?> componentType = clazz.getComponentType();
        if (componentType != null) {
            addClass(dependencies, componentType);
        }
    }

    private static String simpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private static String decapitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() == 1) {
            return value.toLowerCase();
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
