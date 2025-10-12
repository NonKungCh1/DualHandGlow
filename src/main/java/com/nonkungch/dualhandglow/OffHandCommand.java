// OffHandCommand.java

package com.nonkungch.dualhandglow;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class OffHandCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[DualHandGlow] §7: Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        
        // ตรวจสอบ Permission
        if (!player.hasPermission("dualhandglow.swap")) {
            player.sendMessage("§c" + cmd.getPermissionMessage());
            return true;
        }
        
        PlayerInventory inv = player.getInventory();
        
        // 1. ดึงไอเทมปัจจุบัน
        ItemStack mainHandItem = inv.getItemInMainHand();
        ItemStack offHandItem = inv.getItemInOffHand();

        // 2. สลับไอเทม
        inv.setItemInMainHand(offHandItem);
        inv.setItemInOffHand(mainHandItem);

        // 3. ส่งข้อความยืนยัน
        player.sendMessage("§a[DualHandGlow] §7: Item swapped using /" + label + "!");
        
        return true;
    }
          }
