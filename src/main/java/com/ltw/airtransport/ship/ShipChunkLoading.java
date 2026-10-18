package com.ltw.airtransport.ship;

import com.ltw.airtransport.AirTransport;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** Keeps the chunks around autopiloting ships (and parked ships waiting on a route) loaded. */
public final class ShipChunkLoading {
    public static final TicketController CONTROLLER = new TicketController(AirTransport.id("ships"));

    private ShipChunkLoading() {}

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static void forceBlock(ServerLevel level, BlockPos pos, boolean add) {
        CONTROLLER.forceChunk(level, pos, pos.getX() >> 4, pos.getZ() >> 4, add, true);
    }

    /** Swaps the 3x3 chunks a ship holds for the 3x3 around {@code center}. Returns the new set. */
    public static Set<Long> update(ServerLevel level, UUID owner, Set<Long> held, ChunkPos center, boolean wanted) {
        Set<Long> next = new HashSet<>();
        if (wanted) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) next.add(ChunkPos.asLong(center.x + dx, center.z + dz));
            }
        }
        for (long c : held) {
            if (!next.contains(c)) CONTROLLER.forceChunk(level, owner, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
        }
        for (long c : next) {
            if (!held.contains(c)) CONTROLLER.forceChunk(level, owner, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
        }
        return next;
    }
}
