package io.quarkiverse.endive.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EndiveGoQRCodeResourceTest {

    @Test
    public void testQRCodeGeneration() {
        byte[] pngData = given()
                .queryParam("text", "Test QR Code")
                .when().get("/endive/qrcode")
                .then()
                .statusCode(200)
                .header("Content-Type", is("image/png"))
                .extract().asByteArray();

        // Verify it's a valid PNG (starts with PNG signature)
        assert pngData.length > 8;
        assert pngData[0] == (byte) 0x89;
        assert pngData[1] == (byte) 0x50; // 'P'
        assert pngData[2] == (byte) 0x4E; // 'N'
        assert pngData[3] == (byte) 0x47; // 'G'
    }

    @Test
    public void testQRCodeDefaultText() {
        byte[] pngData = given()
                .when().get("/endive/qrcode")
                .then()
                .statusCode(200)
                .header("Content-Type", is("image/png"))
                .extract().asByteArray();

        // Should generate a PNG for default text
        assert pngData.length > 100; // PNG should be at least 100 bytes
    }
}
