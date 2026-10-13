package com.tanroads.weighbridge.profilebuilder.transport;

/** Callbacks a transport drives. */
public interface TransportListener {
    void onConnected();

    void onData(byte[] chunk, int length);

    /** wasExpected is true only when the caller itself requested disconnect(). */
    void onDisconnected(boolean wasExpected);

    void onError(Exception e);
}
