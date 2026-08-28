package com.backtester.server;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.BacktestArtifactReplayResolver;
import com.backtester.ui.javafx.SingleBacktestHelper;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Embedded HTTP server listening on http://127.0.0.1:28987
 * Allows HTML reports opened in browsers to trigger single verification backtests directly in MetaTrader.
 */
public class LocalBacktestHttpServer {

    private static final Logger log = LoggerFactory.getLogger(LocalBacktestHttpServer.class);
    private static final int PORT = 28987;
    private static final int MAX_GALLERY_SESSIONS = 32;
    private static final long GALLERY_SESSION_TTL_MILLIS = 24L * 60L * 60L * 1_000L;
    private static LocalBacktestHttpServer instance;

    private HttpServer server;
    private final GallerySessionStore<GalleryContext> gallerySessions =
            new GallerySessionStore<>(MAX_GALLERY_SESSIONS, GALLERY_SESSION_TTL_MILLIS,
                    System::currentTimeMillis);

    private LocalBacktestHttpServer() {
        startServer();
    }

    public static synchronized LocalBacktestHttpServer getInstance() {
        if (instance == null) {
            instance = new LocalBacktestHttpServer();
        }
        return instance;
    }

    public synchronized String setContext(CustomProject project, DatabankManager databankManager, Window mainWindow) {
        if (server == null) {
            throw new IllegalStateException("Lokaler Galerie-Server ist nicht verfügbar.");
        }
        return gallerySessions.create(new GalleryContext(project, databankManager));
    }

    private void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/run-backtest", withHostValidation(new RunBacktestHandler()));
            // Catch-all context so every request - including unmatched paths - is Host-validated
            server.createContext("/", withHostValidation(exchange ->
                    sendSimpleJson(exchange, 404, "{\"status\":\"error\",\"message\":\"Not found\"}")));
            server.setExecutor(null); // default executor
            server.start();
            log.info("LocalBacktestHttpServer started on http://127.0.0.1:{}", PORT);
        } catch (Exception e) {
            log.warn("Could not start LocalBacktestHttpServer on port {}: {}", PORT, e.getMessage());
        }
    }

    /**
     * DNS-rebinding protection: wraps a handler so that only requests whose Host header
     * matches this server's local address (127.0.0.1 / localhost on our port) are served.
     * All other requests receive 403 without touching the delegate.
     * Also emits CORS headers only for local origins instead of a wildcard.
     */
    private HttpHandler withHostValidation(HttpHandler delegate) {
        return exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!isAllowedHost(host)) {
                log.warn("Rejected request with disallowed Host header: {}", host);
                sendSimpleJson(exchange, 403,
                        "{\"status\":\"error\",\"message\":\"Forbidden: invalid Host header\"}");
                return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (isAllowedLocalOrigin(origin)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin.trim());
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.getResponseHeaders().set("Vary", "Origin");
            }
            delegate.handle(exchange);
        };
    }

    private static boolean isAllowedHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("127.0.0.1:" + PORT) || normalized.equals("localhost:" + PORT);
    }

    private static boolean isAllowedLocalOrigin(String origin) {
        if (origin == null) return false;
        String normalized = origin.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("http://127.0.0.1:" + PORT)
                || normalized.equals("http://localhost:" + PORT);
    }

    private static void sendSimpleJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            gallerySessions.clear();
            log.info("LocalBacktestHttpServer stopped.");
        }
    }

    private class RunBacktestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers are set centrally in withHostValidation() for allowed local origins
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonMessage(exchange, 405, "error", "Method not allowed");
                return;
            }

            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String passStr = queryParams.get("pass");
            String dbName = queryParams.get("db");
            String strategyName = queryParams.get("name");
            String artifactDirectory = queryParams.get("artifact");
            String token = queryParams.get("token");

            GalleryContext galleryContext = gallerySessions.find(token).orElse(null);
            if (galleryContext == null) {
                sendJsonMessage(exchange, 403, "error", "Ungültiger oder abgelaufener Galerie-Zugriff.");
                return;
            }

            if (passStr == null || passStr.isEmpty()) {
                sendJsonMessage(exchange, 400, "error", "Missing pass parameter");
                return;
            }

            int passNum;
            try {
                passNum = Integer.parseInt(passStr);
            } catch (NumberFormatException e) {
                sendJsonMessage(exchange, 400, "error", "Invalid pass number");
                return;
            }

            CombinedPass targetPass = findPass(
                    galleryContext.databankManager(), passNum, strategyName, dbName);
            if (targetPass == null) {
                sendJsonMessage(exchange, 404, "error",
                        "Pass #" + passNum + " in Databank '" + dbName + "' nicht eindeutig gefunden.");
                return;
            }

            BacktestArtifactReplayResolver.Replay replay;
            try {
                replay = BacktestArtifactReplayResolver.resolve(
                        com.backtester.config.AppConfig.getInstance().getReportsDirectory(),
                        artifactDirectory, passNum);
            } catch (IOException | IllegalArgumentException ex) {
                log.warn("Rejected gallery replay for Pass #{}: {}", passNum, ex.getMessage());
                sendJsonMessage(exchange, 400, "error", ex.getMessage());
                return;
            }

            log.info("Received request from HTML viewer to run single backtest for Pass #{}", passNum);

            // Execute on JavaFX UI thread without modal dialog blocking
            Platform.runLater(() -> {
                try {
                    SingleBacktestHelper.runArtifactBacktestInMetaTrader(
                            targetPass, replay, null);
                } catch (Exception ex) {
                    log.error("Failed to launch single backtest from HTTP request", ex);
                }
            });

            sendJsonMessage(exchange, 200, "ok", "Single backtest launched in MetaTrader!");
        }

        private CombinedPass findPass(DatabankManager databankManager,
                                      int passNum, String strategyName, String dbName) {
            if (databankManager == null) return null;

            String cleanDbName = dbName != null ? dbName.replaceAll("\\s*\\(\\d+\\)$", "").trim() : "";

            if (!cleanDbName.isEmpty()) {
                List<CombinedPass> list = databankManager.getDatabank(cleanDbName);
                return findUniquePass(list, passNum, strategyName);
            }

            List<CombinedPass> matches = new ArrayList<>();
            for (String name : databankManager.getDatabankNames()) {
                CombinedPass match = findUniquePass(databankManager.getDatabank(name), passNum, strategyName);
                if (match != null) matches.add(match);
            }
            return matches.size() == 1 ? matches.get(0) : null;
        }

        private CombinedPass findUniquePass(List<CombinedPass> passes, int passNum, String strategyName) {
            if (passes == null) return null;
            List<CombinedPass> numberMatches = new ArrayList<>();
            for (CombinedPass cp : passes) {
                if (cp != null && cp.getPassNumber() == passNum) numberMatches.add(cp);
            }
            if (strategyName != null && !strategyName.isBlank()) {
                List<CombinedPass> exact = numberMatches.stream()
                        .filter(cp -> strategyName.equals(cp.getStrategyName()))
                        .toList();
                if (exact.size() == 1) return exact.get(0);
            }
            return numberMatches.size() == 1 ? numberMatches.get(0) : null;
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> map = new HashMap<>();
            if (query == null || query.isEmpty()) return map;
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length > 1) {
                    map.put(java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                } else if (pair.length == 1) {
                    map.put(java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8), "");
                }
            }
            return map;
        }

        private void sendJsonMessage(HttpExchange exchange, int statusCode, String status, String message)
                throws IOException {
            sendJsonResponse(exchange, statusCode, "{\"status\":\"" + escapeJson(status)
                    + "\",\"message\":\"" + escapeJson(message) + "\"}");
        }

        private String escapeJson(String value) {
            if (value == null) return "";
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private record GalleryContext(CustomProject project, DatabankManager databankManager) {
    }
}
