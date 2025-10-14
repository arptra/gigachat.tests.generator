package com.example.tests.orchestration.analysis;

/**
 * Describes how a dependency object was introduced within the analysed method.
 */
public enum DependencyOrigin {

    /**
     * Object was created and assigned to a local variable inside the method.
     */
    LOCAL_VARIABLE,

    /**
     * Object was instantiated inline as part of a constructor invocation.
     */
    CONSTRUCTOR_ARGUMENT,

    /**
     * Object was instantiated inline as part of a method invocation argument list.
     */
    METHOD_ARGUMENT,

    /**
     * Object was instantiated inline for immediate use as the target of a method call.
     */
    INLINE_TARGET,

    /**
     * Object was created within a return statement.
     */
    RETURN_VALUE,

    /**
     * Object was discovered but its exact introduction site could not be determined.
     */
    UNKNOWN
}
