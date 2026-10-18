package com.ltw.airtransport.block;

import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.menu.BurnerMenu;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One fuel slot. Burners only burn while their ship is in the air; the ship calls {@link #flightTick}.
 * When the slot runs dry the ship lets the burner pull fuel from chests on board.
 */
public class BurnerBlockEntity extends BlockEntity implements Container, MenuProvider {
    private ItemStack fuel = ItemStack.EMPTY;
    private float burnTime;
    private int burnDuration;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return i == 0 ? (int) burnTime : burnDuration;
        }

        @Override
        public void set(int i, int value) {
            if (i == 0) burnTime = value;
            else burnDuration = value;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public BurnerBlockEntity(BlockPos pos, BlockState state) {
        super(AirTransport.BURNER_BE.get(), pos, state);
    }

    public static int fuelValue(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    /**
     * Burns for one tick. {@code refuel} is asked for one fuel item when the slot is empty
     * (it returns an empty stack if the ship has none). Returns whether the burner is lit.
     */
    public boolean flightTick(double multiplier, Function<BurnerBlockEntity, ItemStack> refuel) {
        if (burnTime > 0) burnTime -= (float) multiplier;
        if (burnTime <= 0) {
            burnTime = 0;
            ItemStack next = fuel.isEmpty() ? refuel.apply(this) : fuel.split(1);
            int value = fuelValue(next);
            if (value > 0) {
                ItemStack remainder = next.getCraftingRemainingItem();
                if (!remainder.isEmpty() && fuel.isEmpty()) fuel = remainder; // lava bucket leaves a bucket
                burnTime = value;
                burnDuration = value;
            }
            setChanged();
        }
        return burnTime > 0;
    }

    /** Total ticks of burn left, counting fuel in the slot. */
    public long fuelTicks() {
        return (long) burnTime + (long) fuelValue(fuel) * fuel.getCount();
    }

    // --- Container ----------------------------------------------------------------------------
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return fuel.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return fuel; }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = fuel.split(amount);
        setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = fuel;
        fuel = ItemStack.EMPTY;
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        fuel = stack;
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return fuelValue(stack) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        fuel = ItemStack.EMPTY;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ltwairtransport.balloon_burner");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BurnerMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fuel.isEmpty()) tag.put("fuel", fuel.save(registries));
        tag.putFloat("burnTime", burnTime);
        tag.putInt("burnDuration", burnDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel = tag.contains("fuel") ? ItemStack.parseOptional(registries, tag.getCompound("fuel")) : ItemStack.EMPTY;
        burnTime = tag.getFloat("burnTime");
        burnDuration = tag.getInt("burnDuration");
    }
}
