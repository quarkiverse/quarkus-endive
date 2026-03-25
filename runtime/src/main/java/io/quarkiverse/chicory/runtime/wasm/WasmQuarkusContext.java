package io.quarkiverse.chicory.runtime.wasm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Function;

import org.jboss.logging.Logger;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Machine;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import io.quarkiverse.chicory.runtime.WasmQuarkusConfig;
import io.quarkiverse.chicory.runtime.WasmQuarkusUtils;
import io.quarkus.runtime.LaunchMode;

/**
 * The central API for accessing WebAssembly modules in Quarkus applications.
 * <p>
 * {@code WasmQuarkusContext} provides environment-aware access to WASM modules through CDI injection.
 * It supplies a {@link #getMachineFactory()} configured for the current execution environment
 * (dev, production, or native) and a {@link #getWasmModule()} parsed from the configured source.
 * <p>
 * Instances are created as CDI beans by the extension and can be injected using {@code @Named} qualifiers:
 *
 * <pre>
 * &#64;Inject
 * &#64;Named("my-module")
 * WasmQuarkusContext wasmContext;
 * </pre>
 */
public class WasmQuarkusContext {
    private static final Logger LOG = Logger.getLogger(WasmQuarkusContext.class);

    private final String name;
    private final ExecutionMode executionMode;
    private final WasmQuarkusConfig.ModuleConfig moduleConfig;
    private final boolean isNativePackageType;
    private final boolean isDynamic;
    private final String projectBaseDir;

    // Client code can't create
    WasmQuarkusContext(final String moduleKey, final WasmQuarkusConfig.ModuleConfig moduleConfig,
            final boolean isNativePackageType, final String projectBaseDir) {
        isDynamic = !(moduleConfig.wasmFile().isPresent() || moduleConfig.wasmResource().isPresent());
        // default to runtime compilation
        ExecutionMode actualExecutionMode = ExecutionMode.RuntimeCompiler;
        // dynamic vs. static payload configuration directly affects Wasm context execution mode, depending on
        // native image vs. JVM package type
        if (isDynamic) {
            // wasm payload is not configured, and is meant to be loaded dynamically, therefore only Interpreter
            // execution mode can be used in native package type execution (everything is compiled at build time)
            if (isNativePackageType) {
                LOG.warn("No payload is configured for Wasm module " + moduleKey +
                        ", and native image is being built. Execution mode will fall back to " + ExecutionMode.Interpreter);
                actualExecutionMode = ExecutionMode.Interpreter;
            } else {
                // ... otherwise fallback to the runtime compiler (default), as the payload is loaded dynamically (and
                // the user cannot set it)
                LOG.debug(
                        "No payload is configured for Wasm module " + moduleKey + ", execution mode is " + actualExecutionMode);
            }
        } else {
            // Wasm payload is configured statically, and execution mode as well both for native vs. JVM package
            // type
            actualExecutionMode = moduleConfig.compiler().executionMode();
            LOG.debug("Payload is configured for Wasm module " + moduleKey + ", execution mode is " + actualExecutionMode);
        }
        this.name = moduleConfig.name();
        this.executionMode = actualExecutionMode;
        this.moduleConfig = moduleConfig;
        this.isNativePackageType = isNativePackageType;
        this.projectBaseDir = projectBaseDir;
    }

    /**
     * Returns the fully qualified name of this WASM module.
     *
     * @return The module name as configured via {@code quarkus.chicory.modules.<module-key>.name}
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the execution mode for this WASM module.
     *
     * @return The {@link ExecutionMode} determined by configuration and runtime environment
     */
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    /**
     * Returns a {@link Function} that implements a {@link com.dylibso.chicory.compiler.internal.MachineFactory},
     * based on the configuration and runtime environment.
     *
     * @return A {@link Function} that implements a {@link com.dylibso.chicory.compiler.internal.MachineFactory}
     */
    public Function<Instance, Machine> getMachineFactory() {
        return (LaunchMode.current() == LaunchMode.NORMAL || LaunchMode.current() == LaunchMode.RUN)
                ? new ProdNativeModeMachineFactoryProvider(isDynamic, this.name, this.executionMode).get()
                : new DevTestModeMachineFactoryProvider(this.executionMode).get();
    }

    /**
     * Returns a {@link WasmModule} instance obtained by parsing the Wasm module payload or .meta file,
     * based on the configuration and runtime environment. The caller is responsible for the {@link WasmModule} instance
     * lifecycle management, as it is not stored by the implementation.
     * <p>
     * For dynamically loaded modules (where neither {@code wasm-file} nor {@code wasm-resource} is configured),
     * this method returns {@code null}.
     *
     * @return A {@link WasmModule} instance obtained by parsing the Wasm module payload or .meta file,
     *         or {@code null} for dynamic modules
     * @throws IOException if an error occurs while reading or parsing the WASM module
     */
    public WasmModule getWasmModule() throws IOException {
        // if neither wasm-file nor wasm-resource is configured it means the wasm payload will be provided dynamically
        if (isDynamic) {
            return null;
        }
        // ... otherwise either wasm-file or wasm-resource is defined, so let's use the Meta wasm in Native/PROD mode
        // because it is generated by the build time compiler based on the Wasm payload
        if (isNativePackageType || (LaunchMode.current() == LaunchMode.NORMAL || LaunchMode.current() == LaunchMode.RUN)) {
            try (InputStream is = WasmQuarkusUtils.getMetaWasmResourceStream(this.name)) {
                if (is == null) {
                    throw new IllegalStateException("Meta Wasm module resource for " + this.name + " not found");
                }
                return Parser.parse(is);
            }
        } else {
            // otherwise let's use the Wasm payload itself
            if (moduleConfig.wasmFile().isPresent()) {
                return Parser.parse(moduleConfig.wasmFileAbsolutePath(Path.of(projectBaseDir)));
            } else {
                return Parser.parse(WasmQuarkusUtils.getWasmPathFromResource(moduleConfig.wasmResource().get()));
            }
        }
    }
}
