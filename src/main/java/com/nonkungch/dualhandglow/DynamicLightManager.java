package com.nonkungch.dualhandglow;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicLightManager {

    private final Map<UUID, Location> activeLights = new ConcurrentHashMap<>();
    private final DualHandGlow plugin; // อ้างอิงถึง Main Class

    public DynamicLightManager(DualHandGlow plugin) {
        this.plugin = plugin;
    }

    // ****************************
    // 1. PUBLIC API สำหรับ GlowTask
    // ****************************

    public void updateLight(Player player) {
        // 1.1 ตรวจสอบว่าผู้เล่นควรมีแสงหรือไม่
        // (ตรวจสอบไอเท็มในมือหลัก/รอง ว่าเป็นแหล่งกำเนิดแสงหรือไม่)
        boolean shouldHaveLight = LightUtils.isLightSource(player.getEquipment().getItemInMainHand()) || 
                                 LightUtils.isLightSource(player.getEquipment().getItemInOffHand());
        
        Location currentLocation = player.getLocation().getBlock().getLocation(); // ตำแหน่งบล็อกที่ผู้เล่นยืน
        Location previousLocation = activeLights.get(player.getUniqueId());

        if (shouldHaveLight) {
            if (previousLocation != null && previousLocation.equals(currentLocation)) {
                // ผู้เล่นถือแสง และไม่ได้เคลื่อนที่ -> ไม่ต้องทำอะไร
                return;
            }

            // 1.2 ถ้ามีแสงเดิมอยู่และย้ายตำแหน่ง ให้ลบแสงเดิมออกก่อน
            if (previousLocation != null) {
                removeLight(player, previousLocation);
            }

            // 1.3 สร้างแสงใหม่ที่ตำแหน่งปัจจุบัน
            createLight(player, currentLocation);
            activeLights.put(player.getUniqueId(), currentLocation);

        } else {
            // 1.4 ถ้าไม่ควรมีแสงแล้ว แต่ยังมีแสงเก่าอยู่ ให้ลบออก
            if (previousLocation != null) {
                removeLight(player, previousLocation);
                activeLights.remove(player.getUniqueId());
            }
        }
    }
    
    public void removePlayerLight(Player player) {
        Location previousLocation = activeLights.remove(player.getUniqueId());
        if (previousLocation != null) {
            removeLight(player, previousLocation);
        }
    }

    // ****************************
    // 2. NMS/PACKET LOGIC (ใช้ LightPacketInjector)
    // ****************************

    private void createLight(Player player, Location location) {
        // * โค้ดนี้จะใช้คลาส LightPacketInjector ของคุณ *
        
        // 1. ส่งแพ็คเก็ต BlockChange ไปยังผู้เล่นทุกคนรอบข้าง
        //    เพื่อเซ็ตบล็อกที่ 'location' ให้เป็น 'Light Block' ที่ Light Level 15
        LightPacketInjector.sendFakeBlock(location, 15, LightPacketInjector.LIGHT_BLOCK_TYPE);
        
        // 2. ส่งแพ็คเก็ต LightUpdate ไปยังผู้เล่นทุกคนรอบข้าง (สำคัญมาก!)
        //    เพื่อให้ไคลเอนต์ทำการคำนวณแสงใหม่
        LightPacketInjector.sendLightUpdate(location);
    }

    private void removeLight(Player player, Location location) {
        // * โค้ดนี้จะใช้คลาส LightPacketInjector ของคุณ *

        // 1. ดึงข้อมูลบล็อกจริงที่ตำแหน่งนั้นๆ
        //    (คุณอาจต้องใช้ NMS เพื่อดึงข้อมูล Block Data จริงๆ มาใช้)
        //    สำหรับตอนนี้: ใช้บล็อกจริงจาก World
        org.bukkit.block.Block actualBlock = location.getBlock();

        // 2. ส่งแพ็คเก็ต BlockChange กลับไปยังผู้เล่นทุกคนรอบข้าง
        //    เพื่อเซ็ตบล็อกที่ 'location' กลับไปเป็นบล็อกเดิม
        LightPacketInjector.sendFakeBlock(location, actualBlock.getBlockData());
        
        // 3. ส่งแพ็คเก็ต LightUpdate เพื่อให้ไคลเอนต์ลบแสง
        LightPacketInjector.sendLightUpdate(location);
    }
          }
