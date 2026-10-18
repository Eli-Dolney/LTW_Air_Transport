package com.ltw.airtransport.block;

import com.ltw.airtransport.AirConfig;
import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.pad.PadRegistry;
import com.ltw.airtransport.ship.ShipAssembler;
import com.ltw.airtransport.ship.ShipChunkLoading;
import com.ltw.airtransport.ship.ShipRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ship's brain: its identity (id + name) and its route. This block entity travels with the
 * ship, so the route survives every take-off and landing.
 */
public class ControlPanelBlockEntity extends BlockEntity {
    private UUID shipId = UUID.randomUUID();
    private String shipName = "Airship";
    private final List<UUID> route = new ArrayList<>();
    private int routeIndex;
    private boolean routeActive;
    private boolean loop = true;
    private int waitTicks = -1;
    private boolean chunkTicket;

    public ControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AirTransport.CONTROL_PANEL_BE.get(), pos, state);
    }

    public UUID shipId() { return shipId; }
    public String shipName() { return shipName; }
    public List<UUID> route() { return route; }
    public int routeIndex() { return routeIndex; }
    public boolean routeActive() { return routeActive; }
    public boolean loop() { return loop; }
    public int waitTicks() { return waitTicks; }

    public void setShipName(String name) {
        shipName = name.isBlank() ? "Airship" : (name.length() > 32 ? name.substring(0, 32) : name);
        setChanged();
    }

    public void addStop(UUID pad) {
        if (route.size() < 16) route.add(pad);
        setChanged();
    }

    public void clearRoute() {
        route.clear();
        routeIndex = 0;
        routeActive = false;
        waitTicks = -1;
        setChanged();
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
        setChanged();
    }

    public void startRoute() {
        if (route.isEmpty()) return;
        routeActive = true;
        routeIndex = Math.floorMod(routeIndex, route.size());
        setChanged();
    }

    public void stopRoute() {
        routeActive = false;
        waitTicks = -1;
        setChanged();
    }

    /** The pad the route wants next, or null if no route is running. */
    @Nullable
    public UUID currentStop() {
        return routeActive && !route.isEmpty() ? route.get(Math.floorMod(routeIndex, route.size())) : null;
    }

    /** Called when the ship lands at {@code pad}. Advances the route and starts the wait timer. */
    public void arrivedAt(UUID pad) {
        if (!routeActive || route.isEmpty()) return;
        if (pad.equals(currentStop())) {
            routeIndex++;
            if (routeIndex >= route.size()) {
                if (loop) routeIndex = 0;
                else {
                    routeActive = false;
                    routeIndex = 0;
                }
            }
        }
        waitTicks = routeActive ? AirConfig.ROUTE_WAIT_SECONDS.get() * 20 : -1;
        setChanged();
    }

    /** Ticks only while parked as a real block: counts down the route wait and relaunches. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ControlPanelBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean wantTicket = be.routeActive && AirConfig.CHUNK_LOAD_AUTOPILOT.get();
        if (wantTicket != be.chunkTicket) {
            ShipChunkLoading.forceBlock(server, pos, wantTicket);
            be.chunkTicket = wantTicket;
        }
        if (!be.routeActive) return;
        if (level.getGameTime() % 40 == 0) {
            ShipRegistry.get(server.getServer()).update(be.shipId, be.shipName, level, pos,
                "Waiting " + Math.max(0, be.waitTicks / 20) + "s", null);
        }
        if (be.waitTicks > 0) {
            be.waitTicks--;
            return;
        }
        UUID next = be.currentStop();
        PadRegistry.Pad pad = next == null ? null : PadRegistry.get(server).get(next);
        if (pad == null) {
            be.stopRoute();
            return;
        }
        be.waitTicks = -1;
        if (!ShipAssembler.launch(server, pos, pad, null)) be.waitTicks = 200; // retry later (e.g. out of fuel)
    }

    @Override
    public void setRemoved() {
        if (chunkTicket && level instanceof ServerLevel server) {
            ShipChunkLoading.forceBlock(server, worldPosition, false);
            chunkTicket = false;
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("shipId", shipId);
        tag.putString("shipName", shipName);
        ListTag list = new ListTag();
        for (UUID id : route) list.add(NbtUtils.createUUID(id));
        tag.put("route", list);
        tag.putInt("routeIndex", routeIndex);
        tag.putBoolean("routeActive", routeActive);
        tag.putBoolean("loop", loop);
        tag.putInt("waitTicks", waitTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("shipId")) shipId = tag.getUUID("shipId");
        if (tag.contains("shipName")) shipName = tag.getString("shipName");
        route.clear();
        for (Tag t : tag.getList("route", Tag.TAG_INT_ARRAY)) route.add(NbtUtils.loadUUID(t));
        routeIndex = tag.getInt("routeIndex");
        routeActive = tag.getBoolean("routeActive");
        loop = !tag.contains("loop") || tag.getBoolean("loop");
        waitTicks = tag.contains("waitTicks") ? tag.getInt("waitTicks") : -1;
    }
}
