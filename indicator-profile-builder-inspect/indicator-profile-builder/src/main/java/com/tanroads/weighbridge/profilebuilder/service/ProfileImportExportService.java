package com.tanroads.weighbridge.profilebuilder.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanroads.weighbridge.profilebuilder.model.CaptureSample;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ProfileApprovalStatus;

/**
 * JSON export = the IndicatorProfile fields exactly as the main system expects them, plus
 * a captureSamples array (the evidence) and a small export header. The main system import
 * ignores the extra properties, so the SAME file serves both as this tool's backup/transfer
 * format and as the file an administrator imports into the main system.
 *
 * Import always lands as a new DRAFT, id and audit fields reset, exactly like the main
 * system: an imported profile has not been proven against THIS indicator yet.
 */
public class ProfileImportExportService {

    public static final String EXPORT_FORMAT = "tanroads-indicator-profile/1";

    private static final String[] CSV_HEADER = {
            "id", "name", "manufacturer", "model", "buildVersion", "firmwareVersion", "softwareVersion",
            "protocolName", "protocolVersion", "connectionType", "serialPort", "baudRate", "dataBits",
            "parity", "stopBits", "flowControl", "tcpHost", "tcpPort", "readMode", "queryCommand",
            "framingMode", "messageDelimiter", "fixedMessageLength", "extractionRule", "extractionPattern",
            "weightUnit", "stabilityRule", "stableStatusToken", "profileVersion", "supersedesProfileNo",
            "approvalStatus"
    };

    public record ImportResult(IndicatorProfile profile, List<CaptureSample> samples) { }

    private final ObjectMapper mapper;

    public ProfileImportExportService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> exportBundle(IndicatorProfile profile, List<CaptureSample> samples, String toolVersion) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("exportFormat", EXPORT_FORMAT);
        bundle.put("exportedOn", Instant.now().toString());
        bundle.put("exportedBy", "Indicator Profile Builder " + toolVersion);
        Map<String, Object> fields = mapper.convertValue(profile, new TypeReference<LinkedHashMap<String, Object>>() { });
        bundle.putAll(fields);
        bundle.put("captureSamples", samples != null ? samples : List.of());
        return bundle;
    }

    public String toJson(IndicatorProfile profile, List<CaptureSample> samples, String toolVersion) throws IOException {
        return mapper.writeValueAsString(exportBundle(profile, samples, toolVersion));
    }

    /** Accepts this tool's bundle, a bare main-system IndicatorProfile JSON, or a main
     *  system export -- anything with the IndicatorProfile property names. */
    public ImportResult importAsDraft(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Import text is not a JSON object");
        }
        IndicatorProfile imported = mapper.treeToValue(root, IndicatorProfile.class);
        if (imported.getName() == null || imported.getName().isBlank()) {
            throw new IllegalArgumentException("Imported JSON has no profile name -- is this really an indicator profile?");
        }
        if (imported.getConnectionType() == null) {
            throw new IllegalArgumentException("Imported JSON has no connectionType -- is this really an indicator profile?");
        }
        imported.setId(null);
        imported.setApprovalStatus(ProfileApprovalStatus.DRAFT);
        imported.setCreatedOn(null);
        imported.setUpdatedOn(null);
        imported.setValidatedOn(null);
        imported.setValidatedBy(null);
        imported.setValidatedByName(null);
        if (imported.getProfileVersion() == null) {
            imported.setProfileVersion(1);
        }
        List<CaptureSample> samples = new ArrayList<>();
        JsonNode samplesNode = root.get("captureSamples");
        if (samplesNode != null && samplesNode.isArray()) {
            for (JsonNode sampleNode : samplesNode) {
                CaptureSample sample = mapper.treeToValue(sampleNode, CaptureSample.class);
                sample.setId(null);
                samples.add(sample);
            }
        }
        return new ImportResult(imported, samples);
    }

    public String toCsv(List<IndicatorProfile> profiles) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", CSV_HEADER)).append("\r\n");
        for (IndicatorProfile p : profiles) {
            Object[] row = {
                    p.getId(), p.getName(), p.getManufacturer(), p.getModel(), p.getBuildVersion(), p.getFirmwareVersion(),
                    p.getSoftwareVersion(), p.getProtocolName(), p.getProtocolVersion(), p.getConnectionType(), p.getSerialPort(),
                    p.getBaudRate(), p.getDataBits(), p.getParity(), p.getStopBits(), p.getFlowControl(), p.getTcpHost(), p.getTcpPort(),
                    p.getReadMode(), Escapes.escape(p.getQueryCommand()), p.getFramingMode(), Escapes.escape(p.getMessageDelimiter()),
                    p.getFixedMessageLength(), p.getExtractionRule(), p.getExtractionPattern(), p.getWeightUnit(), p.getStabilityRule(),
                    p.getStableStatusToken(), p.getProfileVersion(), p.getSupersedesProfileNo(), p.getApprovalStatus()
            };
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(csvCell(row[i]));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!needsQuotes) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
