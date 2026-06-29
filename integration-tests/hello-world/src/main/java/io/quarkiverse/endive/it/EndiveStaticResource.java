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

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import run.endive.runtime.Instance;
import run.endive.wasm.WasmModule;

@Path("/endive/static")
@ApplicationScoped
public class EndiveStaticResource {

    @Inject
    @Named("operation-static")
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;

    @PostConstruct
    public void init() throws IOException {
        WasmModule wasmModule = wasmQuarkusContext.getWasmModule();
        if (wasmModule == null) {
            throw new IllegalStateException("Wasm module " + wasmQuarkusContext.getName() + " not found!");
        }
        // The Wasm module is obtained by the name it was registered with,
        // therefore, we can rely on the injected named bean to obtain the Endive MachineFactory in @PostConstruct
        Instance.Builder builder = Instance.builder(wasmModule)
                .withMachineFactory(wasmQuarkusContext.getMachineFactory());
        instance = builder.build();
    }

    @GET
    public Response hello() {
        var result = instance.export("operation").apply(41, 1);
        return Response.ok("Hello endive (static): " + result[0]).build();
    }
}
