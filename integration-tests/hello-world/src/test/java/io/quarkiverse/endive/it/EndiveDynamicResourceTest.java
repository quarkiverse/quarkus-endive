package io.quarkiverse.endive.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.io.File;

import org.junit.jupiter.api.Test;

import io.quarkiverse.endive.runtime.wasm.ExecutionMode;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EndiveDynamicResourceTest {

    public static final String WASM_MODULE_NAME_OPERATION_DYNAMIC = "operation-dynamic";

    @Test
    public void testDynamicHelloEndpoint() {
        File wasmModule = new File("src/main/resources/wasm/operation.wasm");
        given()
                .multiPart("module", wasmModule)
                .multiPart("name", WASM_MODULE_NAME_OPERATION_DYNAMIC)
                .multiPart("execution-mode", ExecutionMode.Interpreter)
                .when().post("/endive/dynamic/upload")
                .then()
                .statusCode(202);

        given()
                .queryParam("name", WASM_MODULE_NAME_OPERATION_DYNAMIC)
                .when().get("/endive/dynamic")
                .then()
                .statusCode(200)
                .body(is("Hello endive (dynamic): " + 42));
    }
}
