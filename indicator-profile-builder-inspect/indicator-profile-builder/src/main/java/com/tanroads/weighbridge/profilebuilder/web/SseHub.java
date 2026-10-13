package com.tanroads.weighbridge.profilebuilder.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

/**
 * Server-Sent Events fan-out (each event is ONE single-line JSON document, because SSE only delivers the text after a data: prefix and a pretty-printed payload would be silently dropped by the browser): every browser tab subscribed to /api/capture/events receives
 * every raw chunk / parsed reading / status change as one JSON event. SSE (not WebSocket)
 * because the JDK HTTP server supports it natively with no extra dependency and the data
 * only ever flows server -> browser.
 */
public class SseHub {

    private final ObjectMapper mapper;
    private final List<Client> clients = new CopyOnWriteArrayList<>();

    public SseHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Takes over the exchange: keeps it open and streams events until the client leaves. */
    public void subscribe(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        Client client = new Client(exchange);
        clients.add(client);
        client.send("{\"type\":\"hello\"}");
    }

    public void publish(Map<String, Object> event) {
        String json;
        try {
            json = mapper.writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT).writeValueAsString(event);
        } catch (IOException e) {
            return;
        }
        for (Client client : clients) {
            if (!client.send(json)) {
                clients.remove(client);
            }
        }
    }

    public int clientCount() {
        return clients.size();
    }

    private static final class Client {
        private final HttpExchange exchange;
        private final OutputStream out;

        Client(HttpExchange exchange) {
            this.exchange = exchange;
            this.out = exchange.getResponseBody();
        }

        synchronized boolean send(String json) {
            try {
                out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException e) {
                try {
                    exchange.close();
                } catch (Exception ignored) {
                    // gone
                }
                return false;
            }
        }
    }
}
