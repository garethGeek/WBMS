package com.tanroads.weighbridge.profilebuilder.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One piece of raw evidence captured during a real survey session, exactly as received.
 * Mirrors tload.indicator.communication.CaptureSample (denormalized on purpose: the
 * station/indicator/connection details are copied at capture time so a sample always shows
 * what configuration was active when THAT byte sequence arrived). The parsed result the
 * active rules produced for the same bytes is recorded alongside so a reviewer can see
 * RAW vs PARSED side by side without re-running anything.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaptureSample {

    private Long id;
    private Long indicatorProfileNo;
    private String stationName;
    private String stationCode;
    private String location;
    private String lane;
    private String manufacturer;
    private String model;
    private String firmwareVersion;
    private String connectionType;
    private String connectionSettings;
    private Integer profileVersion;
    private CaptureSampleLabel label;
    private String rawAscii;
    private String rawHex;
    private String parsedWeight;
    private String parsedStability;
    private String parsedRejectionReason;
    private String notes;
    private String captureSessionId;
    private Instant capturedOn;
    private Long capturedBy;
    private String capturedByName;

    public Long getId() { return id; }
    public CaptureSample setId(Long id) { this.id = id; return this; }
    public Long getIndicatorProfileNo() { return indicatorProfileNo; }
    public CaptureSample setIndicatorProfileNo(Long indicatorProfileNo) { this.indicatorProfileNo = indicatorProfileNo; return this; }
    public String getStationName() { return stationName; }
    public CaptureSample setStationName(String stationName) { this.stationName = stationName; return this; }
    public String getStationCode() { return stationCode; }
    public CaptureSample setStationCode(String stationCode) { this.stationCode = stationCode; return this; }
    public String getLocation() { return location; }
    public CaptureSample setLocation(String location) { this.location = location; return this; }
    public String getLane() { return lane; }
    public CaptureSample setLane(String lane) { this.lane = lane; return this; }
    public String getManufacturer() { return manufacturer; }
    public CaptureSample setManufacturer(String manufacturer) { this.manufacturer = manufacturer; return this; }
    public String getModel() { return model; }
    public CaptureSample setModel(String model) { this.model = model; return this; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public CaptureSample setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; return this; }
    public String getConnectionType() { return connectionType; }
    public CaptureSample setConnectionType(String connectionType) { this.connectionType = connectionType; return this; }
    public String getConnectionSettings() { return connectionSettings; }
    public CaptureSample setConnectionSettings(String connectionSettings) { this.connectionSettings = connectionSettings; return this; }
    public Integer getProfileVersion() { return profileVersion; }
    public CaptureSample setProfileVersion(Integer profileVersion) { this.profileVersion = profileVersion; return this; }
    public CaptureSampleLabel getLabel() { return label; }
    public CaptureSample setLabel(CaptureSampleLabel label) { this.label = label; return this; }
    public String getRawAscii() { return rawAscii; }
    public CaptureSample setRawAscii(String rawAscii) { this.rawAscii = rawAscii; return this; }
    public String getRawHex() { return rawHex; }
    public CaptureSample setRawHex(String rawHex) { this.rawHex = rawHex; return this; }
    public String getParsedWeight() { return parsedWeight; }
    public CaptureSample setParsedWeight(String parsedWeight) { this.parsedWeight = parsedWeight; return this; }
    public String getParsedStability() { return parsedStability; }
    public CaptureSample setParsedStability(String parsedStability) { this.parsedStability = parsedStability; return this; }
    public String getParsedRejectionReason() { return parsedRejectionReason; }
    public CaptureSample setParsedRejectionReason(String parsedRejectionReason) { this.parsedRejectionReason = parsedRejectionReason; return this; }
    public String getNotes() { return notes; }
    public CaptureSample setNotes(String notes) { this.notes = notes; return this; }
    public String getCaptureSessionId() { return captureSessionId; }
    public CaptureSample setCaptureSessionId(String captureSessionId) { this.captureSessionId = captureSessionId; return this; }
    public Instant getCapturedOn() { return capturedOn; }
    public CaptureSample setCapturedOn(Instant capturedOn) { this.capturedOn = capturedOn; return this; }
    public Long getCapturedBy() { return capturedBy; }
    public CaptureSample setCapturedBy(Long capturedBy) { this.capturedBy = capturedBy; return this; }
    public String getCapturedByName() { return capturedByName; }
    public CaptureSample setCapturedByName(String capturedByName) { this.capturedByName = capturedByName; return this; }
}
