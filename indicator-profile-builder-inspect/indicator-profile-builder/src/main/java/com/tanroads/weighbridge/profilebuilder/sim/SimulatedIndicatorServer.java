package com.tanroads.weighbridge.profilebuilder.sim;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * A tiny TCP "indicator" that streams synthetic weight messages, for training and for
 * exercising this tool without hardware. Clearly a SIMULATOR: nothing it produces is
 * evidence about any real indicator. Started with {@code --simulator <port>} or from tests.
 *
 * Default message shape (one per ~250 ms): {@code ST,GS,  12340 kg\r\n} with the status
 * token alternating between ST (stable) and US (unstable) while the weight settles.
 */
public class SimulatedIndicatorServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Supplier<String> messageSupplier;
    private final long intervalMs;
    private final List<Socket> clients = new ArrayList<>();
    private volatile boolean running = true;

    public SimulatedIndicatorServer(int port, long intervalMs) throws IOException {
        this(port, intervalMs, defaultMessages());
    }

    public SimulatedIndicatorServer(int port, long intervalMs, Supplier<String> messageSupplier) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.intervalMs = intervalMs;
        this.messageSupplier = messageSupplier;
        Thread acceptThread = new Thread(this::acceptLoop, "simulated-indicator-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                synchronized (clients) {
                    clients.add(client);
                }
                Thread streamThread = new Thread(() -> streamTo(client), "simulated-indicator-stream");
                streamThread.setDaemon(true);
                streamThread.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void streamTo(Socket client) {
        try (client; OutputStream out = client.getOutputStream()) {
            while (running && !client.isClosed()) {
                out.write(messageSupplier.get().getBytes(StandardCharsets.US_ASCII));
                out.flush();
                Thread.sleep(intervalMs);
            }
        } catch (IOException | InterruptedException ignored) {
            // client went away or we are shutting down
        }
    }

    static Supplier<String> defaultMessages() {
        Random random = new Random();
        int[] state = {0, 12340};
        char cr = (char) 13;
        char lf = (char) 10;
        return () -> {
            state[0]++;
            if (state[0] % 40 == 0) {
                state[1] = 8000 + random.nextInt(30000);
            }
            boolean settling = state[0] % 40 < 8;
            int weight = settling ? state[1] + random.nextInt(60) - 30 : state[1];
            String status = settling ? "US" : "ST";
            return String.format("%s,GS,%7d kg%c%c", status, weight, cr, lf);
        };
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing
        }
        synchronized (clients) {
            for (Socket client : clients) {
                try {
                    client.close();
                } catch (IOException ignored) {
                    // closing
                }
            }
            clients.clear();
        }
    }
}
