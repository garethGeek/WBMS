package com.tanroads.weighbridge.profilebuilder.transport;

/** Any way of exchanging bytes with a weighbridge indicator. The parse pipeline is written
 *  entirely against this interface. Port of the main system contract. */
public interface IndicatorTransport {
    void connect() throws Exception;

    /** Caller-initiated clean disconnect. Safe to call when not connected; must fully
     *  release the underlying socket/port. */
    void disconnect();

    boolean isConnected();

    void setListener(TransportListener listener);

    /** Only meaningful in ReadMode.COMMAND_QUERY. */
    void send(byte[] data) throws Exception;

    /** Human-readable description of the endpoint, for the UI. */
    String describe();
}
