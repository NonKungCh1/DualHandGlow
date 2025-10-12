package com.nonkungch.dualhandglow.light;

import dev.shibomb.lightapi.LightAPI;
import dev.shibomb.lightapi.LightType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// คลาสนี้จัดการ Dynamic Light โดยใช้ LightAPI
public class DynamicLightHandler {

    private final Plugin plugin;
    // ใช้ Map เพื่อเก็บสถานะแสง (Player -> Location) ที่เราสร้างไว้
    private final Map<Player, Location> activeLights = new ConcurrentHashMap<>();
    private static final int LIGHT_LEVEL = 15;

    public DynamicLightHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    // ตรวจสอบว่า LightAPI ถูกติดตั้งและใช้งานได้หรือไม่
    public boolean isLightApiAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("LightAPI") != null;
    }

    public void setDynamicLight(Player player, Location loc) {
        if (!isLightApiAvailable()) return;

        // 1. ลบแสงเก่าออกก่อน
        removeDynamicLight(player);
        
        // 2. สร้างแสงใหม่ด้วย LightAPI
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // LightAPI.get().setLight(World, x, y, z, LightType, lightLevel, runAsync)
        LightAPI.get().setLight(world, x, y, z, LightType.BLOCK, LIGHT_LEVEL, true);
        
        // 3. บันทึกสถานะแสง
        activeLights.put(player, loc.getBlock().getLocation());
    }

    public void removeDynamicLight(Player player) {
        if (!isLightApiAvailable()) return;
        
        Location oldLoc = activeLights.remove(player);
        
        if (oldLoc != null) {
            World world = oldLoc.getWorld();
            int x = oldLoc.getBlockX();
            int y = oldLoc.getBlockY();
            int z = oldLoc.getBlockZ();
            
            // ลบแสงโดยการตั้งระดับแสงเป็น 0
            LightAPI.get().setLight(world, x, y, z, LightType.BLOCK, 0, true);
        }
    }
    
    // ลบแสงทั้งหมดเมื่อปลั๊กอินปิด
    public void removeAllLights(Collection<? extends Player> players) {
        for (Player player : players) {
            removeDynamicLight(player);
        }
    }
}
