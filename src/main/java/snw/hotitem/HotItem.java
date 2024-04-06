package snw.hotitem;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class HotItem extends JavaPlugin implements Listener {
    private final Multimap<UUID, ItemStack> queuedItems = ArrayListMultimap.create();
    private final Set<UUID> safePlayers = new HashSet<>();
    private boolean on;
    private static final ItemStack BUTTON;

    static {
        BUTTON = new ItemStack(Material.STONE_BUTTON);
        BUTTON.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        final ItemMeta meta = BUTTON.getItemMeta();
        assert meta != null;
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        BUTTON.setItemMeta(meta);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!on) return;
            String msg = "持有者: " + getServer().getOnlinePlayers().
                    stream().filter(HotItem::ownHotItem)
                    .map(Player::getName).collect(Collectors.joining(", "));
            Optional.ofNullable(getServer().getPlayerExact("Murasame_mao"))
                    .ifPresent(it -> it.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg)));
        }, 10L, 10L);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        reset();
    }

    private void reset() {
        safePlayers.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        on = !on;
        if (!on) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (ownHotItem(player)) {
                    if (!player.getScoreboardTags().contains("hunter")) {
                        player.getInventory().setHelmet(BUTTON);
                    }
                }
            }
            reset();
        }
        sender.sendMessage(on ? "Now on" : "Now off");
        return true;
    }

    public static boolean isHotItem(ItemStack item) {
        return item.getType().getKey().toString().equals("tzz:feng_yin_neng_liang");
    }

    public static boolean ownHotItem(Player player) {
        for (ItemStack stack : player.getInventory()) {
            if (stack != null) {
                if (isHotItem(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!on) return;
        final Item itemEntity = event.getItemDrop();
        final ItemStack item = itemEntity.getItemStack();
        if (isHotItem(item)) {
            final UUID thrower = event.getPlayer().getUniqueId();
            final int sec = 8;
            getServer().getScheduler().runTaskLater(this, () -> {
                if (itemEntity.isValid()) {
                    itemEntity.remove();
                    Optional.ofNullable(getServer().getPlayer(thrower))
                            .ifPresentOrElse(
                                    p -> {
                                        p.getInventory().addItem(item);
                                        p.sendMessage(ChatColor.RED + "物品在 " + sec + " 秒内未被拾取，已返回到你的背包");
                                    },
                                    () -> queuedItems.get(thrower).add(item));
                }
            }, sec * 20L);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!on) return;
        final Item itemEntity = event.getItem();
        if (isHotItem(itemEntity.getItemStack())) {
            if (event.getEntity() instanceof Player player) {
                if (!ownHotItem(player) && !safePlayers.contains(player.getUniqueId())) {
                    final UUID thrower = itemEntity.getThrower();
                    if (!player.getUniqueId().equals(thrower)) {
                        safePlayers.add(thrower);
                        player.sendMessage(ChatColor.RED + "提示：你获得了任务道具");
                        return;
                    }
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Collection<ItemStack> queue = queuedItems.get(event.getPlayer().getUniqueId());
        if (!queue.isEmpty()) {
            event.getPlayer().getInventory().addItem(queue.toArray(ItemStack[]::new));
            queue.clear();
        }
    }
}
