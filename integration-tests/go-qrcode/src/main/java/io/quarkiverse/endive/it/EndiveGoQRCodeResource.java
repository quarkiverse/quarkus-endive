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
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.runtime.Store;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.WasmModule;

@Path("/endive/qrcode")
@ApplicationScoped
public class EndiveGoQRCodeResource {

    // We use the quarkus-endive Wasm context to dynamically provide a MachineFactory instance
    @Inject
    @Named("qrcode")
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;
    ExportFunction malloc;
    ExportFunction free;
    ExportFunction generateQR;
    Memory memory;

    @PostConstruct
    public void init() throws IOException {
        WasmModule wasmModule = wasmQuarkusContext.getWasmModule();
        if (wasmModule == null) {
            throw new IllegalStateException("Wasm module " + wasmQuarkusContext.getName() + " not found!");
        }

        // STDOUT and STDERR streams to be used by WasiOptions
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        WasiOptions options = WasiOptions.builder()
                .withStdout(stdout)
                .withStderr(stderr)
                .build();

        // Create WASI with options
        WasiPreview1 wasi = WasiPreview1.builder()
                .withOptions(options)
                .build();

        // Store manages WASI host functions
        Store store = new Store().addFunction(wasi.toHostFunctions());

        // Instance.builder combines both Store imports AND MachineFactory
        instance = Instance.builder(wasmModule)
                .withMachineFactory(wasmQuarkusContext.getMachineFactory())
                .withImportValues(store.toImportValues())
                .build();

        // Get exported functions
        malloc = instance.export("malloc");
        free = instance.export("free");
        generateQR = instance.export("generateQR");
        memory = instance.memory();

    }

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response generate(@QueryParam("text") @DefaultValue("Hello Endive QR Code!") String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

        // Allocate memory for input text
        int textPtr = (int) malloc.apply(textBytes.length)[0];
        if (textPtr == 0) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to allocate memory for input text").build();
        }

        // Allocate memory for output size (4 bytes for int)
        int sizePtr = (int) malloc.apply(4)[0];
        if (sizePtr == 0) {
            free.apply(textPtr);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to allocate memory for output size").build();
        }

        try {
            // Write input text to WASM memory
            memory.write(textPtr, textBytes);

            // Call generateQR(textPtr, textLen, sizePtr)
            long[] result = generateQR.apply(textPtr, textBytes.length, sizePtr);
            int qrPtr = (int) result[0];

            if (qrPtr == 0) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Failed to generate QR code").build();
            }

            // Read the output size
            byte[] sizeBytes = memory.readBytes(sizePtr, 4);
            int size = java.nio.ByteBuffer.wrap(sizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();

            // Read the PNG data
            byte[] pngData = memory.readBytes(qrPtr, size);

            // *Note*: We do not free qrPtr because it was allocated by Go's make(), not malloc,
            // therefore we rely on Go's GC to handle it.
            // Calling free() on Go-allocated memory causes a "trap".
            return Response.ok(pngData)
                    .header("Content-Type", "image/png")
                    .header("Content-Disposition", "inline; filename=\"qrcode.png\"")
                    .build();
        } finally {
            // Free our allocated memory
            free.apply(textPtr);
            free.apply(sizePtr);
        }
    }
}
