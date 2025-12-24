package dev.aegistick.paper.adapter;

import dev.aegistick.core.ChunkKey;
import dev.aegistick.core.platform.EntityAdapter;
import dev.aegistick.core.platform.PlayerAdapter;
import dev.aegistick.core.platform.WorldAdapter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Paper/Bukkit implementation of WorldAdapter.
 */
public class PaperWorldAdapter implements WorldAdapter {

    private final World world;

    public PaperWorldAdapter(World world) {
        this.world = world;
    }

    @Override
    public UUID getWorldId() {
        return world.getUID();
    }

    @Override
    public String getWorldName() {
        return world.getName();
    }

    @Override
    public Collection<ChunkKey> getLoadedChunks() {
        UUID worldId = world.getUID();
        return Arrays.stream(world.getLoadedChunks())
            .map(chunk -> new ChunkKey(worldId, chunk.getX(), chunk.getZ()))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<EntityAdapter> getEntitiesInChunk(ChunkKey chunk) {
        Chunk bukkitChunk = world.getChunkAt(chunk.x(), chunk.z());
        return Arrays.stream(bukkitChunk.getEntities())
            .map(PaperEntityAdapter::new)
            .collect(Collectors.toList());
    }

    @Override
    public boolean setChunkRandomTickSpeed(ChunkKey chunk, int tickSpeed) {
        // Paper doesn't support per-chunk random tick speed natively
        // This would require NMS or a Paper-specific API
        // For now, return false to indicate not supported
        return false;
    }

    @Override
    public Collection<PlayerAdapter> getPlayersNearChunk(ChunkKey chunk, int blockRadius) {
        int centerX = chunk.x() * 16 + 8;
        int centerZ = chunk.z() * 16 + 8;

        return world.getPlayers().stream()
            .filter(player -> {
                double dx = player.getLocation().getX() - centerX;
                double dz = player.getLocation().getZ() - centerZ;
                return (dx * dx + dz * dz) <= (blockRadius * blockRadius);
            })
            .map(p -> new PaperPlayerAdapter(p, null))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isSpawnChunk(ChunkKey chunk) {
        // Check if within spawn chunk radius
        int spawnX = world.getSpawnLocation().getBlockX() >> 4;
        int spawnZ = world.getSpawnLocation().getBlockZ() >> 4;
        int dx = Math.abs(chunk.x() - spawnX);
        int dz = Math.abs(chunk.z() - spawnZ);
        // Spawn chunks are typically in a radius of ~12 chunks
        return dx <= 12 && dz <= 12;
    }

    public World getBukkitWorld() {
        return world;
    }
}
