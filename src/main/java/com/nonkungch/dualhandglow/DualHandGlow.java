// DualHandGlow.java

package com.nonkungch.dualhandglow;

import org.bukkit.plugin.java.JavaPlugin;

public class DualHandGlow extends JavaPlugin {

    private DynamicLightManager lightManager;

    @Override
    public void onEnable() {
        // ตรวจสอบ ProtocolLib (เป็นสิ่งจำเป็นสำหรับการจำลอง Dynamic Light)
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib not found! DualHandGlow requires ProtocolLib to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 1. ลงทะเบียนคำสั่งสลับมือ
        this.getCommand("offhand").setExecutor(new OffHandCommand());
        
        // 2. เริ่มต้น Dynamic Light Manager
        lightManager = new DynamicLightManager(this);

        // 3. เริ่ม Timer Task สำหรับ Dynamic Light
        // ตรวจสอบทุกๆ 4 Ticks (~0.2 วินาที)
        new GlowTask(this, lightManager).runTaskTimer(this, 0L, 4L); 
        
        getLogger().info("DualHandGlow (v" + getDescription().getVersion() + ") has been enabled.");
    }

    @Override
    public void onDisable() {
        // ลบแสงทั้งหมดเมื่อปลั๊กอินปิดตัว เพื่อไม่ให้มีแสงค้างในโลก
        if (lightManager != null) {
            lightManager.removeAllLights();
        }
        getLogger().info("DualHandGlow has been disabled.");
    }
          }
