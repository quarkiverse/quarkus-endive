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
package io.quarkiverse.chicory.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.jboss.logging.Logger;

import io.quarkus.runtime.util.StringUtil;

/**
 * Quarkus Chicory utilities.
 */
public class WasmQuarkusUtils {
    private static final Logger LOG = Logger.getLogger(WasmQuarkusUtils.class);

    /**
     * Writes a classpath Wasm resource to a temporary file and returns its path.
     *
     * @param resource The name of the Wasm resource
     * @return The {@link Path} of the temporary file
     * @throws IllegalArgumentException if the resource name is null or empty
     * @throws IllegalStateException if the resource cannot be accessed or the temporary file cannot be created
     */
    public static Path getWasmPathFromResource(String resource) {
        if (StringUtil.isNullOrEmpty(resource)) {
            throw new IllegalArgumentException("Wasm module resource cannot be null or empty");
        }
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            Path wasmFile = Files.createTempFile("chicory-", ".wasm");
            if (is != null) {
                Files.copy(is, wasmFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new IllegalStateException("Cannot access Wasm module resource: " + resource);
            }
            return wasmFile;
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("Cannot create Wasm module resource (%s) temporary file", resource), e);
        }
    }

    /**
     * Returns the Meta Wasm resource input stream.
     *
     * @param wasmModuleName The fully qualified name of the Wasm module that generates the Meta Wasm resource
     * @return The {@link InputStream} to read the Meta Wasm resource, or {@code null} if not found
     * @throws IllegalArgumentException if the module name is null or empty
     */
    public static InputStream getMetaWasmResourceStream(String wasmModuleName) {
        if (StringUtil.isNullOrEmpty(wasmModuleName)) {
            throw new IllegalArgumentException("Wasm module name cannot be null or empty");
        }
        final String resourcePath = getWasmModuleClassPath(wasmModuleName);
        final String resource = resourcePath + "/" + getWasmModuleClassName(wasmModuleName) + ".meta";
        LOG.debug("Getting Wasm module resource for " + resource);
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
    }

    /**
     * Extract the simple class name from the FQN represented by the configured Wasm module name.
     *
     * @param wasmModuleName The name of the Wasm module that generates the Meta Wasm resource
     * @return The simple name of the class represented by the configured Wasm module name
     */
    public static String getWasmModuleClassName(final String wasmModuleName) {
        if (StringUtil.isNullOrEmpty(wasmModuleName)) {
            throw new IllegalArgumentException("Wasm module name cannot be null or empty");
        }
        final String[] splitClassName = wasmModuleName.split("\\.");
        return splitClassName[splitClassName.length - 1];
    }

    /**
     * Extract the class path from the FQN represented by the configured Wasm module name.
     *
     * @param wasmModuleName The name of the Wasm module that generates the Meta Wasm resource
     * @return The path of the class represented by the configured Wasm module name
     */
    public static String getWasmModuleClassPath(final String wasmModuleName) {
        if (StringUtil.isNullOrEmpty(wasmModuleName)) {
            throw new IllegalArgumentException("Wasm module name cannot be null or empty");
        }
        final String normalized = wasmModuleName.replace('.', '/');
        final int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash == -1) {
            throw new IllegalArgumentException("Wasm module name must be a FQN");
        }
        return normalized.substring(0, normalized.lastIndexOf('/'));
    }
}
