package com.ltw.airtransport.block;

import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.pad.PadRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PadBeaconBlockEntity extends BlockEntity {
    private UUID padId = UUID.randomUUID();
    private String name = "";

    public PadBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(AirTransport.PAD_BEACON_BE.get(), pos, state);
    }

    public UUID padId() {
        return padId;
    }

    public String name() {
        return name.isBlank() ? "Pad " + worldPosition.getX() + ", " + worldPosition.getZ() : name;
    }

    public void setName(String name) {
        this.name = name.length() > 32 ? name.substring(0, 32) : name;
        setChanged();
    }

    /** (Re)writes this beacon into the dimension's waypoint list. */
    public void register(ServerLevel level) {
        PadRegistry.get(level).put(padId, name(), worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) register(server);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("padId", padId);
        tag.putString("name", name);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("padId")) padId = tag.getUUID("padId");
        name = tag.getString("name");
    }
}
