package com.snowleopard1863.APTurrets;


import net.countercraft.movecraft.craft.CraftManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static com.snowleopard1863.APTurrets.Util.demountTurret;
import static com.snowleopard1863.APTurrets.Util.setupConfig;

public final class Main extends JavaPlugin implements Listener {
    private PluginDescriptionFile pdFile = getDescription();
    static Logger logger;
    static List<Player> onTurrets = new ArrayList<>();
    static List<Player> reloading = new ArrayList<>();
    static List<Arrow> tracedArrows = new CopyOnWriteArrayList<>();
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
        logger = Logger.getLogger("Minecraft");
        logger.info(pdFile.getName() + " v" + pdFile.getVersion() + " has been enbaled.");
        getServer().getPluginManager().registerEvents(new EventListener(this), this);

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
            demountTurret(player, player.getLocation());
            onTurrets.remove(player);
        }
        reloading.clear();
        tracedArrows.clear();
        logger.info(pdFile.getName() + " v" + pdFile.getVersion() + " has been disabled.");
    }

}

