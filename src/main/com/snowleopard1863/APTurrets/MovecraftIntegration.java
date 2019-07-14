package com.snowleopard1863.APTurrets;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.utils.MovecraftLocation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.snowleopard1863.APTurrets.Main.TURRET_AMMO;

public class MovecraftIntegration {
    /**
     * Converts a Movecraft Location Object to a Bukkit Location Object
     *
     * @param movecraftLoc Movecraft Location Object
     * @param world        World The Craft Is In
     * @return Location Object
     */
    private static Location movecraftLocationToBukkitLocation(MovecraftLocation movecraftLoc, World world) {
        return new Location(world, movecraftLoc.getX(), movecraftLoc.getY(), movecraftLoc.getZ());
    }

    /**
     * Converts a list of movecraftLocation Object to a bukkit Location Object
     *
     * @param movecraftLocations the movecraftLocations to be converted
     * @param world              the world of the location
     * @return the converted location
     */
    public static ArrayList<Location> movecraftLocationToBukkitLocation(List<MovecraftLocation> movecraftLocations, World world) {
        ArrayList<Location> locations = new ArrayList<>();
        for (MovecraftLocation movecraftLoc : movecraftLocations) {
            locations.add(movecraftLocationToBukkitLocation(movecraftLoc, world));
        }
        return locations;
    }

    /**
     * Converts a list of movecraftLocation Object to a bukkit Location Object
     *
     * @param movecraftLocations the movecraftLocations to be converted
     * @param world              the world of the location
     * @return the converted location
     */
    private static ArrayList<Location> movecraftLocationToBukkitLocation(MovecraftLocation[] movecraftLocations, World world) {
        ArrayList<Location> locations = new ArrayList<>();
        for (MovecraftLocation movecraftLoc : movecraftLocations) {
            locations.add(movecraftLocationToBukkitLocation(movecraftLoc, world));
        }
        return locations;
    }

    public static MovecraftLocation bukkitLocationToMovecraftLocation(Location loc) {
        return new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Gets the first inventory of a lookup material type on a craft holding a specific item, returns null if none found
     * an input of null for item searches without checking inventory contents
     * an input of an ItemStack with type set to Material.AIR for searches for empty space in an inventory
     *
     * @param craft  the craft to scan
     * @param item   the item to look for during the scan
     * @param lookup the materials to compare against while scanning
     * @return the first inventory matching a lookup material on the craft
     */
    private static Inventory firstInventory(Craft craft, ItemStack item, Material... lookup) {
        if (craft == null)
            throw new IllegalArgumentException("craft must not be null");

        for (Location loc : movecraftLocationToBukkitLocation(craft.getBlockList(), craft.getW()))
            for (Material m : lookup)
                if (loc.getBlock().getType() == m) {
                    Inventory inv = ((InventoryHolder) loc.getBlock().getState()).getInventory();
                    if (item == null)
                        return inv;
                    for (ItemStack i : inv)
                        if ((item.getType() == Material.AIR && (i == null || i.getType() == Material.AIR)) || (i != null && i.isSimilar(item)))
                            return inv;
                }
        return null;
    }

    /**
     * Takes Ammo From Player's Inventory For Firing The Turret.
     *
     * @param player Player Who Is Having Ammo Thier Ammo Taken
     * @return Ammo Successfully Taken
     */
    static boolean takeAmmoFromShip(Player player, Craft c) {
            Inventory i = firstInventory(c, TURRET_AMMO, Material.CHEST, Material.TRAPPED_CHEST);
            if (i != null) {
                i.removeItem(TURRET_AMMO);
                return true;
            }
        return false;
    }

}
