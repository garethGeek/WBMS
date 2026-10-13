package com.tanroads.weighbridge.profilebuilder.model;

/** How one complete message is found in the byte stream. DELIMITER: a configured byte
 *  sequence (e.g. CR LF) ends every message. FIXED_LENGTH: exactly N bytes per message. */
public enum FramingMode {
    DELIMITER,
    FIXED_LENGTH
}
