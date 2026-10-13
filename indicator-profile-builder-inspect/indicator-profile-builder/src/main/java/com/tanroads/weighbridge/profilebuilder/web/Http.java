package com.tanroads.weighbridge.profilebuilder.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

/** Small helpers over the JDK HttpExchange. */
public final class Http {

    private Http() {
    }

    public static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    public static String body(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static <T> T bodyAs(HttpExchange exchange, ObjectMapper mapper, Class<T> type) throws IOException {
        String body = body(exchange);
        if (body.isBlank()) {
            throw new IllegalArgumentException("Request body is empty");
        }
        return mapper.readValue(body, type);
    }

    public static void json(HttpExchange exchange, ObjectMapper mapper, int status, Object payload) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static void ok(HttpExchange exchange, ObjectMapper mapper, Object payload) throws IOException {
        json(exchange, mapper, 200, payload);
    }

    public static void message(HttpExchange exchange, ObjectMapper mapper, String message) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("message", message);
        json(exchange, mapper, 200, payload);
    }

    public static void error(HttpExchange exchange, ObjectMapper mapper, int status, String message) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "01");
        payload.put("message", message);
        json(exchange, mapper, status, payload);
    }

    public static void download(HttpExchange exchange, String fileName, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
