/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.quarkiverse.endive.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;

import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.runtime.Store;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.WasmModule;

@Path("/endive")
@ApplicationScoped
public class EndiveGoCelResource {

    @Inject
    @Named("go-cel")
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;
    ExportFunction malloc;
    ExportFunction free;
    ExportFunction evalPolicy;
    Memory memory;

    @PostConstruct
    public void init() throws IOException {
        WasmModule wasmModule = wasmQuarkusContext.getWasmModule();
        if (wasmModule == null) {
            throw new IllegalStateException("Wasm module " + wasmQuarkusContext.getName() + " not found!");
        }

        // Create WASI support
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        WasiOptions options = WasiOptions.builder()
                .withStdout(stdout)
                .withStderr(stderr)
                .build();

        WasiPreview1 wasi = WasiPreview1.builder()
                .withOptions(options)
                .build();

        Store store = new Store().addFunction(wasi.toHostFunctions());

        // WasmQuarkusContext provides Instance with MachineFactory dynamically
        instance = Instance.builder(wasmModule)
                .withMachineFactory(wasmQuarkusContext.getMachineFactory())
                .withImportValues(store.toImportValues())
                // Don't auto-run _start(), we'll call it manually
                .withStart(false)
                .build();

        // Get exported functions BEFORE calling _start
        malloc = instance.export("malloc");
        free = instance.export("free");
        evalPolicy = instance.export("evalPolicy");
        memory = instance.memory();

        // Initialize Go runtime by calling _start()
        // This runs main() which exits, but we catch the expected WasiExitException
        try {
            ExportFunction start = instance.export("_start");
            start.apply();
        } catch (run.endive.wasi.WasiExitException e) {
            // Expected - Go main() exits after completing
            if (e.exitCode() != 0) {
                throw new RuntimeException("Go runtime initialization failed with exit code: " + e.exitCode());
            }
            // Exit code 0 is success - runtime is now initialized and exported functions are ready
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("validate")
    public Response validate(
            @RestForm String manifestJson,
            @RestForm String celPolicy) {

        byte[] policyBytes = celPolicy.getBytes(StandardCharsets.UTF_8);
        byte[] inputBytes = manifestJson.getBytes(StandardCharsets.UTF_8);

        // Allocate memory for policy string
        int policyPtr = (int) malloc.apply(policyBytes.length)[0];
        if (policyPtr == 0) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to allocate memory for policy").build();
        }

        // Allocate memory for input JSON
        int inputPtr = (int) malloc.apply(inputBytes.length)[0];
        if (inputPtr == 0) {
            free.apply(policyPtr);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to allocate memory for input").build();
        }

        try {
            // Write policy and input to WASM memory
            memory.write(policyPtr, policyBytes);
            memory.write(inputPtr, inputBytes);

            // Call evalPolicy(policyPtr, policyLen, inputPtr, inputLen)
            long[] result = evalPolicy.apply(policyPtr, policyBytes.length, inputPtr, inputBytes.length);
            int returnCode = (int) result[0];

            // Interpret result
            String message;
            if (returnCode == 1) {
                message = "Policy ALLOWS the request";
            } else if (returnCode == 0) {
                message = "Policy DENIES the request";
            } else {
                // Negative values are errors
                String errorMsg = switch (returnCode) {
                    case -1 -> "JSON parse error";
                    case -2 -> "CEL environment creation error";
                    case -3 -> "CEL compilation error";
                    case -4 -> "CEL program creation error";
                    case -5 -> "CEL runtime error";
                    default -> "Unknown error: " + returnCode;
                };
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("CEL evaluation failed: " + errorMsg).build();
            }

            return Response.ok(returnCode + " - " + message).build();
        } finally {
            // Free allocated memory
            free.apply(policyPtr);
            free.apply(inputPtr);
        }
    }
}
