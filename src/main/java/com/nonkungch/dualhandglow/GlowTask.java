package com.nonkungch.dualhandglow.task;

import com.nonkungch.dualhandglow.light.DynamicLightHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

// ปรับปรุงให้รับ DynamicLightHandler แทน LightPacketInjector
public class GlowTask extends BukkitRunnable {

    private final DynamicLightHandler lightHandler;

    public GlowTask(Plugin plugin, DynamicLightHandler lightHandler) {
        this.lightHandler = lightHandler;
    }

    @Override
    public void run() {
        if (!lightHandler.isLightApiAvailable()) {
            // หาก LightAPI ไม่พร้อมใช้งาน ให้หยุด Task
            this.cancel();
            return;
        }

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            Location playerLoc = player.getLocation();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            if (isLightSource(offHand.getType())) {
                // ถ้าถือแหล่งกำเนิดแสง ให้สร้าง Dynamic Light ที่ตำแหน่งผู้เล่น
                lightHandler.setDynamicLight(player, playerLoc);
            } else {
                // ถ้าไม่ได้ถือ ให้ลบ Dynamic Light ออก
                lightHandler.removeDynamicLight(player);
            }
        }
    }
    
    // ฟังก์ชันตรวจสอบว่าเป็น Block ที่มีแสงหรือไม่ (ใช้ตามความเหมาะสม)
    private boolean isLightSource(Material material) {
        return material == Material.TORCH || 
               material == Material.LANTERN ||
               material == Material.GLOWSTONE ||
               material == Material.LAVA_BUCKET;
    }
}
