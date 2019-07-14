package com.snowleopard1863.APTurrets;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import static com.snowleopard1863.APTurrets.TurretsMain.*;

public class Helpers {

    /**
     * Initial plugin configuration setup. Loads in properties from configuration file, creating the
     * fields if they do not already exist
     * @param plugin Plugin to load configuration for
     */
    static void setupConfig(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        // Sets Default Config Values if None Exist
        config.addDefault("Debug mode", false);
        config.addDefault("Cost to Place", 15000.00);
        config.addDefault("Take arrows from inventory", true);
        config.addDefault("Take arrows from chest", true);
        config.addDefault("Require Ammo", true);
        config.addDefault("Damage per arrow", 2.5);
        config.addDefault("Incindiary chance", 0.10);
        config.addDefault("Knockback strength", 2);
        config.addDefault("Arrow velocity", 4.0);
        config.addDefault("Particle tracers", true);
        config.addDefault("Delay between shots", 0.2);
        config.options().copyDefaults(true);
        plugin.saveConfig();

        // Loads Config Values If They Exist
        TurretsMain.Debug = config.getBoolean("Debug mode");
        takeFromChest = config.getBoolean("Take arrows from chest");
        takeFromInventory = config.getBoolean("Take arrows from inventory");
        TurretsMain.costToPlace = config.getDouble("Cost to Place");
        TurretsMain.requireAmmo = config.getBoolean("Require Ammo");
        TurretsMain.knockbackStrength = config.getInt("Knockback strength");
        TurretsMain.incendiaryChance = config.getDouble("Incindiary chance");
        TurretsMain.damage = config.getDouble("Damage per arrow");
        TurretsMain.arrowVelocity = config.getDouble("Arrow velocity");
        TurretsMain.useParticleTracers = config.getBoolean("Particle tracers");
        TurretsMain.delayBetweenShots = config.getDouble("Delay between shots");
    }


    /**
     * Checks The Block That Is Being Used As A Support. Eg: Block Sign Is Placed Against
     *
     * @param block Block That You Are Checking For Support
     * @return Block That Is Supporting
     */
    static Block getBlockSignAttachedTo(Block block) {
        if (block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN) {
            org.bukkit.material.Sign s = (org.bukkit.material.Sign) block.getState().getData();
            return block.getRelative(s.getAttachedFace());
        }
        return null;
    }

    /**
     * Sends a message in the correct and consistent chat formatting for the plugin
     * @param p Player to send message to
     * @param message Message to send
     */
    static void sendMessage(Player p, String message) {
        p.sendMessage(ChatColor.GRAY + "[" + ChatColor.DARK_RED + "APTurrets" + ChatColor.GRAY + "] " + ChatColor.WHITE + message);
    }

    /**
     * Takes Ammo From Player's Inventory For Firing The Turret.
     *
     * @param player Player Who Is Having Ammo Thier Ammo Taken
     * @return Ammo Successfully Taken
     */
    static boolean takeAmmo(Player player) {
        if (takeFromChest) {
            Block signBlock = player.getLocation().getBlock();
            if (signBlock.getType() == Material.WALL_SIGN || signBlock.getType() == Material.SIGN_POST) {
                Sign s = (Sign) signBlock.getState();

                if (craftManager != null)
                {
                    Craft c = craftManager.getCraftByPlayer(player);
                    if (c != null) {
                        return MovecraftIntegration.takeAmmoFromShip(player, c);
                    }
                }

                Block adjacentBlock = Helpers.getBlockSignAttachedTo(signBlock);
                if (adjacentBlock != null && adjacentBlock.getState() instanceof InventoryHolder) {
                    InventoryHolder inventoryHolder = (InventoryHolder) adjacentBlock.getState();
                    if (inventoryHolder.getInventory().containsAtLeast(TURRET_AMMO, 1)) {
                        inventoryHolder.getInventory().removeItem(TURRET_AMMO);
                        return true;
                    }
                }
            }
        }

        if (takeFromInventory) {
            if (player.getInventory().containsAtLeast(TURRET_AMMO, 1)) {
                player.getInventory().removeItem(TURRET_AMMO);
                player.updateInventory();
                return true;
            }
        }
        return false;
    }

}
