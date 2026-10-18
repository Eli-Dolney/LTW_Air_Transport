package com.ltw.airtransport.ship;

import com.ltw.airtransport.AirTransport;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ShipTags {
    /** Blocks that are never pulled into a ship (assembly stops at them). */
    public static final TagKey<Block> SHIP_BLACKLIST = TagKey.create(Registries.BLOCK, AirTransport.id("ship_blacklist"));

    private ShipTags() {}
}
