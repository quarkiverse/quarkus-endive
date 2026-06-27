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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.ValType;

@Path("/endive")
@ApplicationScoped
public class EndiveResourceWithImports {

    @Inject
    @Named("operation")
    WasmQuarkusContext wasmQuarkusContext;

    Instance instance;

    private static final Deque expectedStack = new ArrayDeque<Integer>(2);

    @PostConstruct
    public void init() throws IOException {
        WasmModule wasmModule = wasmQuarkusContext.getWasmModule();
        if (wasmModule == null) {
            throw new IllegalStateException("Wasm module " + wasmQuarkusContext.getName() + " not found!");
        }
        Instance.Builder builder = Instance
                .builder(wasmModule)
                .withImportValues(ImportValues.builder()
                        .addFunction(
                                new HostFunction(
                                        "env",
                                        "host_log",
                                        FunctionType.of(List.of(ValType.I32), List.of()),
                                        (inst, args) -> {
                                            var num = (int) args[0];
                                            assert expectedStack.pop().equals(num);
                                            System.out.println("Number: " + num);
                                            return null;
                                        }))
                        .build())
                .withMachineFactory(wasmQuarkusContext.getMachineFactory());
        instance = builder.build();
    }

    @GET
    public Response hello() {
        expectedStack.add(41);
        expectedStack.add(1);

        var result = instance
                .exports()
                .function("operation")
                .apply(41, 1);

        return Response.ok("Hello endive: " + result[0]).build();
    }
}
