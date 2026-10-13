package com.tanroads.weighbridge.profilebuilder;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tanroads.weighbridge.profilebuilder.service.CaptureSessionManager;
import com.tanroads.weighbridge.profilebuilder.service.ProfileImportExportService;
import com.tanroads.weighbridge.profilebuilder.service.ProfileLifecycleService;
import com.tanroads.weighbridge.profilebuilder.sim.SimulatedIndicatorServer;
import com.tanroads.weighbridge.profilebuilder.store.JsonMapperFactory;
import com.tanroads.weighbridge.profilebuilder.store.ProfileRepository;
import com.tanroads.weighbridge.profilebuilder.store.SampleRepository;
import com.tanroads.weighbridge.profilebuilder.web.HttpApi;
import com.tanroads.weighbridge.profilebuilder.web.SseHub;
import com.tanroads.weighbridge.profilebuilder.web.StaticFiles;

/**
 * Entry point. Starts a local web server (default http://127.0.0.1:8765), stores profiles
 * as JSON files in a data folder next to the program, and opens the browser.
 *
 * Usage: java -jar indicator-profile-builder.jar [--port 8765] [--host 127.0.0.1]
 *        [--data <dir>] [--no-browser] [--simulator <tcpPort>]
 */
public final class ProfileBuilderApp {

    public static final String VERSION = "1.0.0";

    private ProfileBuilderApp() {
    }

    public static void main(String[] args) throws Exception {
        int port = 8765;
        String host = "127.0.0.1";
        Path dataDir = defaultDataDirectory();
        boolean openBrowser = true;
        int simulatorPort = -1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--data" -> dataDir = Path.of(args[++i]);
                case "--no-browser" -> openBrowser = false;
                case "--simulator" -> simulatorPort = Integer.parseInt(args[++i]);
                case "--help", "-h" -> { printUsage(); return; }
                default -> {
                    System.err.println("Unrecognized argument: " + args[i]);
                    printUsage();
                    System.exit(2);
                }
            }
        }

        Server server = start(host, port, dataDir);
        System.out.println("Indicator Profile Builder " + VERSION);
        System.out.println("  UI:       " + server.url());
        System.out.println("  Profiles: " + dataDir.resolve("profiles").toAbsolutePath());
        System.out.println("  Samples:  " + dataDir.resolve("samples").toAbsolutePath());
        if (simulatorPort >= 0) {
            SimulatedIndicatorServer simulator = new SimulatedIndicatorServer(simulatorPort, 250);
            System.out.println("  SIMULATOR (synthetic data, not a real indicator): TCP 127.0.0.1:" + simulator.port());
            Runtime.getRuntime().addShutdownHook(new Thread(simulator::close));
        }
        System.out.println("Press Ctrl+C to stop.");
        if (openBrowser) {
            openBrowser(server.url());
        }
        Thread.currentThread().join();
    }

    public record Server(HttpServer httpServer, CaptureSessionManager capture, String url) {
        public void stop() {
            capture.disconnect();
            httpServer.stop(0);
        }
    }

    public static Server start(String host, int port, Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        ObjectMapper mapper = JsonMapperFactory.create();
        ProfileRepository profiles = new ProfileRepository(dataDir.resolve("profiles"), mapper);
        SampleRepository samples = new SampleRepository(dataDir.resolve("samples"), mapper);
        SseHub sseHub = new SseHub(mapper);
        CaptureSessionManager capture = new CaptureSessionManager(sseHub::publish);
        HttpApi api = new HttpApi(mapper, profiles, samples, new ProfileLifecycleService(),
                new ProfileImportExportService(mapper), capture, sseHub, VERSION);

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.createContext("/api", api);
        httpServer.createContext("/", new StaticFiles());
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "profile-builder-http");
            t.setDaemon(true);
            return t;
        }));
        httpServer.start();
        String url = "http://" + host + ":" + httpServer.getAddress().getPort() + "/";
        Runtime.getRuntime().addShutdownHook(new Thread(capture::disconnect));
        return new Server(httpServer, capture, url);
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            System.out.println("Open " + url + " in your browser.");
        }
    }

    private static Path defaultDataDirectory() {
        try {
            Path location = Path.of(ProfileBuilderApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path programDir = Files.isDirectory(location) ? location : location.getParent();
            return programDir.resolve("data");
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of("data");
        }
    }

    private static void printUsage() {
        System.out.println("""
                Indicator Profile Builder

                Usage:
                  java -jar indicator-profile-builder.jar [options]

                Options:
                  --port <n>          HTTP port for the local UI (default 8765)
                  --host <addr>       bind address (default 127.0.0.1; use 0.0.0.0 to allow other PCs on the LAN)
                  --data <dir>        folder for profiles/ and samples/ (default: a data folder next to the jar)
                  --no-browser        do not open the browser automatically
                  --simulator <port>  also start a SIMULATED TCP indicator on this port (synthetic data, for training only)
                """);
    }
}
