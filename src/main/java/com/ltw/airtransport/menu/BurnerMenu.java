package com.ltw.airtransport.menu;

import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.block.BurnerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One fuel slot plus the player inventory. */
public class BurnerMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public BurnerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), new SimpleContainerData(2));
    }

    public BurnerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(AirTransport.BURNER_MENU.get(), id);
        this.container = container;
        this.data = data;
        addSlot(new Slot(container, 0, 80, 44) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BurnerBlockEntity.fuelValue(stack) > 0;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    /** 0..1 of the current fuel item left. */
    public float burnProgress() {
        int duration = data.get(1);
        return duration <= 0 ? 0 : Math.min(1f, data.get(0) / (float) duration);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else if (BurnerBlockEntity.fuelValue(stack) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
