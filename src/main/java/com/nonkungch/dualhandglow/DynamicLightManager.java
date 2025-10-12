// DynamicLightManager.java

package com.nonkungch.dualhandglow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.BlockPosition;
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
        // เพิ่มไอเทมอื่นๆ ที่ให้แสงสว่างได้ตามต้องการ
    }};
    
    // Light Block (Material.LIGHT) เป็นบล็อกพิเศษในเวอร์ชัน 1.17+
    // ควรตรวจสอบว่า Material.LIGHT มีอยู่ในเวอร์ชันที่ใช้หรือไม่
    private static final Material LIGHT_BLOCK = Material.LIGHT; 

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
        // ใช้ getBlock().getLocation() เพื่อให้แน่ใจว่าเป็นตำแหน่งบล็อกที่แน่นอน
        Location newLightLoc = player.getLocation().add(0, 1, 0).getBlock().getLocation();
        Location oldLightLoc = playerLightLocations.get(player.getUniqueId());

        // A. ลบแสงเก่า (ถ้ามีการเปลี่ยนตำแหน่งหรือระดับแสงเป็น 0)
        if (oldLightLoc != null) {
            // ต้องลบเมื่อ: 1. เปลี่ยนตำแหน่ง, หรือ 2. ไม่ถือไอเทมแสงแล้ว
            if (!oldLightLoc.equals(newLightLoc) || lightLevel == 0) {
                // ส่ง Packet เพื่อให้ผู้เล่นเห็นบล็อกเดิม (โดยการดึง Type จากโลกจริง)
                sendBlockChange(player, oldLightLoc, oldLightLoc.getBlock().getType());
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

    /**
     * ส่ง Packet BLOCK_CHANGE เพื่อจำลอง Light Block
     * @param receiver ผู้เล่นที่จะเห็นการเปลี่ยนแปลง
     * @param loc ตำแหน่งที่จะสร้างแสง
     * @param lightLevel ระดับแสง (1-15)
     */
    private void sendLightBlockChange(Player receiver, Location loc, int lightLevel) {
        try {
            // Light Block State String สำหรับ 1.17+ ถึง 1.21+
            // format: Material[property=value]
            WrappedBlockData lightData = WrappedBlockData.createData(LIGHT_BLOCK, "[level=" + lightLevel + "]");
            
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            // ตั้งค่าตำแหน่งบล็อก
            packet.getBlockPositionModifier().write(0, new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            // ตั้งค่า BlockData
            packet.getBlockData().write(0, lightData);

            // ส่ง Packet ให้กับผู้รับ
            protocolManager.sendServerPacket(receiver, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Light Block packet for player " + receiver.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * ส่ง Packet BLOCK_CHANGE เพื่อเปลี่ยนบล็อกกลับเป็นสภาพเดิม (ในโลกจริง)
     * @param receiver ผู้เล่นที่จะเห็นการเปลี่ยนแปลง
     * @param loc ตำแหน่งที่จะคืนค่า
     * @param originalMaterial Material เดิมของบล็อกจริง
     */
    private void sendBlockChange(Player receiver, Location loc, Material originalMaterial) {
        try {
            // ใช้ Block Data เดิมจากโลกจริง (ไม่ต้องระบุ state, ใช้ Material ธรรมดา)
            WrappedBlockData originalData = WrappedBlockData.createData(originalMaterial); 
            
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            // ตั้งค่าตำแหน่งบล็อก
            packet.getBlockPositionModifier().write(0, new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            // ตั้งค่า BlockData เดิม
            packet.getBlockData().write(0, originalData);

            // ส่ง Packet ให้กับผู้รับ
            protocolManager.sendServerPacket(receiver, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Block Change (restore) packet for player " + receiver.getName() + ": " + e.getMessage());
        }
    }

    /**
     * ลบแสงที่จำลองขึ้นทั้งหมดเมื่อปลั๊กอินปิด
     */
    public void removeAllLights() {
        for (UUID uuid : playerLightLocations.keySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Location loc = playerLightLocations.get(uuid);
                // คืนค่าบล็อกเดิมในโลกจริงให้กับผู้เล่น
                sendBlockChange(player, loc, loc.getBlock().getType()); 
            }
        }
        playerLightLocations.clear();
        plugin.getLogger().info("Cleaned up all dynamic lights.");
    }
}
