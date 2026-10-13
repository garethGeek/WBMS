package wbms.tanroads.wbms.capture;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import wbms.tanroads.wbms.dtos.SimulatorStatusDto;
import wbms.tanroads.wbms.enums.WeightDisplayMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import wbms.tanroads.wbms.repositories.IndicatorProfileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeighbridgeSimulatorService {

    private final IndicatorProfileRepository profileRepository;

    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean running = false;
    private volatile String host = "0.0.0.0";
    private volatile int port = 2016;
    private volatile double weightKg = 0;
    private volatile boolean stable = true;
    private volatile WeightDisplayMode weightDisplayMode = WeightDisplayMode.SINGLE_CELL;
    private volatile List<Double> cellWeightsKg = List.of();
    private volatile List<Double> cellReferenceWeightsKg = List.of();
    private volatile boolean framingEnabled = false;
    private volatile String startBytes = "02";
    private volatile String endBytes = "03";
    private volatile String separator = "049";
    private volatile String profileUuid;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledFuture<?> streamTask;
    private ScheduledFuture<?> demoTask;

    public synchronized SimulatorStatusDto start(String requestedHost, Integer requestedPort, Double requestedWeightKg,
                                                 WeightDisplayMode requestedDisplayMode, List<Double> requestedCellWeightsKg,
                                                 Boolean requestedFramingEnabled, String requestedStartBytes, String requestedEndBytes,
                                                 String requestedSeparator, String requestedProfileUuid) {
        if (running) {
            return status();
        }

        host = requestedHost == null || requestedHost.isBlank() ? "0.0.0.0" : requestedHost.trim();
        port = requestedPort == null || requestedPort < 1 || requestedPort > 65535 ? 2016 : requestedPort;
        cellWeightsKg = cleanCellWeights(requestedCellWeightsKg);
        weightKg = requestedWeightKg == null ? 0 : requestedWeightKg;
        stable = true;
        weightDisplayMode = requestedDisplayMode == null ? WeightDisplayMode.SINGLE_CELL : requestedDisplayMode;
        profileUuid = requestedProfileUuid;
        if (profileUuid != null && !profileUuid.isBlank()) {
            var profile = profileRepository.findByUuidAndActiveTrue(profileUuid).orElse(null);
            if (profile != null) {
                requestedFramingEnabled = true;
                requestedStartBytes = profile.getStartBytes();
                requestedEndBytes = profile.getEndBytes();
                requestedSeparator = profile.getSeparator();
            }
        }
        framingEnabled = Boolean.TRUE.equals(requestedFramingEnabled);
        startBytes = requestedStartBytes == null || requestedStartBytes.isBlank() ? "02" : requestedStartBytes.trim();
        endBytes = requestedEndBytes == null || requestedEndBytes.isBlank() ? "03" : requestedEndBytes.trim();
        separator = normalizeSeparator(requestedSeparator);
        if (weightDisplayMode == WeightDisplayMode.MULTI_CELL && cellWeightsKg.isEmpty()) {
            throw new IllegalArgumentException("cellWeightsKg is required for MULTI_CELL simulation");
        }
        if (weightDisplayMode == WeightDisplayMode.MULTI_CELL) {
            cellReferenceWeightsKg = List.copyOf(cellWeightsKg);
            weightKg = cellWeightsKg.stream().mapToDouble(Double::doubleValue).sum();
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            acceptThread = new Thread(this::acceptLoop, "wbms-indicator-simulator-accept");
            acceptThread.start();
            streamTask = scheduler.scheduleAtFixedRate(() -> broadcast(formatReading()), 0, 1, TimeUnit.SECONDS);
            startVehicleCycle();
            log.info("WBMS simulator started on {}:{} with {} kg", host, port, weightKg);
            return status();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start simulator on port " + port + ": " + e.getMessage(), e);
        }
    }

    public synchronized SimulatorStatusDto stop() {
        running = false;
        stopDemo();
        if (streamTask != null) {
            streamTask.cancel(true);
            streamTask = null;
        }
        closeServerSocket();
        for (Socket client : clients) {
            closeClient(client);
        }
        clients.clear();
        log.info("WBMS simulator stopped");
        return status();
    }

    public synchronized SimulatorStatusDto setWeight(Double newWeightKg, Boolean isStable, List<Double> requestedCellWeightsKg) {
        stopDemo();
        if (weightDisplayMode == WeightDisplayMode.MULTI_CELL && requestedCellWeightsKg != null && !requestedCellWeightsKg.isEmpty()) {
            cellWeightsKg = cleanCellWeights(requestedCellWeightsKg);
            cellReferenceWeightsKg = List.copyOf(cellWeightsKg);
            newWeightKg = cellWeightsKg.stream().mapToDouble(Double::doubleValue).sum();
        }
        updateWeight(newWeightKg == null ? 0 : newWeightKg, isStable == null || isStable);
        return status();
    }

    public synchronized SimulatorStatusDto runDemo() {
        ensureRunning();
        startVehicleCycle();
        return status();
    }

    private synchronized void startVehicleCycle() {
        stopDemo();

        double fullWeightKg = weightKg > 0 ? weightKg : 32480;
        double[] weights = buildVehicleCrossingProfile(fullWeightKg);
        final int[] index = {0};

        updateWeight(weights[0], true);
        demoTask = scheduler.scheduleAtFixedRate(() -> {
            index[0] = (index[0] + 1) % weights.length;
            if (index[0] == 0) {
                updateWeight(0d, true);
                return;
            }
            boolean isStable = index[0] >= 24 && index[0] <= 34 || index[0] == weights.length - 1;
            updateWeight(weights[index[0]], isStable);
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public SimulatorStatusDto status() {
        return SimulatorStatusDto.builder()
                .running(running)
                .host(host)
                .port(port)
                .weightKg(weightKg)
                .stable(stable)
                .vehicleCycleRunning(demoTask != null && !demoTask.isCancelled())
                .clients(clients.size())
                .rawLine(formatReading())
                .weightDisplayMode(weightDisplayMode)
                .cellWeightsKg(cellWeights())
                .framingEnabled(framingEnabled).startBytes(startBytes).endBytes(endBytes).separator(separator)
                .profileUuid(profileUuid)
                .build();
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                clients.add(client);
                send(client, formatReading());
                log.info("WBMS simulator client connected: {}:{}", client.getInetAddress(), client.getPort());
            } catch (IOException e) {
                if (running) {
                    log.warn("WBMS simulator accept failed: {}", e.getMessage());
                }
            }
        }
    }

    private void broadcast(String line) {
        for (Socket client : clients) {
            if (!send(client, line)) {
                closeClient(client);
                clients.remove(client);
            }
        }
    }

    private boolean send(Socket client, String line) {
        try {
            String payload = line + ", ";
            client.getOutputStream().write(payload.getBytes());
            client.getOutputStream().flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void updateWeight(double newWeightKg, boolean isStable) {
        if (weightDisplayMode == WeightDisplayMode.MULTI_CELL && !cellReferenceWeightsKg.isEmpty()) {
            double baseTotal = cellReferenceWeightsKg.stream().mapToDouble(Double::doubleValue).sum();
            if (baseTotal > 0) {
                double scale = newWeightKg / baseTotal;
                cellWeightsKg = cellReferenceWeightsKg.stream().map(value -> value * scale).toList();
            }
        }
        weightKg = newWeightKg;
        stable = isStable;
        broadcast(formatReading());
    }

    private String formatReading() {
        if (weightDisplayMode == WeightDisplayMode.MULTI_CELL) {
            String values = cellWeightsKg.stream()
                    .map(value -> String.format("%02d", Math.round(value)))
                    .reduce((left, right) -> left + separator + right)
                    .orElse("");
            // Simulate the TANROADS five-part frame: four cells followed by
            // the indicator's GVM value.
            if (!values.isEmpty()) {
                values = values + separator + String.format("%02d", Math.round(weightKg));
            }
            return frame(values);
        }
        String status = stable ? "ST" : "US";
        String sign = weightKg < 0 ? "-" : "+";
        String value = String.valueOf(Math.abs(Math.round(weightKg))).replaceAll("\\.0$", "");
        return frame(status + ",GS," + sign + String.format("%07d", Long.parseLong(value)) + " kg");
    }

    private String frame(String payload) {
        if (!framingEnabled) return payload + ", ";
        try {
            return new String(CustomFrameDecoder.parseHex(startBytes), java.nio.charset.StandardCharsets.ISO_8859_1)
                    + payload + new String(CustomFrameDecoder.parseHex(endBytes), java.nio.charset.StandardCharsets.ISO_8859_1);
        } catch (RuntimeException e) {
            log.warn("Invalid simulator frame HEX; sending unframed payload: {}", e.getMessage());
            return payload + ", ";
        }
    }

    private String normalizeSeparator(String value) {
        if (value == null) return " ";
        if (value.trim().equalsIgnoreCase("SPACE") || value.equals("\\s")) return " ";
        return value;
    }

    private List<Double> cellWeights() {
        if (weightDisplayMode != WeightDisplayMode.MULTI_CELL) return List.of();
        return List.copyOf(cellWeightsKg);
    }

    private List<Double> cleanCellWeights(List<Double> values) {
        if (values == null) return List.of();
        List<Double> clean = new ArrayList<>();
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) clean.add(value);
        }
        return List.copyOf(clean);
    }

    private double[] buildVehicleCrossingProfile(double fullWeightKg) {
        double[] profile = new double[54];
        int index = 0;

        for (int i = 0; i < 6; i++) {
            profile[index++] = 0;
        }

        for (int i = 1; i <= 18; i++) {
            double progress = i / 18d;
            profile[index++] = Math.round(fullWeightKg * easeInOut(progress));
        }

        for (int i = 0; i < 12; i++) {
            double vibration = i % 2 == 0 ? 0 : Math.min(40, fullWeightKg * 0.001);
            profile[index++] = Math.round(fullWeightKg + vibration);
        }

        for (int i = 17; i >= 0; i--) {
            double progress = i / 18d;
            profile[index++] = Math.round(fullWeightKg * easeInOut(progress));
        }

        return profile;
    }

    private double easeInOut(double progress) {
        return progress < 0.5
                ? 2 * progress * progress
                : 1 - Math.pow(-2 * progress + 2, 2) / 2;
    }

    private void ensureRunning() {
        if (!running) {
            throw new RuntimeException("Simulator is not running");
        }
    }

    private synchronized void stopDemo() {
        if (demoTask != null) {
            demoTask.cancel(true);
            demoTask = null;
        }
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        serverSocket = null;
    }

    private void closeClient(Socket client) {
        try {
            if (client != null && !client.isClosed()) {
                client.close();
            }
        } catch (IOException ignored) {
        }
    }
}
