package com.ltw.airtransport.ship;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Last known whereabouts of every airship, for the Ship Tracker. Stored in the overworld. */
public final class ShipRegistry extends SavedData {
    public record Info(String name, ResourceKey<Level> dimension, BlockPos pos, String status, @Nullable BlockPos destination, long gameTime) {}

    private final Map<UUID, Info> ships = new HashMap<>();

    public static ShipRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ShipRegistry::new, ShipRegistry::load), "ltwairtransport_ships");
    }

    public void update(UUID id, String name, Level level, BlockPos pos, String status, @Nullable BlockPos destination) {
        ships.put(id, new Info(name, level.dimension(), pos.immutable(), status, destination, level.getGameTime()));
        setDirty();
    }

    @Nullable
    public Info get(UUID id) {
        return ships.get(id);
    }

    private static ShipRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipRegistry reg = new ShipRegistry();
        CompoundTag all = tag.getCompound("ships");
        for (String key : all.getAllKeys()) {
            CompoundTag c = all.getCompound(key);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("dim")));
            BlockPos dest = c.contains("dest") ? NbtUtils.readBlockPos(c, "dest").orElse(null) : null;
            reg.ships.put(UUID.fromString(key), new Info(c.getString("name"), dim,
                NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO), c.getString("status"), dest, c.getLong("time")));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        ships.forEach((id, info) -> {
            CompoundTag c = new CompoundTag();
            c.putString("name", info.name());
            c.putString("dim", info.dimension().location().toString());
            c.put("pos", NbtUtils.writeBlockPos(info.pos()));
            c.putString("status", info.status());
            if (info.destination() != null) c.put("dest", NbtUtils.writeBlockPos(info.destination()));
            c.putLong("time", info.gameTime());
            all.put(id.toString(), c);
        });
        tag.put("ships", all);
        return tag;
    }
}
