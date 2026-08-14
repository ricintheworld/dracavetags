package com.dracave.tags.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GuiConfig {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final Map<String, MenuDef> menus = new HashMap<>();

    public record MenuDef(String title, int size, Map<Integer, IconDef> slots, java.util.Set<Integer> contentSlots) {}

    public record IconDef(Material material, String display, List<String> lore,
                          IconAction left, IconAction right, IconAction shiftLeft, String permission) {}

    public record ParsedAction(IconAction action, String param) {
        public static ParsedAction none() { return new ParsedAction(IconAction.NONE, null); }
    }

    public GuiConfig(File folder) {
        if (!folder.exists() || !folder.isDirectory()) return;
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String menuKey = file.getName().replace(".yml", "");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String title = yaml.getString("title", "");
            int size = yaml.getInt("size", 54);
            List<String> inventory = yaml.getStringList("inventory");
            ConfigurationSection icons = yaml.getConfigurationSection("icons");
            Map<Integer, IconDef> slots = new HashMap<>();
            Set<Integer> contentSlots = new LinkedHashSet<>();
            parseGrid(inventory, icons, size, slots, contentSlots);
            menus.put(menuKey, new MenuDef(title, size, slots, contentSlots));
        }
    }

    private void parseGrid(List<String> inventory, ConfigurationSection icons, int size,
                           Map<Integer, IconDef> slots, java.util.Set<Integer> contentSlots) {
        if (inventory.isEmpty() || icons == null) return;
        int row = 0;
        for (String line : inventory) {
            for (int col = 0; col < line.length() && col < 9; col++) {
                char c = line.charAt(col);
                if (c == ' ' || c == '\t') continue;
                String key = String.valueOf(c);
                int slot = row * 9 + col;
                if (slot >= size) continue;
                ConfigurationSection sec = icons.getConfigurationSection(key);
                if (sec == null) continue;
                if (sec.getBoolean("content", false) || (sec.getString("left", "").contains("wear") || sec.getString("left", "").contains("buy")
                        || sec.getString("material", "").equalsIgnoreCase("NAME_TAG")
                        && sec.getString("display", "").contains("{tag_display}"))) {
                    contentSlots.add(slot);
                    continue;
                }
                Material mat = parseMaterial(sec.getString("material", "BARRIER"));
                String display = sec.getString("display", "");
                List<String> lore = sec.getStringList("lore");
                String permission = sec.getString("permission");
                IconDef def = new IconDef(mat, display, lore,
                        parseAction(sec, "left"), parseAction(sec, "right"),
                        parseAction(sec, "shift-left"),
                        permission != null && !permission.isEmpty() ? permission : null);
                slots.put(slot, def);
            }
            row++;
        }
    }

    public MenuDef get(String menuKey) {
        return menus.get(menuKey);
    }

    /**
     * 杩斿洖鍏ㄩ儴宸叉敞鍐岀殑鑿滃崟 key锛堝搴?gui/ 鐩綍涓嬬殑 yml 鏂囦欢鍚嶏紝涓嶅惈鎵╁睍鍚嶏級銆俙r
     * 鐢ㄤ簬 /dctags menu <key> 鐨?tab 琛ュ叏銆俙r
     */
    public Set<String> keys() {
        return menus.keySet();
    }

    public IconDef iconAt(MenuDef menu, int slot, char fillWith) {
        if (menu == null) return null;
        IconDef def = menu.slots().get(slot);
        if (def != null) return def;
        if (fillWith != 0) {
            return menu.slots().get(fillWith);
        }
        return null;
    }

    public ItemStack buildItem(IconDef def, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String display = def.display();
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            display = display.replace(e.getKey(), e.getValue());
        }
        meta.displayName(MINI.deserialize(display));
        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        for (String line : def.lore()) {
            String processed = line;
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                processed = processed.replace(e.getKey(), e.getValue());
            }
            loreComponents.add(MINI.deserialize(processed));
        }
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack simpleItem(Material material, String display) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI.deserialize(display));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ParsedAction parse(String actionCmd) {
        if (actionCmd == null || actionCmd.isEmpty()) return new ParsedAction(IconAction.NONE, null);
        String[] parts = actionCmd.split(" ", 2);
        return switch (parts[0]) {
            case "open" -> parts.length > 1 ? parseOpen(parts[1]) : new ParsedAction(IconAction.NONE, null);
            case "wear" -> new ParsedAction(IconAction.WEAR_TAG, parts.length > 1 ? parts[1] : null);
            case "buy" -> new ParsedAction(IconAction.BUY_TAG, parts.length > 1 ? parts[1] : null);
            case "edit" -> new ParsedAction(IconAction.EDIT_TAG, parts.length > 1 ? parts[1] : null);
            case "delete" -> new ParsedAction(IconAction.DELETE_TAG, parts.length > 1 ? parts[1] : null);
            case "edit-custom" -> new ParsedAction(IconAction.EDIT_CUSTOM, parts.length > 1 ? parts[1] : null);
            case "delete-custom" -> new ParsedAction(IconAction.DELETE_CUSTOM, parts.length > 1 ? parts[1] : null);
            case "create-custom" -> new ParsedAction(IconAction.CREATE_CUSTOM, null);
            case "filter" -> parts.length > 1 ? parseFilter(parts[1]) : new ParsedAction(IconAction.FILTER_ALL, null);
            case "page" -> parts.length > 1 && "next".equals(parts[1]) ? new ParsedAction(IconAction.PAGE_NEXT, null)
                    : new ParsedAction(IconAction.PAGE_PREV, null);
            case "close" -> new ParsedAction(IconAction.CLOSE, null);
            case "command" -> new ParsedAction(
                    parts[0].equals("dctags upload all --check") || actionCmd.contains("check")
                    ? IconAction.COMMAND_CHECK : IconAction.COMMAND_UPLOAD, parts.length > 1 ? parts[1] : null);
            default -> new ParsedAction(IconAction.NONE, null);
        };
    }

    private static ParsedAction parseOpen(String target) {
        return switch (target) {
            case "vault" -> new ParsedAction(IconAction.OPEN_VAULT, null);
            case "shop" -> new ParsedAction(IconAction.OPEN_SHOP, null);
            case "custom" -> new ParsedAction(IconAction.OPEN_CUSTOM, null);
            case "reward" -> new ParsedAction(IconAction.OPEN_REWARD, null);
            case "admin-shop" -> new ParsedAction(IconAction.OPEN_ADMIN, null);
            case "main-menu" -> new ParsedAction(IconAction.OPEN_MAIN_MENU, null);
            case "ranking" -> new ParsedAction(IconAction.OPEN_RANKING, null);
            case "player-management" -> new ParsedAction(IconAction.OPEN_PLAYER_MANAGEMENT, null);
            default -> new ParsedAction(IconAction.NONE, null);
        };
    }

    private static ParsedAction parseFilter(String filter) {
        return switch (filter) {
            case "all" -> new ParsedAction(IconAction.FILTER_ALL, null);
            case "vault" -> new ParsedAction(IconAction.FILTER_VAULT, null);
            case "playerpoints" -> new ParsedAction(IconAction.FILTER_POINTS, null);
            case "coin" -> new ParsedAction(IconAction.FILTER_COIN, null);
            case "item" -> new ParsedAction(IconAction.FILTER_ITEM, null);
            default -> new ParsedAction(IconAction.FILTER_ALL, null);
        };
    }

    public static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return Material.BARRIER;
        }
    }

    private static IconAction parseAction(ConfigurationSection sec, String key) {
        List<String> actions = sec.getStringList(key);
        if (actions.isEmpty()) return IconAction.NONE;
        String cmd = actions.get(0);
        if (cmd == null || cmd.isEmpty()) return IconAction.NONE;
        String[] parts = cmd.split(" ", 2);
        return switch (parts[0]) {
            case "open" -> {
                String target = parts.length > 1 ? parts[1] : "";
                yield switch (target) {
                    case "vault" -> IconAction.OPEN_VAULT;
                    case "shop" -> IconAction.OPEN_SHOP;
                    case "custom" -> IconAction.OPEN_CUSTOM;
                    case "reward" -> IconAction.OPEN_REWARD;
                    case "admin-shop" -> IconAction.OPEN_ADMIN;
                    case "main-menu" -> IconAction.OPEN_MAIN_MENU;
                    case "ranking" -> IconAction.OPEN_RANKING;
                    default -> IconAction.NONE;
                };
            }
            case "wear" -> IconAction.WEAR_TAG;
            case "buy" -> IconAction.BUY_TAG;
            case "edit" -> IconAction.EDIT_TAG;
            case "delete" -> IconAction.DELETE_TAG;
            case "edit-custom" -> IconAction.EDIT_CUSTOM;
            case "delete-custom" -> IconAction.DELETE_CUSTOM;
            case "create-custom" -> IconAction.CREATE_CUSTOM;
            case "create" -> IconAction.CREATE;
            case "filter" -> {
                String f = parts.length > 1 ? parts[1] : "all";
                yield switch (f) {
                    case "vault" -> IconAction.FILTER_VAULT;
                    case "playerpoints" -> IconAction.FILTER_POINTS;
                    case "coin" -> IconAction.FILTER_COIN;
                    case "item" -> IconAction.FILTER_ITEM;
                    default -> IconAction.FILTER_ALL;
                };
            }
            case "page" -> parts.length > 1 && "next".equals(parts[1]) ? IconAction.PAGE_NEXT : IconAction.PAGE_PREV;
            case "close" -> IconAction.CLOSE;
            case "command" -> cmd.contains("check") ? IconAction.COMMAND_CHECK : IconAction.COMMAND_UPLOAD;
            default -> IconAction.NONE;
        };
    }


    public enum IconAction {
        NONE, OPEN_VAULT, OPEN_SHOP, OPEN_CUSTOM, OPEN_REWARD, OPEN_ADMIN,
        OPEN_MAIN_MENU, WEAR_TAG, BUY_TAG, EDIT_TAG, DELETE_TAG, EDIT_CUSTOM, DELETE_CUSTOM,
        CREATE_CUSTOM, CREATE, FILTER_ALL, FILTER_VAULT, FILTER_POINTS, FILTER_COIN, FILTER_ITEM,
        PAGE_PREV, PAGE_NEXT, CLOSE, OPEN_RANKING, COMMAND_UPLOAD, COMMAND_CHECK, OPEN_PLAYER_MANAGEMENT
    }
}
