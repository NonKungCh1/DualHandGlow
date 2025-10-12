package com.nonkungch.dualhandglow.nms;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// คลาสนี้ต้องถูกวางไว้ใน com.nonkungch.dualhandglow.nms
public class LightPacketInjector {

    // NMS Reflection Fields/Classes/Methods
    private final Class<?> ClientboundLightUpdatePacketClass;
    private final Constructor<?> ClientboundLightUpdatePacketConstructor;
    private final Method getHandleMethod;
    private final Method sendPacketMethod;

    // สถานะแสงที่เคยส่งไปแล้ว เพื่อให้สามารถลบออกได้
    // Key: Player UUID, Value: Map<ChunkLocation, LightLevel>
    private final Map<Player, Map<Location, Integer>> sentLights = new ConcurrentHashMap<>();

    // Light level (15) for our fake light source
    private static final int LIGHT_LEVEL = 15;
    
    // LightPacketInjector constructor
    public LightPacketInjector() throws Exception {
        
        // 1. คลาส NMS ที่สำคัญ (อ้างอิงจาก 1.21 NMS structure)
        ClientboundLightUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundLightUpdatePacket");
        
        // 2. Constructor ที่จำเป็น (ClientboundLightUpdatePacket)
        // Constructor parameters: ChunkPos, BitSet skyYMask, BitSet blockYMask, 
        //                        List<byte[]> skyUpdates, List<byte[]> blockUpdates, boolean trustEdges, boolean refreshSectionCache
        ClientboundLightUpdatePacketConstructor = ClientboundLightUpdatePacketClass.getConstructor(
            Class.forName("net.minecraft.world.level.ChunkPos"), // ChunkPos
            BitSet.class, // skyYMask
            BitSet.class, // blockYMask
            java.util.List.class, // skyUpdates
            java.util.List.class, // blockUpdates
            boolean.class, // trustEdges
            boolean.class  // refreshSectionCache
        );
        
        // 3. Method สำหรับส่ง Packet
        Class<?> playerConnectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
        sendPacketMethod = playerConnectionClass.getMethod("send", packetClass);
        
        // 4. Method สำหรับดึง PlayerConnection
        Class<?> entityPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        getHandleMethod = Class.forName("org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer").getMethod("getHandle");
    }


    public void sendLightPacket(Player player, Location loc, int lightLevel) {
        if (!player.isOnline()) return;

        // ตำแหน่ง Chunk และ Section
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        int sectionY = loc.getBlockY() >> 4;

        try {
            // **ส่วนที่ 1: สร้าง Light Data Array (ขนาด 2048 bytes)**
            // 2048 bytes (16*16*16*0.5 byte) = 4096 nibbles (0-15 light levels)
            byte[] data = new byte[2048];
            
            // ตำแหน่งภายใน Section (0-15)
            int x = loc.getBlockX() & 15;
            int y = loc.getBlockY() & 15;
            int z = loc.getBlockZ() & 15;
            
            // Index ภายใน array data
            int index = (y << 8) | (z << 4) | x; 
            int byteIndex = index / 2;
            
            // ใส่ Light Level ลงใน data array (4 bits ต่อระดับแสง)
            if (index % 2 == 0) {
                // Nibble ล่าง
                data[byteIndex] = (byte) (lightLevel & 0xF); 
            } else {
                // Nibble บน
                data[byteIndex] = (byte) ((lightLevel << 4) | (data[byteIndex] & 0xF));
            }
            
            // **ส่วนที่ 2: สร้าง Packet**

            // ChunkPos (ต้องใช้ Reflection เพื่อสร้าง)
            Class<?> chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            Constructor<?> chunkPosConstructor = chunkPosClass.getConstructor(int.class, int.class);
            Object chunkPos = chunkPosConstructor.newInstance(chunkX, chunkZ);
            
            // BitSet สำหรับ Block Light Mask (ระบุว่า Section ไหนมีการอัปเดต)
            BitSet blockYMask = new BitSet();
            blockYMask.set(sectionY + 1); // +1 เพราะ 0 คือ Section ล่างสุดของโลก

            // Lists สำหรับการอัปเดตแสง
            java.util.List<byte[]> skyUpdates = Collections.emptyList(); // ไม่ใช้ Sky Light
            java.util.List<byte[]> blockUpdates = Collections.singletonList(data);
            
            // สร้าง ClientboundLightUpdatePacket
            Object lightPacket = ClientboundLightUpdatePacketConstructor.newInstance(
                chunkPos,
                new BitSet(), // skyYMask (ว่างเปล่า)
                blockYMask,   // blockYMask
                skyUpdates,
                blockUpdates,
                false, // trustEdges
                true   // refreshSectionCache
            );

            // **ส่วนที่ 3: ส่ง Packet**
            Object nmsPlayer = getHandleMethod.invoke(((CraftPlayer) player));
            Object connection = nmsPlayer.getClass().getField("connection").get(nmsPlayer); // .connection field
            
            sendPacketMethod.invoke(connection, lightPacket);
            
            // บันทึกสถานะแสง
            sentLights.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(loc, lightLevel);

        } catch (Exception e) {
            System.err.println("Error sending custom light packet for " + player.getName());
            e.printStackTrace();
        }
    }
    
    // ฟังก์ชันลบแสง (ส่ง Packet แสงระดับ 0)
    public void removeLightPacket(Player player, Location loc) {
        // ลบแสงที่ตำแหน่งนั้น
        sendLightPacket(player, loc, 0); 
        // ลบออกจาก Map สถานะ
        sentLights.getOrDefault(player, new HashMap<>()).remove(loc);
    }
    
    // ฟังก์ชันลบแสงทั้งหมดเมื่อปลั๊กอินปิด/ผู้เล่นออก
    public void removeAllLights(java.util.Collection<? extends Player> players) {
        for (Player player : players) {
            Map<Location, Integer> lights = sentLights.get(player);
            if (lights != null) {
                for (Location loc : lights.keySet()) {
                    removeLightPacket(player, loc);
                }
            }
            sentLights.remove(player);
        }
    }
  }
