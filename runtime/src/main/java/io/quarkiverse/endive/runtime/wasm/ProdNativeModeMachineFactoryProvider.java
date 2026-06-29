package io.quarkiverse.endive.runtime.wasm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import io.quarkus.logging.Log;
import run.endive.compiler.MachineFactoryCompiler;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;
import run.endive.runtime.Machine;

/**
 * Discriminates between statically configured vs. dynamically loaded payload in PROD/RUN/NATIVE mode.
 */
public class ProdNativeModeMachineFactoryProvider implements Supplier<Function<Instance, Machine>> {
    private final boolean isDynamic;
    private final String machineName;
    private final ExecutionMode executionMode;

    public ProdNativeModeMachineFactoryProvider(final boolean isDynamic, final String machineName,
            final ExecutionMode executionMode) {
        this.isDynamic = isDynamic;
        this.machineName = machineName;
        this.executionMode = executionMode;
    }

    @Override
    public Function<Instance, Machine> get() {
        if (!isDynamic) {
            // PROD/NATIVE mode + Static Wasm payload means a Java API is generated at build time, and SHOULD
            // definitely be used - let's override the execution mode
            Log.info("  PROD/NATIVE mode enabled + static Wasm payload, build-time compiler will be used");
            return instance -> {
                // similar to the generated raw Java class "create()"
                final String machineClazzName = machineName + "Machine";
                try {
                    Class<?> machineClazz = Thread.currentThread().getContextClassLoader()
                            .loadClass(machineClazzName);
                    Class<?>[] parameterTypes = new Class<?>[] { Instance.class };
                    Constructor<?> constructor = machineClazz.getConstructor(parameterTypes);
                    Object[] arguments = new Object[] { instance };
                    return (Machine) constructor.newInstance(arguments);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Cannot load class: " + machineClazzName, e);
                } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                        | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            };
        } else {
            // PROD/NATIVE mode + Dynamic Wasm payload, generated Java API does not exist, use the
            // configured execution mode
            if (Objects.requireNonNull(executionMode) == ExecutionMode.RuntimeCompiler) {
                Log.info("  PROD/NATIVE mode enabled + dynamic Wasm payload, runtime compiler will be used");
                return MachineFactoryCompiler::compile;
            } else {
                Log.warn("  PROD/NATIVE mode enabled + dynamic Wasm payload, interpreter will be used");
                return InterpreterMachine::new;
            }
        }
    }
}
