package com.github.arboriginal.ElytraLanding;

import static com.github.arboriginal.ElytraLanding.Plugin.inst;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

class Listeners implements Listener {
    private final String elgla = "el_gliding_activator";

    @EventHandler(ignoreCancelled = true)
    private void onEntityDamage(EntityDamageEvent e) {
        if (e.getCause() != DamageCause.FALL || !(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();
        if (!p.hasPermission("ElytraLanding.landing") || !Utils.elytraValidity(p)) return;
        UUID uid = p.getUniqueId(); // @formatter:off
        if ((!inst.landings.containsKey(uid) || System.currentTimeMillis() > inst.landings.get(uid))
          && !p.hasPermission("ElytraLanding.auto")) return;
        // @formatter:on
        double damages = Math.min(p.getHealth() - inst.conf.LAND_MIN, e.getDamage() * inst.conf.LAND_MTP);
        e.setDamage(damages);

        if (p.hasPermission("ElytraLanding.damage")) Utils.landingProceed(p, damages);
    }

    @EventHandler(ignoreCancelled = true)
    private void onEntityToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();
        if (p.hasMetadata(elgla)) {
            Long now = System.currentTimeMillis();
            for (MetadataValue meta : p.getMetadata(elgla)) if (meta.asLong() < now + 1000) {
                e.setCancelled(true);
                p.removeMetadata(elgla, inst);
                return;
            }
        }

        if (inst.conf.FALL && p.isGliding() && p.hasPermission("ElytraLanding.smoothFall")) {
            ItemStack elytra = Utils.elytraWeared(p);
            if (elytra == null || Utils.elytraDamages(elytra) != Material.ELYTRA.getMaxDurability() - 1) return;
            p.addPotionEffect(inst.conf.FALL_PE);
        }
    }

    @EventHandler(ignoreCancelled = false)
    private void onPlayerInteract(PlayerInteractEvent e) {
        if (!inst.conf.LAUNCH || e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player p = e.getPlayer(); // @formatter:off
        if (p.isGliding() || !p.isOnGround()|| !p.hasPermission("ElytraLanding.launching")
         || p.getInventory().getItemInMainHand().getType() != Material.FIREWORK_ROCKET
         || p.getLocation().getPitch() > inst.conf.LAUNCH_MP || !Utils.elytraValidity(p)
         || inst.landings.containsKey(p.getUniqueId())) return;
        // @formatter:on
        ItemStack stack = p.getInventory().getItemInMainHand();
        stack.setAmount(stack.getAmount() - 1);
        p.getInventory().setItemInMainHand(stack);
        p.setVelocity(p.getLocation().getDirection().normalize().multiply(inst.conf.LAUNCH_FM));
        p.setMetadata(elgla, new FixedMetadataValue(inst, System.currentTimeMillis()));
        p.setGliding(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                p.setGliding(true);
            }
        }.runTaskLaterAsynchronously(inst, 1);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("ElytraLanding.landing")) return;
        boolean land = inst.landings.containsKey(p.getUniqueId());
        if (p.isGliding()) {
            if (land) Utils.landingReset(p);
            if (p.isSneaking()) Utils.landingInit(p);
            if (inst.conf.SWIM && Utils.checkUnderWater(p)) {
                p.setGliding(false);
                p.setSwimming(true);
            }
        }
        else if (land && p.isOnGround()) Utils.landingReset(p);
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent e) {
        UUID uid = e.getPlayer().getUniqueId();
        Utils.taskClear(uid, inst.tasks.get(uid));
        inst.landings.remove(uid);
    }
}
