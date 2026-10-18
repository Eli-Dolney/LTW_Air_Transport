package com.ltw.airtransport.menu;

import com.ltw.airtransport.entity.AirshipEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lets a menu use a block entity that is riding on a flying ship. Only item access is forwarded;
 * open/close hooks are skipped because the block entity is not in the world right now.
 */
public final class ShipContainer implements Container {
    private final Container delegate;
    private final AirshipEntity ship;

    public ShipContainer(Container delegate, AirshipEntity ship) {
        this.delegate = delegate;
        this.ship = ship;
    }

    @Override public int getContainerSize() { return delegate.getContainerSize(); }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return delegate.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return delegate.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return delegate.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { delegate.setItem(slot, stack); }
    @Override public int getMaxStackSize() { return delegate.getMaxStackSize(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return delegate.canPlaceItem(slot, stack); }
    @Override public void setChanged() {}
    @Override public void clearContent() { delegate.clearContent(); }

    @Override
    public boolean stillValid(Player player) {
        double r = ship.structure().radius() + 8;
        return !ship.isRemoved() && player.distanceToSqr(ship) < r * r;
    }
}
