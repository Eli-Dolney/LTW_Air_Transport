package com.ltw.airtransport.ship;

import com.ltw.airtransport.AirConfig;
import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.block.BurnerBlock;
import com.ltw.airtransport.block.ControlPanelBlock;
import com.ltw.airtransport.block.EnvelopeBlock;
import com.ltw.airtransport.block.PropellerBlock;
import com.ltw.airtransport.block.SeatBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The blocks of one airship, in ship-local coordinates. Local (0,0,0) is the Control Panel and the
 * local axes are the world axes at the moment the ship was assembled. The airship entity sits at the
 * bottom-center of the Control Panel block.
 */
public final class ShipStructure {
    /** A collision box in rotated ship space (relative to the entity position) and the block it belongs to. */
    public record Box(AABB box, BlockPos local) {}

    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    private Direction bow = Direction.NORTH;

    // Derived, rebuilt by recompute()
    private int envelopes, burners, propellers;
    private double weight;
    private final List<BlockPos> seats = new ArrayList<>();
    private final List<BlockPos> burnerPositions = new ArrayList<>();
    @Nullable private BlockPos captainSeat;
    @SuppressWarnings("unchecked")
    private final List<Box>[] boxes = new List[4];
    private final AABB[] bounds = new AABB[4];

    public void put(BlockPos local, BlockState state, @Nullable CompoundTag beTag) {
        blocks.put(local.immutable(), state);
        if (beTag != null) blockEntities.put(local.immutable(), beTag);
    }

    public Map<BlockPos, BlockState> blocks() { return blocks; }
    public Map<BlockPos, CompoundTag> blockEntities() { return blockEntities; }
    public Direction bow() { return bow; }
    public void setBow(Direction bow) { this.bow = bow; }
    public int size() { return blocks.size(); }
    public int envelopes() { return envelopes; }
    public int burners() { return burners; }
    public int propellers() { return propellers; }
    public double weight() { return weight; }
    public List<BlockPos> seats() { return seats; }
    public List<BlockPos> burnerPositions() { return burnerPositions; }
    @Nullable public BlockPos captainSeat() { return captainSeat; }

    public boolean isCaptainSeat(BlockPos local) {
        return local.equals(captainSeat);
    }

    /** Recounts lift parts and rebuilds the collision cache. Call after the block map changes. */
    public void recompute() {
        envelopes = burners = propellers = 0;
        weight = 0;
        seats.clear();
        burnerPositions.clear();
        captainSeat = null;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            Block block = e.getValue().getBlock();
            if (block instanceof EnvelopeBlock) {
                envelopes++;
                weight += AirConfig.ENVELOPE_WEIGHT.get();
                continue;
            }
            weight += 1;
            if (block instanceof BurnerBlock) {
                burners++;
                burnerPositions.add(e.getKey());
            } else if (block instanceof PropellerBlock) {
                propellers++;
            } else if (block instanceof SeatBlock seat) {
                seats.add(e.getKey());
                if (seat.isCaptain() && captainSeat == null) captainSeat = e.getKey();
            } else if (block instanceof ControlPanelBlock) {
                bow = e.getValue().getValue(ControlPanelBlock.FACING).getOpposite();
            }
        }
        for (int q = 0; q < 4; q++) {
            boxes[q] = null;
            bounds[q] = null;
        }
    }

    public static Rotation rotation(int quadrant) {
        return switch (Math.floorMod(quadrant, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Collision boxes for the ship turned by {@code quadrant} * 90 degrees clockwise. */
    public List<Box> boxes(int quadrant) {
        int q = Math.floorMod(quadrant, 4);
        if (boxes[q] == null) {
            Rotation rot = rotation(q);
            List<Box> list = new ArrayList<>();
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
                BlockPos r = e.getKey().rotate(rot);
                BlockState state = e.getValue().rotate(rot);
                for (AABB part : state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                    AABB moved = part.move(r.getX() - 0.5, r.getY(), r.getZ() - 0.5);
                    list.add(new Box(moved, e.getKey()));
                }
                minX = Math.min(minX, r.getX() - 0.5);
                minY = Math.min(minY, r.getY());
                minZ = Math.min(minZ, r.getZ() - 0.5);
                maxX = Math.max(maxX, r.getX() + 0.5);
                maxY = Math.max(maxY, r.getY() + 1);
                maxZ = Math.max(maxZ, r.getZ() + 0.5);
            }
            boxes[q] = list;
            bounds[q] = list.isEmpty() && blocks.isEmpty() ? new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5) : new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return boxes[q];
    }

    /** Block-grid bounds of the rotated ship, relative to the entity position. */
    public AABB bounds(int quadrant) {
        boxes(quadrant);
        return bounds[Math.floorMod(quadrant, 4)];
    }

    /** Radius of the ship around its pivot, for render culling while it turns. */
    public double radius() {
        AABB b = bounds(0);
        double x = Math.max(Math.abs(b.minX), Math.abs(b.maxX));
        double z = Math.max(Math.abs(b.minZ), Math.abs(b.maxZ));
        return Math.sqrt(x * x + z * z);
    }

    /** Rotates a ship-local vector by a smooth yaw (degrees, clockwise seen from above, like Minecraft yaw). */
    public static Vec3 rotate(double x, double y, double z, float yawDegrees) {
        double r = Math.toRadians(yawDegrees);
        double cos = Math.cos(r), sin = Math.sin(r);
        return new Vec3(x * cos - z * sin, y, x * sin + z * cos);
    }

    /** Inverse of {@link #rotate}. */
    public static Vec3 unrotate(Vec3 v, float yawDegrees) {
        return rotate(v.x, v.y, v.z, -yawDegrees);
    }

    // --- Serialization (palette keeps big ships small) -----------------------------------------
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            int id = index.computeIfAbsent(e.getValue(), s -> {
                palette.add(s);
                return palette.size() - 1;
            });
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey().asLong());
            c.putInt("s", id);
            CompoundTag be = blockEntities.get(e.getKey());
            if (be != null) c.put("be", be);
            list.add(c);
        }
        ListTag paletteTag = new ListTag();
        for (BlockState s : palette) paletteTag.add(NbtUtils.writeBlockState(s));
        tag.put("palette", paletteTag);
        tag.put("blocks", list);
        tag.putString("bow", bow.getName());
        return tag;
    }

    public static ShipStructure load(CompoundTag tag, HolderGetter<Block> blockLookup) {
        ShipStructure s = new ShipStructure();
        List<BlockState> palette = new ArrayList<>();
        for (Tag t : tag.getList("palette", Tag.TAG_COMPOUND)) palette.add(NbtUtils.readBlockState(blockLookup, (CompoundTag) t));
        for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            int id = c.getInt("s");
            if (id < 0 || id >= palette.size()) continue;
            s.put(BlockPos.of(c.getLong("p")), palette.get(id), c.contains("be") ? c.getCompound("be") : null);
        }
        Direction bow = Direction.byName(tag.getString("bow"));
        if (bow != null) s.bow = bow;
        s.recompute();
        return s;
    }

    public static HolderGetter<Block> blockLookup(net.minecraft.world.level.Level level) {
        return level.holderLookup(Registries.BLOCK);
    }

    static boolean isShipStopper(BlockState state) {
        return state.is(AirTransport.LANDING_PAD.get()) || state.is(AirTransport.PAD_BEACON.get())
            || state.is(ShipTags.SHIP_BLACKLIST);
    }
}
