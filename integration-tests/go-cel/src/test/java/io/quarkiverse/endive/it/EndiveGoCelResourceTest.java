package io.quarkiverse.endive.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EndiveGoCelResourceTest {

    static final String CEL_POLICY;

    static {
        try {
            CEL_POLICY = readResource("cel.policy");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testCorrectPodDefinitionValidation() throws IOException {
        final String manifestJson = readResource("correct-pod.json");

        given()
                .multiPart("manifestJson", manifestJson)
                .multiPart("celPolicy", CEL_POLICY)
                .when()
                .post("/endive/validate")
                .then()
                .statusCode(200)
                .body(is("1 - Policy ALLOWS the request"));
    }

    @Test
    public void testRejectedPodDefinitionValidation() throws IOException {
        final String manifestJson = readResource("rejected-pod.json");

        given()
                .multiPart("manifestJson", manifestJson)
                .multiPart("celPolicy", CEL_POLICY)
                .when()
                .post("/endive/validate")
                .then()
                .statusCode(200)
                .body(is("0 - Policy DENIES the request"));
    }

    private static String readResource(String fileName) throws IOException {
        final URL url = Thread.currentThread().getContextClassLoader().getResource(fileName);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + fileName);
        }
        return Files.readString(Path.of(url.getPath()));
    }
}
