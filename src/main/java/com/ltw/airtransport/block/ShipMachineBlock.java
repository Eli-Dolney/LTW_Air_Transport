package com.ltw.airtransport.block;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * A block that only does something while its ship is flying (Harvester, Kelp Dryer). The ship
 * runs it; in the world it is just a block.
 */
public class ShipMachineBlock extends Block {
    private final String tooltip;

    public ShipMachineBlock(Properties properties, String tooltip) {
        super(properties);
        this.tooltip = tooltip;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
    }
}
