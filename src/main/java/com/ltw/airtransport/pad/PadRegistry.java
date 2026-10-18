package com.ltw.airtransport.pad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Every named Pad Beacon (waypoint) in one dimension. */
public final class PadRegistry extends SavedData {
    public record Pad(UUID id, String name, BlockPos pos) {}

    private final Map<UUID, Pad> pads = new LinkedHashMap<>();

    public static PadRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(PadRegistry::new, PadRegistry::load), "ltwairtransport_pads");
    }

    public void put(UUID id, String name, BlockPos pos) {
        pads.put(id, new Pad(id, name, pos.immutable()));
        setDirty();
    }

    public void remove(UUID id) {
        if (pads.remove(id) != null) setDirty();
    }

    @Nullable
    public Pad get(UUID id) {
        return pads.get(id);
    }

    /** All pads, nearest to {@code from} first. */
    public List<Pad> sorted(BlockPos from) {
        List<Pad> list = new ArrayList<>(pads.values());
        list.sort(Comparator.comparingDouble(p -> p.pos().distSqr(from)));
        return list;
    }

    private static PadRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        PadRegistry reg = new PadRegistry();
        for (Tag t : tag.getList("pads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            UUID id = c.getUUID("id");
            BlockPos pos = NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO);
            reg.pads.put(id, new Pad(id, c.getString("name"), pos));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Pad p : pads.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", p.id());
            c.putString("name", p.name());
            c.put("pos", NbtUtils.writeBlockPos(p.pos()));
            list.add(c);
        }
        tag.put("pads", list);
        return tag;
    }
}
