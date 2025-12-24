package com.pluginmanager.test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginManagerTestPlugin extends JavaPlugin implements Listener {
  private static final String DEFAULT_BACKEND_URL = "http://localhost:3001";
  private final Gson gson = new Gson();
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private final List<String> pendingAckIds = new CopyOnWriteArrayList<>();
  private volatile boolean registered = false;

  private String backendUrl;
  private String pluginName;
  private String pluginDescription;
  private String pluginVersion;
  private String serverIp;
  private int serverPort;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    reloadFromConfig();

    Bukkit.getPluginManager().registerEvents(this, this);

    runAsync(
        () ->
            register(
                ok -> {
                  registered = ok;
                  if (ok) sendStatus(true, ignored -> {});
                }));

    int heartbeatIntervalTicks = Math.max(20, getConfig().getInt("heartbeatIntervalTicks", 100));
    int playersIntervalTicks = Math.max(20, getConfig().getInt("playersIntervalTicks", 100));
    int consoleIntervalTicks = Math.max(20, getConfig().getInt("consoleIntervalTicks", 200));

    Bukkit.getScheduler()
        .runTaskTimerAsynchronously(
            this,
            () -> {
              if (!registered) {
                register(ok -> registered = ok);
                return;
              }
              heartbeat(ignored -> {});
            },
            40L,
            heartbeatIntervalTicks);

    Bukkit.getScheduler()
        .runTaskTimerAsynchronously(
            this,
            () -> {
              if (!registered) return;
              sendPlayersSnapshot(ignored -> {});
            },
            60L,
            playersIntervalTicks);

    Bukkit.getScheduler()
        .runTaskTimerAsynchronously(
            this,
            () -> {
              if (!registered) return;
              List<String> lines = new ArrayList<>();
              lines.add("[PluginManagerTestPlugin] console tick");
              lines.add("[PluginManagerTestPlugin] onlinePlayers=" + Bukkit.getOnlinePlayers().size());
              sendConsole(lines, ignored -> {});
            },
            80L,
            consoleIntervalTicks);
  }

  @Override
  public void onDisable() {
    runAsync(() -> sendStatus(false, ignored -> {}));
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    if (!registered) return;
    runAsync(() -> sendPlayersSnapshot(ignored -> {}));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    if (!registered) return;
    runAsync(() -> sendPlayersSnapshot(ignored -> {}));
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("pmtptest")) return false;
    String sub = args.length > 0 ? args[0].toLowerCase() : "all";
    sender.sendMessage("Running: " + sub);

    switch (sub) {
      case "register" -> runAsync(() -> register(ok -> sender.sendMessage("register ok=" + ok)));
      case "heartbeat" -> runAsync(() -> heartbeat(ok -> sender.sendMessage("heartbeat ok=" + ok)));
      case "players" -> runAsync(() -> sendPlayersSnapshot(ok -> sender.sendMessage("players ok=" + ok)));
      case "console" ->
          runAsync(
              () ->
                  sendConsole(
                      List.of("[PluginManagerTestPlugin] manual console test"),
                      ok -> sender.sendMessage("console ok=" + ok)));
      case "status" -> {
        boolean enabled = args.length < 2 || !"off".equalsIgnoreCase(args[1]);
        runAsync(() -> sendStatus(enabled, ok -> sender.sendMessage("status ok=" + ok)));
      }
      default -> runAsync(() -> runAll(sender));
    }
    return true;
  }

  private void runAll(CommandSender sender) {
    register(
        ok -> {
          registered = ok;
          sender.sendMessage("register ok=" + ok);
          if (!ok) return;
          sendStatus(true, s1 -> sender.sendMessage("status ok=" + s1));
          heartbeat(h1 -> sender.sendMessage("heartbeat ok=" + h1));
          sendPlayersSnapshot(p1 -> sender.sendMessage("players ok=" + p1));
          sendConsole(List.of("[PluginManagerTestPlugin] all endpoints test"), c1 -> sender.sendMessage("console ok=" + c1));
        });
  }

  private void reloadFromConfig() {
    String cfgUrl = getConfig().getString("backendUrl", DEFAULT_BACKEND_URL);
    this.backendUrl = trimTrailingSlash(cfgUrl);
    this.pluginName = getDescription().getName();
    this.pluginDescription =
        getDescription().getDescription() == null ? "Test plugin" : getDescription().getDescription();
    this.pluginVersion = getDescription().getVersion();

    String ipOverride = getConfig().getString("serverIpOverride", "");
    String ip = ipOverride == null ? "" : ipOverride.trim();
    if (ip.isEmpty()) ip = Bukkit.getServer().getIp();
    if (ip == null || ip.isEmpty()) ip = "127.0.0.1";
    this.serverIp = ip;
    this.serverPort = Bukkit.getServer().getPort();
  }

  private static String trimTrailingSlash(String url) {
    String u = url == null ? "" : url.trim();
    if (u.endsWith("/")) return u.substring(0, u.length() - 1);
    return u.isEmpty() ? "http://localhost:3001" : u;
  }

  private void runAsync(Runnable r) {
    Bukkit.getScheduler().runTaskAsynchronously(this, r);
  }

  private void register(Consumer<Boolean> cb) {
    JsonObject body = baseBody();
    body.addProperty("enabled", true);
    postJson(
        "/plugin/register",
        body,
        resp -> {
          boolean ok = resp != null && resp.has("ok") && resp.get("ok").getAsBoolean();
          cb.accept(ok);
        },
        err -> cb.accept(false));
  }

  private void heartbeat(Consumer<Boolean> cb) {
    JsonObject body = baseBody();
    body.addProperty("enabled", true);
    body.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
    if (!pendingAckIds.isEmpty()) {
      JsonArray ack = new JsonArray();
      for (String id : pendingAckIds) ack.add(id);
      body.add("ackCommandIds", ack);
    }

    postJson(
        "/plugin/heartbeat",
        body,
        resp -> {
          if (resp != null) {
            handlePendingCommands(resp);
          }
          cb.accept(resp != null && resp.has("ok") && resp.get("ok").getAsBoolean());
        },
        err -> cb.accept(false));
  }

  private void sendPlayersSnapshot(Consumer<Boolean> cb) {
    JsonObject body = baseBody();
    JsonArray players = new JsonArray();
    for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
    body.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
    body.add("players", players);

    postJson(
        "/plugin/players",
        body,
        resp -> cb.accept(resp != null && resp.has("ok") && resp.get("ok").getAsBoolean()),
        err -> cb.accept(false));
  }

  private void sendConsole(List<String> lines, Consumer<Boolean> cb) {
    JsonObject body = baseBody();
    JsonArray arr = new JsonArray();
    for (String l : lines) arr.add(l);
    body.add("lines", arr);

    postJson(
        "/plugin/console",
        body,
        resp -> cb.accept(resp != null && resp.has("ok") && resp.get("ok").getAsBoolean()),
        err -> cb.accept(false));
  }

  private void sendStatus(boolean enabled, Consumer<Boolean> cb) {
    JsonObject body = baseBody();
    body.addProperty("enabled", enabled);

    postJson(
        "/plugin/status",
        body,
        resp -> cb.accept(resp != null && resp.has("ok") && resp.get("ok").getAsBoolean()),
        err -> cb.accept(false));
  }

  private void sendEvent(String event, JsonObject details, Consumer<Boolean> cb) {
    JsonObject body = baseBody();
    body.addProperty("event", event);
    if (details != null) body.add("details", details);
    postJson(
        "/plugin/event",
        body,
        resp -> cb.accept(resp != null && resp.has("ok") && resp.get("ok").getAsBoolean()),
        err -> cb.accept(false));
  }

  private JsonObject baseBody() {
    JsonObject body = new JsonObject();
    body.addProperty("pluginName", pluginName);
    body.addProperty("pluginDescription", pluginDescription);
    body.addProperty("pluginVersion", pluginVersion);
    body.addProperty("serverIp", serverIp);
    body.addProperty("serverPort", serverPort);
    return body;
  }

  private void handlePendingCommands(JsonObject heartbeatResponse) {
    if (!heartbeatResponse.has("pendingCommands")) return;
    JsonArray pending = heartbeatResponse.getAsJsonArray("pendingCommands");
    if (pending == null || pending.size() == 0) return;

    List<String> ackIds = new ArrayList<>();
    for (int i = 0; i < pending.size(); i++) {
      JsonObject cmd = pending.get(i).getAsJsonObject();
      if (cmd == null) continue;
      String id = cmd.has("id") ? cmd.get("id").getAsString() : null;
      String command = cmd.has("command") ? cmd.get("command").getAsString() : null;
      if (id != null && !id.isEmpty()) ackIds.add(id);
      if (command == null) continue;

      if ("plugin:disable".equalsIgnoreCase(command)) {
        String finalId = id;
        Bukkit.getScheduler()
            .runTask(
                this,
                () -> {
                  try {
                    Bukkit.getPluginManager().disablePlugin(this);
                  } catch (Throwable ignored) {
                  }
                  JsonObject details = new JsonObject();
                  if (finalId != null) details.addProperty("commandId", finalId);
                  details.addProperty("command", command);
                  runAsync(() -> sendEvent("command_executed", details, ignored -> {}));
                });
        continue;
      }

      String finalCommand = command;
      String finalId = id;
      Bukkit.getScheduler()
          .runTask(
              this,
              () -> {
                try {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                } catch (Throwable ignored) {
                }
                JsonObject details = new JsonObject();
                if (finalId != null) details.addProperty("commandId", finalId);
                details.addProperty("command", finalCommand);
                runAsync(() -> sendEvent("command_executed", details, ignored -> {}));
              });
    }

    pendingAckIds.clear();
    pendingAckIds.addAll(ackIds);
  }

  private void postJson(
      String path, JsonObject body, Consumer<JsonObject> onOk, Consumer<Throwable> onError) {
    String url = backendUrl + path;
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build();

    client
        .sendAsync(req, HttpResponse.BodyHandlers.ofString())
        .thenAccept(
            res -> {
              if (res.statusCode() < 200 || res.statusCode() >= 300) {
                onError.accept(new RuntimeException("HTTP " + res.statusCode() + " " + res.body()));
                return;
              }
              JsonObject json = gson.fromJson(res.body(), JsonObject.class);
              onOk.accept(json);
            })
        .exceptionally(
            err -> {
              onError.accept(err);
              return null;
            });
  }
}
