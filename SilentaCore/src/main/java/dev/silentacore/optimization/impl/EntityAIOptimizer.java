package dev.silentacore.optimization.impl;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.List;

public class EntityAIOptimizer implements Optimizer {

    private final SilentaCore plugin;
    private int activationRange;
    private List<String> excludeTypes;

    public EntityAIOptimizer(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        reloadConfig();
    }

    @Override
    public void disable() {
        // Restore AI on disable
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Mob) {
                    ((Mob) entity).setAware(true);
                }
            }
        }
    }

    @Override
    public void tick() {
        // Run check periodically (e.g., every 100 ticks) as configured
        // The manager calls tick() every 20 ticks (1 sec)
        // We'll use a simple counter or just check current tick
        long interval = plugin.getConfigManager().getInt("entity-ai-optimization.check-interval", 100);
        if (Bukkit.getCurrentTick() % interval != 0) return;

        // If TPS is perfect, maybe we skip optimization? 
        // Philosophy: "optimize only when needed".
        if (plugin.getPerformanceMonitor().getCurrentTps() > 19.8) {
             // Maybe ensure everyone is awake?
             // For now, let's just apply the logic. If TPS is high, we might want to be less aggressive,
             // but "Smart Entity AI Sleep" usually implies distance-based regardless of TPS, 
             // OR strictly TPS aware. The prompt says "Temporarily pauses AI... when TPS drops" is implied by "Adaptive".
             // Actually prompt says "Core Features ... Temporarily pauses AI ... Never disables AI near players".
             // It doesn't explicitly say "only when TPS drops" for AI, but "Optimization Philosophy" says "Adaptive, TPS-aware".
             // So if TPS > 19, we should probably wake everyone up to be "Vanilla first".
             
             wakeAll();
             return;
        }

        runOptimization();
    }

    private void reloadConfig() {
        this.activationRange = plugin.getConfigManager().getInt("entity-ai-optimization.activation-range", 32);
        this.excludeTypes = plugin.getConfigManager().getStringList("entity-ai-optimization.exclude-types");
    }

    private void wakeAll() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Mob mob) {
                    if (!mob.isAware()) mob.setAware(true);
                }
            }
        }
    }

    private void runOptimization() {
        int rangeSq = activationRange * activationRange;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (!mob.isValid()) continue;
                
                // Check exclusions
                if (excludeTypes.contains(entity.getType().name())) continue;
                if (entity.getCustomName() != null) continue; // Usually safe to ignore named mobs
                if (entity instanceof Boss) continue;
                
                // Check protection (combat)
                if (plugin.getProtectionManager().isEntityProtected(entity)) {
                    if (!mob.isAware()) mob.setAware(true);
                    continue;
                }

                // Check distance to nearest player
                Player nearest = null;
                double nearestDistSq = Double.MAX_VALUE;

                for (Player p : world.getPlayers()) {
                    if (plugin.getProtectionManager().isPlayerAfk(p)) continue; // Ignore AFK players for activation range? Maybe.
                    
                    double d = p.getLocation().distanceSquared(entity.getLocation());
                    if (d < nearestDistSq) {
                        nearestDistSq = d;
                        nearest = p;
                    }
                }

                boolean shouldBeAware = false;
                
                // If no players in world, sleep (unless chunk loader? debatable. Safe to sleep for AI).
                if (nearest == null) {
                    shouldBeAware = false;
                } else {
                    if (nearestDistSq <= rangeSq) {
                        shouldBeAware = true;
                    }
                }

                // If targeted, wake up
                if (mob.getTarget() != null) shouldBeAware = true;

                if (mob.isAware() != shouldBeAware) {
                    mob.setAware(shouldBeAware);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Smart Entity AI Sleep";
    }
}
