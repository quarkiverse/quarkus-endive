package io.quarkiverse.endive.runtime.wasm;

import org.jboss.logging.Logger;

import io.quarkiverse.endive.runtime.WasmQuarkusConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * A {@link Recorder} that creates configured {@link WasmQuarkusContext} instances at runtime.
 */
@Recorder
public class WasmQuarkusContextRecorder {

    private static final Logger LOG = Logger.getLogger(WasmQuarkusContextRecorder.class);

    /**
     * Creates a {@link WasmQuarkusContext} instance based on a configured Wasm module, and returns it as a
     * runtime value.
     *
     * @param key The configuration key of a given Wasm module
     * @param config The application configuration, storing all the configured Wasm modules.
     * @return A {@link RuntimeValue} referencing the configured {@link WasmQuarkusContext}.
     */
    public RuntimeValue<?> createContext(final String key, final WasmQuarkusConfig config, final boolean isNativePackageType,
            final String projectBaseDir) {
        WasmQuarkusConfig.ModuleConfig moduleConfig = config.modules().get(key);
        LOG.info("A configured Wasm module " + key + " will be created");
        WasmQuarkusContext wasmQuarkusContext = new WasmQuarkusContext(key, moduleConfig, isNativePackageType, projectBaseDir);
        return new RuntimeValue<>(wasmQuarkusContext);
    }
}
