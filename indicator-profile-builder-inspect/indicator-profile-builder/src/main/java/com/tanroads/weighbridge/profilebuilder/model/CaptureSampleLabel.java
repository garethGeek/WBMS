package com.tanroads.weighbridge.profilebuilder.model;

/** How a technician classifies a saved raw capture. Never assigned automatically. */
public enum CaptureSampleLabel {
    STABLE_SAMPLE,
    UNSTABLE_SAMPLE,
    STARTUP_SAMPLE,
    ERROR_SAMPLE,
    UNKNOWN_SAMPLE
}
