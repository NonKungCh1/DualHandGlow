package com.nonkungch.dualhandglow; // *** Package ถูกแก้ไขแล้ว ***

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

// ชื่อคลาสหลัก: DualHandGlow
public class DualHandGlow extends JavaPlugin implements Listener {

    private final Map<UUID, Location> playerLightBlock = new HashMap<>();
    private final Map<UUID, ArmorStand> fakeOffhands = new HashMap<>();
    private BukkitTask mainTask;

    @Override
    public void onEnable() {
        getLogger().info("✅ DualHandGlow (Block Light Edition) Enabled!"); 
        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdater();
    }

    @Override
    public void onDisable() {
        if (mainTask != null) mainTask.cancel();

        for (Location loc : playerLightBlock.values()) {
            if (loc != null && loc.getBlock().getType() == Material.LIGHT) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        playerLightBlock.clear();

        for (ArmorStand stand : fakeOffhands.values()) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        fakeOffhands.clear();

        getLogger().info("✅ DualHandGlow Disabled - cleaned up all lights and entities.");
    }

    private void startUpdater() {
        mainTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack off = p.getInventory().getItemInOffHand();
                boolean hasOff = off != null && off.getType() != Material.AIR;

                if (hasOff && isLightItem(off.getType())) {
                    
                    Location playerLoc = p.getLocation();
                    Location blockLoc = playerLoc.clone();
                    
                    // คำนวณตำแหน่งแสงที่เท้า (Y-1.0)
                    blockLoc.setX(Math.floor(blockLoc.getX()) + 0.5); 
                    blockLoc.setY(Math.floor(blockLoc.getY()) - 1); // <--- วางแสงที่เท้า
                    blockLoc.setZ(Math.floor(blockLoc.getZ()) + 0.5);
                    Block targetBlock = blockLoc.getBlock();

                    Location prev = playerLightBlock.get(p.getUniqueId());
                    if (prev == null || !isSameBlock(prev, blockLoc)) {
                        if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
                            prev.getBlock().setType(Material.AIR);
                        }
                        if (targetBlock.getType() == Material.AIR) {
                            targetBlock.setType(Material.LIGHT);
                            targetBlock.setBlockData(Bukkit.createBlockData("minecraft:light[level=15]"));
                            playerLightBlock.put(p.getUniqueId(), targetBlock.getLocation());
                        } else {
                            playerLightBlock.put(p.getUniqueId(), targetBlock.getLocation());
                        }
                    }

                    updateFakeOffhand(p, off);

                } else {
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

        Location standLoc = stand.getLocation();
        Location needed = handLocationFor(p);

        if (!isSameBlock(standLoc, needed)) {
            stand.teleport(needed);
        }

        ItemStack cur = stand.getEquipment().getItem(EquipmentSlot.HAND);
        if (cur == null || !cur.isSimilar(offItem)) {
            stand.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
        }
    }

    private Location handLocationFor(Player p) {
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

        // รองรับคำสั่งหลัก "offhand" และ Alias "ofh"
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
