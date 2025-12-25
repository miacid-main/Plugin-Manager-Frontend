package dev.silentacore.optimization.impl;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemMergeOptimizer implements Optimizer {

    private final SilentaCore plugin;
    private double radius;
    private int maxStackSize;

    public ItemMergeOptimizer(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        this.radius = plugin.getConfigManager().getDouble("item-merge-optimization.radius");
        this.maxStackSize = plugin.getConfigManager().getInt("item-merge-optimization.max-stack-size", 64);
    }

    @Override
    public void disable() {}

    @Override
    public void tick() {
        long interval = plugin.getConfigManager().getInt("item-merge-optimization.check-interval", 40);
        if (Bukkit.getCurrentTick() % interval != 0) return;

        // If TPS is Level 1 or worse, run merge
        if (!plugin.getPerformanceMonitor().isLevel1()) return;

        for (World world : Bukkit.getWorlds()) {
            List<Item> items = new ArrayList<>();
            for (Entity e : world.getEntities()) {
                if (e instanceof Item) {
                    items.add((Item) e);
                }
            }

            // Simple O(N^2) within chunks or just distance check.
            // Since we want "Micro-optimization", iterating all items is okay if count is low,
            // but bad if count is high (lag machines).
            // Optimization: Only check items that are "mergeable" (not pickup delay infinite, not processed).
            
            // To avoid N^2 global, we can group by chunk?
            // Bukkit entity iteration is per-chunk usually internally, but getEntities() returns a list.
            // Better: Iterate chunks, then entities in chunk.
            
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                Entity[] chunkEntities = chunk.getEntities();
                List<Item> chunkItems = new ArrayList<>();
                for (Entity e : chunkEntities) {
                    if (e instanceof Item item && item.isValid() && !item.isDead()) {
                        chunkItems.add(item);
                    }
                }
                
                if (chunkItems.isEmpty()) continue;
                
                mergeItems(chunkItems);
            }
        }
    }
    
    private void mergeItems(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            Item a = items.get(i);
            if (a.isDead() || !a.isValid()) continue;

            for (int j = i + 1; j < items.size(); j++) {
                Item b = items.get(j);
                if (b.isDead() || !b.isValid()) continue;

                if (a.getLocation().distanceSquared(b.getLocation()) <= radius * radius) {
                    tryMerge(a, b);
                }
            }
        }
    }

    private void tryMerge(Item a, Item b) {
        ItemStack sa = a.getItemStack();
        ItemStack sb = b.getItemStack();

        if (sa.isSimilar(sb)) {
            int total = sa.getAmount() + sb.getAmount();
            if (total <= maxStackSize) {
                sa.setAmount(total);
                a.setItemStack(sa);
                b.remove();
            } else {
                // Merge as much as possible to A
                int toTransfer = maxStackSize - sa.getAmount();
                if (toTransfer > 0) {
                    sa.setAmount(maxStackSize);
                    a.setItemStack(sa);
                    
                    sb.setAmount(sb.getAmount() - toTransfer);
                    b.setItemStack(sb);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Safe Item Entity Merging";
    }
}
