package com.tanroads.weighbridge.profilebuilder.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plain (no JPA, no Spring) mirror of tload.indicator.communication.IndicatorProfile.
 * Property names are kept IDENTICAL to that entity so the JSON this tool writes is exactly
 * what IndicatorProfileImportExportService.importAsDraft() in the main system reads.
 * Fields that only make sense in the main system (createdBy/updatedBy/validatedBy user ids)
 * are kept as nullable Longs and left null here; this tool records the technician NAME in
 * separate *Name fields instead, which the main system import ignores (it is configured
 * with FAIL_ON_UNKNOWN_PROPERTIES = false).
 *
 * Delimiters, query commands and similar are stored as the REAL characters (a real CR LF,
 * not the four characters backslash r backslash n) -- exactly what the main system
 * packetizer expects. The web UI shows them escaped for readability; see Escapes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class IndicatorProfile {

    private Long id;
    private String name;
    private String manufacturer;
    private String model;
    private String buildVersion;
    private String firmwareVersion;
    private String softwareVersion;
    private String protocolName;
    private String protocolVersion;
    private Integer profileVersion = 1;
    private Long supersedesProfileNo;
    private ProfileApprovalStatus approvalStatus = ProfileApprovalStatus.DRAFT;
    private String technicianNotes;
    private Instant validatedOn;
    private Long validatedBy;
    private String validatedByName;

    private ConnectionType connectionType;
    private String serialPort;
    private Integer baudRate;
    private Integer dataBits;
    private String parity;
    private Integer stopBits;
    private String flowControl;
    private String tcpHost;
    private Integer tcpPort;

    private ReadMode readMode;
    private String queryCommand;
    private FramingMode framingMode;
    private String messageDelimiter;
    private Integer fixedMessageLength;
    private ExtractionRule extractionRule;
    private String extractionPattern;
    private String weightUnit = "kg";
    private StabilityRule stabilityRule = StabilityRule.NONE;
    private String stableStatusToken;
    private Integer requiredConsecutiveMatches;
    private Integer stabilityDurationMs;
    private BigDecimal stabilityTolerance;
    private Integer connectTimeoutMs = 10000;
    private Integer readTimeoutMs = 5000;
    private Integer reconnectIntervalMs = 5000;
    private Integer maxBufferBytes = 4096;
    private String flag = "1";

    private Instant createdOn;
    private Long createdBy;
    private String createdByName;
    private Instant updatedOn;
    private Long updatedBy;
    private String updatedByName;

    public Long getId() { return id; }
    public IndicatorProfile setId(Long id) { this.id = id; return this; }
    public String getName() { return name; }
    public IndicatorProfile setName(String name) { this.name = name; return this; }
    public String getManufacturer() { return manufacturer; }
    public IndicatorProfile setManufacturer(String manufacturer) { this.manufacturer = manufacturer; return this; }
    public String getModel() { return model; }
    public IndicatorProfile setModel(String model) { this.model = model; return this; }
    public String getBuildVersion() { return buildVersion; }
    public IndicatorProfile setBuildVersion(String buildVersion) { this.buildVersion = buildVersion; return this; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public IndicatorProfile setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; return this; }
    public String getSoftwareVersion() { return softwareVersion; }
    public IndicatorProfile setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; return this; }
    public String getProtocolName() { return protocolName; }
    public IndicatorProfile setProtocolName(String protocolName) { this.protocolName = protocolName; return this; }
    public String getProtocolVersion() { return protocolVersion; }
    public IndicatorProfile setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; return this; }
    public Integer getProfileVersion() { return profileVersion; }
    public IndicatorProfile setProfileVersion(Integer profileVersion) { this.profileVersion = profileVersion; return this; }
    public Long getSupersedesProfileNo() { return supersedesProfileNo; }
    public IndicatorProfile setSupersedesProfileNo(Long supersedesProfileNo) { this.supersedesProfileNo = supersedesProfileNo; return this; }
    public ProfileApprovalStatus getApprovalStatus() { return approvalStatus; }
    public IndicatorProfile setApprovalStatus(ProfileApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; return this; }
    public String getTechnicianNotes() { return technicianNotes; }
    public IndicatorProfile setTechnicianNotes(String technicianNotes) { this.technicianNotes = technicianNotes; return this; }
    public Instant getValidatedOn() { return validatedOn; }
    public IndicatorProfile setValidatedOn(Instant validatedOn) { this.validatedOn = validatedOn; return this; }
    public Long getValidatedBy() { return validatedBy; }
    public IndicatorProfile setValidatedBy(Long validatedBy) { this.validatedBy = validatedBy; return this; }
    public String getValidatedByName() { return validatedByName; }
    public IndicatorProfile setValidatedByName(String validatedByName) { this.validatedByName = validatedByName; return this; }
    public ConnectionType getConnectionType() { return connectionType; }
    public IndicatorProfile setConnectionType(ConnectionType connectionType) { this.connectionType = connectionType; return this; }
    public String getSerialPort() { return serialPort; }
    public IndicatorProfile setSerialPort(String serialPort) { this.serialPort = serialPort; return this; }
    public Integer getBaudRate() { return baudRate; }
    public IndicatorProfile setBaudRate(Integer baudRate) { this.baudRate = baudRate; return this; }
    public Integer getDataBits() { return dataBits; }
    public IndicatorProfile setDataBits(Integer dataBits) { this.dataBits = dataBits; return this; }
    public String getParity() { return parity; }
    public IndicatorProfile setParity(String parity) { this.parity = parity; return this; }
    public Integer getStopBits() { return stopBits; }
    public IndicatorProfile setStopBits(Integer stopBits) { this.stopBits = stopBits; return this; }
    public String getFlowControl() { return flowControl; }
    public IndicatorProfile setFlowControl(String flowControl) { this.flowControl = flowControl; return this; }
    public String getTcpHost() { return tcpHost; }
    public IndicatorProfile setTcpHost(String tcpHost) { this.tcpHost = tcpHost; return this; }
    public Integer getTcpPort() { return tcpPort; }
    public IndicatorProfile setTcpPort(Integer tcpPort) { this.tcpPort = tcpPort; return this; }
    public ReadMode getReadMode() { return readMode; }
    public IndicatorProfile setReadMode(ReadMode readMode) { this.readMode = readMode; return this; }
    public String getQueryCommand() { return queryCommand; }
    public IndicatorProfile setQueryCommand(String queryCommand) { this.queryCommand = queryCommand; return this; }
    public FramingMode getFramingMode() { return framingMode; }
    public IndicatorProfile setFramingMode(FramingMode framingMode) { this.framingMode = framingMode; return this; }
    public String getMessageDelimiter() { return messageDelimiter; }
    public IndicatorProfile setMessageDelimiter(String messageDelimiter) { this.messageDelimiter = messageDelimiter; return this; }
    public Integer getFixedMessageLength() { return fixedMessageLength; }
    public IndicatorProfile setFixedMessageLength(Integer fixedMessageLength) { this.fixedMessageLength = fixedMessageLength; return this; }
    public ExtractionRule getExtractionRule() { return extractionRule; }
    public IndicatorProfile setExtractionRule(ExtractionRule extractionRule) { this.extractionRule = extractionRule; return this; }
    public String getExtractionPattern() { return extractionPattern; }
    public IndicatorProfile setExtractionPattern(String extractionPattern) { this.extractionPattern = extractionPattern; return this; }
    public String getWeightUnit() { return weightUnit; }
    public IndicatorProfile setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; return this; }
    public StabilityRule getStabilityRule() { return stabilityRule; }
    public IndicatorProfile setStabilityRule(StabilityRule stabilityRule) { this.stabilityRule = stabilityRule; return this; }
    public String getStableStatusToken() { return stableStatusToken; }
    public IndicatorProfile setStableStatusToken(String stableStatusToken) { this.stableStatusToken = stableStatusToken; return this; }
    public Integer getRequiredConsecutiveMatches() { return requiredConsecutiveMatches; }
    public IndicatorProfile setRequiredConsecutiveMatches(Integer requiredConsecutiveMatches) { this.requiredConsecutiveMatches = requiredConsecutiveMatches; return this; }
    public Integer getStabilityDurationMs() { return stabilityDurationMs; }
    public IndicatorProfile setStabilityDurationMs(Integer stabilityDurationMs) { this.stabilityDurationMs = stabilityDurationMs; return this; }
    public BigDecimal getStabilityTolerance() { return stabilityTolerance; }
    public IndicatorProfile setStabilityTolerance(BigDecimal stabilityTolerance) { this.stabilityTolerance = stabilityTolerance; return this; }
    public Integer getConnectTimeoutMs() { return connectTimeoutMs; }
    public IndicatorProfile setConnectTimeoutMs(Integer connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; return this; }
    public Integer getReadTimeoutMs() { return readTimeoutMs; }
    public IndicatorProfile setReadTimeoutMs(Integer readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; return this; }
    public Integer getReconnectIntervalMs() { return reconnectIntervalMs; }
    public IndicatorProfile setReconnectIntervalMs(Integer reconnectIntervalMs) { this.reconnectIntervalMs = reconnectIntervalMs; return this; }
    public Integer getMaxBufferBytes() { return maxBufferBytes; }
    public IndicatorProfile setMaxBufferBytes(Integer maxBufferBytes) { this.maxBufferBytes = maxBufferBytes; return this; }
    public String getFlag() { return flag; }
    public IndicatorProfile setFlag(String flag) { this.flag = flag; return this; }
    public Instant getCreatedOn() { return createdOn; }
    public IndicatorProfile setCreatedOn(Instant createdOn) { this.createdOn = createdOn; return this; }
    public Long getCreatedBy() { return createdBy; }
    public IndicatorProfile setCreatedBy(Long createdBy) { this.createdBy = createdBy; return this; }
    public String getCreatedByName() { return createdByName; }
    public IndicatorProfile setCreatedByName(String createdByName) { this.createdByName = createdByName; return this; }
    public Instant getUpdatedOn() { return updatedOn; }
    public IndicatorProfile setUpdatedOn(Instant updatedOn) { this.updatedOn = updatedOn; return this; }
    public Long getUpdatedBy() { return updatedBy; }
    public IndicatorProfile setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; return this; }
    public String getUpdatedByName() { return updatedByName; }
    public IndicatorProfile setUpdatedByName(String updatedByName) { this.updatedByName = updatedByName; return this; }

    /** Copies every CONFIGURATION field (identity + connection + parsing + stability) but
     *  none of the id/lifecycle/audit fields -- the basis of "create new version". */
    public IndicatorProfile copyConfigurationTo(IndicatorProfile target) {
        target.setName(name).setManufacturer(manufacturer).setModel(model)
                .setBuildVersion(buildVersion).setFirmwareVersion(firmwareVersion).setSoftwareVersion(softwareVersion)
                .setProtocolName(protocolName).setProtocolVersion(protocolVersion)
                .setTechnicianNotes(technicianNotes)
                .setConnectionType(connectionType).setSerialPort(serialPort).setBaudRate(baudRate)
                .setDataBits(dataBits).setParity(parity).setStopBits(stopBits).setFlowControl(flowControl)
                .setTcpHost(tcpHost).setTcpPort(tcpPort)
                .setReadMode(readMode).setQueryCommand(queryCommand)
                .setFramingMode(framingMode).setMessageDelimiter(messageDelimiter).setFixedMessageLength(fixedMessageLength)
                .setExtractionRule(extractionRule).setExtractionPattern(extractionPattern).setWeightUnit(weightUnit)
                .setStabilityRule(stabilityRule).setStableStatusToken(stableStatusToken)
                .setRequiredConsecutiveMatches(requiredConsecutiveMatches).setStabilityDurationMs(stabilityDurationMs)
                .setStabilityTolerance(stabilityTolerance)
                .setConnectTimeoutMs(connectTimeoutMs).setReadTimeoutMs(readTimeoutMs)
                .setReconnectIntervalMs(reconnectIntervalMs).setMaxBufferBytes(maxBufferBytes);
        return target;
    }

    public String describeConnection() {
        if (connectionType == ConnectionType.TCP) {
            return tcpHost + ":" + tcpPort;
        }
        String parityLetter = parity != null && !parity.isEmpty() ? parity.substring(0, 1).toUpperCase() : "N";
        return serialPort + " @" + baudRate + "-" + dataBits + "-" + parityLetter + "-" + stopBits;
    }
}
