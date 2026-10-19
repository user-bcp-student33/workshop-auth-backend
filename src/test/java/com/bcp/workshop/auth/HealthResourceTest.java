package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;

@QuarkusTest
class HealthResourceTest {
    @Test
    void reportsHealthWithoutAuthentication() {
        given()
                .accept("application/json")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void acceptsRepeatedRequestsWithoutBodyOrAcceptHeader() {
        for (int attempt = 0; attempt < 2; attempt++) {
            given()
                    .when().get("/q/health")
                    .then()
                    .statusCode(200)
                    .contentType("application/json")
                    .body("$", aMapWithSize(2))
                    .body("status", is("UP"))
                    .body("checks", empty());
        }
    }
}
