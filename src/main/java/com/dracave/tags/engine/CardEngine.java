package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.EcoType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CardEngine {
    public static final String KEY_KIND = "card_kind";
    public static final String KEY_TYPE = "card_type";
    public static final String KEY_DAYS = "card_days";
    public static final String KEY_TAG_ID = "card_tag_id";

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DraCaveTags plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey daysKey;
    private final NamespacedKey tagIdKey;

    public CardEngine(DraCaveTags plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, KEY_KIND);
        this.typeKey = new NamespacedKey(plugin, KEY_TYPE);
        this.daysKey = new NamespacedKey(plugin, KEY_DAYS);
        this.tagIdKey = new NamespacedKey(plugin, KEY_TAG_ID);
    }

    public ItemStack randomCard(EcoType currency, int days) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String currencyDisplay = plugin.getConfig().getString("shop.currencies." + currency.id() + ".display", currency.id());
        meta.displayName(MINI.deserialize("<gold>随机称号卡（" + stripTags(currencyDisplay) + "）</gold>"));
        meta.lore(List.of(
                MINI.deserialize("<gray>使用后随机获得一个"),
                MINI.deserialize("<gray>" + (days > 0 ? "限时 " + days + " 天的" : "永久的") + "可购买称号"),
                MINI.deserialize("<gray>货币类型：" + currencyDisplay),
                MINI.deserialize(""),
                MINI.deserialize("<yellow>右键空气或方块使用</yellow>")
        ));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "random");
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, currency.id());
        meta.getPersistentDataContainer().set(daysKey, PersistentDataType.INTEGER, days);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack tagCard(String tagId, int days) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize("<aqua>称号卡</aqua>"));
        meta.lore(List.of(
                MINI.deserialize("<gray>使用后获得指定称号"),
                MINI.deserialize("<gray>称号：<white>" + MINI.escapeTags(tagId)),
                MINI.deserialize("<gray>期限：" + (days > 0 ? "限时 " + days + " 天" : "永久")),
                MINI.deserialize(""),
                MINI.deserialize("<yellow>右键空气或方块使用</yellow>")
        ));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "fixed");
        meta.getPersistentDataContainer().set(tagIdKey, PersistentDataType.STRING, tagId);
        meta.getPersistentDataContainer().set(daysKey, PersistentDataType.INTEGER, days);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCard(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(kindKey, PersistentDataType.STRING);
    }

    public String cardKind(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }

    public int cardDays(ItemStack item) {
        Integer days = item.getItemMeta().getPersistentDataContainer().get(daysKey, PersistentDataType.INTEGER);
        return days == null ? 0 : days;
    }

    public String cardTagId(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(tagIdKey, PersistentDataType.STRING);
    }

    public EcoType cardCurrency(ItemStack item) {
        String id = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        try {
            return id == null ? null : EcoType.parse(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public DCTag randomPurchasable(EcoType currency, Player player) {
        var data = plugin.tagEngine().getCached(player.getUniqueId());
        if (data == null) {
            return null;
        }
        List<DCTag> candidates = plugin.registry().all().stream()
                .filter(t -> t.purchasable() && t.purchaseOffer().currency() == currency)
                .filter(t -> !data.unlocked().contains(t.id()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private String stripTags(String miniMessage) {
        return miniMessage.replaceAll("<[^>]+>", "");
    }
}
