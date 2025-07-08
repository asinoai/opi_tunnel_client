package opi.tunnel;

// TunnelClient.java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TunnelClient {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String serverUrl;
    private final int localPort;
    private final String tunnelName;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final int maxReconnectAttempts = 10;

    private WebSocket webSocket;
    private String tunnelUrl;

    public TunnelClient(String serverUrl, int localPort, String tunnelName) {
        this.serverUrl = serverUrl;
        this.localPort = localPort;
        this.tunnelName = tunnelName;
    }

    public void connect() {
        System.out.println("🚇 Connecting to tunnel server: " + serverUrl);

        // Convert HTTP URL to WebSocket URL
        String wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://");

        try {
            WebSocket.Builder wsBuilder = httpClient.newWebSocketBuilder();
            wsBuilder.header("User-Agent", "TunnelProxy-JavaClient/1.0.0");

            webSocket = wsBuilder.buildAsync(URI.create(wsUrl), new TunnelWebSocketListener())
                    .join();

        } catch (Exception e) {
            System.err.println("❌ Failed to connect: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (reconnectAttempts.get() < maxReconnectAttempts) {
            int attempts = reconnectAttempts.incrementAndGet();
            long delay = Math.min(1000L * (1L << attempts), 30000L); // Exponential backoff, max 30s

            System.out.println("🔄 Reconnecting in " + (delay/1000) + "s... (attempt " +
                    attempts + "/" + maxReconnectAttempts + ")");

            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        } else {
            System.err.println("💥 Max reconnection attempts reached. Please check your connection and try again.");
            System.exit(1);
        }
    }

    private void handleRequest(JsonNode requestData) {
        long startTime = System.currentTimeMillis();

        try {
            String method = requestData.get("method").asText();
            String url = requestData.get("url").asText();

            System.out.println(method + " " + url);

            // Build the local request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + localPort + url))
                    .timeout(Duration.ofSeconds(25));

            // Add headers
            JsonNode headers = requestData.get("headers");
            if (headers != null && headers.isObject()) {
                headers.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue().asText();

                    // Skip problematic headers
                    if (!key.equalsIgnoreCase("host") &&
                            !key.equalsIgnoreCase("content-length") &&
                            !key.equalsIgnoreCase("x-forwarded-for") &&
                            !key.equalsIgnoreCase("x-forwarded-proto") &&
                            !key.equalsIgnoreCase("connection")) {
                        requestBuilder.header(key, value);
                    }
                });
            }

            // Add body if present
            JsonNode body = requestData.get("body");
            if (body != null && !body.isNull()) {
                String bodyStr = body.asText();
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(bodyStr));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();

            // Send request asynchronously
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        long duration = System.currentTimeMillis() - startTime;
                        System.out.println("   → " + response.statusCode() + " (" + duration + "ms)");

                        try {
                            // Build response object
                            ObjectNode responseObj = objectMapper.createObjectNode();
                            responseObj.put("type", "response");
                            responseObj.put("statusCode", response.statusCode());

                            // Add headers
                            ObjectNode responseHeaders = objectMapper.createObjectNode();
                            response.headers().map().forEach((key, values) -> {
                                if (!values.isEmpty()) {
                                    responseHeaders.put(key, values.get(0));
                                }
                            });
                            responseObj.set("headers", responseHeaders);

                            // Add body
                            String responseBody = response.body();
                            if (responseBody != null && !responseBody.isEmpty()) {
                                // Try to parse as JSON, fallback to string
                                try {
                                    JsonNode jsonBody = objectMapper.readTree(responseBody);
                                    responseObj.set("body", jsonBody);
                                } catch (Exception e) {
                                    responseObj.put("body", responseBody);
                                }
                            }

                            // Send response back to server
                            webSocket.sendText(objectMapper.writeValueAsString(responseObj), true);

                        } catch (Exception e) {
                            System.err.println("❌ Error processing response: " + e.getMessage());
                            sendErrorResponse(502, "Failed to process response");
                        }
                    })
                    .exceptionally(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;
                        System.err.println("   → ERROR (" + duration + "ms): " + throwable.getMessage());

                        sendErrorResponse(502, "Failed to connect to local server");
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("❌ Error handling request: " + e.getMessage());
            sendErrorResponse(500, "Internal client error");
        }
    }

    private void sendErrorResponse(int statusCode, String message) {
        try {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("type", "response");
            errorResponse.put("statusCode", statusCode);

            ObjectNode headers = objectMapper.createObjectNode();
            headers.put("Content-Type", "application/json");
            errorResponse.set("headers", headers);

            ObjectNode errorBody = objectMapper.createObjectNode();
            errorBody.put("error", message);
            errorBody.put("localPort", localPort);
            errorResponse.set("body", errorBody);

            webSocket.sendText(objectMapper.writeValueAsString(errorResponse), true);
        } catch (Exception e) {
            System.err.println("❌ Failed to send error response: " + e.getMessage());
        }
    }

    private void register(WebSocket webSocket) {
        try {
            ObjectNode registerMsg = objectMapper.createObjectNode();
            registerMsg.put("type", "register");
            registerMsg.put("localPort", localPort);

            if (tunnelName != null && !tunnelName.isEmpty()) {
                registerMsg.put("tunnelName", tunnelName);
            }

            ObjectNode clientInfo = objectMapper.createObjectNode();
            clientInfo.put("version", "1.0.0");
            clientInfo.put("platform", "Java");
            clientInfo.put("runtime", System.getProperty("java.version"));
            registerMsg.set("clientInfo", clientInfo);

            webSocket.sendText(objectMapper.writeValueAsString(registerMsg), true);
        } catch (Exception e) {
            System.err.println("❌ Failed to register: " + e.getMessage());
        }
    }

    private class TunnelWebSocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("✅ Connected to tunnel server");
            isConnected.set(true);
            reconnectAttempts.set(0);
            register(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonNode message = objectMapper.readTree(data.toString());
                String type = message.get("type").asText();

                switch (type) {
                    case "registered":
                        tunnelUrl = message.get("url").asText();
                        String tunnelName = message.get("tunnelName").asText();

                        System.out.println("\n🌐 Tunnel active!");
                        System.out.println("   Public URL: " + tunnelUrl);
                        System.out.println("   Local URL:  http://localhost:" + localPort);
                        System.out.println("   Tunnel name:  " + tunnelName);
                        System.out.println("\n💡 Your local server is now accessible from the internet!");
                        break;

                    case "request":
                        handleRequest(message);
                        break;

                    default:
                        System.out.println("Unknown message type: " + type);
                }
            } catch (Exception e) {
                System.err.println("❌ Error processing message: " + e.getMessage());
            }

            webSocket.request(1);

            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            isConnected.set(false);
            System.out.println("❌ Disconnected from tunnel server (code: " + statusCode + ")");
            if (reason != null && !reason.isEmpty()) {
                System.out.println("   Reason: " + reason);
            }
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("❌ WebSocket error: " + error.getMessage());
        }
    }

    public void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client shutting down");
        }
        scheduler.shutdown();
    }

    private static void checkLocalServer(int port) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(2))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        // Server is running
                    })
                    .exceptionally(throwable -> {
                        System.out.println("⚠️  Warning: No server detected on localhost:" + port);
                        System.out.println("   Make sure your local server is running before connecting.\n");
                        return null;
                    })
                    .join();
        } catch (Exception e) {
            System.out.println("⚠️  Warning: Could not check local server on port " + port);
        }
    }

    public static void main(String[] args) {
        // Configuration from environment variables or defaults
        String serverUrl = System.getenv().getOrDefault("TUNNEL_SERVER", "wss://opi-tunnel.onrender.com");
        int localPort = Integer.parseInt(System.getenv().getOrDefault("LOCAL_PORT", "9001"));
        String tunnelName = System.getenv().getOrDefault("TUNNEL_NAME", "aron");

        System.out.println("🚀 Starting Tunnel Proxy Java Client\n");
        System.out.println("   Server:     " + serverUrl);
        System.out.println("   Local Port: " + localPort);
        System.out.println("   Tunnel Name:  " + (tunnelName != null ? tunnelName : "auto-generated"));
        System.out.println();

        // Validate server URL
        if (!serverUrl.contains("onrender.com") && !serverUrl.contains("localhost")) {
            System.out.println("⚠️  Warning: Server URL doesn't appear to be a Render.com URL");
        }

        // Check if local server is running
        checkLocalServer(localPort);

        // Create and start tunnel client
        TunnelClient client = new TunnelClient(serverUrl, localPort, tunnelName);

        // Handle shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down tunnel client...");
            client.close();
        }));

        // Start connection
        client.connect();

        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}