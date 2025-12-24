package dev.aegistick.paper.listener;

import dev.aegistick.core.AegisTickCore;
import dev.aegistick.core.ChunkKey;
import dev.aegistick.paper.adapter.PaperPlayerAdapter;
import dev.aegistick.paper.adapter.PaperPlatformAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

/**
 * Listens for player events to track activity and safety states.
 */
public class PlayerListener implements Listener {

    private final AegisTickCore core;
    private final Plugin plugin;

    public PlayerListener(AegisTickCore core, PaperPlatformAdapter platform) {
        this.core = core;
        // Get plugin reference through reflection or store in adapter
        this.plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("AegisTick");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PaperPlayerAdapter.markBuildAction(player, plugin);
        
        ChunkKey key = new ChunkKey(
            event.getBlock().getWorld().getUID(),
            event.getBlock().getX() >> 4,
            event.getBlock().getZ() >> 4
        );
        core.onPlayerInteraction(key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PaperPlayerAdapter.markBuildAction(player, plugin);
        
        ChunkKey key = new ChunkKey(
            event.getBlock().getWorld().getUID(),
            event.getBlock().getX() >> 4,
            event.getBlock().getZ() >> 4
        );
        core.onPlayerInteraction(key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            ChunkKey key = new ChunkKey(
                event.getClickedBlock().getWorld().getUID(),
                event.getClickedBlock().getX() >> 4,
                event.getClickedBlock().getZ() >> 4
            );
            core.onPlayerInteraction(key);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Track player combat
        if (event.getDamager() instanceof Player player) {
            PaperPlayerAdapter.markCombatAction(player, plugin);
        }
        if (event.getEntity() instanceof Player player) {
            PaperPlayerAdapter.markCombatAction(player, plugin);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only track significant movement (not just head rotation)
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            
            PaperPlayerAdapter.markMovement(event.getPlayer(), plugin);
        }
    }
}
