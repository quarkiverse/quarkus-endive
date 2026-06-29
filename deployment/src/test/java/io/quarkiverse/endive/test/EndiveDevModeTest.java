package io.quarkiverse.endive.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * Tests that verify quarkus-endive works correctly in development mode,
 * including hot reload of WASM modules when files change.
 */
public class EndiveDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(io.quarkiverse.endive.test.devmode.MathResource.class)
                    .addAsResource("dev-mode-test-application.properties", "application.properties"))
            // WASM file is loaded from filesystem via wasm-file config property
            .setBuildSystemProperty("project.basedir",
                    Paths.get("").toAbsolutePath().toString());

    private static Path absoluteScriptPath;

    @BeforeAll
    static void verifyEnvironment() {
        // Resolve path (relative to the deployment module)
        // We build the WASM in the SOURCE directory (not target/test-classes)
        // so Quarkus can detect the file change for hot reload, since we use the "wasm-file" config
        // property, i.e. the one that quarkus-endive uses to watch the WASM payload and hot reload when it is
        // modified.
        final String relativePath = "src/test/resources/dev-mode/wasm";
        final Path basePath = Paths.get("");
        absoluteScriptPath = basePath.toAbsolutePath()
                .resolve(relativePath)
                .resolve("build.sh");

        // Check if the file exists
        if (!Files.exists(absoluteScriptPath)) {
            throw new IllegalStateException(String.format(
                    """
                            WASM build script NOT FOUND!
                            Expected at: %s
                            Current Working Directory: %s
                            Please ensure the 'scripts' folder is in the %s directory (relative to the deployment module).""",
                    absoluteScriptPath, basePath.toAbsolutePath(), relativePath));
        }

        // Check for execution permissions (to make this work for CI checks, not just locally)
        if (!Files.isExecutable(absoluteScriptPath)) {
            // Try to set it programmatically (if possible...)
            boolean success = absoluteScriptPath.toFile().setExecutable(true);
            if (!success) {
                throw new IllegalStateException("Build script is not executable and cannot be fixed: " + absoluteScriptPath);
            }
        }
    }

    /**
     * Test 1: Verify initial WASM module works correctly with addition operation
     */
    @Test
    public void testInitialWasmModuleExecution() {
        // Initial WASM does addition: 10 + 5 = 15
        given()
                .when().get("/test/math/add")
                .then()
                .statusCode(200)
                .body(is("15"));
    }

    /**
     * Test 2: Verify hot reload works when WASM file changes from addition to multiplication
     * This test modifies the WASM file in the source directory and verifies Quarkus detects
     * the change and hot reloads the module.
     */
    @Test
    public void testHotReloadOnWasmFileChange() throws InterruptedException {
        // Initial state: 10 + 5 = 15 (addition)
        given()
                .when().get("/test/math/add")
                .then()
                .statusCode(200)
                .body(is("15"));

        // Build multiplication WASM directly in the source directory
        // Quarkus watches this file and should detect the change
        buildWasmVariant("multiply");

        // Give Quarkus time to detect the file change and hot reload
        Thread.sleep(3000);

        // After hot reload: 10 * 5 = 50 (multiplication)
        given()
                .when().get("/test/math/add")
                .then()
                .statusCode(200)
                .body(is("50"));

        // Restore addition WASM for other tests
        buildWasmVariant("add");
        Thread.sleep(2000);
    }

    private void buildWasmVariant(final String variant) {
        try {
            final Process process = new ProcessBuilder("/bin/bash", absoluteScriptPath.toString(), variant)
                    .inheritIO()
                    // Set the working directory to where the script is located
                    .directory(absoluteScriptPath.getParent().toFile())
                    .start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Script failed with exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("WASM build execution failed", e);
        }
    }
}
