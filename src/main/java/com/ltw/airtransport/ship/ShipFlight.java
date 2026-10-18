package com.ltw.airtransport.ship;

import com.ltw.airtransport.AirConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lift math and ship-vs-world collision. */
public final class ShipFlight {
    private ShipFlight() {}

    /** Hot air lifts what the envelopes can hold, but only as much as the lit burners can heat. */
    public static double liftCapacity(int envelopes, int litBurners) {
        return Math.min(envelopes * AirConfig.ENVELOPE_LIFT.get(), litBurners * AirConfig.BURNER_HEAT.get());
    }

    public static double cruiseSpeed(int propellers) {
        return Math.min(AirConfig.MAX_SPEED.get(), AirConfig.BASE_SPEED.get() + propellers * AirConfig.PROPELLER_SPEED.get());
    }

    /**
     * Solid world boxes inside {@code region}. Unloaded chunks and fluids count as solid so ships
     * never fly into the void of an unloaded chunk or sink into the sea.
     */
    public static List<AABB> worldSolids(Level level, AABB region) {
        List<AABB> out = new ArrayList<>();
        int x0 = Mth.floor(region.minX), y0 = Mth.floor(region.minY), z0 = Mth.floor(region.minZ);
        int x1 = Mth.floor(region.maxX), y1 = Mth.floor(region.maxY), z1 = Mth.floor(region.maxZ);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                m.set(x, y0, z);
                if (!level.isLoaded(m)) {
                    out.add(new AABB(x, y0, z, x + 1, y1 + 1, z + 1));
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    m.set(x, y, z);
                    if (level.isOutsideBuildHeight(y)) {
                        if (y < level.getMinBuildHeight()) out.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                        continue;
                    }
                    BlockState state = level.getBlockState(m);
                    if (state.isAir()) continue;
                    if (!state.getFluidState().isEmpty()) {
                        out.add(new AABB(x, y, z, x + 1, y + 0.9, z + 1));
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, m);
                    if (shape.isEmpty()) continue;
                    for (AABB box : shape.toAabbs()) out.add(box.move(x, y, z));
                }
            }
        }
        return out;
    }

    /** Would the ship's boxes (rotated, relative to {@code pos}) hit any of {@code solids}? */
    public static boolean collides(List<ShipStructure.Box> boxes, Vec3 pos, List<AABB> solids) {
        if (solids.isEmpty()) return false;
        for (ShipStructure.Box b : boxes) {
            AABB world = b.box().move(pos).deflate(1.0E-4);
            for (AABB solid : solids) {
                if (world.intersects(solid)) return true;
            }
        }
        return false;
    }

    /**
     * Moves the ship by {@code motion} as far as the world allows, axis by axis (Y first).
     * Returns the movement actually applied.
     */
    public static Vec3 move(Level level, ShipStructure structure, int quadrant, Vec3 pos, Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-10) return Vec3.ZERO;
        AABB bounds = structure.bounds(quadrant);
        AABB swept = bounds.move(pos).expandTowards(motion).inflate(0.01);
        List<AABB> solids = worldSolids(level, swept);
        List<ShipStructure.Box> boxes = structure.boxes(quadrant);
        if (!collides(boxes, pos.add(motion), solids)) return motion;
        double dy = collides(boxes, pos.add(0, motion.y, 0), solids) ? 0 : motion.y;
        Vec3 at = pos.add(0, dy, 0);
        double dx = collides(boxes, at.add(motion.x, 0, 0), solids) ? 0 : motion.x;
        at = at.add(dx, 0, 0);
        double dz = collides(boxes, at.add(0, 0, motion.z), solids) ? 0 : motion.z;
        return new Vec3(dx, dy, dz);
    }

    /** Is there ground (or water) within {@code depth} blocks below the ship? */
    public static boolean groundBelow(Level level, ShipStructure structure, int quadrant, Vec3 pos, double depth) {
        AABB bounds = structure.bounds(quadrant).move(pos);
        AABB below = new AABB(bounds.minX, bounds.minY - depth, bounds.minZ, bounds.maxX, bounds.minY, bounds.maxZ);
        List<AABB> solids = worldSolids(level, below);
        if (solids.isEmpty()) return false;
        for (ShipStructure.Box b : structure.boxes(quadrant)) {
            AABB swept = b.box().move(pos).expandTowards(0, -depth, 0).deflate(1.0E-4);
            for (AABB solid : solids) {
                if (swept.intersects(solid)) return true;
            }
        }
        return false;
    }
}
