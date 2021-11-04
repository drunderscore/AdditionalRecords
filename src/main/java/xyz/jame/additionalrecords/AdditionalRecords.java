package xyz.jame.additionalrecords;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Jukebox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdditionalRecords extends JavaPlugin implements Listener
{
    private static final int PLAYER_TITLE_RADIUS = 32;
    private NamespacedKey soundKey;
    private NamespacedKey titleKey;

    @Override
    public void onEnable()
    {
        soundKey = new NamespacedKey(this, "sound");
        titleKey = new NamespacedKey(this, "title");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        if (command.getName().equalsIgnoreCase("additionalrecords"))
        {
            if (!(sender instanceof Player))
            {
                sender.sendMessage(Component
                        .text("You must be a player to execute this command.")
                        .color(NamedTextColor.RED));
                return false;
            }

            var player = (Player) sender;

            var item = player.getInventory().getItemInMainHand();
            if (!item.getType().isRecord())
            {
                sender.sendMessage(Component
                        .text("You are not holding a record.")
                        .color(NamedTextColor.RED));
                return false;
            }

            if (args.length < 1)
            {
                sender.sendMessage(Component
                        .text("Usage: /" + label + " <sound> [title]")
                        .color(NamedTextColor.RED));
                return false;
            }

            var sound = args[0];
            final Component title;
            if (args.length >= 2)
            {
                var argsWithoutSound = new String[args.length - 1];
                System.arraycopy(args, 1, argsWithoutSound, 0, argsWithoutSound.length);
                var titleString = String.join(" ", argsWithoutSound);

                try
                {
                    // Yeah, we serialize this into a component, and then immediately pass it to
                    // createRecordForItemStack to be deserialized, but this ensures it's a valid Component.
                    title = GsonComponentSerializer.gson().deserialize(titleString);
                }
                catch (Exception e)
                {
                    sender.sendMessage(Component
                            .text("That title is not a valid component: "
                                    + e.getMessage())
                            .color(NamedTextColor.RED));
                    return false;
                }
            }
            else
            {
                title = null;
            }

            createRecordForItemStack(item, sound, title);
            sender.sendMessage(Component
                    .text("The record in your hand has been modified.")
                    .color(NamedTextColor.GREEN));

            return true;
        }
        return false;
    }

    public void createRecordForItemStack(@NotNull ItemStack item, @NotNull String sound, @Nullable Component title)
    {
        item.editMeta(meta ->
        {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(soundKey, PersistentDataType.STRING, sound);
            if (title != null)
                pdc.set(titleKey, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(title));
        });
    }

    @EventHandler
    private void onPlayerInteract(PlayerInteractEvent e)
    {
        // TODO: Should we handle this? Otherwise we just play the vanilla disc, which isn't great.
        if (e.getHand() != EquipmentSlot.HAND)
            return;

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        var state = e.getClickedBlock().getState(false);

        if (!(state instanceof Jukebox))
            return;

        var jukebox = (Jukebox) state;

        // The Jukebox is playing a vanilla record -- we don't touch that.
        if (jukebox.isPlaying())
            return;

        var maybePlayingSound = jukebox.getPersistentDataContainer().get(soundKey, PersistentDataType.STRING);

        if (maybePlayingSound != null)
        {
            // If you're holding a disc, and we do all this, the Jukebox will be empty and not playing
            // and then will start playing the disc in your hand, as a Vanilla disc.
            // We cancel this event to make sure it doesn't do this.
            e.setCancelled(true);
            // If the remove call found an existing value, let's stop that sound.
            // If you have multiple Jukeboxes near each other playing the same thing, too bad!
            e.getClickedBlock().getLocation().getWorld().stopSound(SoundStop.named(Key.key(maybePlayingSound)));
            jukebox.getPersistentDataContainer().remove(soundKey);
            jukebox.eject();
            return;
        }

        var item = e.getItem();

        if (item == null || !item.getType().isRecord())
            return;

        var meta = item.getItemMeta();
        if (meta == null)
            return;

        var maybeSound = meta.getPersistentDataContainer().get(soundKey, PersistentDataType.STRING);
        if (maybeSound == null)
            return;

        e.setCancelled(true);
        jukebox.setRecord(item);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
            e.getPlayer().getInventory().setItem(e.getHand(), null);
        e.getClickedBlock().getWorld().playSound(e.getClickedBlock().getLocation(), maybeSound, SoundCategory.RECORDS, 1.0f, 1.0f);
        jukebox.getPersistentDataContainer().set(soundKey, PersistentDataType.STRING, maybeSound);

        var maybeTitleString = meta.getPersistentDataContainer().get(titleKey, PersistentDataType.STRING);
        if (maybeTitleString != null)
        {
            final Component title;
            try
            {
                title = GsonComponentSerializer.gson().deserialize(maybeTitleString);
            }
            catch (Exception ex)
            {
                getSLF4JLogger().error("Couldn't deserialize custom record title to a Component", ex);
                return;
            }

            var compiledComponent = Component
                    .text("Now Playing: ")
                    .color(NamedTextColor.YELLOW)
                    .append(title);

            for (var player : e.getClickedBlock().getWorld().getNearbyPlayers(e.getClickedBlock().getLocation(), PLAYER_TITLE_RADIUS))
                player.sendActionBar(compiledComponent);
        }
    }

    @EventHandler
    private void onBlockBreak(BlockBreakEvent e)
    {
        var state = e.getBlock().getState(false);
        if (!(state instanceof Jukebox))
            return;

        var jukebox = (Jukebox) state;
        var maybePlayingSound = jukebox.getPersistentDataContainer().get(soundKey, PersistentDataType.STRING);

        // I suppose this shouldn't do this if the sound already stopped, but it's not a big deal
        // If you have multiple Jukeboxes near each other playing the same thing, too bad!
        if (maybePlayingSound != null)
            e.getBlock().getLocation().getWorld().stopSound(SoundStop.named(Key.key(maybePlayingSound)));
    }
}
