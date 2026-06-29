package io.quarkiverse.endive.runtime.wasm;

/**
 * Defines how WASM module code can be executed by Endive.
 */
public enum ExecutionMode {
    /**
     * Uses Endive's runtime compiler to compile WASM bytecode to JVM bytecode at runtime.
     * Provides better performance than the interpreter but requires compilation overhead.
     * This is the default mode for production JVM builds.
     */
    RuntimeCompiler,

    /**
     * Uses Endive's interpreter to execute WASM bytecode directly without compilation.
     * Lower performance than the runtime compiler but works in all environments including
     * native image builds where runtime compilation is not available.
     */
    Interpreter;
}
