package io.quarkiverse.endive.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EndiveResourceWithImportsTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/endive")
                .then()
                .statusCode(200)
                .body(is("Hello endive: " + 42));
    }
}
