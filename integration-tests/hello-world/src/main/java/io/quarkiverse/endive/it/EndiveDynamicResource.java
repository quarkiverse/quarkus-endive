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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import io.quarkiverse.endive.runtime.wasm.ExecutionMode;
import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import io.quarkus.logging.Log;
import run.endive.runtime.Instance;
import run.endive.wasm.Parser;

@Path("/endive/dynamic")
@ApplicationScoped
public class EndiveDynamicResource {

    private static final String WASM_MODULE_KEY_OPERATION_DYNAMIC = "operation-dynamic";

    @Inject
    @Named(WASM_MODULE_KEY_OPERATION_DYNAMIC)
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;

    @GET
    public Response hello() {
        if (instance == null) {
            return Response.status(Response.Status.METHOD_NOT_ALLOWED)
                    .entity("Instance not yet initialized. Use \"/dynamic/upload\" to upload a Wasm module and initialize an instance")
                    .build();
        }
        var result = instance.export("operation").apply(41, 1);
        return Response.ok("Hello endive (dynamic): " + result[0]).build();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@RestForm("module") FileUpload wasmModule,
            @RestForm("execution-mode") ExecutionMode executionMode) throws IOException {
        try (final InputStream is = Files.newInputStream(wasmModule.uploadedFile())) {
            if (is.available() <= 0) {
                throw new IllegalArgumentException("ERROR: Wasm module NOT uploaded 0");
            }
            Log.info("Wasm module uploaded, execution mode is " + executionMode);
            instance = Instance.builder(Parser.parse(is.readAllBytes()))
                    .withMachineFactory(wasmQuarkusContext.getMachineFactory())
                    .build();
            return Response.accepted(wasmQuarkusContext).build();
        }
    }
}
