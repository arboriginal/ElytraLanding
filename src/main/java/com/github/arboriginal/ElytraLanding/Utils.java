package com.github.arboriginal.ElytraLanding;

import static com.github.arboriginal.ElytraLanding.Plugin.inst;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

class Utils {
    static boolean checkUnderWater(Player p) {
        Block b = p.getLocation().getBlock();
        if (!b.isLiquid()) return false;
        if (inst.conf.SWIM_OB == 0) return true;
        for (int i = 0; i < inst.conf.SWIM_OB; i++) {
            b = b.getRelative(BlockFace.UP);
            if (!b.isLiquid()) return false;
        }
        return true;
    }

    static int elytraDamages(ItemStack elytra) {
        if (elytra.getItemMeta().isUnbreakable()) return 0;
        ItemMeta meta = elytra.getItemMeta();
        return (meta instanceof Damageable) ? ((Damageable) meta).getDamage() : 0;
    }

    static boolean elytraValidity(Player p) {
        ItemStack elytra = elytraWeared(p);
        if (elytra == null) return false;
        return (elytraDamages(elytra) + 1 < Material.ELYTRA.getMaxDurability());
    }

    static ItemStack elytraWeared(Player p) {
        ItemStack elytra = p.getInventory().getChestplate();
        return (elytra != null && elytra.getType() == Material.ELYTRA) ? elytra : null;
    }

    static void landingInit(Player p) {
        UUID uid = p.getUniqueId();
        taskClear(uid, inst.tasks.get(uid));
        taskSet(p, (int) Math.ceil(inst.conf.LAND_TIME / 50 - 2));
        inst.landings.put(uid, System.currentTimeMillis() + inst.conf.LAND_TIME);
        p.setGliding(false);
        p.setVelocity(inst.conf.LAND_VEC);
    }

    static void landingProceed(Player p, double damages) {
        taskSet(p, inst.conf.LAND_REST);

        if (inst.conf.SNDE)
            p.playSound(p.getLocation(), inst.conf.SNDS, inst.conf.SNDV, inst.conf.SNDP);

        if (inst.conf.PCLE) particleEffect(p);
        if (!inst.conf.HURT) return;

        Location loc    = p.getLocation();
        Vector   vector = loc.toVector();
        // @formatter:off
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        for (Entity entity : p.getNearbyEntities(inst.conf.HURT_X, inst.conf.HURT_Y, inst.conf.HURT_Z))
            if (entity instanceof org.bukkit.entity.Damageable && (
                   (inst.conf.HURT_A && entity instanceof Animals)
                || (inst.conf.HURT_M && entity instanceof Monster)
                || (inst.conf.HURT_P && entity instanceof Player)
            ) && (inst.conf.HURT_T || !(entity instanceof Tameable) || !((Tameable) entity).isTamed())) {
                Location l = entity.getLocation();
                double edX = Math.abs(x - l.getX()); if (edX > inst.conf.HURT_X) continue;
                double edY = Math.abs(y - l.getY()); if (edY > inst.conf.HURT_Y) continue;
                double edZ = Math.abs(z - l.getZ()); if (edZ > inst.conf.HURT_Z) continue;
                double dmf = (
                    (inst.conf.HURT_X - edX) / inst.conf.HURT_X +
                    (inst.conf.HURT_Y - edY) / inst.conf.HURT_Y +
                    (inst.conf.HURT_Z - edZ) / inst.conf.HURT_Z
                ) / 3;
                // @formatter:on
                ((org.bukkit.entity.Damageable) entity).damage(dmf * inst.conf.HURT_D * damages, p);
                entity.setVelocity(l.toVector().subtract(vector).normalize().multiply(dmf * inst.conf.HURT_F));
            }
    }

    static void landingReset(Player p) {
        taskSet(p, inst.conf.LAND_REST);
    }

    static void taskClear(UUID uid, BukkitRunnable task) {
        if (task == null) task = inst.tasks.get(uid);
        if (task != null) {
            task.cancel();
            inst.tasks.remove(uid);
        }
    }

    // Private methods -------------------------------------------------------------------------------------------------

    private static void particleEffect(Player p) {
        Location l = p.getLocation();
        World    w = p.getWorld();
        double   x = l.getX(), y = l.getY(), z = l.getZ();
        int      d = 0;
        for (double dist = 0; dist < inst.conf.PCLT; dist += inst.conf.PCLS)
            particleRing(w, x, y, z, dist, d += inst.conf.PCLD);
    }

    private static void particleRing(World w, double x, double y, double z, double d, int delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < inst.conf.PCLA; i += 1) {
                    double a = i * inst.conf.PCLR;
                    w.spawnParticle(inst.conf.PCLP, x + (d * Math.cos(a)), y, z + (d * Math.sin(a)), inst.conf.PCLT);
                }
                this.cancel();
            }
        }.runTaskLaterAsynchronously(inst, delay);
    }

    private static void taskSet(Player p, Integer delay) {
        UUID uid = p.getUniqueId();
        if (!inst.ready) {
            taskClear(uid, inst.tasks.get(uid));
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (isCancelled()) return;
                if (inst.landings.remove(uid) != null) {
                    p.setVelocity(new Vector(0, 0, 0));
                    if (elytraValidity(p)) p.setGliding(true);
                }
                taskClear(uid, this);
            }
        };

        task.runTaskLaterAsynchronously(inst, delay);
        if (task != null) {
            taskClear(uid, inst.tasks.get(uid));
            inst.tasks.put(uid, task);
        }
    }
}
