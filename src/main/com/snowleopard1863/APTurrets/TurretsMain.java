package com.snowleopard1863.APTurrets;


import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.managers.RegionManager;
import net.countercraft.movecraft.craft.CraftManager;
import net.milkbowl.vault.economy.Economy;
import net.minecraft.server.v1_12_R1.PacketPlayOutEntityDestroy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static com.snowleopard1863.APTurrets.Helpers.sendMessage;
import static com.snowleopard1863.APTurrets.Helpers.setupConfig;
import static com.snowleopard1863.APTurrets.Helpers.takeAmmo;

public final class TurretsMain extends JavaPlugin implements Listener {
    private PluginDescriptionFile pdFile = getDescription();
    private Logger logger = Logger.getLogger("Minecraft");
    private static List<Player> onTurrets = new ArrayList<>();
    private static List<Player> reloading = new ArrayList<>();
    private static List<Arrow> tracedArrows = new CopyOnWriteArrayList<>();
    static boolean Debug = false;
    static boolean takeFromInventory, takeFromChest, requireAmmo;
    static double costToPlace, damage, incendiaryChance, arrowVelocity;
    static int knockbackStrength;
    static boolean useParticleTracers;
    static double delayBetweenShots;

    //Plugin integration:
    static Economy economy; //Vault Economy
    static CraftManager craftManager; //Movecraft

    private static final Material[] INVENTORY_MATERIALS = new Material[]{Material.CHEST, Material.TRAPPED_CHEST,
            Material.FURNACE, Material.HOPPER, Material.DROPPER, Material.DISPENSER, Material.BREWING_STAND};
    static final ItemStack TURRET_AMMO = new ItemStack(Material.ARROW, 1);

    @Override
    public void onEnable() {
        //Basic Setup
        final Plugin p = this;
        logger.info(pdFile.getName() + " v" + pdFile.getVersion() + " has been enbaled.");
        getServer().getPluginManager().registerEvents(this, this);

        setupConfig(this);

        //
        // Vault Support
        //
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                logger.info("Found a compatible Vault plugin.");
            } else {
                logger.info("[WARNING] Could not find compatible Vault plugin. Disabling Vault integration.");
                economy = null;
            }
        } else {
            logger.info("Could not find compatible Vault plugin. Disabling Vault integration.");
            economy = null;
        }

        //
        // Movecraft Support
        //
        if (getServer().getPluginManager().getPlugin("Movecraft") != null) {
            craftManager = CraftManager.getInstance();
            logger.info("Compatible Version Of Movecraft Found.");
        } else {
            logger.info("[WARNING] Could not find compatible Movecraft Version... Disabling");
            craftManager = null;
        }


        if (useParticleTracers) {
            // Schedule task to run in background that replaced the vanilla arrow tracers with an accurate trail
            // that reflects the arrows true trajectory. The vanilla trajectory isn't accurate for sufficiently
            // high arrows speeds so we must compensate for it directly.
            getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
                for (Arrow a : tracedArrows) {
                    if (a.isOnGround() || a.isDead() || a.getTicksLived() > 100) {
                        a.removeMetadata("tracer", p);
                        tracedArrows.remove(a);
                        a.remove();
                    }
                    World world = a.getWorld();
                    world.spawnParticle(Particle.CRIT, a.getLocation(), 3, 0.0, 0.0, 0.0, 0);
                }
            }, 0, 0);
        }

    }

    @Override
    public void onLoad() {
        super.onLoad();
        logger = getLogger();
    }

    @Override
    public void onDisable() {
        for (Player player : onTurrets) { //Demount all players in turrets
            demount(player, player.getLocation());
            onTurrets.remove(player);
        }
        reloading.clear();
        tracedArrows.clear();
        logger.info(pdFile.getName() + " v" + pdFile.getVersion() + " has been disabled.");
    }


    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (Debug) {
                logger.info(event.getPlayer() + " has right clicked");
            }
            Player player = event.getPlayer();
            if (onTurrets.contains(player) && player.hasPermission("ap-turrets.use")) {
                if (Debug) {
                    logger.info(event.getPlayer() + " is on a turret");
                }
                //Prevents Players From Drinking Milk Buckets to Clear Potion Effects
                if (player.getInventory().getItemInMainHand().getType() == Material.MILK_BUCKET || player.getInventory().getItemInOffHand().getType() == Material.MILK_BUCKET) {
                    if (Debug) {
                        logger.info(event.getPlayer() + " has right clicked a milk bucket!");
                    }
                    event.setCancelled(true);
                }
                //Fires Turret If Player Is Not Reloading.
                if ((player.getInventory().getItemInMainHand().getType() == Material.STONE_BUTTON
                        || player.getInventory().getItemInOffHand().getType() == Material.STONE_BUTTON)) {

                    if (reloading.contains(player))
                        return;

                    fireTurret(player);
                    event.setCancelled(true);
                    if (Debug) {
                        logger.info(event.getPlayer() + " has started to shoot");
                    }
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (Debug) {
                logger.info("A block has been right clicked");
            }
            if (event.getClickedBlock().getType() == Material.SIGN_POST
                    || event.getClickedBlock().getType() == Material.WALL_SIGN
                    || event.getClickedBlock().getType() == Material.SIGN) {
                if (Debug) {
                    logger.info("A sign was clicked");
                }
                Sign sign = (Sign) event.getClickedBlock().getState();
                if ("Mounted".equalsIgnoreCase(sign.getLine(0)) && "Gun".equalsIgnoreCase(sign.getLine(1))) {
                    if (Debug) {
                        logger.info("A Mounted Gun sign has been clicked");
                    }
                    Block b = sign.getLocation().subtract(0,1,0).getBlock();
                    if (b.getType() != Material.SLIME_BLOCK)
                    {
                        Location signPos = event.getClickedBlock().getLocation();
                        signPos.setPitch(event.getPlayer().getLocation().getPitch());
                        signPos.setDirection(event.getPlayer().getVelocity());
                        if (!sign.getLine(0).equals("Mounted")) sign.setLine(0,"Mounted");
                        if (!sign.getLine(1).equals("Gun")) sign.setLine(1,"Gun");
                        mount(event.getPlayer(), signPos);
                    }
                }
            }
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.STONE_BUTTON) {
            if (Debug) {
                logger.info("A block has been left clicked while holding a button");
            }
            if (event.getClickedBlock().getType() == Material.SIGN_POST
                    || event.getClickedBlock().getType() == Material.WALL_SIGN
                    || event.getClickedBlock().getType() == Material.SIGN) {
                if (Debug) {
                    logger.info("A sign was left clicked");
                }
                Sign sign = (Sign) event.getClickedBlock().getState();
                if ("Mounted".equalsIgnoreCase(sign.getLine(0)) && "Gun".equalsIgnoreCase(sign.getLine(1))) {
                    if (Debug) {
                        logger.info("A Mounted Gun sign has been left clicked");
                    }
                    event.setCancelled(true);
                    // Generate the turret info message to send to the player

                    String message = "\n" +
                            ChatColor.GOLD + "Damage/Shot: " + ChatColor.GRAY + damage + "\n" +
                            ChatColor.GOLD + "Delay Between Shots: " + ChatColor.GRAY + delayBetweenShots + "\n" +
                            ChatColor.GOLD + "Velocity: " + ChatColor.GRAY + arrowVelocity + "\n" +
                            ChatColor.GOLD + "Fire Chance: " + ChatColor.GRAY + incendiaryChance * 100 + "%\n" +
                            ChatColor.GOLD + "Knockback: " + ChatColor.GRAY + knockbackStrength + "\n" +
                            ChatColor.GOLD + "Cost to Place: $" + ChatColor.GRAY + costToPlace;
                    if (!sign.getLine(3).equals("")) { //If there is a turret type, add it to the message here
                        message = "\n" + ChatColor.GOLD + "Type: " + ChatColor.BLACK + sign.getLine(3) + message;
                    }
                    sendMessage(event.getPlayer(), message);
                }
            }
        }

    }

    //
    // Prevents Players From Mounting Entities To De-Mount A Gun.
    //
    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (onTurrets.contains(p) && (e.getRightClicked() instanceof Boat || e.getRightClicked() instanceof Horse)) {
            demount(p, p.getLocation());
            if (Debug) logger.info("Player: " + p.getName() + "Has Mounted An Entity.");
        }
    }


    
    @EventHandler
    public void eventSignChanged(SignChangeEvent event) {
        //get player who placed the sign
        Player player = event.getPlayer();
        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
        Location location = player.getLocation();
        RegionManager rm = WGBukkit.getRegionManager(player.getWorld());
        //check if the sign matches the cases for a turret
        if ("Mounted".equalsIgnoreCase(event.getLine(0)) && "Gun".equalsIgnoreCase(event.getLine(1))) {
        	if (rm.getApplicableRegions(location).size() <= 0) {
        		   sendMessage(player,"You must be inside a airspace or region.");
                   event.setCancelled(true);
                   if (Debug) {
                       logger.info("A Mounted Gun sign failed to place");
                   }
                   return;

        	}
            //check if player has permission to place a turret, than check if they have enough money to place the sign
            if (player.hasPermission("ap-turrets.place")) {
                if (economy != null) {
                    if (economy.has(player, costToPlace)) {
                        //if true charge player a configurable amount and send a message
                        economy.withdrawPlayer(player, 15000);
                        sendMessage(player, "Turret Created!");
                        event.setLine(0, "Mounted");
                        event.setLine(1, "Gun");
                        if (Debug) {
                            logger.info("A Mounted Gun sign has been place");
                        }
                    } else {
                        if (Debug) {
                            logger.info("A Mounted Gun sign failed to place");
                        }
                        //if false, clear the sign and return a permission error
                       
                        sendMessage(player, "You Don't Have Enough Money To Place A Turret. Cost To Place: " + ChatColor.RED + costToPlace);
                    }
                } else {
                    sendMessage(player, "Turret Created!");
                    if (Debug) {
                        logger.info("A Mounted Gun sign has been placed for free due to no vault instalation");
                    }
                }
            } else {
                if (Debug) {
                    logger.info("A Mounted Gun sign failed to place");
                }
                //if false, clear the sign and return a permision error
                event.setCancelled(true);
                sendMessage(player, ChatColor.RED + "ERROR " + ChatColor.WHITE + "You Must Be Donor To Place Mounted Guns!");
            }
        }
    }


    private void fireTurret(final Player player) {
        if (player.isGliding()) {
            demount(player, player.getLocation());
            return;
        }
        reloading.add(player);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> reloading.remove(player), (int) (delayBetweenShots * 10));
        boolean hasAmmoBeenTaken;
        hasAmmoBeenTaken = !requireAmmo || takeAmmo(player);
        if (!hasAmmoBeenTaken) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1, 2);
            if (Debug) logger.info(player + " is out of ammo");
            return;
        }
        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setShooter(player);
        arrow.setVelocity(player.getLocation().getDirection().multiply(arrowVelocity));
        arrow.setBounce(false);
        arrow.setMetadata("isTurretBullet", new FixedMetadataValue(this, true));
        arrow.setKnockbackStrength(knockbackStrength);
        double rand = Math.random();
        if (rand <= incendiaryChance) {
            arrow.setFireTicks(500);
        }
        if (useParticleTracers) {
            arrow.setMetadata("tracer", new FixedMetadataValue(this, true));
            tracedArrows.add(arrow);
            arrow.setCritical(false);

            PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(arrow.getEntityId());
            for (Player p : getServer().getOnlinePlayers()) {
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

    @EventHandler
    public void onPlayerToggleSneakEvent(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (Debug) {
            logger.info(player + " sneaked");
        }
        if (player.isSneaking() && onTurrets.contains(player)) {
            demount(player, player.getLocation());
            if (Debug) {
                logger.info(player + " got out of their turret");
            }
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getEntity();
            if (arrow.hasMetadata("isTurretBullet")) {
                if (Debug) {
                    logger.info("A bullet has landed");
                }

                Location arrowLoc = arrow.getLocation();
                World world = event.getEntity().getWorld();
                Location l = arrowLoc.getBlock().getLocation();
                arrow.getWorld().playEffect(arrowLoc,Effect.STEP_SOUND,1);

            }
        }
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent event) {
        if (Debug) {
            logger.info("An entity was damaged");
        }

        if (event.getEntity() instanceof Player) {
            if (Debug) {
                logger.info("It was a player");
            }
            Player player = (Player) event.getEntity();
            if (onTurrets.contains(player)) {
                if (Debug) {
                    logger.info("on a turret");
                }
                demount(player, player.getLocation());
            }

            if (event.getEntity().hasMetadata("isTurretBullet")) {
                if (player.isGliding()) {
                    player.setGliding(false);
                    player.setSprinting(false);
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow) {
            //if (event.getDamager().getCustomName() == "Bullet") {
            if (event.getDamager().hasMetadata("isTurretBullet")) {
                event.setDamage(damage);
                if (Debug) {
                    Arrow a = (Arrow) event.getDamager();
                    Player shooter = (Player) a.getShooter();
                    logger.info(event.getEntity() + " was shot by " + shooter.getName() + " for " + event.getDamage() + " It should be doing " + damage);
                }
            }
        }
    }

    private void mount(Player player, Location signPos) {
        if (signPos.getBlock().getType() == Material.SIGN || signPos.getBlock().getType() == Material.SIGN_POST
                || signPos.getBlock().getType() == Material.WALL_SIGN) {
            if (Debug) {
                logger.info("Sign detected");
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

    private void demount(Player player, Location signPos) {
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
     * When the player leaves the game, we need to handle cleanup and remove
     * them from all of the lists they might be on/turrets they are on
     * @param e PlayerQuitEvent triggered
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        demount(e.getPlayer(), e.getPlayer().getLocation());
    }


    /**
     * Add custom death messages to plugin
     * @param e PlayerDeathEvent triggered
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (onTurrets.contains(e.getEntity().getKiller())) { //If killer is on a gun, show the message
            e.setDeathMessage(e.getEntity().getDisplayName() + " was gunned down by " + e.getEntity().getKiller().getDisplayName() + ".");
        }
    }

}

