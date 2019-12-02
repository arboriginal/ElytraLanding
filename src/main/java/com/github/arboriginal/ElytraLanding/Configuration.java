package com.github.arboriginal.ElytraLanding;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

class Configuration {
    final boolean      HURT, HURT_A, HURT_M, HURT_P, FALL, SNDE, PCLE, LAUNCH, SWIM;
    final double       HURT_D, HURT_F, LAND_MTP, LAUNCH_FM, LAUNCH_MP, PCLR;
    final float        SNDP, SNDV;
    final int          HURT_X, HURT_Y, HURT_Z, LAND_MIN, LAND_REST, LAND_TIME, PCLA, PCLD, PCLL, PCLS, PCLT, SWIM_OB;
    final Particle     PCLP;
    final PotionEffect FALL_PE;
    final Sound        SNDS;
    final String       RELOADED;
    final Vector       LAND_VEC;

    Configuration(FileConfiguration config) {
        config.options().copyDefaults(true);
        LAND_MIN  = config.getInt("landing.damage.min_life");
        LAND_MTP  = config.getDouble("landing.damage.reduction");
        LAND_REST = config.getInt("landing.reset");
        LAND_TIME = config.getInt("landing.delay");
        LAND_VEC  = new Vector(0, -config.getDouble("landing.force"), 0);
        RELOADED  = config.getString("messages.configuration_reloaded");

        FALL_PE = (FALL = config.getBoolean("falling.enable")) ? new PotionEffect(PotionEffectType.SLOW_FALLING,
                config.getInt("falling.duration") * 20, 0, false, false, false) : null;

        if (HURT = config.getBoolean("affect.enable")) {
            HURT_A = config.getBoolean("affect.types.animal");
            HURT_M = config.getBoolean("affect.types.monster");
            HURT_P = config.getBoolean("affect.types.player");
            HURT_D = config.getDouble("affect.damages");
            HURT_F = config.getDouble("affect.push");
            HURT_X = getPositiveNotNullInt(config, "affect.distance.x");
            HURT_Y = getPositiveNotNullInt(config, "affect.distance.y");
            HURT_Z = getPositiveNotNullInt(config, "affect.distance.z");
        }
        else {
            HURT_A = HURT_M = HURT_P = false;
            HURT_D = HURT_F = 0;
            HURT_X = HURT_Y = HURT_Z = 0;
        }

        if (LAUNCH = config.getBoolean("launching.enable")) {
            LAUNCH_FM = config.getDouble("launching.force");
            LAUNCH_MP = config.getDouble("launching.max_pitch");
        }
        else LAUNCH_FM = LAUNCH_MP = 0;

        SWIM_OB = (SWIM = config.getBoolean("swimming.enable")) ? config.getInt("swimming.blocks_over") : 0;

        if (!config.getBoolean("particle.enable")) PCLP = null;
        else {
            PCLP = (Particle) getEnumValue(Particle.class, config.getString("particle.name"));
            if (PCLP == null) Bukkit.getLogger().warning(config.getString("messages.invalid_particle_name"));
        }

        if (PCLP == null) {
            PCLE = false;
            PCLA = PCLD = PCLL = PCLS = PCLT = 0;
            PCLR = 0;
        }
        else {
            PCLE = true;
            PCLA = config.getInt("particle.amount");
            PCLD = config.getInt("particle.step_delay");
            PCLL = config.getInt("particle.distance");
            PCLT = config.getInt("particle.duration");
            PCLR = (2 * Math.PI) / PCLA;
            PCLS = PCLT / config.getInt("particle.step");
        }

        if (!config.getBoolean("sound.enable")) SNDS = null;
        else {
            SNDS = (Sound) getEnumValue(Sound.class, config.getString("sound.name"));
            if (SNDS == null) Bukkit.getLogger().warning(config.getString("messages.invalid_sound_name"));
        }

        if (SNDS == null) {
            SNDE = false;
            SNDP = 0;
            SNDV = 0;
        }
        else {
            SNDE = true;
            SNDP = (float) config.getDouble("sound.pitch");
            SNDV = (float) config.getDouble("sound.volume");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Enum<?> getEnumValue(Class<?> enumClass, String value) {
        try {
            return Enum.valueOf((Class<Enum>) enumClass, value);
        }
        catch (Exception e) {
            return null;
        }
    }

    private int getPositiveNotNullInt(FileConfiguration config, String key) {
        int val = config.getInt(key);
        if (val < 1) {
            Bukkit.getLogger().warning(key + ": " + config.getString("messages.invalid_int_value"));
            val = 1;
        }
        return val;
    }
}
