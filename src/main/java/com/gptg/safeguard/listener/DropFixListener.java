package com.gptg.safeguard.listener;

import com.gptg.safeguard.GPSafeGuardPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modul DropFix: mencegah bug item hilang saat di-drop dari cursor (klik di luar UI inventory)
 * ketika PlayerDropItemEvent dibatalkan (mis. flag WorldGuard item-drop: deny)
 * dan inventory pemain sedang penuh.
 */
public class DropFixListener implements Listener {

    /** Data drop yang sedang "dipantau" per player (hanya hidup 1 tick). */
    private static final class Pending {
        final ItemStack template;   // item yang di-drop (amount 1, untuk isSimilar)
        final int dropAmount;       // jumlah yang akan di-drop
        final int countBefore;      // total item sejenis (inventory + cursor) sebelum drop
        boolean cancelled = false;  // apakah PlayerDropItemEvent dibatalkan

        Pending(ItemStack template, int dropAmount, int countBefore) {
            this.template = template;
            this.dropAmount = dropAmount;
            this.countBefore = countBefore;
        }
    }

    private final GPSafeGuardPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public DropFixListener(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    // 1) Deteksi klik di luar window inventory dengan item di cursor.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!plugin.getConfigManager().isDropFixEnabled()) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getSlotType() != SlotType.OUTSIDE) return;

        ClickType type = e.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT) return;

        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;

        int dropAmount = (type == ClickType.LEFT) ? cursor.getAmount() : 1;
        ItemStack template = cursor.clone();
        template.setAmount(1);

        int before = countSimilar(player, template);
        Pending p = new Pending(template, dropAmount, before);
        pending.put(player.getUniqueId(), p);

        // PlayerDropItemEvent dipanggil di tick yang sama, jadi cek di tick berikutnya.
        plugin.getServer().getScheduler().runTask(plugin, () -> verify(player, p));
    }

    // 2) Tandai jika drop dibatalkan (WorldGuard / plugin lain).
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.getConfigManager().isDropFixEnabled()) return;
        Pending p = pending.get(e.getPlayer().getUniqueId());
        if (p == null) return;
        if (e.isCancelled() && e.getItemDrop().getItemStack().isSimilar(p.template)) {
            p.cancelled = true;
        }
    }

    // 3) Cek apakah item hilang, lalu kembalikan.
    private void verify(Player player, Pending p) {
        if (pending.get(player.getUniqueId()) == p) {
            pending.remove(player.getUniqueId());
        }
        if (!p.cancelled || !player.isOnline()) return;

        int after = countSimilar(player, p.template);
        int missing = Math.min(p.countBefore - after, p.dropAmount);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug] DropFix check: player=" + player.getName()
                    + " before=" + p.countBefore + " after=" + after + " missing=" + missing);
        }
        if (missing <= 0) return; // server sudah mengembalikan item dengan benar

        ItemStack lost = p.template.clone();
        lost.setAmount(missing);
        int notReturned = giveBack(player, lost);

        if (notReturned > 0) {
            plugin.getLogger().warning("Tidak bisa mengembalikan " + notReturned + "x "
                    + p.template.getType() + " untuk " + player.getName());
        }

        // Gunakan sistem MiniMessage di messages.yml (atau fallback ke legacy config 'message' jika ada)
        String legacyMsg = plugin.getConfig().getString("message", "");
        if (legacyMsg != null && !legacyMsg.isBlank()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', legacyMsg));
        } else if (!plugin.getMessages().isBlank("drop-fix.item-returned")) {
            plugin.getMessages().send(player, "drop-fix.item-returned");
        }
    }

    /** Kembalikan item: storage -> offhand -> cursor. Return sisa yang gagal dikembalikan. */
    private int giveBack(Player player, ItemStack item) {
        PlayerInventory inv = player.getInventory();

        // a) Slot storage biasa
        Map<Integer, ItemStack> left = inv.addItem(item);
        if (left.isEmpty()) return 0;
        ItemStack rest = left.values().iterator().next();

        // b) Offhand (slot yang tadi dikosongkan player)
        ItemStack off = inv.getItemInOffHand();
        if (off == null || off.getType() == Material.AIR) {
            inv.setItemInOffHand(rest);
            return 0;
        }
        if (off.isSimilar(rest)) {
            int space = off.getMaxStackSize() - off.getAmount();
            int move = Math.min(space, rest.getAmount());
            if (move > 0) {
                off.setAmount(off.getAmount() + move);
                inv.setItemInOffHand(off);
                rest.setAmount(rest.getAmount() - move);
                if (rest.getAmount() <= 0) return 0;
            }
        }

        // c) Cursor
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            player.setItemOnCursor(rest);
            return 0;
        }
        if (cursor.isSimilar(rest)) {
            int space = cursor.getMaxStackSize() - cursor.getAmount();
            int move = Math.min(space, rest.getAmount());
            if (move > 0) {
                cursor.setAmount(cursor.getAmount() + move);
                player.setItemOnCursor(cursor);
                rest.setAmount(rest.getAmount() - move);
            }
        }
        return Math.max(rest.getAmount(), 0);
    }

    /** Hitung item sejenis di seluruh inventory (storage, armor, offhand) + cursor. */
    private int countSimilar(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.isSimilar(template)) total += is.getAmount();
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.isSimilar(template)) total += cursor.getAmount();
        return total;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    public void clear() {
        pending.clear();
    }
}
