package com.snowleopard1863.APTurrets;

import net.countercraft.movecraft.craft.Craft;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.EntityTippedArrow;
import net.minecraft.server.v1_12_R1.PacketPlayOutEntityDestroy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static com.snowleopard1863.APTurrets.Main.*;

class Util {

    private static JavaPlugin plugin;

    /**
     * Initial plugin configuration setup. Loads in properties from configuration file, creating the
     * fields if they do not already exist
     * @param pl Plugin to load configuration for
     */
    static void setupConfig(JavaPlugin pl) {
        plugin = pl;
        FileConfiguration config = pl.getConfig();
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
        pl.saveConfig();

        // Loads Config Values If They Exist
        Main.Debug = config.getBoolean("Debug mode");
        takeFromChest = config.getBoolean("Take arrows from chest");
        takeFromInventory = config.getBoolean("Take arrows from inventory");
        Main.costToPlace = config.getDouble("Cost to Place");
        Main.requireAmmo = config.getBoolean("Require Ammo");
        Main.knockbackStrength = config.getInt("Knockback strength");
        Main.incendiaryChance = config.getDouble("Incindiary chance");
        Main.damage = config.getDouble("Damage per arrow");
        Main.arrowVelocity = config.getDouble("Arrow velocity");
        Main.useParticleTracers = config.getBoolean("Particle tracers");
        Main.delayBetweenShots = config.getDouble("Delay between shots");
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
    private static boolean takeAmmo(Player player) {
        if (takeFromChest) {
            Block signBlock = player.getLocation().getBlock();
            if (signBlock.getType() == Material.WALL_SIGN || signBlock.getType() == Material.SIGN_POST) {
                Sign s = (Sign) signBlock.getState();

                if (craftManager != null)
                {
                    Craft c = craftManager.getCraftByPlayer(player);
                    if (c != null) {
                        return MovecraftIntegration.takeAmmoFromShip(c);
                    }
                }

                Block adjacentBlock = Util.getBlockSignAttachedTo(signBlock);
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


    /**
     * Mounts a player on a turret.
     * @param player Player to mount
     * @param signPos Position of turret sign
     */
    static void mountTurret(Player player, Location signPos) {
        if (signPos.getBlock().getType() == Material.SIGN || signPos.getBlock().getType() == Material.SIGN_POST
                || signPos.getBlock().getType() == Material.WALL_SIGN) {
            if (Debug) {
                Main.logger.info("Sign detected");
            }
            Sign sign = (Sign) signPos.getBlock().getState();
            if (onTurrets.contains(player)) {
                sendMessage(player, "You May Only Have One Person Mounted Per Turret.");
                if (Debug) {
                    logger.info("1 player per turret");
                }
            } else {
                if (Debug) {
                    logger.info(player.getName() + " is now on a turret");
                }
                sign.setLine(2, player.getName());
                sign.update();
                onTurrets.add(player);
                signPos.add(0.5, 0, 0.5);
                signPos.setDirection(player.getEyeLocation().getDirection());
                player.teleport(signPos);
                player.setWalkSpeed(0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 1000000, 200));
            }
        } else {
            logger.warning("Sign not found!");
        }
    }

    static void fireTurret(final Player player) {
        if (player.isGliding()) { //Prevent flying/swimming player from mounting turret
            demountTurret(player, player.getLocation());
            return;
        }
        reloading.add(player);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> reloading.remove(player), (int) (delayBetweenShots * 10));
        boolean hasAmmoBeenTaken;
        hasAmmoBeenTaken = !requireAmmo || takeAmmo(player);
        if (!hasAmmoBeenTaken) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1, 2);
            if (Debug) logger.info(player + " is out of ammo");
            return;
        }

        Arrow arrow = launchArrow(player);
        arrow.setShooter(player);
        arrow.setVelocity(player.getLocation().getDirection().multiply(arrowVelocity));
        arrow.setBounce(false);
        arrow.setMetadata("isTurretBullet", new FixedMetadataValue(plugin, true));
        arrow.setKnockbackStrength(knockbackStrength);
        double rand = Math.random();
        if (rand <= incendiaryChance) {
            arrow.setFireTicks(500);
        }
        if (useParticleTracers) {
            arrow.setMetadata("tracer", new FixedMetadataValue(plugin, true));
            tracedArrows.add(arrow);
            arrow.setCritical(false);

            PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(arrow.getEntityId());
            for (Player p : player.getServer().getOnlinePlayers()) {
                ((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
            }
        } else {
            arrow.setCritical(true);
        }
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_BLAST, 1, 2);
        world.playEffect(player.getLocation(), Effect.MOBSPAWNER_FLAMES, 0);

        if (Debug) logger.info("Mounted Gun Fired.");
    }

    /**
     * Shoots an arrow with no gravity
     * @param player Player shooting arrow
     * @return Bukkit Arrow object
     */
    private static Arrow launchArrow(Player player) {
        EntityPlayer ep = ((CraftPlayer) player).getHandle();
        net.minecraft.server.v1_12_R1.World world = ep.getWorld();
        EntityTippedArrow arrow = new EntityTippedArrow(world, ep);
        arrow.setNoGravity(true);
        world.addEntity(arrow);

        return (Arrow) arrow.getBukkitEntity();
    }

    static void demountTurret(Player player, Location signPos) {
        if (Debug) {
            logger.info(player.getName() + " is being taken off a turret");
        }
        onTurrets.remove(player);
        reloading.remove(player);
        if (signPos.getBlock().getType() == Material.SIGN || signPos.getBlock().getType() == Material.SIGN_POST
                || signPos.getBlock().getType() == Material.WALL_SIGN) {
            if (Debug) {
                logger.info("sign found and updated");
            }
            Sign sign = (Sign) signPos.getBlock().getState();
            sign.setLine(2, "");
            sign.update();
        } else {
            logger.warning("Sign not found!");
        }
        signPos.subtract(-0.5, 0, -0.5);
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    /**
     * Checks The Block That Is Being Used As A Support. Eg: Block Sign Is Placed Against
     *
     * @param block Block That You Are Checking For Support
     * @return Block That Is Supporting
     */
    private static Block getBlockSignAttachedTo(Block block) {
        if (block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN) {
            org.bukkit.material.Sign s = (org.bukkit.material.Sign) block.getState().getData();
            return block.getRelative(s.getAttachedFace());
        }
        return null;
    }
}
