package com.example.playerfutureticker;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class PlayerFutureTicker extends JavaPlugin implements Listener {

    // Store the last known location of players when they log off
    private final Map<Player, Location> playerLocations = new HashMap<>();
    private final Map<Player, Boolean> activeTickingZones = new HashMap<>();

    private int chunkRadius;
    private int maxTickingZones;

    @Override
    public void onEnable() {
        // Load the configuration
        saveDefaultConfig();
        loadConfig();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("PlayerFutureTicker Enabled");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("PlayerFutureTicker Shutdown \nGoodbye!");
    }

    private void loadConfig() {
        // Load config values
        chunkRadius = getConfig().getInt("chunk-radius", 3);   // Default to a 3x3 chunk grid
        if  (chunkRadius < 1) {
            chunkRadius = 3;
            getLogger().warning("Invalid chunk radius. Setting to default: 3");
        }
        maxTickingZones = getConfig().getInt("max-ticking-zones", 5);  // Default to 5 max ticking zones
        if (maxTickingZones < 0) {
            maxTickingZones = 5;
            getLogger().warning("Invalid max ticking zones. Setting to default: 5");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();

        playerLocations.put(player, loc);
        activeTickingZones.put(player, true); // Mark this player as having an active ticking zone

        // Keep chunks around the player's current chunk loaded
        keepChunksLoaded(loc, true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Stop keeping chunks loaded when the player logs back in
        Location loc = playerLocations.get(player);
        if (loc != null) {
            keepChunksLoaded(loc, false);
            playerLocations.remove(player);
            activeTickingZones.remove(player);  // Remove this player from the active zones
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("mycommand")) {
            if (sender.hasPermission("PlayerFutureTicker.mycommand")) {
                sender.sendMessage("You executed my custom command!");
                return true;
            } else {
                sender.sendMessage("You don't have permission to use this command.");
                return true;
            }
        }
        return false;
    }

    private void keepChunksLoaded(Location loc, boolean keepLoaded) {
        // Load configurable grid of chunks around the player's current chunk
        int baseX = loc.getChunk().getX();
        int baseZ = loc.getChunk().getZ();

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                Chunk chunk = loc.getWorld().getChunkAt(baseX + x, baseZ + z);
                if (keepLoaded) {
                    chunk.addPluginChunkTicket(this);  // Keep chunk loaded
                    forceMobTicking(chunk);             // Ensure mob ticking
                    forceRedstoneTicking(chunk);        // Ensure redstone stays active
                } else {
                    chunk.removePluginChunkTicket(this);  // Unload chunk
                }
            }
        }
    }

    private void forceMobTicking(Chunk chunk) {
        // Force mobs to tick in the chunk
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof org.bukkit.entity.Mob) {
                entity.setTicksLived(entity.getTicksLived() + 1);  // This ensures mobs are ticking
            }
        }
    }

    private void forceRedstoneTicking(Chunk chunk) {
        // Force redstone to stay active by updating redstone-related blocks
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < chunk.getWorld().getMaxHeight(); y++) {
                for (int z = 0; z < 16; z++) {
                    if (isRedstoneBlock(chunk.getBlock(x, y, z).getType())) {
                        chunk.getBlock(x, y, z).getState().update(true);  // Force redstone to tick
                    }
                }
            }
        }
    }

    private boolean isRedstoneBlock(Material material) {
        // Check if the block is redstone-related (you can extend this list as needed)
        return material == Material.REDSTONE_WIRE ||
               material == Material.REDSTONE_TORCH ||
               material == Material.REPEATER ||
               material == Material.COMPARATOR;
    }
}
