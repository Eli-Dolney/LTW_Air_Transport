package com.ltw.airtransport.ship;

import com.ltw.airtransport.entity.AirshipEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Keeps track of loaded airships per level (entity lookups miss big entities whose origin is in a
 * far section) and turns their blocks into collision shapes for everything else.
 */
public final class ShipCollision {
    private static final Map<Level, Set<AirshipEntity>> SHIPS = Collections.synchronizedMap(new WeakHashMap<>());

    private ShipCollision() {}

    public static void track(AirshipEntity ship) {
        SHIPS.computeIfAbsent(ship.level(), l -> ConcurrentHashMap.newKeySet()).add(ship);
    }

    public static void untrack(AirshipEntity ship) {
        Set<AirshipEntity> set = SHIPS.get(ship.level());
        if (set != null) set.remove(ship);
    }

    public static Set<AirshipEntity> ships(Level level) {
        Set<AirshipEntity> set = SHIPS.get(level);
        return set == null ? Set.of() : set;
    }

    /**
     * Adds ship block shapes to the shapes an entity collides with. Server-side players are skipped:
     * their client is authoritative and already collides with the ship, so the server must not
     * second-guess (and rubber-band) them.
     */
    public static List<VoxelShape> withShipShapes(Entity entity, AABB query, List<VoxelShape> original) {
        if (entity instanceof AirshipEntity || entity instanceof ServerPlayer || entity.isPassenger()) return original;
        Set<AirshipEntity> ships = ships(entity.level());
        if (ships.isEmpty()) return original;
        List<VoxelShape> out = null;
        for (AirshipEntity ship : ships) {
            if (ship.isRemoved() || !ship.getBoundingBox().intersects(query)) continue;
            Vec3 pos = ship.position();
            for (ShipStructure.Box b : ship.structure().boxes(ship.quadrant())) {
                AABB world = b.box().move(pos);
                if (!world.intersects(query)) continue;
                if (out == null) out = new ArrayList<>(original);
                out.add(Shapes.create(world));
            }
        }
        return out == null ? original : out;
    }

    /** True if the entity is close to any ship (used to stop "flying is not enabled" kicks on decks). */
    public static boolean nearShip(Entity entity) {
        AABB box = entity.getBoundingBox().inflate(1.0, 2.0, 1.0);
        for (AirshipEntity ship : ships(entity.level())) {
            if (!ship.isRemoved() && ship.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    /** Is the entity standing on this ship's deck (feet resting on a ship block)? */
    public static boolean isOnDeck(AirshipEntity ship, Entity entity, Vec3 shipPos, int quadrant) {
        AABB feet = entity.getBoundingBox();
        AABB probe = new AABB(feet.minX + 0.01, feet.minY - 0.3, feet.minZ + 0.01, feet.maxX - 0.01, feet.minY + 0.2, feet.maxZ - 0.01);
        if (!ship.structure().bounds(quadrant).move(shipPos).inflate(0.5).intersects(probe)) return false;
        for (ShipStructure.Box b : ship.structure().boxes(quadrant)) {
            AABB world = b.box().move(shipPos);
            if (world.intersects(probe) && world.maxY <= feet.minY + 0.25) return true;
        }
        return false;
    }

    /** A ray hit on a ship block. */
    public record Hit(AirshipEntity ship, ShipStructure.Box box, double distanceSqr) {}

    @Nullable
    public static Hit raycast(Level level, Vec3 from, Vec3 to) {
        Hit best = null;
        AABB ray = new AABB(from, to);
        for (AirshipEntity ship : ships(level)) {
            if (ship.isRemoved() || !ship.getBoundingBox().intersects(ray)) continue;
            Vec3 pos = ship.position();
            for (ShipStructure.Box b : ship.structure().boxes(ship.quadrant())) {
                Optional<Vec3> clip = b.box().move(pos).clip(from, to);
                if (clip.isEmpty()) continue;
                double d = clip.get().distanceToSqr(from);
                if (best == null || d < best.distanceSqr()) best = new Hit(ship, b, d);
            }
        }
        return best;
    }
}
