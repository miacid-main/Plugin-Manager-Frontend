package dev.aegistick.paper.listener;

import dev.aegistick.core.AegisTickCore;
import dev.aegistick.core.ChunkKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Listens for chunk load/unload events to manage profiling.
 */
public class ChunkListener implements Listener {

    private final AegisTickCore core;

    public ChunkListener(AegisTickCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        ChunkKey key = new ChunkKey(
            event.getWorld().getUID(),
            event.getChunk().getX(),
            event.getChunk().getZ()
        );
        core.onChunkLoad(key);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        ChunkKey key = new ChunkKey(
            event.getWorld().getUID(),
            event.getChunk().getX(),
            event.getChunk().getZ()
        );
        core.onChunkUnload(key);
    }
}
