package com.tanroads.weighbridge.profilebuilder.model;

/** How a numeric weight (and optional status token) is pulled out of one framed message.
 *  FIRST_NUMBER: first digit run, ignoring surrounding text.
 *  DELIMITED_TOKENS: split on a separator, every numeric token is one value (multi-deck).
 *  FIXED_POSITION: "offset,length" character window.
 *  REGEX: first capturing group is the value.
 *  STATUS_TOKEN: regex with two groups: (status)(value). */
public enum ExtractionRule {
    FIRST_NUMBER,
    DELIMITED_TOKENS,
    FIXED_POSITION,
    REGEX,
    STATUS_TOKEN
}
