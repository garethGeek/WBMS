package com.tanroads.weighbridge.profilebuilder.model;

/** Mirrors tload.indicator.communication.ConnectionType exactly -- the main system only
 *  knows these two values, so this tool deliberately offers no others. */
public enum ConnectionType {
    SERIAL,
    TCP
}
