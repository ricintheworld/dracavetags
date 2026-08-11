package com.dracave.tags.listen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.Locale;
import com.dracave.tags.engine.CardEngine;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.render.DCTagRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class CardListener implements Listener {
    private final DraCaveTags plugin;

    public CardListener(DraCaveTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        CardEngine cards = plugin.cardEngine();
        if (cards == null || !cards.isCard(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String kind = cards.cardKind(item);
        int days = cards.cardDays(item);
        if ("random".equals(kind)) {
            EcoType currency = cards.cardCurrency(item);
            if (currency == null) {
                plugin.messages().send(player, "card-none");
                return;
            }
            DCTag tag = cards.randomPurchasable(currency, player);
            if (tag == null) {
                plugin.messages().send(player, "card-none");
                return;
            }
            consumeOne(player, item);
            plugin.tagEngine().grant(player.getUniqueId(), tag.id(), days).thenAccept(result ->
                    plugin.messages().send(player, "card-used",
                            Locale.parsed("title", DCTagRenderer.miniMessage(tag, System.currentTimeMillis()))));
        } else if ("fixed".equals(kind)) {
            String tagId = cards.cardTagId(item);
            if (tagId == null || plugin.registry().get(tagId) == null) {
                return;
            }
            consumeOne(player, item);
            plugin.tagEngine().grant(player.getUniqueId(), tagId, days).thenAccept(result ->
                    plugin.messages().send(player, "card-used",
                            Locale.parsed("title", DCTagRenderer.miniMessage(plugin.registry().get(tagId), System.currentTimeMillis()))));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return;
        }
        CardEngine cards = plugin.cardEngine();
        if (cards == null || !cards.isCard(item)) {
            return;
        }
        event.setCancelled(true);
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
