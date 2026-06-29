package io.quarkiverse.endive.test.devmode;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import run.endive.runtime.Instance;
import run.endive.wasm.WasmModule;

@Path("/test/math")
@ApplicationScoped
public class MathResource {

    @Inject
    @Named("math-module")
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;

    @PostConstruct
    public void init() throws IOException {
        WasmModule wasmModule = wasmQuarkusContext.getWasmModule();
        if (wasmModule == null) {
            throw new IllegalStateException("Wasm module not found!");
        }
        instance = Instance.builder(wasmModule)
                .withMachineFactory(wasmQuarkusContext.getMachineFactory())
                .build();
    }

    @GET
    @Path("/add")
    public Response add() {
        var result = instance.export("operation").apply(10, 5);
        return Response.ok(result[0]).build();
    }
}
