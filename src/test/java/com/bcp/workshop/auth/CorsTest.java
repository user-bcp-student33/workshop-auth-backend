package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class CorsTest {
    @Test
    void permitsConfiguredOriginOnPreflight() {
        given()
                .header("Origin", "https://allowed.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", is("https://allowed.test"));
    }

    @Test
    void doesNotGrantCorsPermissionToOtherOrigin() {
        given()
                .header("Origin", "https://other.test")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void permitsConfiguredOriginOnLoginResponse() {
        given()
                .header("Origin", "https://allowed.test")
                .contentType("application/json")
                .body("{}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .header("Access-Control-Allow-Origin", is("https://allowed.test"));
    }
}
