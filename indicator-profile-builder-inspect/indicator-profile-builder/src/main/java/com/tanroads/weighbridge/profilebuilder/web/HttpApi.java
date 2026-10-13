package com.tanroads.weighbridge.profilebuilder.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tanroads.weighbridge.profilebuilder.engine.NormalizedWeightReading;
import com.tanroads.weighbridge.profilebuilder.engine.ParsePipeline;
import com.tanroads.weighbridge.profilebuilder.model.CaptureSample;
import com.tanroads.weighbridge.profilebuilder.model.CaptureSampleLabel;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ProfileApprovalStatus;
import com.tanroads.weighbridge.profilebuilder.service.CaptureSessionManager;
import com.tanroads.weighbridge.profilebuilder.service.Escapes;
import com.tanroads.weighbridge.profilebuilder.service.ProfileImportExportService;
import com.tanroads.weighbridge.profilebuilder.service.ProfileLifecycleService;
import com.tanroads.weighbridge.profilebuilder.store.ProfileRepository;
import com.tanroads.weighbridge.profilebuilder.store.SampleRepository;
import com.tanroads.weighbridge.profilebuilder.transport.SerialPorts;

/**
 * JSON API behind the single-page UI. One handler mounted at /api; routes are matched on
 * method + path below. Every error is returned as {status:"01", message} with an HTTP
 * status so the UI can show the exact reason (never a silent failure).
 */
public class HttpApi implements HttpHandler {

    private final ObjectMapper mapper;
    private final ProfileRepository profiles;
    private final SampleRepository samples;
    private final ProfileLifecycleService lifecycle;
    private final ProfileImportExportService importExport;
    private final CaptureSessionManager capture;
    private final SseHub sseHub;
    private final String toolVersion;

    public HttpApi(ObjectMapper mapper, ProfileRepository profiles, SampleRepository samples,
                   ProfileLifecycleService lifecycle, ProfileImportExportService importExport,
                   CaptureSessionManager capture, SseHub sseHub, String toolVersion) {
        this.mapper = mapper;
        this.profiles = profiles;
        this.samples = samples;
        this.lifecycle = lifecycle;
        this.importExport = importExport;
        this.capture = capture;
        this.sseHub = sseHub;
        this.toolVersion = toolVersion;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try {
            if (!route(method, path, exchange)) {
                Http.error(exchange, mapper, 404, "No such API route: " + method + " " + path);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            Http.error(exchange, mapper, 400, messageOf(e));
        } catch (Exception e) {
            Http.error(exchange, mapper, 500, messageOf(e));
        }
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }

    private boolean route(String method, String path, HttpExchange exchange) throws Exception {
        String[] parts = path.split("/");
        // parts: "", "api", segment, ...
        if (parts.length < 3) {
            return false;
        }
        String segment = parts[2];
        switch (segment) {
            case "status" -> { status(exchange); return true; }
            case "serial-ports" -> { Http.ok(exchange, mapper, SerialPorts.list()); return true; }
            case "profiles" -> { return routeProfiles(method, parts, exchange); }
            case "samples" -> {
                if ("DELETE".equals(method) && parts.length == 4) {
                    deleteSample(Long.valueOf(parts[3]), exchange);
                    return true;
                }
                return false;
            }
            case "capture" -> { return routeCapture(method, parts, exchange); }
            default -> { return false; }
        }
    }

    private void status(HttpExchange exchange) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolVersion", toolVersion);
        payload.put("dataDirectory", profiles.directory().getParent().toAbsolutePath().toString());
        payload.put("profileCount", profiles.findAll().size());
        payload.put("connected", capture.isConnected());
        payload.put("connectionState", capture.connectionState());
        payload.put("connectedProfileId", capture.connectedProfile() != null ? capture.connectedProfile().getId() : null);
        payload.put("minimumMessagesForValidation", ProfileLifecycleService.MINIMUM_MESSAGES_FOR_VALIDATION);
        Http.ok(exchange, mapper, payload);
    }

    // ------------------------------------------------------------------ profiles

    private boolean routeProfiles(String method, String[] parts, HttpExchange exchange) throws Exception {
        if (parts.length == 3) {
            if ("GET".equals(method)) { listProfiles(exchange); return true; }
            if ("POST".equals(method)) { saveDraft(exchange); return true; }
            return false;
        }
        if (parts.length == 4) {
            if ("export.csv".equals(parts[3]) && "GET".equals(method)) { exportCsv(exchange); return true; }
            if ("import".equals(parts[3]) && "POST".equals(method)) { importProfile(exchange); return true; }
            Long id = Long.valueOf(parts[3]);
            if ("GET".equals(method)) { fetchProfile(id, exchange); return true; }
            if ("DELETE".equals(method)) { deleteProfile(id, exchange); return true; }
            return false;
        }
        if (parts.length == 5) {
            Long id = Long.valueOf(parts[3]);
            switch (parts[4]) {
                case "new-version" -> { createNewVersion(id, exchange); return true; }
                case "advance" -> { advance(id, exchange); return true; }
                case "demote" -> { demote(id, exchange); return true; }
                case "export" -> { exportProfile(id, exchange, true); return true; }
                case "export-json" -> { exportProfile(id, exchange, false); return true; }
                case "samples" -> {
                    if ("GET".equals(method)) { listSamples(id, exchange); return true; }
                    if ("POST".equals(method)) { saveSample(id, exchange); return true; }
                    return false;
                }
                default -> { return false; }
            }
        }
        return false;
    }

    private void listProfiles(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IndicatorProfile profile : profiles.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", profile.getId());
            row.put("name", profile.getName());
            row.put("manufacturer", profile.getManufacturer());
            row.put("model", profile.getModel());
            row.put("connectionType", profile.getConnectionType());
            row.put("connection", profile.describeConnection());
            row.put("approvalStatus", profile.getApprovalStatus());
            row.put("profileVersion", profile.getProfileVersion());
            row.put("sampleCount", samples.findByProfile(profile.getId()).size());
            row.put("updatedOn", profile.getUpdatedOn());
            rows.add(row);
        }
        Http.ok(exchange, mapper, Map.of("status", "00", "profiles", rows));
    }

    private void fetchProfile(Long id, HttpExchange exchange) throws IOException {
        IndicatorProfile profile = requireProfile(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("profile", profile);
        payload.put("editable", lifecycle.isEditableInPlace(profile.getApprovalStatus()));
        payload.put("canAdvance", lifecycle.canAdvance(profile.getApprovalStatus()));
        payload.put("nextStatus", lifecycle.canAdvance(profile.getApprovalStatus()) ? lifecycle.nextStatus(profile.getApprovalStatus()) : null);
        payload.put("samples", samples.findByProfile(id));
        payload.put("file", profiles.fileFor(id).toAbsolutePath().toString());
        Http.ok(exchange, mapper, payload);
    }

    private IndicatorProfile requireProfile(Long id) {
        return profiles.findById(id).orElseThrow(() -> new IllegalArgumentException("Profile " + id + " not found"));
    }

    /** Body: { "profile": {IndicatorProfile fields}, "technicianName": "..." }. */
    private void saveDraft(HttpExchange exchange) throws Exception {
        JsonNode body = mapper.readTree(Http.body(exchange));
        JsonNode profileNode = body.get("profile");
        if (profileNode == null || !profileNode.isObject()) {
            throw new IllegalArgumentException("Body must contain a profile object");
        }
        IndicatorProfile incoming = mapper.treeToValue(profileNode, IndicatorProfile.class);
        validateForSave(incoming);
        String technician = textOrNull(body, "technicianName");
        Instant now = Instant.now();

        IndicatorProfile target;
        boolean isNew = incoming.getId() == null || profiles.findById(incoming.getId()).isEmpty();
        if (isNew) {
            target = new IndicatorProfile();
            target.setApprovalStatus(ProfileApprovalStatus.DRAFT);
            target.setProfileVersion(incoming.getProfileVersion() != null ? incoming.getProfileVersion() : 1);
            target.setSupersedesProfileNo(incoming.getSupersedesProfileNo());
            target.setCreatedOn(now);
            target.setCreatedByName(technician);
        } else {
            target = profiles.findById(incoming.getId()).get();
            if (!lifecycle.isEditableInPlace(target.getApprovalStatus())) {
                throw new IllegalStateException("This profile is " + target.getApprovalStatus()
                        + " and cannot be edited in place -- use Create New Version instead");
            }
        }
        incoming.copyConfigurationTo(target);
        target.setUpdatedOn(now);
        target.setUpdatedByName(technician);
        IndicatorProfile saved = profiles.save(target);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("message", isNew ? "Profile saved as new DRAFT" : "Draft saved");
        payload.put("id", saved.getId());
        payload.put("profile", saved);
        Http.ok(exchange, mapper, payload);
    }

    private static void validateForSave(IndicatorProfile profile) {
        if (profile.getName() == null || profile.getName().isBlank()) {
            throw new IllegalArgumentException("Profile Name is required");
        }
        if (profile.getConnectionType() == null) {
            throw new IllegalArgumentException("Communication Type is required");
        }
        if (profile.getReadMode() == null) {
            throw new IllegalArgumentException("Read Mode is required");
        }
        if (profile.getFramingMode() == null) {
            throw new IllegalArgumentException("Message Framing is required");
        }
        if (profile.getExtractionRule() == null) {
            throw new IllegalArgumentException("Extraction Rule is required");
        }
        if (profile.getWeightUnit() == null || profile.getWeightUnit().isBlank()) {
            profile.setWeightUnit("kg");
        }
        if (profile.getStabilityRule() == null) {
            profile.setStabilityRule(com.tanroads.weighbridge.profilebuilder.model.StabilityRule.NONE);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private void deleteProfile(Long id, HttpExchange exchange) throws IOException {
        IndicatorProfile profile = requireProfile(id);
        if (!lifecycle.isDeletable(profile.getApprovalStatus())) {
            throw new IllegalStateException("An " + profile.getApprovalStatus() + " profile cannot be deleted -- send it back to Draft first");
        }
        if (capture.connectedProfile() != null && id.equals(capture.connectedProfile().getId()) && capture.isConnected()) {
            capture.disconnect();
        }
        samples.deleteByProfile(id);
        profiles.delete(id);
        Http.message(exchange, mapper, "Profile deleted, together with its saved samples");
    }

    private void createNewVersion(Long id, HttpExchange exchange) throws Exception {
        IndicatorProfile previous = requireProfile(id);
        JsonNode body = readBodyTree(exchange);
        IndicatorProfile next = lifecycle.createNewVersion(previous);
        Instant now = Instant.now();
        next.setCreatedOn(now).setUpdatedOn(now)
                .setCreatedByName(textOrNull(body, "technicianName"))
                .setUpdatedByName(textOrNull(body, "technicianName"));
        IndicatorProfile saved = profiles.save(next);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("message", "New version " + saved.getProfileVersion() + " created as DRAFT (previous version untouched)");
        payload.put("id", saved.getId());
        Http.ok(exchange, mapper, payload);
    }

    private JsonNode readBodyTree(HttpExchange exchange) throws IOException {
        String body = Http.body(exchange);
        return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private void advance(Long id, HttpExchange exchange) throws Exception {
        IndicatorProfile profile = requireProfile(id);
        JsonNode body = readBodyTree(exchange);
        boolean confirmed = body.path("technicianConfirmedCorrect").asBoolean(false);
        String technician = textOrNull(body, "technicianName");
        ProfileApprovalStatus target = lifecycle.nextStatus(profile.getApprovalStatus());
        if (target == ProfileApprovalStatus.VALIDATED) {
            IndicatorProfile connected = capture.connectedProfile();
            if (connected == null || !id.equals(connected.getId())) {
                throw new IllegalStateException("Connect with THIS profile and capture at least "
                        + ProfileLifecycleService.MINIMUM_MESSAGES_FOR_VALIDATION + " messages before marking it VALIDATED");
            }
        }
        lifecycle.requireSufficientEvidence(target, capture.statistics(), confirmed);
        profile.setApprovalStatus(target);
        if (target == ProfileApprovalStatus.VALIDATED) {
            profile.setValidatedOn(Instant.now());
            profile.setValidatedByName(technician);
        }
        profile.setUpdatedOn(Instant.now()).setUpdatedByName(technician);
        profiles.save(profile);
        Http.message(exchange, mapper, "Profile advanced to " + target.name());
    }

    private void demote(Long id, HttpExchange exchange) throws Exception {
        IndicatorProfile profile = requireProfile(id);
        JsonNode body = readBodyTree(exchange);
        profile.setApprovalStatus(lifecycle.demoteToDraft());
        profile.setUpdatedOn(Instant.now()).setUpdatedByName(textOrNull(body, "technicianName"));
        profiles.save(profile);
        Http.message(exchange, mapper, "Profile returned to DRAFT for rework");
    }

    private void exportProfile(Long id, HttpExchange exchange, boolean asDownload) throws Exception {
        IndicatorProfile profile = requireProfile(id);
        String json = importExport.toJson(profile, samples.findByProfile(id), toolVersion);
        if (asDownload) {
            String safeName = (profile.getName() != null ? profile.getName() : "profile").replaceAll("[^A-Za-z0-9._-]+", "_");
            String fileName = "indicator-profile-" + safeName + "-v" + profile.getProfileVersion() + ".json";
            Http.download(exchange, fileName, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("json", json);
        Http.ok(exchange, mapper, payload);
    }

    private void exportCsv(HttpExchange exchange) throws Exception {
        String csv = importExport.toCsv(profiles.findAll());
        Http.download(exchange, "indicator-profiles.csv", "text/csv; charset=utf-8", csv.getBytes(StandardCharsets.UTF_8));
    }

    private void importProfile(HttpExchange exchange) throws Exception {
        JsonNode body = readBodyTree(exchange);
        String json = textOrNull(body, "json");
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Paste the profile JSON first");
        }
        ProfileImportExportService.ImportResult result = importExport.importAsDraft(json);
        Instant now = Instant.now();
        String technician = textOrNull(body, "technicianName");
        result.profile().setCreatedOn(now).setUpdatedOn(now).setCreatedByName(technician).setUpdatedByName(technician);
        IndicatorProfile saved = profiles.save(result.profile());
        int importedSamples = 0;
        for (CaptureSample sample : result.samples()) {
            sample.setIndicatorProfileNo(saved.getId());
            samples.save(sample);
            importedSamples++;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("message", "Imported as new DRAFT" + (importedSamples > 0 ? " with " + importedSamples + " evidence sample(s)" : ""));
        payload.put("id", saved.getId());
        Http.ok(exchange, mapper, payload);
    }

    // ------------------------------------------------------------------ samples

    private void listSamples(Long id, HttpExchange exchange) throws IOException {
        requireProfile(id);
        Http.ok(exchange, mapper, Map.of("status", "00", "samples", samples.findByProfile(id)));
    }

    /** Persists the most recently received raw chunk (exactly as received) as evidence. */
    private void saveSample(Long id, HttpExchange exchange) throws Exception {
        IndicatorProfile profile = requireProfile(id);
        JsonNode body = readBodyTree(exchange);
        CaptureSessionManager.RawSnapshot snapshot = capture.lastRawSnapshot();
        if (snapshot == null) {
            throw new IllegalStateException("No raw data has been received on this connection yet -- nothing to save");
        }
        IndicatorProfile connected = capture.connectedProfile();
        if (connected == null || !id.equals(connected.getId())) {
            throw new IllegalStateException("The live connection was opened with a different profile -- connect with this profile first");
        }
        String labelText = textOrNull(body, "label");
        CaptureSampleLabel label = labelText != null ? CaptureSampleLabel.valueOf(labelText) : CaptureSampleLabel.UNKNOWN_SAMPLE;
        NormalizedWeightReading lastReading = capture.lastReading();

        CaptureSample sample = new CaptureSample()
                .setIndicatorProfileNo(profile.getId())
                .setStationName(textOrNull(body, "stationName"))
                .setStationCode(textOrNull(body, "stationCode"))
                .setLocation(textOrNull(body, "location"))
                .setLane(textOrNull(body, "lane"))
                .setManufacturer(profile.getManufacturer())
                .setModel(profile.getModel())
                .setFirmwareVersion(profile.getFirmwareVersion())
                .setConnectionType(profile.getConnectionType() != null ? profile.getConnectionType().name() : null)
                .setConnectionSettings(profile.describeConnection())
                .setProfileVersion(profile.getProfileVersion())
                .setLabel(label)
                .setRawAscii(snapshot.ascii())
                .setRawHex(snapshot.hex())
                .setParsedWeight(lastReading != null && lastReading.getWeight() != null ? lastReading.getWeight().toPlainString() : null)
                .setParsedStability(lastReading != null ? lastReading.getStability().name() : null)
                .setParsedRejectionReason(lastReading != null ? lastReading.getRejectionReason() : null)
                .setNotes(textOrNull(body, "notes"))
                .setCaptureSessionId(capture.captureSessionId())
                .setCapturedOn(Instant.now())
                .setCapturedByName(textOrNull(body, "technicianName"));
        CaptureSample saved = samples.save(sample);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("message", "Sample saved as evidence");
        payload.put("id", saved.getId());
        payload.put("sample", saved);
        Http.ok(exchange, mapper, payload);
    }

    private void deleteSample(Long id, HttpExchange exchange) throws IOException {
        if (!samples.delete(id)) {
            throw new IllegalArgumentException("Sample " + id + " not found");
        }
        Http.message(exchange, mapper, "Sample deleted");
    }

    // ------------------------------------------------------------------ capture

    private boolean routeCapture(String method, String[] parts, HttpExchange exchange) throws Exception {
        if (parts.length != 4) {
            return false;
        }
        switch (parts[3]) {
            case "events" -> { sseHub.subscribe(exchange); return true; }
            case "statistics" -> { Http.ok(exchange, mapper, capture.statisticsEvent()); return true; }
            case "connect" -> { connect(exchange); return true; }
            case "disconnect" -> { capture.disconnect(); Http.message(exchange, mapper, "Disconnected"); return true; }
            case "reset-statistics" -> { capture.resetStatistics(); Http.message(exchange, mapper, "Counters reset"); return true; }
            case "request-reading" -> { capture.requestReading(); Http.message(exchange, mapper, "Query command sent"); return true; }
            case "send" -> { sendRaw(exchange); return true; }
            case "apply-rules" -> { applyRules(exchange); return true; }
            case "replay" -> { replay(exchange); return true; }
            case "raw-history" -> { rawHistory(exchange); return true; }
            default -> { return false; }
        }
    }

    private void connect(HttpExchange exchange) throws Exception {
        JsonNode body = readBodyTree(exchange);
        Long id = body.path("profileId").isNumber() ? body.get("profileId").asLong() : null;
        IndicatorProfile profile = requireProfile(id);
        capture.connect(profile);
        Http.message(exchange, mapper, "Connected: " + profile.describeConnection());
    }

    private void sendRaw(HttpExchange exchange) throws Exception {
        JsonNode body = readBodyTree(exchange);
        String text = textOrNull(body, "text");
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Nothing to send");
        }
        capture.sendRaw(Escapes.unescape(text));
        Http.message(exchange, mapper, "Sent");
    }

    private IndicatorProfile rulesFromBody(JsonNode body) throws IOException {
        JsonNode rulesNode = body.get("profile");
        if (rulesNode == null || !rulesNode.isObject()) {
            throw new IllegalArgumentException("Body must contain a profile object with the parsing rules");
        }
        IndicatorProfile rules = mapper.treeToValue(rulesNode, IndicatorProfile.class);
        if (rules.getFramingMode() == null || rules.getExtractionRule() == null) {
            throw new IllegalArgumentException("Framing mode and extraction rule are required");
        }
        return rules;
    }

    private void applyRules(HttpExchange exchange) throws Exception {
        JsonNode body = readBodyTree(exchange);
        capture.applyRules(rulesFromBody(body));
        Http.message(exchange, mapper, "Parsing rules applied to the live stream");
    }

    /** Re-runs framing + extraction + stability over the recently captured raw chunks
     *  (or over pasted text) with the rules in the request, without touching the live
     *  pipeline. This is how a technician tries a rule change against real bytes. */
    private void replay(HttpExchange exchange) throws Exception {
        JsonNode body = readBodyTree(exchange);
        IndicatorProfile rules = rulesFromBody(body);
        String rawText = textOrNull(body, "rawText");
        List<byte[]> chunks = new ArrayList<>();
        String sourceDescription;
        if (rawText != null && !rawText.isEmpty()) {
            chunks.add(Escapes.unescape(rawText).getBytes(StandardCharsets.ISO_8859_1));
            sourceDescription = "pasted text";
        } else {
            chunks = capture.rawHistory();
            sourceDescription = chunks.size() + " captured chunk(s)";
        }
        ParsePipeline pipeline = new ParsePipeline(rules, "replay");
        List<Map<String, Object>> results = new ArrayList<>();
        long valid = 0;
        long invalid = 0;
        long stable = 0;
        int totalBytes = 0;
        for (byte[] chunk : chunks) {
            totalBytes += chunk.length;
            for (ParsePipeline.Event event : pipeline.feed(chunk)) {
                if (event.bufferOverflow()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("stability", "COMMUNICATION_ERROR");
                    row.put("rejectionReason", "buffer overflow: no complete message found within maxBufferBytes -- is the delimiter right?");
                    results.add(row);
                    continue;
                }
                NormalizedWeightReading reading = event.reading();
                if (reading.getWeight() != null) {
                    valid++;
                } else {
                    invalid++;
                }
                if (reading.getStability() == com.tanroads.weighbridge.profilebuilder.model.ReadingStability.STABLE) {
                    stable++;
                }
                if (results.size() < 500) {
                    results.add(CaptureSessionManager.readingEvent(reading, event.rawMessage()));
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "00");
        payload.put("source", sourceDescription);
        payload.put("totalBytes", totalBytes);
        payload.put("messages", valid + invalid);
        payload.put("valid", valid);
        payload.put("invalid", invalid);
        payload.put("stable", stable);
        payload.put("pendingBytes", pipeline.pendingBytes());
        payload.put("results", results);
        Http.ok(exchange, mapper, payload);
    }

    private void rawHistory(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (byte[] chunk : capture.rawHistory()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ascii", Escapes.visibleAscii(chunk, chunk.length));
            row.put("hex", Escapes.hex(chunk, chunk.length));
            row.put("length", chunk.length);
            rows.add(row);
        }
        Http.ok(exchange, mapper, Map.of("status", "00", "chunks", rows));
    }
}
