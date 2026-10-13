package com.tanroads.weighbridge.profilebuilder.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanroads.weighbridge.profilebuilder.ProfileBuilderApp;
import com.tanroads.weighbridge.profilebuilder.sim.SimulatedIndicatorServer;

/** Boots the real HTTP server against a temp data folder and a simulated TCP indicator,
 *  then drives the same API the browser uses: save -> connect -> live parsing ->
 *  replay with different rules -> save evidence -> lifecycle -> export/import. */
class HttpApiIntegrationTest {

    @TempDir
    static Path dataDir;
    static ProfileBuilderApp.Server server;
    static SimulatedIndicatorServer simulator;
    static final HttpClient client = HttpClient.newHttpClient();
    static final ObjectMapper mapper = new ObjectMapper();
    static final String STATUS_REGEX = "^(ST|US),GS,\\\\s*(\\\\d+)";

    @BeforeAll
    static void start() throws Exception {
        simulator = new SimulatedIndicatorServer(0, 40);
        server = ProfileBuilderApp.start("127.0.0.1", 0, dataDir);
    }

    @AfterAll
    static void stop() {
        server.stop();
        simulator.close();
    }

    private static HttpResponse<String> raw(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.url() + path.substring(1)));
        if (body != null) {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode call(String method, String path, String body) throws Exception {
        HttpResponse<String> response = raw(method, path, body);
        assertTrue(response.statusCode() < 300, () -> method + " " + path + " -> " + response.statusCode() + " " + response.body());
        return mapper.readTree(response.body());
    }

    private static String profileJson(String extractionRule, String pattern, String stabilityRule) {
        return "{\"profile\":{\"name\":\"Simulated\",\"manufacturer\":\"Sim\",\"connectionType\":\"TCP\",\"tcpHost\":\"127.0.0.1\","
                + "\"tcpPort\":" + simulator.port() + ",\"readMode\":\"CONTINUOUS\",\"framingMode\":\"DELIMITER\",\"messageDelimiter\":\"\\r\\n\","
                + "\"extractionRule\":\"" + extractionRule + "\",\"extractionPattern\":" + (pattern == null ? "null" : "\"" + pattern + "\"")
                + ",\"weightUnit\":\"kg\",\"stabilityRule\":\"" + stabilityRule + "\",\"stableStatusToken\":\"ST\"},\"technicianName\":\"Test Tech\"}";
    }

    private static JsonNode waitForMessages(long minimum) throws Exception {
        JsonNode stats = null;
        for (int i = 0; i < 150; i++) {
            Thread.sleep(50);
            stats = call("GET", "/api/capture/statistics", null);
            if (stats.get("messagesReceived").asLong() >= minimum) {
                break;
            }
        }
        return stats;
    }

    @Test
    void endToEndCaptureAndLifecycle() throws Exception {
        assertEquals(200, raw("GET", "/", null).statusCode());

        JsonNode saved = call("POST", "/api/profiles", profileJson("FIRST_NUMBER", null, "NONE"));
        long id = saved.get("id").asLong();
        assertEquals("DRAFT", saved.get("profile").get("approvalStatus").asText());

        call("POST", "/api/capture/connect", "{\"profileId\":" + id + "}");
        JsonNode stats = waitForMessages(25);
        assertNotNull(stats);
        assertTrue(stats.get("messagesReceived").asLong() >= 25, "expected live messages, got " + stats);
        assertEquals(stats.get("messagesReceived").asLong(), stats.get("valid").asLong(), "every simulated message must parse with FIRST_NUMBER");

        // Replay the captured bytes with a status-token rule: the simulator alternates ST/US.
        JsonNode replay = call("POST", "/api/capture/replay", profileJson("STATUS_TOKEN", STATUS_REGEX, "STATUS_TOKEN"));
        assertTrue(replay.get("messages").asLong() > 0);
        assertEquals(replay.get("messages").asLong(), replay.get("valid").asLong());
        assertTrue(replay.get("stable").asLong() <= replay.get("messages").asLong());

        // Replay with a wrong delimiter: nothing frames, and the tool must say so.
        JsonNode wrong = call("POST", "/api/capture/replay",
                "{\"profile\":{\"framingMode\":\"DELIMITER\",\"messageDelimiter\":\"~\",\"extractionRule\":\"FIRST_NUMBER\",\"weightUnit\":\"kg\",\"maxBufferBytes\":100000}}");
        assertEquals(0, wrong.get("messages").asLong());
        assertTrue(wrong.get("pendingBytes").asLong() > 0);

        // Hot-swap the live rules (counters reset), then save evidence.
        call("POST", "/api/capture/apply-rules", profileJson("STATUS_TOKEN", STATUS_REGEX, "STATUS_TOKEN"));
        waitForMessages(5);
        JsonNode sample = call("POST", "/api/profiles/" + id + "/samples",
                "{\"stationName\":\"Kibaha\",\"stationCode\":\"KBH\",\"lane\":\"1\",\"label\":\"UNKNOWN_SAMPLE\",\"notes\":\"first\",\"technicianName\":\"Test Tech\"}");
        assertTrue(sample.get("sample").get("rawHex").asText().length() > 0);
        assertEquals("Kibaha", sample.get("sample").get("stationName").asText());

        // Lifecycle: DRAFT -> TESTING -> VALIDATED (needs evidence + confirmation) -> APPROVED.
        call("POST", "/api/profiles/" + id + "/advance", "{}");
        waitForMessages(20);
        HttpResponse<String> refused = raw("POST", "/api/profiles/" + id + "/advance", "{\"technicianConfirmedCorrect\":false}");
        assertEquals(400, refused.statusCode(), "VALIDATED without confirmation must be refused");
        call("POST", "/api/profiles/" + id + "/advance", "{\"technicianConfirmedCorrect\":true,\"technicianName\":\"Test Tech\"}");
        call("POST", "/api/profiles/" + id + "/advance", "{}");
        JsonNode fetched = call("GET", "/api/profiles/" + id, null);
        assertEquals("APPROVED", fetched.get("profile").get("approvalStatus").asText());
        assertEquals(false, fetched.get("editable").asBoolean());
        assertEquals("Test Tech", fetched.get("profile").get("validatedByName").asText());

        // Approved profiles cannot be edited in place; a new version can be created instead.
        HttpResponse<String> editRefused = raw("POST", "/api/profiles",
                profileJson("FIRST_NUMBER", null, "NONE").replace("{\"profile\":{", "{\"profile\":{\"id\":" + id + ","));
        assertEquals(400, editRefused.statusCode(), editRefused.body());
        JsonNode version = call("POST", "/api/profiles/" + id + "/new-version", "{}");
        assertEquals(2, call("GET", "/api/profiles/" + version.get("id").asLong(), null).get("profile").get("profileVersion").asInt());

        // Export bundle round-trips through import as a fresh DRAFT carrying the sample.
        JsonNode export = call("GET", "/api/profiles/" + id + "/export-json", null);
        String bundle = export.get("json").asText();
        assertTrue(bundle.contains("\"captureSamples\""));
        JsonNode imported = call("POST", "/api/profiles/import", mapper.writeValueAsString(java.util.Map.of("json", bundle)));
        JsonNode importedProfile = call("GET", "/api/profiles/" + imported.get("id").asLong(), null);
        assertEquals("DRAFT", importedProfile.get("profile").get("approvalStatus").asText());
        assertEquals(1, importedProfile.get("samples").size());
        assertEquals(200, raw("GET", "/api/profiles/" + id + "/export", null).statusCode());
        assertEquals(200, raw("GET", "/api/profiles/export.csv", null).statusCode());

        call("POST", "/api/capture/disconnect", null);
        assertEquals(false, call("GET", "/api/capture/statistics", null).get("connected").asBoolean());
        assertEquals(3, call("GET", "/api/profiles", null).get("profiles").size());

        // Connection failure must come back as a clear 500 with the reason, not a hang.
        JsonNode dead = call("POST", "/api/profiles", profileJson("FIRST_NUMBER", null, "NONE").replace("\"tcpPort\":" + simulator.port(), "\"tcpPort\":1"));
        HttpResponse<String> failed = raw("POST", "/api/capture/connect", "{\"profileId\":" + dead.get("id").asLong() + "}");
        assertTrue(failed.statusCode() >= 400, failed.body());
        assertTrue(mapper.readTree(failed.body()).get("message").asText().length() > 0);
    }

    /** Every SSE event must be a single "data: {json}" line: a pretty-printed payload is
     *  silently discarded by the browser EventSource, which is exactly the bug this guards. */
    @Test
    void eventStreamEmitsSingleLineJsonEvents() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.url() + "api/capture/events")).GET().build();
        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()));
        String first = reader.readLine();
        assertNotNull(first);
        assertTrue(first.startsWith("data: {"), first);
        JsonNode hello = mapper.readTree(first.substring("data: ".length()));
        assertEquals("hello", hello.get("type").asText());
        assertEquals("", reader.readLine(), "an SSE event ends with an empty line");
        server.capture().publishStatistics();
        String second = reader.readLine();
        assertTrue(second.startsWith("data: {") && second.endsWith("}"), "single-line JSON expected, got: " + second);
        assertEquals("statistics", mapper.readTree(second.substring("data: ".length())).get("type").asText());
        response.body().close();
    }
}
