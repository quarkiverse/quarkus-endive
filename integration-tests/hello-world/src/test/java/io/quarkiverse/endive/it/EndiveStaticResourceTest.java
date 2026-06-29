package io.quarkiverse.endive.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EndiveStaticResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/endive/static")
                .then()
                .statusCode(200)
                .body(is("Hello endive (static): " + 42));
    }
}
