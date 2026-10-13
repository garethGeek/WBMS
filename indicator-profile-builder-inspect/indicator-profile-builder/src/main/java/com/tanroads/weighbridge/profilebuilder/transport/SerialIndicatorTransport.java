package com.tanroads.weighbridge.profilebuilder.transport;

import java.io.IOException;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

/**
 * jSerialComm serial transport (same library and version as the main system). Every
 * parameter comes from the profile. A native-library load failure (jSerialComm throws an
 * Error, not an Exception, when its DLL cannot load) is normalized into an IOException so
 * the UI can show it as a plain connection error.
 */
public class SerialIndicatorTransport implements IndicatorTransport {

    private final String portDescriptor;
    private final int baudRate;
    private final int dataBits;
    private final int stopBits;
    private final String parity;
    private final String flowControl;

    private SerialPort serialPort;
    private volatile boolean connected;
    private TransportListener listener;

    public SerialIndicatorTransport(String portDescriptor, int baudRate, int dataBits, int stopBits,
                                    String parity, String flowControl) {
        this.portDescriptor = portDescriptor;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.flowControl = flowControl;
    }

    @Override
    public void setListener(TransportListener listener) {
        this.listener = listener;
    }

    @Override
    public synchronized void connect() throws IOException {
        if (connected) {
            return;
        }
        if (portDescriptor == null || portDescriptor.isBlank()) {
            throw new IOException("No COM port configured");
        }
        SerialPort port;
        try {
            port = SerialPort.getCommPort(portDescriptor);
            port.setComPortParameters(baudRate, dataBits, mapStopBits(stopBits), mapParity(parity));
            port.setFlowControl(mapFlowControl(flowControl));
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
        } catch (Throwable t) {
            throw new IOException("Serial transport unavailable: " + t.getMessage(), t);
        }
        if (!port.openPort()) {
            throw new IOException("Unable to open serial port " + portDescriptor
                    + " (does it exist, and is it already in use by another program?)");
        }
        serialPort = port;
        connected = true;
        port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
                    handleUnexpectedDisconnect();
                    return;
                }
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    return;
                }
                int available = port.bytesAvailable();
                if (available <= 0) {
                    return;
                }
                byte[] buffer = new byte[available];
                int read = port.readBytes(buffer, buffer.length);
                if (read > 0 && listener != null) {
                    listener.onData(buffer, read);
                }
            }
        });
        if (listener != null) {
            listener.onConnected();
        }
    }

    private synchronized void handleUnexpectedDisconnect() {
        if (!connected) {
            return;
        }
        connected = false;
        if (serialPort != null) {
            serialPort.removeDataListener();
            serialPort.closePort();
        }
        if (listener != null) {
            listener.onError(new IOException("Serial port " + portDescriptor + " was disconnected (cable unplugged?)"));
            listener.onDisconnected(false);
        }
    }

    static int mapParity(String parity) {
        if (parity == null) {
            return SerialPort.NO_PARITY;
        }
        return switch (parity.trim().toUpperCase()) {
            case "ODD" -> SerialPort.ODD_PARITY;
            case "EVEN" -> SerialPort.EVEN_PARITY;
            case "MARK" -> SerialPort.MARK_PARITY;
            case "SPACE" -> SerialPort.SPACE_PARITY;
            default -> SerialPort.NO_PARITY;
        };
    }

    static int mapStopBits(int stopBits) {
        return stopBits == 2 ? SerialPort.TWO_STOP_BITS : SerialPort.ONE_STOP_BIT;
    }

    static int mapFlowControl(String flowControl) {
        if (flowControl == null) {
            return SerialPort.FLOW_CONTROL_DISABLED;
        }
        return switch (flowControl.trim().toUpperCase()) {
            case "RTS_CTS" -> SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            case "XON_XOFF" -> SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
            default -> SerialPort.FLOW_CONTROL_DISABLED;
        };
    }

    @Override
    public synchronized void disconnect() {
        boolean wasConnected = connected;
        connected = false;
        if (serialPort != null) {
            serialPort.removeDataListener();
            serialPort.closePort();
            serialPort = null;
        }
        if (wasConnected && listener != null) {
            listener.onDisconnected(true);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void send(byte[] data) throws IOException {
        SerialPort port = serialPort;
        if (!connected || port == null) {
            throw new IOException("Not connected");
        }
        int written = port.writeBytes(data, data.length);
        if (written != data.length) {
            throw new IOException("Serial write incomplete: wrote " + written + " of " + data.length + " bytes");
        }
    }

    @Override
    public String describe() {
        return "SERIAL " + portDescriptor + " @" + baudRate;
    }
}
