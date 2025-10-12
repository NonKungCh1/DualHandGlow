package com.nonkunghc.offhandlight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// เปลี่ยนชื่อคลาสจาก OffhandLight เป็น DualHandGlow
public class DualHandGlow extends JavaPlugin implements Listener {

    // เก็บตำแหน่งบล็อกแสงปัจจุบันของแต่ละผู้เล่น (block coords)
    private final Map<UUID, Location> playerLightBlock = new HashMap<>();
    // เก็บ ArmorStand แสดงไอเท็มมือรองสำหรับแต่ละผู้เล่น
    private final Map<UUID, ArmorStand> fakeOffhands = new HashMap<>();
    private BukkitTask mainTask;

    @Override
    public void onEnable() {
        getLogger().info("✅ DualHandGlow Enabled"); // อัปเดตข้อความ
        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdater();
    }

    @Override
    public void onDisable() {
        if (mainTask != null) mainTask.cancel();

        // ลบบล็อกแสงทั้งหมดที่เราวางไว้
        for (Location loc : playerLightBlock.values()) {
            if (loc != null && loc.getBlock().getType() == Material.LIGHT) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        playerLightBlock.clear();

        // ลบ ArmorStand ทั้งหมด
        for (ArmorStand stand : fakeOffhands.values()) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        fakeOffhands.clear();

        getLogger().info("✅ DualHandGlow Disabled - cleaned up"); // อัปเดตข้อความ
    }

    private void startUpdater() {
        // อัปเดตทุก 10 tick (0.5s)
        mainTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // ตรวจของในมือรอง
                ItemStack off = p.getInventory().getItemInOffHand();
                boolean hasOff = off != null && off.getType() != Material.AIR;

                if (hasOff && isLightItem(off.getType())) {
                    
                    // คำนวณบล็อกที่จะวาง light: บล็อกใต้ตัวผู้เล่น (floor)
                    Location playerLoc = p.getLocation();
                    Location blockLoc = playerLoc.clone();
                    
                    // *** โค้ดที่วางแสงใต้เท้าอยู่แล้ว ***
                    blockLoc.setX(Math.floor(blockLoc.getX()) + 0.5); 
                    blockLoc.setY(Math.floor(blockLoc.getY()) - 1); // วางใต้เท้า
                    blockLoc.setZ(Math.floor(blockLoc.getZ()) + 0.5);
                    Block targetBlock = blockLoc.getBlock();
                    // **********************************

                    Location prev = playerLightBlock.get(p.getUniqueId());
                    if (prev == null || !isSameBlock(prev, blockLoc)) {
                        // ลบของเก่า
                        if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
                            prev.getBlock().setType(Material.AIR);
                        }
                        // วาง light ถ้าว่าง
                        if (targetBlock.getType() == Material.AIR) {
                            targetBlock.setType(Material.LIGHT);
                            targetBlock.setBlockData(Bukkit.createBlockData("minecraft:light[level=15]"));
                            playerLightBlock.put(p.getUniqueId(), targetBlock.getLocation());
                        } else {
                            // ถ้าบล็อกไม่ว่าง ให้เก็บเป็น Location ล่าสุด เพื่อหลีกเลี่ยงการเขียนซ้ำ
                            playerLightBlock.put(p.getUniqueId(), targetBlock.getLocation());
                        }
                    }

                    // จัดการ ArmorStand ให้แสดงของในมือรอง (สำหรับ Bedrock/Geyser)
                    updateFakeOffhand(p, off);

                } else {
                    // ผู้เล่นไม่มีไอเท็มให้แสงในมือรอง -> ลบบล็อกแสง และ ArmorStand
                    Location prev = playerLightBlock.remove(p.getUniqueId());
                    if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
                        prev.getBlock().setType(Material.AIR);
                    }
                    removeFakeOffhand(p);
                }
            }
        }, 0L, 10L);
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.getWorld().equals(b.getWorld());
    }

    private boolean isLightItem(Material type) {
        return type == Material.TORCH ||
               type == Material.LANTERN ||
               type == Material.SOUL_TORCH ||
               type == Material.SOUL_LANTERN ||
               type == Material.GLOWSTONE ||
               type == Material.SEA_LANTERN ||
               type == Material.SHROOMLIGHT ||
               type == Material.JACK_O_LANTERN ||
               type == Material.REDSTONE_TORCH;
    }

    private void updateFakeOffhand(Player p, ItemStack offItem) {
        UUID id = p.getUniqueId();
        ArmorStand stand = fakeOffhands.get(id);

        // สร้างครั้งเดียวเมื่อยังไม่มี
        if (stand == null || stand.isDead()) {
            ArmorStand s = p.getWorld().spawn(p.getLocation(), ArmorStand.class);
            s.setInvisible(true);
            s.setMarker(true); 
            s.setGravity(false);
            s.setSmall(true);
            s.setSilent(true);
            s.teleport(handLocationFor(p));
            s.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
            fakeOffhands.put(id, s);
            return;
        }

        // ถ้าผู้เล่นย้ายบล็อก ให้ย้าย ArmorStand และอัปเดตไอเท็ม
        Location standLoc = stand.getLocation();
        Location needed = handLocationFor(p);

        if (!isSameBlock(standLoc, needed)) {
            stand.teleport(needed);
        }

        // อัปเดตไอเท็มเฉพาะเมื่อแตกต่าง (ลดการเขียน)
        ItemStack cur = stand.getEquipment().getItem(EquipmentSlot.HAND);
        if (cur == null || !cur.isSimilar(offItem)) {
            stand.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
        }
    }

    private Location handLocationFor(Player p) {
        // ตำแหน่งโดยประมาณของมือรอง
        Location loc = p.getLocation().clone();
        loc.add(0.25 * Math.sin(Math.toRadians(p.getLocation().getYaw())), 1.45, -0.25 * Math.cos(Math.toRadians(p.getLocation().getYaw())));
        loc.setPitch(0);
        return loc;
    }

    private void removeFakeOffhand(Player p) {
        ArmorStand stand = fakeOffhands.remove(p.getUniqueId());
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Cleanup เมื่อผู้เล่นออกจากเกม
        UUID id = e.getPlayer().getUniqueId();
        Location prev = playerLightBlock.remove(id);
        if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
            prev.getBlock().setType(Material.AIR);
        }
        ArmorStand s = fakeOffhands.remove(id);
        if (s != null && !s.isDead()) s.remove();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("คำสั่งนี้ใช้ได้เฉพาะผู้เล่นเท่านั้น");
            return true;
        }
        Player p = (Player) sender;

        // *** ส่วนที่แก้ไข: ให้รองรับ label "offhand" หรือ "ofh" ***
        if (cmd.getName().equalsIgnoreCase("offhand") || label.equalsIgnoreCase("ofh")) {
            
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();

            p.getInventory().setItemInMainHand(off);
            p.getInventory().setItemInOffHand(main);

            p.sendMessage("§a✅ สลับของในมือเรียบร้อยแล้ว!");
            return true;
        }
        return false;
    }
  }
