package com.github.arboriginal.ElytraLanding;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Plugin extends JavaPlugin implements Listener {
    static Plugin                 inst;
    Configuration                 conf;
    HashMap<UUID, BukkitRunnable> tasks;
    HashMap<UUID, Long>           landings = new HashMap<UUID, Long>();
    boolean                       ready    = false;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().toLowerCase().equals("el-reload")) return super.onCommand(sender, command, label, args);
        reloadConfig();
        sender.sendMessage(conf.RELOADED);
        return true;
    }

    @Override
    public void onDisable() {
        ready = false;
        tasks.keySet().forEach(uid -> {
            Utils.taskClear(uid, tasks.get(uid));
        });
        super.onDisable();
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
            return;
        }
        inst = this;
        reloadConfig();
        getServer().getPluginManager().registerEvents(new Listeners(), this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        saveDefaultConfig();
        ready = false;
        tasks = new HashMap<UUID, BukkitRunnable>();
        conf  = new Configuration(getConfig());
        ready = true;
        saveConfig();
    }
}
