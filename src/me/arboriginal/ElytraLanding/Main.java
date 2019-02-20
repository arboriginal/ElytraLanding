package me.arboriginal.ElytraLanding;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Main extends JavaPlugin implements Listener {
  private HashMap<UUID, Long>           landings;
  private HashMap<UUID, BukkitRunnable> tasks;
  private FileConfiguration             config;
  private boolean                       ready = false;

  // ----------------------------------------------------------------------------------------------
  // JavaPlugin methods
  // ----------------------------------------------------------------------------------------------

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().toLowerCase().equals("el-reload")) {
      reloadConfig();
      sender.sendMessage(config.getString("messages.configuration_reloaded"));
      return true;
    }

    return super.onCommand(sender, command, label, args);
  }

  @Override
  public void onDisable() {
    ready = false;
    super.onDisable();
    tasks.keySet().forEach(uid -> {
      taskClear(uid, tasks.get(uid));
    });
  }

  @Override
  public void onEnable() {
    super.onEnable();

    try {
      getServer().spigot();
    }
    catch (Exception e) {
      getServer().getPluginManager().disablePlugin(this);
      getLogger().severe("This plugin only works on Spigot servers!");
      // No need to go on, it will not work
      return;
    }

    landings = new HashMap<UUID, Long>();

    reloadConfig();
    getServer().getPluginManager().registerEvents(this, this);
  }

  @Override
  public void reloadConfig() {
    super.reloadConfig();
    saveDefaultConfig();

    ready  = false;
    tasks  = new HashMap<UUID, BukkitRunnable>();
    config = getConfig();
    config.options().copyDefaults(true);
    checkConfig();
    saveConfig();
    ready = true;
  }

  // ----------------------------------------------------------------------------------------------
  // Listener methods
  // ----------------------------------------------------------------------------------------------

  @EventHandler
  public void onEntityDamage(EntityDamageEvent event) {
    if (event.isCancelled() || !event.getCause().equals(DamageCause.FALL) || !(event.getEntity() instanceof Player))
      return;

    Player player = (Player) event.getEntity();

    if (!player.hasPermission("ElytraLanding.landing") || !elytraValidity(player)) return;
    if ((!landings.containsKey(player.getUniqueId()) || System.currentTimeMillis() > landings.get(player.getUniqueId()))
        && !player.hasPermission("ElytraLanding.auto"))
      return;

    double damages = Math.min(
        player.getHealth() - config.getInt("landing.damage.min_life"),
        event.getDamage() * config.getDouble("landing.damage.reduction"));

    event.setDamage(damages);

    if (player.hasPermission("ElytraLanding.damage")) landingProceed(player, damages);
  }

  @EventHandler
  public void onEntityToggleGlide(EntityToggleGlideEvent event) {
    if (event.isCancelled() || !config.getBoolean("falling.enable") || !(event.getEntity() instanceof Player)) return;
    Player player = (Player) event.getEntity();
    if (!player.isGliding() || !player.hasPermission("ElytraLanding.smoothFall")) return;
    ItemStack elytra = elytraWeared(player);
    if (elytra == null || elytraDamages(elytra) != Material.ELYTRA.getMaxDurability() - 1) return;

    player.addPotionEffect(new PotionEffect(
        PotionEffectType.SLOW_FALLING, config.getInt("falling.duration") * 20, 0, false, false, false));
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (!config.getBoolean("launching.enable") || !event.getAction().equals(Action.RIGHT_CLICK_AIR)) return;

    Player player = event.getPlayer();

    if (!player.hasPermission("ElytraLanding.launching")
        || !player.getInventory().getItemInMainHand().getType().equals(Material.FIREWORK_ROCKET)
        || player.isGliding() || !elytraValidity(player)
        || checkUnderBlockEmpty(player) && !landings.containsKey(player.getUniqueId())
        || player.getLocation().getPitch() > config.getDouble("launching.max_pitch"))
      return;

    ItemStack stack = player.getInventory().getItemInMainHand();
    stack.setAmount(stack.getAmount() - 1);
    player.getInventory().setItemInMainHand(stack);
    player.setVelocity(player.getLocation().getDirection().normalize().multiply(config.getDouble("launching.force")));

    new BukkitRunnable() {
      @Override
      public void run() {
        player.setGliding(true);
      }
    }.runTaskLaterAsynchronously(this, 1);
  }

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.isCancelled()) return;
    Player player = event.getPlayer();
    if (!player.hasPermission("ElytraLanding.landing")) return;
    if (player.isGliding()) {
      landingReset(player);
      if (player.isSneaking()) landingInit(player);
      if (config.getBoolean("swimming.enable") && checkUnderWater(player)) {
        player.setGliding(false);
        player.setSwimming(true);
      }
    }
    else if (!checkUnderBlockEmpty(player)) landingReset(player);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID uid = event.getPlayer().getUniqueId();
    taskClear(uid, tasks.get(uid));
    landings.remove(uid);
  }

  // ----------------------------------------------------------------------------------------------
  // Private methods
  // ----------------------------------------------------------------------------------------------

  private void checkConfig() {
    if (config.getBoolean("particle.enable"))
      try {
        Particle.valueOf(config.getString("particle.name"));
      }
      catch (Exception e) {
        getLogger().warning(config.getString("messages.invalid_particle_name"));
        config.set("particle.enable", false);
      }

    if (config.getBoolean("sound.enable"))
      try {
        Sound.valueOf(config.getString("sound.name"));
      }
      catch (Exception e) {
        getLogger().warning(config.getString("messages.invalid_sound_name"));
        config.set("sound.enable", false);
      }
  }

  private boolean checkUnderBlockEmpty(Player player) {
    return player.getLocation().getBlock().getRelative(BlockFace.DOWN).isEmpty();
  }

  private boolean checkUnderWater(Player player) {
    Block block = player.getLocation().getBlock();
    if (!block.isLiquid()) return false;
    int over = config.getInt("swimming.blocks_over");
    if (over == 0) return true;
    for (int i = 0; i < over; i++) {
      block = block.getRelative(BlockFace.UP);
      if (!block.isLiquid()) return false;
    }
    return true;
  }

  private int elytraDamages(ItemStack elytra) {
    if (elytra.getItemMeta().isUnbreakable()) return 0;
    Map<String, Object> metas = elytra.getItemMeta().serialize();
    return metas.containsKey("Damage") ? (int) metas.get("Damage") : 0;
  }

  private boolean elytraValidity(Player player) {
    ItemStack elytra = elytraWeared(player);
    if (elytra == null) return false;
    return (elytraDamages(elytra) + 1 < Material.ELYTRA.getMaxDurability());
  }

  private ItemStack elytraWeared(Player player) {
    ItemStack elytra = player.getInventory().getChestplate();
    return (elytra != null && elytra.getType().equals(Material.ELYTRA)) ? elytra : null;
  }

  private void landingInit(Player player) {
    int  delay = config.getInt("landing.delay");
    UUID uid   = player.getUniqueId();

    taskClear(uid, tasks.get(uid));
    taskSet(player, (int) Math.ceil(delay / 50 - 2));
    landings.put(uid, System.currentTimeMillis() + delay);

    player.setGliding(false);
    player.setVelocity(new Vector(0, -config.getDouble("landing.force"), 0));
  }

  private void landingProceed(Player player, double damages) {
    taskSet(player, config.getInt("landing.reset"));

    if (config.getBoolean("sound.enable"))
      player.playSound(
          player.getLocation(),
          Sound.valueOf(config.getString("sound.name")),
          (float) config.getDouble("sound.volume"),
          (float) config.getDouble("sound.pitch"));

    if (config.getBoolean("particle.enable")) particleEffect(player);
    if (!config.getBoolean("affect.enable")) return;

    Location location = player.getLocation();
    Vector   vector   = location.toVector();
    double   factor   = config.getDouble("affect.damages");
    double   pushMtp  = config.getDouble("affect.push");
    int      maxDistX = config.getInt("affect.distance.x");
    int      maxDistY = config.getInt("affect.distance.y");
    int      maxDistZ = config.getInt("affect.distance.z");

    for (Entity entity : player.getNearbyEntities(maxDistX, maxDistY, maxDistZ))
      if (entity instanceof Damageable && ( // @formatter:off
           (config.getBoolean("affect.types.animal")  && entity instanceof Animals)
        || (config.getBoolean("affect.types.monster") && entity instanceof Monster)
        || (config.getBoolean("affect.types.player")  && entity instanceof Player)
      )) { // @formatter:on
        Location loc = entity.getLocation();
        double distX = Math.abs(location.getX() - loc.getX());
        double distY = Math.abs(location.getY() - loc.getY());
        double distZ = Math.abs(location.getZ() - loc.getZ());
        if (distX > maxDistX || distY > maxDistY || distZ > maxDistZ) continue;

        double fact = ( // @formatter:off
          (maxDistX - distX) / maxDistX + 
          (maxDistY - distY) / maxDistY + 
          (maxDistZ - distZ) / maxDistZ
        ) / 3; // @formatter:on

        ((Damageable) entity).damage(fact * factor * damages, player);
        entity.setVelocity(entity.getLocation().toVector().subtract(vector).normalize().multiply(fact * pushMtp));
      }
  }

  private void landingReset(Player player) {
    if (landings.containsKey(player.getUniqueId())) taskSet(player, config.getInt("landing.reset"));
  }

  private void particleEffect(Player player) {
    Particle particle = Particle.valueOf(config.getString("particle.name"));

    double posX   = player.getLocation().getX();
    double posY   = player.getLocation().getY();
    double posZ   = player.getLocation().getZ();
    int    delay  = 0;
    int    pDelay = config.getInt("particle.duration");
    int    amount = config.getInt("particle.amount");
    double aStep  = (2 * Math.PI) / amount;
    double dStep  = config.getInt("particle.distance") / config.getInt("particle.step");

    for (double d = 0; d < config.getInt("particle.distance"); d += dStep) {
      particleRing(player, particle, posX, posY, posZ, amount, aStep, d, delay, pDelay);
      delay += config.getInt("particle.step_delay");
    }
  }

  private void particleRing(
      Player player, Particle particle, double x, double y, double z,
      int amount, double aStep, double dist, int delay, int duration //
  ) {
    new BukkitRunnable() {
      @Override
      public void run() {
        for (int i = 0; i < amount; i += 1) {
          double angle = i * aStep;

          player.getWorld().spawnParticle(
              particle,
              x + (dist * Math.cos(angle)),
              y,
              z + (dist * Math.sin(angle)),
              duration);
        }

        this.cancel();
      }
    }.runTaskLaterAsynchronously(this, delay);
  }

  private void taskClear(UUID uid, BukkitRunnable task) {
    if (task == null) task = tasks.get(uid);
    if (task != null) {
      task.cancel();
      tasks.remove(uid);
    }
  }

  private void taskSet(Player player, Integer delay) {
    UUID uid = player.getUniqueId();

    if (!ready) {
      taskClear(uid, tasks.get(uid));
      return;
    }

    BukkitRunnable task = new BukkitRunnable() {
      @Override
      public void run() {
        if (isCancelled()) return;
        if (landings.remove(uid) != null) {
          player.setVelocity(new Vector(0, 0, 0));
          if (elytraValidity(player)) player.setGliding(true);
        }
        taskClear(uid, this);
      }
    };

    task.runTaskLaterAsynchronously(this, delay);

    if (task != null) {
      taskClear(uid, tasks.get(uid));
      tasks.put(uid, task);
    }
  }
}
