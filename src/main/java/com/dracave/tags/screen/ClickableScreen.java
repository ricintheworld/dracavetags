package com.dracave.tags.screen;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public interface ClickableScreen extends InventoryHolder {
    void click(int rawSlot, ClickType clickType);

    @Override
    Inventory getInventory();

    default void onClose() {}
}
