package com.orderplatform.qa.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.orderplatform.qa.util.Json;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transport-neutral view of an HTTP response. Tests never see Rest Assured
 * types, which keeps the client layer replaceable and assertions explicit.
 */
public record ApiResponse(int status, String body, Map<String, List<String>> headers) {

    public ApiResponse {
        body = body == null ? "" : body;
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, values) ->
                    copy.put(name, values == null ? List.of() : List.copyOf(values)));
        }
        headers = Collections.unmodifiableMap(copy);
    }

    public static ApiResponse from(Response response) {
        Map<String, List<String>> headerMap = new LinkedHashMap<>();
        response.getHeaders().forEach(header ->
                headerMap.computeIfAbsent(header.getName(), ignored -> new ArrayList<>()).add(header.getValue()));
        return new ApiResponse(response.statusCode(), response.getBody().asString(), headerMap);
    }

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    public Optional<String> firstHeader(String name) {
        return headerValues(name).stream().findFirst();
    }

    public List<String> headerValues(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    public Optional<String> contentType() {
        return firstHeader("Content-Type");
    }

    public <T> T as(Class<T> type) {
        return Json.read(body, type);
    }

    public <T> T as(TypeReference<T> type) {
        return Json.read(body, type);
    }

    public JsonNode json() {
        return Json.readTree(body);
    }
}
