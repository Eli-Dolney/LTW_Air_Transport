package com.ltw.airtransport.ship;

import com.ltw.airtransport.AirConfig;
import com.ltw.airtransport.block.BurnerBlock;
import com.ltw.airtransport.block.BurnerBlockEntity;
import com.ltw.airtransport.block.ControlPanelBlock;
import com.ltw.airtransport.block.ControlPanelBlockEntity;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.pad.PadRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Turns placed blocks into a flying {@link AirshipEntity} and back. */
public final class ShipAssembler {
    /** Set blocks quietly: tell clients, but no neighbor updates, shape updates or drops. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_MOVE_BY_PISTON;

    private ShipAssembler() {}

    /** Everything connected to {@code start}, or an error explaining why it can't be a ship. */
    public record Scan(@Nullable Set<BlockPos> positions, @Nullable BlockPos panel, @Nullable Component error) {}

    public static Scan scan(Level level, BlockPos start) {
        int max = AirConfig.MAX_SHIP_BLOCKS.get();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos panel = null;
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ControlPanelBlock) {
                if (panel != null && !panel.equals(pos)) {
                    return new Scan(null, null, Component.translatable("message.ltwairtransport.two_panels"));
                }
                panel = pos;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (seen.contains(next) || !canInclude(level, next)) continue;
                seen.add(next);
                if (seen.size() > max) {
                    return new Scan(null, null, Component.translatable("message.ltwairtransport.too_big", max));
                }
                queue.add(next);
            }
        }
        if (panel == null) return new Scan(null, null, Component.translatable("message.ltwairtransport.no_panel"));
        return new Scan(seen, panel, null);
    }

    private static boolean canInclude(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false; // bedrock, barriers, ...
        return !ShipStructure.isShipStopper(state);
    }

    /** Assembles the ship whose Control Panel is at {@code panelPos}. Errors go to {@code feedback}. */
    @Nullable
    public static AirshipEntity assemble(ServerLevel level, BlockPos panelPos, @Nullable ServerPlayer feedback) {
        Scan scan = scan(level, panelPos);
        if (scan.error() != null) {
            tell(feedback, scan.error());
            return null;
        }
        Set<BlockPos> positions = scan.positions();
        HolderLookup.Provider registries = level.registryAccess();

        ShipStructure structure = new ShipStructure();
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            structure.put(pos.subtract(panelPos), level.getBlockState(pos), be == null ? null : be.saveWithFullMetadata(registries));
        }
        structure.recompute();

        Component problem = checkFlightReady(level, structure, positions);
        if (problem != null) {
            tell(feedback, problem);
            return null;
        }

        // Clear block entities first so nothing spills its inventory, then remove the blocks quietly.
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container c) c.clearContent();
            if (be != null) level.removeBlockEntity(pos);
        }
        for (BlockPos pos : positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);

        AirshipEntity ship = new AirshipEntity(level, structure);
        ship.setPos(panelPos.getX() + 0.5, panelPos.getY(), panelPos.getZ() + 0.5);
        level.addFreshEntity(ship);
        level.playSound(null, panelPos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 0.7f);
        return ship;
    }

    /** What the Control Panel shows for a parked ship. */
    public record Preflight(@Nullable Component problem, double liftRatio, int blocks, int envelopes, int burners) {}

    public static Preflight preflight(ServerLevel level, BlockPos panelPos) {
        Scan scan = scan(level, panelPos);
        if (scan.error() != null) return new Preflight(scan.error(), 0, 0, 0, 0);
        ShipStructure s = new ShipStructure();
        for (BlockPos pos : scan.positions()) s.put(pos.subtract(panelPos), level.getBlockState(pos), null);
        s.recompute();
        double lift = ShipFlight.liftCapacity(s.envelopes(), fueledBurners(level, s, scan.positions()));
        return new Preflight(checkFlightReady(level, s, scan.positions()), s.weight() <= 0 ? 0 : lift / s.weight(),
            s.size(), s.envelopes(), s.burners());
    }

    /** Null when the ship has what it needs to fly, otherwise the reason it can't. */
    @Nullable
    private static Component checkFlightReady(ServerLevel level, ShipStructure s, Set<BlockPos> positions) {
        if (s.envelopes() == 0) return Component.translatable("message.ltwairtransport.no_envelope");
        if (s.burners() == 0) return Component.translatable("message.ltwairtransport.no_burner");
        int fueledBurners = fueledBurners(level, s, positions);
        if (fueledBurners == 0) return Component.translatable("message.ltwairtransport.no_fuel");
        double lift = ShipFlight.liftCapacity(s.envelopes(), fueledBurners);
        if (lift < s.weight()) {
            return Component.translatable("message.ltwairtransport.too_heavy", (int) lift, (int) Math.ceil(s.weight()));
        }
        return null;
    }

    /** Burners that will light: those with their own fuel, or all of them if the cargo holds fuel. */
    private static int fueledBurners(ServerLevel level, ShipStructure s, Set<BlockPos> positions) {
        boolean cargoFuel = false;
        int fueledBurners = 0;
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BurnerBlockEntity burner) {
                if (burner.fuelTicks() > 0) fueledBurners++;
            } else if (be instanceof Container c && !cargoFuel) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (BurnerBlockEntity.fuelValue(c.getItem(i)) > 0) {
                        cargoFuel = true;
                        break;
                    }
                }
            }
        }
        return cargoFuel ? s.burners() : fueledBurners;
    }

    /** Assembles the ship at {@code panelPos} and sends it to {@code pad} on autopilot. */
    public static boolean launch(ServerLevel level, BlockPos panelPos, PadRegistry.Pad pad, @Nullable ServerPlayer feedback) {
        if (pad.pos().distSqr(panelPos) < 9 * 9) {
            tell(feedback, Component.translatable("message.ltwairtransport.already_there"));
            return false;
        }
        AirshipEntity ship = assemble(level, panelPos, feedback);
        if (ship == null) return false;
        ship.flyTo(pad);
        return true;
    }

    /** Right-clicking a parked Captain's Chair: assemble the ship and sit down at the helm. */
    public static void boardFromChair(ServerLevel level, BlockPos chairPos, ServerPlayer player) {
        Scan scan = scan(level, chairPos);
        if (scan.error() != null) {
            tell(player, scan.error());
            return;
        }
        BlockPos panel = scan.panel();
        AirshipEntity ship = assemble(level, panel, player);
        if (ship == null) return;
        ship.seat(player, chairPos.subtract(panel));
        tell(player, Component.translatable("message.ltwairtransport.boarded"));
    }

    /**
     * Turns the ship back into blocks at its current (grid-snapped) position.
     * Returns false and leaves the ship flying if anything is in the way.
     */
    public static boolean disassemble(AirshipEntity ship) {
        if (!(ship.level() instanceof ServerLevel level)) return false;
        ShipStructure structure = ship.structure();
        int quadrant = ship.quadrant();
        Rotation rot = ShipStructure.rotation(quadrant);
        BlockPos base = BlockPos.containing(ship.getX(), Math.ceil(ship.getY() - 0.1), ship.getZ());

        for (BlockPos local : structure.blocks().keySet()) {
            BlockPos target = base.offset(local.rotate(rot));
            if (level.isOutsideBuildHeight(target)) return false;
            BlockState existing = level.getBlockState(target);
            if (!existing.isAir() && !existing.canBeReplaced()) return false;
        }

        ship.syncBlockEntities();
        HolderLookup.Provider registries = level.registryAccess();
        List<Entity> riders = new ArrayList<>(ship.getPassengers());
        List<Vec3> riderSpots = new ArrayList<>();
        for (Entity rider : riders) {
            BlockPos seat = ship.seatOf(rider);
            BlockPos spot = seat == null ? base : base.offset(seat.rotate(rot));
            riderSpots.add(new Vec3(spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5));
        }
        ship.ejectPassengers();

        for (Map.Entry<BlockPos, BlockState> e : structure.blocks().entrySet()) {
            BlockPos target = base.offset(e.getKey().rotate(rot));
            BlockState state = e.getValue().rotate(rot);
            if (state.getBlock() instanceof BurnerBlock) state = state.setValue(BurnerBlock.LIT, false);
            level.setBlock(target, state, QUIET);
        }
        for (Map.Entry<BlockPos, CompoundTag> e : structure.blockEntities().entrySet()) {
            BlockPos target = base.offset(e.getKey().rotate(rot));
            BlockEntity be = level.getBlockEntity(target);
            if (be == null) continue;
            CompoundTag tag = e.getValue().copy();
            tag.putInt("x", target.getX());
            tag.putInt("y", target.getY());
            tag.putInt("z", target.getZ());
            be.loadWithComponents(tag, registries);
            be.setChanged();
        }
        for (int i = 0; i < riders.size(); i++) {
            Vec3 spot = riderSpots.get(i);
            riders.get(i).teleportTo(spot.x, spot.y, spot.z);
        }

        BlockPos panelPos = base;
        if (level.getBlockEntity(panelPos) instanceof ControlPanelBlockEntity panel) {
            ShipRegistry.get(level.getServer()).update(panel.shipId(), panel.shipName(), level, panelPos, "Parked", null);
            PadRegistry.Pad pad = ship.arrivalPad();
            if (pad != null) panel.arrivedAt(pad.id());
        }
        level.playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        ship.discardAfterLanding();
        return true;
    }

    private static void tell(@Nullable ServerPlayer player, Component message) {
        if (player != null) player.displayClientMessage(message, false);
    }
}
