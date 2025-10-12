// GlowTask.java

package com.nonkungch.dualhandglow;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class GlowTask extends BukkitRunnable {
    
    private final DynamicLightManager lightManager;

    public GlowTask(JavaPlugin plugin, DynamicLightManager lightManager) {
        this.lightManager = lightManager;
    }

    @Override
    public void run() {
        // วนลูปตรวจสอบผู้เล่นทุกคนที่ออนไลน์อยู่
        for (Player player : Bukkit.getOnlinePlayers()) {
            lightManager.updateLight(player);
        }
    }
}
