package com.tanroads.weighbridge.profilebuilder.model;

/** CONTINUOUS: the indicator streams readings unprompted. COMMAND_QUERY: a command must be
 *  sent and one response awaited before a reading is available. Mirrors the main system. */
public enum ReadMode {
    CONTINUOUS,
    COMMAND_QUERY
}
