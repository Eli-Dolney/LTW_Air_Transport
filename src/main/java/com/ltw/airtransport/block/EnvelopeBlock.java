package com.ltw.airtransport.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;

/** Balloon fabric. Light and gives lift while a burner heats it. Tinted per color on the client. */
public class EnvelopeBlock extends Block {
    private final DyeColor color;

    public EnvelopeBlock(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public DyeColor color() {
        return color;
    }
}
