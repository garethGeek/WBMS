package com.tanroads.weighbridge.profilebuilder.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/** Serves the bundled single-page UI from the jar (classpath /web). No external CDN: the
 *  tool must work on a weighbridge laptop with no internet connection. */
public class StaticFiles implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        try (InputStream in = StaticFiles.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                byte[] notFound = "Not found".getBytes();
                exchange.sendResponseHeaders(404, notFound.length);
                exchange.getResponseBody().write(notFound);
                exchange.close();
                return;
            }
            byte[] bytes = in.readAllBytes();
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
