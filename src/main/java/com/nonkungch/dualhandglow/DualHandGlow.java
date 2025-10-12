// DualHandGlow.java

package com.nonkungch.dualhandglow;

import com.nonkungch.dualhandglow.nms.LightPacketInjector; // ต้องสร้างคลาสนี้
import org.bukkit.plugin.java.JavaPlugin;

public class DualHandGlow extends JavaPlugin {

    private LightPacketInjector lightInjector;
    
    @Override
    public void onEnable() {
        // 1. ลงทะเบียนคำสั่งสลับมือ
        this.getCommand("offhand").setExecutor(new OffHandCommand());
        
        // 2. Initial NMS Light Injector (สมมติว่า LightPacketInjector ถูกสร้างสำเร็จ)
        try {
            lightInjector = new LightPacketInjector();
            // 3. เริ่มงาน Task
            new GlowTask(this, lightInjector).runTaskTimer(this, 0L, 4L); 
            getLogger().info("Custom Dynamic Light functionality ENABLED.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize NMS Light Injector! Dynamic Light is disabled.");
            e.printStackTrace();
        }
        
        getLogger().info("DualHandGlow (v" + getDescription().getVersion() + ") has been enabled.");
    }

    @Override
    public void onDisable() {
        // ลบแสงทั้งหมดเมื่อปลั๊กอินปิด
        if (lightInjector != null) {
            lightInjector.removeAllLights(getServer().getOnlinePlayers());
        }
        getLogger().info("DualHandGlow has been disabled.");
    }
}
