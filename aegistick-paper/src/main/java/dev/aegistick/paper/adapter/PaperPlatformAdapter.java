package dev.aegistick.paper.adapter;

import dev.aegistick.core.ChunkKey;
import dev.aegistick.core.platform.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Paper/Bukkit implementation of PlatformAdapter.
 */
public class PaperPlatformAdapter implements PlatformAdapter {

    private final JavaPlugin plugin;
    private final Map<UUID, PaperWorldAdapter> worldAdapters = new ConcurrentHashMap<>();

    public PaperPlatformAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public double getCurrentTps() {
        // Paper provides TPS access
        return Bukkit.getTPS()[0]; // 1-minute average
    }

    @Override
    public Collection<ChunkKey> getLoadedChunks() {
        List<ChunkKey> chunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks.add(new ChunkKey(worldId, chunk.getX(), chunk.getZ()));
            }
        }
        return chunks;
    }

    @Override
    public Collection<WorldAdapter> getWorlds() {
        return Bukkit.getWorlds().stream()
            .map(this::getOrCreateWorldAdapter)
            .collect(Collectors.toList());
    }

    @Override
    public WorldAdapter getWorld(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        return world != null ? getOrCreateWorldAdapter(world) : null;
    }

    private PaperWorldAdapter getOrCreateWorldAdapter(World world) {
        return worldAdapters.computeIfAbsent(world.getUID(), 
            id -> new PaperWorldAdapter(world));
    }

    @Override
    public Collection<PlayerAdapter> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
            .map(p -> new PaperPlayerAdapter(p, this))
            .collect(Collectors.toList());
    }

    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public TaskHandle scheduleRepeating(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskHandle(bukkitTask);
    }

    @Override
    public void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        plugin.getLogger().severe(message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.PAPER;
    }

    @Override
    public Path getDataFolder() {
        return plugin.getDataFolder().toPath();
    }

    /**
     * Wrapper for Bukkit task handles.
     */
    private static class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask task;

        BukkitTaskHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isActive() {
            return !task.isCancelled();
        }
    }
}
