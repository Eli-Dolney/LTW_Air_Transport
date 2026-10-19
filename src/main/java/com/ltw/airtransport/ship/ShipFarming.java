package com.ltw.airtransport.ship;

import com.ltw.airtransport.AirConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;

/**
 * Farms on a flying ship. Growth and harvesting work on the {@link ShipStructure} block map only,
 * never on world positions (vanilla {@code randomTick} would grow whatever is under the ship).
 * Every method that changes the map also records the new state in {@code changes} (air = removed)
 * so the ship can rebuild its collision and tell clients.
 */
public final class ShipFarming {
    /** Sugar cane and cactus stop at three blocks, like in the world. */
    private static final int SHORT_PLANT_HEIGHT = 3;
    /** Chance per random tick that a kelp head grows (vanilla value). */
    private static final double KELP_GROW_CHANCE = 0.14;

    private ShipFarming() {}

    /** Blocks that grow when random-ticked on a ship. */
    public static boolean isFarmable(BlockState state) {
        Block b = state.getBlock();
        return b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof SweetBerryBushBlock
            || b == Blocks.KELP || b == Blocks.SUGAR_CANE || b == Blocks.CACTUS || b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING;
    }

    private static boolean sameColumn(BlockState a, BlockState b) {
        if (isKelp(a)) return isKelp(b);
        return a.getBlock() == b.getBlock();
    }

    private static boolean isKelp(BlockState s) {
        return s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT);
    }

    /** Plants harvested by cutting everything above the bottom block. */
    private static boolean isColumnPlant(BlockState s) {
        return isKelp(s) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO);
    }

    private static int heightBelow(ShipStructure s, BlockPos local, BlockState state) {
        int h = 1;
        BlockPos.MutableBlockPos m = local.mutable();
        while (true) {
            m.move(Direction.DOWN);
            BlockState below = s.blocks().get(m);
            if (below == null || !sameColumn(state, below)) return h;
            h++;
        }
    }

    private static void set(ShipStructure s, BlockPos local, BlockState state, Map<BlockPos, BlockState> changes) {
        s.set(local, state);
        changes.put(local.immutable(), state);
    }

    /**
     * One random tick of the ship block at {@code local}. {@code room} says whether a plant may grow
     * into an empty ship-local position (it is not part of the ship and the world there is clear).
     * Returns whether anything changed.
     */
    public static boolean randomTick(ShipStructure s, BlockPos local, RandomSource random, Predicate<BlockPos> room,
                                     Map<BlockPos, BlockState> changes) {
        BlockState state = s.blocks().get(local);
        if (state == null) return false;
        Block block = state.getBlock();
        int cap = AirConfig.FARM_PLANT_HEIGHT.get();

        if (block instanceof CropBlock crop) {
            if (crop.isMaxAge(state)) return false;
            BlockState soil = s.blocks().get(local.below());
            boolean moist = soil != null && soil.getBlock() instanceof FarmBlock && soil.getValue(FarmBlock.MOISTURE) > 0;
            if (random.nextInt(moist ? 7 : 13) != 0) return false;
            set(s, local, crop.getStateForAge(crop.getAge(state) + 1), changes);
            return true;
        }
        if (block instanceof NetherWartBlock) {
            int age = state.getValue(NetherWartBlock.AGE);
            if (age >= 3 || random.nextInt(10) != 0) return false;
            set(s, local, state.setValue(NetherWartBlock.AGE, age + 1), changes);
            return true;
        }
        if (block instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            if (age >= 3 || random.nextInt(5) != 0) return false;
            set(s, local, state.setValue(SweetBerryBushBlock.AGE, age + 1), changes);
            return true;
        }
        BlockPos above = local.above();
        if (block == Blocks.KELP) {
            int age = state.getValue(GrowingPlantHeadBlock.AGE);
            if (age >= GrowingPlantHeadBlock.MAX_AGE || random.nextDouble() >= KELP_GROW_CHANCE) return false;
            boolean intoWater = ShipAssembler.isWaterSource(s.blocks().getOrDefault(above, Blocks.AIR.defaultBlockState()));
            if (heightBelow(s, local, state) >= cap || !(intoWater || room.test(above))) return false;
            set(s, local, Blocks.KELP_PLANT.defaultBlockState(), changes);
            set(s, above, state.setValue(GrowingPlantHeadBlock.AGE, age + 1), changes);
            return true;
        }
        if (block == Blocks.SUGAR_CANE || block == Blocks.CACTUS) {
            var ageProp = block == Blocks.CACTUS ? CactusBlock.AGE : SugarCaneBlock.AGE;
            if (s.blocks().containsKey(above) || heightBelow(s, local, state) >= SHORT_PLANT_HEIGHT) return false;
            int age = state.getValue(ageProp);
            if (age < 15) {
                set(s, local, state.setValue(ageProp, age + 1), changes);
                return true;
            }
            if (!room.test(above)) return false;
            set(s, local, state.setValue(ageProp, 0), changes);
            set(s, above, block.defaultBlockState(), changes);
            return true;
        }
        if (block == Blocks.BAMBOO_SAPLING) {
            if (random.nextInt(3) != 0 || !room.test(above)) return false;
            set(s, local, Blocks.BAMBOO.defaultBlockState(), changes);
            set(s, above, Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL), changes);
            return true;
        }
        if (block == Blocks.BAMBOO) {
            if (state.getValue(BambooStalkBlock.STAGE) != 0 || random.nextInt(3) != 0) return false;
            if (s.blocks().containsKey(above)) return false;
            int height = heightBelow(s, local, state);
            if (height >= cap) {
                set(s, local, state.setValue(BambooStalkBlock.STAGE, 1), changes);
                return true;
            }
            if (!room.test(above)) return false;
            BlockState top = state.setValue(BambooStalkBlock.LEAVES, height >= 2 ? BambooLeaves.LARGE : BambooLeaves.SMALL)
                .setValue(BambooStalkBlock.STAGE, height + 1 >= cap ? 1 : 0);
            set(s, above, top, changes);
            return true;
        }
        return false;
    }

    /** What a harvester would take from one spot: the states cut (for drops) and the new block map. */
    public record Harvest(List<BlockState> cut, Map<BlockPos, BlockState> result) {}

    /**
     * Plans the harvest of the ship block at {@code local}: ripe crops are picked and replanted,
     * columns (kelp, sugar cane, cactus, bamboo) are cut from here up, keeping the block below.
     * Returns null if nothing there is ready.
     */
    @javax.annotation.Nullable
    public static Harvest planHarvest(ShipStructure s, BlockPos local) {
        BlockState state = s.blocks().get(local);
        if (state == null) return null;
        Block block = state.getBlock();
        Map<BlockPos, BlockState> result = new java.util.LinkedHashMap<>();
        if (block instanceof CropBlock crop) {
            if (!crop.isMaxAge(state)) return null;
            result.put(local, crop.getStateForAge(0));
            return new Harvest(List.of(state), result);
        }
        if (block instanceof NetherWartBlock) {
            if (state.getValue(NetherWartBlock.AGE) < 3) return null;
            result.put(local, state.setValue(NetherWartBlock.AGE, 0));
            return new Harvest(List.of(state), result);
        }
        if (block instanceof SweetBerryBushBlock) {
            if (state.getValue(SweetBerryBushBlock.AGE) < 3) return null;
            result.put(local, state.setValue(SweetBerryBushBlock.AGE, 1));
            return new Harvest(List.of(state), result);
        }
        if (!isColumnPlant(state)) return null;
        BlockState below = s.blocks().get(local.below());
        if (below == null || !sameColumn(state, below)) return null; // never cut the bottom block
        List<BlockState> cut = new ArrayList<>();
        List<BlockPos> cutPositions = new ArrayList<>();
        BlockPos.MutableBlockPos m = local.mutable();
        while (true) {
            BlockState here = s.blocks().get(m);
            if (here == null || !sameColumn(state, here)) break;
            cut.add(here);
            cutPositions.add(m.immutable());
            m.move(Direction.UP);
        }
        // Kelp cut inside a water tank leaves water behind, like in the world; elsewhere it leaves air.
        // Top down, so water above flows into the gap.
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int i = cutPositions.size() - 1; i >= 0; i--) {
            BlockPos p = cutPositions.get(i);
            boolean wet = isKelp(state) && (isWater(s, result, p.above()) || Direction.Plane.HORIZONTAL.stream().anyMatch(d -> isWater(s, result, p.relative(d))));
            result.put(p, wet ? water : Blocks.AIR.defaultBlockState());
        }
        // The block left on top becomes the growing tip again.
        BlockState newTop = isKelp(below) ? Blocks.KELP.defaultBlockState()
            : below.is(Blocks.BAMBOO) ? below.setValue(BambooStalkBlock.STAGE, 0)
            : below.is(Blocks.CACTUS) ? below.setValue(CactusBlock.AGE, 0)
            : below.is(Blocks.SUGAR_CANE) ? below.setValue(SugarCaneBlock.AGE, 0) : below;
        if (newTop != below) result.put(local.below(), newTop);
        return new Harvest(cut, result);
    }

    private static boolean isWater(ShipStructure s, Map<BlockPos, BlockState> planned, BlockPos pos) {
        BlockState state = planned.containsKey(pos) ? planned.get(pos) : s.blocks().get(pos);
        return state != null && ShipAssembler.isWaterSource(state);
    }

    public static void apply(ShipStructure s, Map<BlockPos, BlockState> result, Map<BlockPos, BlockState> changes) {
        result.forEach((pos, state) -> set(s, pos, state, changes));
    }

    // --- Inventories --------------------------------------------------------------------------
    /** Copies of every slot, to try changes on before touching the real container. */
    public static ItemStack[] snapshot(Container c) {
        ItemStack[] items = new ItemStack[c.getContainerSize()];
        for (int i = 0; i < items.length; i++) items[i] = c.getItem(i).copy();
        return items;
    }

    public static void writeBack(Container c, ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            if (!ItemStack.matches(c.getItem(i), items[i])) c.setItem(i, items[i]);
        }
        c.setChanged();
    }

    /** Puts all of {@code stack} into {@code items}. Returns false (items partly changed) if it doesn't fit. */
    public static boolean insert(Container c, ItemStack[] items, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < items.length && !rest.isEmpty(); i++) {
            ItemStack slot = items[i];
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, rest)) continue;
            int move = Math.min(rest.getCount(), c.getMaxStackSize(slot) - slot.getCount());
            if (move > 0) {
                slot.grow(move);
                rest.shrink(move);
            }
        }
        for (int i = 0; i < items.length && !rest.isEmpty(); i++) {
            if (!items[i].isEmpty() || !c.canPlaceItem(i, rest)) continue;
            int move = Math.min(rest.getCount(), c.getMaxStackSize(rest));
            items[i] = rest.split(move);
        }
        return rest.isEmpty();
    }

    /** Kelp it takes to make one dried kelp block (9 dried kelp, smelted one each). */
    public static final int KELP_PER_BLOCK = 9;

    /** Turns nine kelp in the container into one dried kelp block. Returns whether it did. */
    public static boolean dryKelp(Container c) {
        ItemStack[] items = snapshot(c);
        int need = KELP_PER_BLOCK;
        for (int i = 0; i < items.length && need > 0; i++) {
            if (!items[i].is(Items.KELP)) continue;
            int take = Math.min(need, items[i].getCount());
            items[i].shrink(take);
            if (items[i].isEmpty()) items[i] = ItemStack.EMPTY;
            need -= take;
        }
        if (need > 0 || !insert(c, items, new ItemStack(Items.DRIED_KELP_BLOCK))) return false;
        writeBack(c, items);
        return true;
    }
}
