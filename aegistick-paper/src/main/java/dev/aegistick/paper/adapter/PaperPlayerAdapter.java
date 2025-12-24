package dev.aegistick.paper.adapter;

import dev.aegistick.core.ChunkKey;
import dev.aegistick.core.platform.PlayerAdapter;
import dev.aegistick.core.platform.WorldAdapter;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.UUID;

/**
 * Paper/Bukkit implementation of PlayerAdapter.
 */
public class PaperPlayerAdapter implements PlayerAdapter {

    private final Player player;
    private final PaperPlatformAdapter platform;

    // Metadata keys for tracking player state
    private static final String META_LAST_BUILD = "aegistick_last_build";
    private static final String META_LAST_COMBAT = "aegistick_last_combat";
    private static final String META_LAST_MOVE = "aegistick_last_move";

    public PaperPlayerAdapter(Player player, PaperPlatformAdapter platform) {
        this.player = player;
        this.platform = platform;
    }

    @Override
    public UUID getPlayerId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public ChunkKey getCurrentChunk() {
        return new ChunkKey(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4
        );
    }

    @Override
    public double getX() {
        return player.getLocation().getX();
    }

    @Override
    public double getY() {
        return player.getLocation().getY();
    }

    @Override
    public double getZ() {
        return player.getLocation().getZ();
    }

    @Override
    public WorldAdapter getWorld() {
        return new PaperWorldAdapter(player.getWorld());
    }

    @Override
    public boolean isInCreativeOrSpectator() {
        return player.getGameMode() == GameMode.CREATIVE || 
               player.getGameMode() == GameMode.SPECTATOR;
    }

    @Override
    public boolean isFlying() {
        return player.isFlying();
    }

    @Override
    public boolean hasBuiltRecently(int withinTicks) {
        return checkMetadataRecency(META_LAST_BUILD, withinTicks);
    }

    @Override
    public boolean hasBeenInCombatRecently(int withinTicks) {
        return checkMetadataRecency(META_LAST_COMBAT, withinTicks);
    }

    @Override
    public boolean isAfk(int afkThresholdTicks) {
        return !checkMetadataRecency(META_LAST_MOVE, afkThresholdTicks);
    }

    private boolean checkMetadataRecency(String key, int withinTicks) {
        if (!player.hasMetadata(key)) {
            return false;
        }
        
        for (MetadataValue value : player.getMetadata(key)) {
            long lastTime = value.asLong();
            long ticksAgo = (System.currentTimeMillis() - lastTime) / 50; // 50ms per tick
            if (ticksAgo <= withinTicks) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LookDirection getLookDirection() {
        var dir = player.getLocation().getDirection();
        return new LookDirection(dir.getX(), dir.getY(), dir.getZ());
    }

    @Override
    public boolean hasAdminPermission() {
        return player.hasPermission("aegistick.admin");
    }

    public Player getBukkitPlayer() {
        return player;
    }

    /**
     * Update the last build time for this player.
     */
    public static void markBuildAction(Player player, org.bukkit.plugin.Plugin plugin) {
        player.setMetadata(META_LAST_BUILD, 
            new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
    }

    /**
     * Update the last combat time for this player.
     */
    public static void markCombatAction(Player player, org.bukkit.plugin.Plugin plugin) {
        player.setMetadata(META_LAST_COMBAT,
            new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
    }

    /**
     * Update the last movement time for this player.
     */
    public static void markMovement(Player player, org.bukkit.plugin.Plugin plugin) {
        player.setMetadata(META_LAST_MOVE,
            new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
    }
}
