package dev.aegistick.paper.listener;

import dev.aegistick.core.AegisTickCore;
import dev.aegistick.core.ChunkKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.Hopper;

/**
 * Listens for entity and block events to track activity metrics.
 */
public class EntityListener implements Listener {

    private final AegisTickCore core;

    public EntityListener(AegisTickCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Track hopper transfers
        Inventory source = event.getSource();
        if (source.getHolder() instanceof Hopper hopper) {
            ChunkKey key = new ChunkKey(
                hopper.getWorld().getUID(),
                hopper.getX() >> 4,
                hopper.getZ() >> 4
            );
            core.onHopperTransfer(key);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        ChunkKey key = new ChunkKey(
            event.getBlock().getWorld().getUID(),
            event.getBlock().getX() >> 4,
            event.getBlock().getZ() >> 4
        );
        core.onRedstoneUpdate(key);
    }
}
