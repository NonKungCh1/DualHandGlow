// DynamicLightManager.java

package com.nonkungch.dualhandglow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DynamicLightManager {

    private final ProtocolManager protocolManager;
    private final Plugin plugin;
    // Map สำหรับเก็บตำแหน่งของ Light Block ที่ถูกจำลองขึ้นมาสำหรับผู้เล่นแต่ละคน
    private final Map<UUID, Location> playerLightLocations = new HashMap<>();
    
    // กำหนดระดับแสงสำหรับไอเทมต่างๆ
    private static final Map<Material, Integer> LIGHT_LEVELS = new HashMap<>() {{
        put(Material.TORCH, 14);
        put(Material.LANTERN, 15);
        put(Material.SEA_LANTERN, 15);
        put(Material.GLOWSTONE, 15);
        put(Material.SHROOMLIGHT, 15);
        put(Material.MAGMA_BLOCK, 3);
        // สามารถเพิ่มไอเทมอื่นๆ ที่ให้แสงสว่างได้ตามต้องการ
    }};
    
    // Light Block (Material.LIGHT) เป็นบล็อกพิเศษในเวอร์ชันใหม่
    private static final Material LIGHT_BLOCK = Material.valueOf("LIGHT"); 

    public DynamicLightManager(Plugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void updateLight(Player player) {
        // 1. ค้นหาระดับแสงที่ควรจะมี
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        int lightLevel = getHighestLightLevel(mainHand, offHand);

        // 2. ตำแหน่งแสงใหม่ (1 บล็อกเหนือศีรษะ)
        Location newLightLoc = player.getLocation().add(0, 1, 0).getBlock().getLocation();
        Location oldLightLoc = playerLightLocations.get(player.getUniqueId());

        // A. ลบแสงเก่า (ถ้ามีการเปลี่ยนตำแหน่งหรือระดับแสงเป็น 0)
        if (oldLightLoc != null) {
            if (!oldLightLoc.equals(newLightLoc) || lightLevel == 0) {
                // ส่ง Packet เพื่อให้ผู้เล่นเห็นบล็อกเดิม (โดยการดึง Type จากโลกจริง)
                sendBlockChange(player, oldLightLoc, oldLightLoc.getBlock().getType(), 0);
                playerLightLocations.remove(player.getUniqueId());
            }
        }

        // B. สร้างแสงใหม่
        if (lightLevel > 0) {
            // ส่ง Packet เพื่อให้ผู้เล่นเห็น Light Block
            sendLightBlockChange(player, newLightLoc, lightLevel); 
            playerLightLocations.put(player.getUniqueId(), newLightLoc);
        }
    }

    private int getHighestLightLevel(ItemStack... items) {
        int max = 0;
        for (ItemStack item : items) {
            if (item != null && LIGHT_LEVELS.containsKey(item.getType())) {
                max = Math.max(max, LIGHT_LEVELS.get(item.getType()));
            }
        }
        return max;
    }

    // *** เมธอดสำคัญ: ส่ง Packet เพื่อเปลี่ยนบล็อกที่ผู้เล่นเห็น ***
    private void sendLightBlockChange(Player receiver, Location loc, int lightLevel) {
        try {
            // สร้าง BlockData สำหรับ Light Block ที่มีคุณสมบัติ 'level' เท่ากับ lightLevel
            // นี่คือการจำลอง Block State ของ Light Block
            WrappedBlockData lightData = WrappedBlockData.createData(LIGHT_BLOCK, "[level=" + lightLevel + "]");
            
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            // ตั้งค่าตำแหน่งบล็อก
            packet.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            // ตั้งค่า BlockData
            packet.getBlockData().write(0, lightData);

            // ส่ง Packet ให้กับผู้รับ
            protocolManager.sendServerPacket(receiver, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Light Block packet: " + e.getMessage());
        }
    }
    
    // *** เมธอดสำคัญ: ส่ง Packet เพื่อเปลี่ยนบล็อกที่ผู้เล่นเห็นกลับเป็นบล็อกเดิม ***
    private void sendBlockChange(Player receiver, Location loc, Material originalMaterial, int data) {
        try {
            // ใช้ Block State เดิมจากโลกจริง (ในที่นี้คือ Material/AIR)
            WrappedBlockData originalData = WrappedBlockData.createData(originalMaterial); 
            
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            // ตั้งค่าตำแหน่งบล็อก
            packet.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            // ตั้งค่า BlockData เดิม
            packet.getBlockData().write(0, originalData);

            // ส่ง Packet ให้กับผู้รับ
            protocolManager.sendServerPacket(receiver, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Block Change (restore) packet: " + e.getMessage());
        }
    }

    public void removeAllLights() {
        for (UUID uuid : playerLightLocations.keySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Location loc = playerLightLocations.get(uuid);
                // คืนค่าบล็อกเดิมในโลกจริงให้กับผู้เล่น
                sendBlockChange(player, loc, loc.getBlock().getType(), 0); 
            }
        }
        playerLightLocations.clear();
    }
}
