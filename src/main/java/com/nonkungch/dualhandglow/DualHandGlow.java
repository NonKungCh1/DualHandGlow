// DualHandGlow.java

package com.nonkungch.dualhandglow;

import com.nonkungch.dualhandglow.command.OffHandCommand;
import com.nonkungch.dualhandglow.light.DynamicLightHandler;
import com.nonkungch.dualhandglow.task.GlowTask;
import org.bukkit.plugin.java.JavaPlugin;

public class DualHandGlow extends JavaPlugin {

    private DynamicLightHandler lightHandler;
    
    @Override
    public void onEnable() {
        // 1. Initial Light Handler
        lightHandler = new DynamicLightHandler(this);
        
        // 2. ลงทะเบียนคำสั่งสลับมือ
        this.getCommand("offhand").setExecutor(new OffHandCommand());
        
        // 3. เริ่มงาน Task
        if (lightHandler.isLightApiAvailable()) {
            // Task จะรับ Handler ตัวใหม่ไปใช้
            new GlowTask(this, lightHandler).runTaskTimer(this, 0L, 4L); 
            getLogger().info("Dynamic Light functionality ENABLED using LightAPI.");
        } else {
            getLogger().warning("LightAPI not found. Dynamic Light is disabled. Please install LightAPI for full functionality.");
        }
        
        getLogger().info("DualHandGlow (v" + getDescription().getVersion() + ") has been enabled.");
    }

    @Override
    public void onDisable() {
        // ลบแสงทั้งหมดเมื่อปลั๊กอินปิด
        if (lightHandler != null) {
            lightHandler.removeAllLights(getServer().getOnlinePlayers());
        }
        getLogger().info("DualHandGlow has been disabled.");
    }
}
