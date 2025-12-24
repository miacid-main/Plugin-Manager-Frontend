package dev.aegistick.paper.adapter;

import dev.aegistick.core.platform.EntityAdapter;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Paper/Bukkit implementation of EntityAdapter.
 */
public class PaperEntityAdapter implements EntityAdapter {

    private final Entity entity;

    public PaperEntityAdapter(Entity entity) {
        this.entity = entity;
    }

    @Override
    public UUID getEntityId() {
        return entity.getUniqueId();
    }

    @Override
    public String getEntityType() {
        return entity.getType().name();
    }

    @Override
    public boolean hasAI() {
        return entity instanceof Mob;
    }

    @Override
    public void setAIActive(boolean active) {
        if (entity instanceof Mob mob) {
            mob.setAware(active);
        }
    }

    @Override
    public boolean isAIActive() {
        if (entity instanceof Mob mob) {
            return mob.isAware();
        }
        return false;
    }

    @Override
    public boolean isItemEntity() {
        return entity instanceof Item;
    }

    @Override
    public boolean tryMergeItems(double radius) {
        if (!(entity instanceof Item item)) {
            return false;
        }

        // Find nearby items of the same type
        for (Entity nearby : entity.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Item nearbyItem && nearby != entity) {
                if (item.getItemStack().isSimilar(nearbyItem.getItemStack())) {
                    int totalAmount = item.getItemStack().getAmount() + nearbyItem.getItemStack().getAmount();
                    int maxStack = item.getItemStack().getMaxStackSize();
                    
                    if (totalAmount <= maxStack) {
                        item.getItemStack().setAmount(totalAmount);
                        nearbyItem.remove();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public double getX() {
        return entity.getLocation().getX();
    }

    @Override
    public double getY() {
        return entity.getLocation().getY();
    }

    @Override
    public double getZ() {
        return entity.getLocation().getZ();
    }

    @Override
    public boolean isBeingLookedAt(double maxDistance) {
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distance(entity.getLocation()) > maxDistance) {
                continue;
            }

            // Ray trace from player eyes to entity
            Vector toEntity = entity.getLocation().toVector()
                .subtract(player.getEyeLocation().toVector())
                .normalize();
            Vector lookDir = player.getLocation().getDirection();

            // Check if looking roughly at entity (within ~30 degrees)
            double dot = toEntity.dot(lookDir);
            if (dot > 0.85) { // cos(30°) ≈ 0.866
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isInCombat() {
        if (entity instanceof LivingEntity living) {
            // Check if recently damaged or targeting a player
            if (living.getNoDamageTicks() > 0) {
                return true;
            }
            if (entity instanceof Mob mob && mob.getTarget() instanceof Player) {
                return true;
            }
        }
        return false;
    }

    public Entity getBukkitEntity() {
        return entity;
    }
}
