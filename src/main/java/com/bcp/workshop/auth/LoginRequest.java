package com.bcp.workshop.auth;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

public class LoginRequest {
    public String username;
    public String password;
    public String client;

    @JsonSetter("client")
    public void setClient(JsonNode value) {
        client = value != null && value.isTextual() ? value.textValue() : null;
    }
}
