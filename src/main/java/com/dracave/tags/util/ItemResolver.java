package com.dracave.tags.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class ItemResolver {
    private ItemResolver() {
    }

    public static ItemStack resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ItemStack(Material.NAME_TAG);
        }
        String value = raw.trim();
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return parseMaterial(value);
        }
        String prefix = value.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
        String content = value.substring(colon + 1);
        return switch (prefix) {
            case "hdb", "headdatabase" -> resolveHeadDatabase(content);
            case "base64", "textures" -> resolveBase64Head(content);
            case "ia", "itemsadder" -> resolveItemsAdder(content);
            case "material" -> parseMaterial(content);
            default -> parseMaterial(value);
        };
    }

    private static ItemStack parseMaterial(String name) {
        try {
            Material material = Material.valueOf(name.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_'));
            return new ItemStack(material);
        } catch (IllegalArgumentException ex) {
            return new ItemStack(Material.NAME_TAG);
        }
    }

    private static ItemStack resolveHeadDatabase(String id) {
        try {
            Plugin hdb = Bukkit.getPluginManager().getPlugin("HeadDatabase");
            if (hdb != null && hdb.isEnabled()) {
                Object api = hdb.getClass().getMethod("getAPI").invoke(hdb);
                Object item = api.getClass().getMethod("getItemHead", String.class).invoke(api, id);
                if (item instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static ItemStack resolveBase64Head(String textures) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                String decoded = new String(Base64.getDecoder().decode(textures));
                String url = decoded.contains("\"url\"")
                        ? decoded.substring(decoded.indexOf("\"url\"") + 6).replaceAll("[\"}].*", "").trim()
                        : textures;
                com.destroystokyo.paper.profile.PlayerProfile profile =
                        Bukkit.createProfile(UUID.randomUUID(), null);
                profile.getTextures().setSkin(new java.net.URL(url));
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
            }
        } catch (Exception ignored) {
        }
        return head;
    }

    private static ItemStack resolveItemsAdder(String id) {
        try {
            Plugin ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            if (ia != null && ia.isEnabled()) {
                Object api = Class.forName("dev.lone.itemsadder.api.CustomStack").getMethod("getInstance", String.class)
                        .invoke(null, id);
                if (api != null) {
                    Object item = api.getClass().getMethod("getItemStack").invoke(api);
                    if (item instanceof ItemStack stack) {
                        return stack;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(Material.NAME_TAG);
    }

    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim();
        int colon = value.indexOf(':');
        if (colon > 0) {
            String prefix = value.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
            return switch (prefix) {
                case "hdb", "headdatabase" -> Bukkit.getPluginManager().getPlugin("HeadDatabase") != null;
                case "base64", "textures" -> true;
                case "ia", "itemsadder" -> Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
                case "material" -> isMaterial(value.substring(colon + 1));
                default -> isMaterial(value);
            };
        }
        return isMaterial(value);
    }

    private static boolean isMaterial(String name) {
        try {
            Material material = Material.valueOf(name.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_'));
            return !material.isAir();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static List<String> allItemIds() {
        List<String> ids = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (!mat.isAir() && mat.isItem()) {
                ids.add("material:" + mat.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return ids;
    }
}
