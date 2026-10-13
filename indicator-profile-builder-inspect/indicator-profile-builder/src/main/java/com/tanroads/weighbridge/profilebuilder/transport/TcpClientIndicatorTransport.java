package com.tanroads.weighbridge.profilebuilder.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Plain JDK socket TCP client: one blocking background reader thread per connection.
 * Port of the main system transport. readTimeoutMs is applied as SO_TIMEOUT so a silent,
 * idle indicator surfaces as a (non-fatal) read timeout rather than hanging forever.
 */
public class TcpClientIndicatorTransport implements IndicatorTransport {

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private Socket socket;
    private volatile boolean connected;
    private volatile boolean expectedDisconnect;
    private TransportListener listener;

    public TcpClientIndicatorTransport(String host, int port, int connectTimeoutMs, int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
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
        if (host == null || host.isBlank()) {
            throw new IOException("No IP address / host configured");
        }
        if (port < 1 || port > 65535) {
            throw new IOException("TCP port must be between 1 and 65535");
        }
        expectedDisconnect = false;
        Socket ownSocket = new Socket();
        ownSocket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        ownSocket.setSoTimeout(readTimeoutMs);
        ownSocket.setKeepAlive(true);
        socket = ownSocket;
        connected = true;
        if (listener != null) {
            listener.onConnected();
        }
        Thread readerThread = new Thread(() -> readLoop(ownSocket), "indicator-tcp-" + host + ":" + port);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop(Socket ownSocket) {
        byte[] buffer = new byte[1024];
        InputStream input;
        try {
            input = ownSocket.getInputStream();
        } catch (IOException e) {
            handleFailure(ownSocket, e);
            return;
        }
        while (socket == ownSocket && connected) {
            try {
                int read = input.read(buffer);
                if (read == -1) {
                    handleFailure(ownSocket, null);
                    return;
                }
                if (listener != null) {
                    listener.onData(buffer, read);
                }
            } catch (SocketTimeoutException e) {
                if (listener != null && socket == ownSocket && connected) {
                    listener.onError(new IOException("No data received for " + readTimeoutMs + " ms (read timeout) -- still connected"));
                }
            } catch (IOException e) {
                handleFailure(ownSocket, e);
                return;
            }
        }
    }

    private synchronized void handleFailure(Socket ownSocket, IOException cause) {
        if (socket != ownSocket) {
            return;
        }
        boolean wasExpected = expectedDisconnect;
        connected = false;
        closeQuietly(ownSocket);
        if (listener != null) {
            if (cause != null && !wasExpected) {
                listener.onError(cause);
            }
            listener.onDisconnected(wasExpected);
        }
    }

    @Override
    public synchronized void disconnect() {
        if (!connected && socket == null) {
            return;
        }
        expectedDisconnect = true;
        connected = false;
        closeQuietly(socket);
        socket = null;
        if (listener != null) {
            listener.onDisconnected(true);
        }
    }

    private static void closeQuietly(Socket target) {
        if (target != null) {
            try {
                target.close();
            } catch (IOException ignored) {
                // already closing
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void send(byte[] data) throws IOException {
        Socket current = socket;
        if (!connected || current == null) {
            throw new IOException("Not connected");
        }
        OutputStream out = current.getOutputStream();
        out.write(data);
        out.flush();
    }

    @Override
    public String describe() {
        return "TCP " + host + ":" + port;
    }
}
