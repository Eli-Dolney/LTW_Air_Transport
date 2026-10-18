package com.ltw.airtransport.item;

import com.ltw.airtransport.block.ControlPanelBlockEntity;
import com.ltw.airtransport.ship.ShipRegistry;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * GPS for one airship. Right-click a Control Panel (parked or flying) to link it, then hold it to see
 * where the ship is, what it is doing and how far away it is.
 */
public class ShipTrackerItem extends Item {
    public ShipTrackerItem(Properties properties) {
        super(properties);
    }

    public static void link(ItemStack stack, ControlPanelBlockEntity panel, Player player) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ship", panel.shipId());
        tag.putString("name", panel.shipName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.displayClientMessage(Component.translatable("message.ltwairtransport.tracker_linked", panel.shipName()), true);
    }

    @Nullable
    private static UUID linked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID("ship") ? tag.getUUID("ship") : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof ControlPanelBlockEntity panel && ctx.getPlayer() != null) {
            if (!ctx.getLevel().isClientSide) link(ctx.getItemInHand(), panel, ctx.getPlayer());
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) report(stack, sp, false);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (selected && entity instanceof ServerPlayer player && level.getGameTime() % 20 == 0) report(stack, player, true);
    }

    private static void report(ItemStack stack, ServerPlayer player, boolean actionBar) {
        UUID id = linked(stack);
        if (id == null) {
            if (!actionBar) player.displayClientMessage(Component.translatable("message.ltwairtransport.tracker_unlinked"), false);
            return;
        }
        ShipRegistry.Info info = ShipRegistry.get(player.getServer()).get(id);
        if (info == null) {
            player.displayClientMessage(Component.translatable("message.ltwairtransport.tracker_unknown"), actionBar);
            return;
        }
        Component where;
        if (!info.dimension().equals(player.level().dimension())) {
            where = Component.translatable("message.ltwairtransport.tracker_other_dim", info.dimension().location().getPath());
        } else {
            BlockPos p = info.pos();
            double dx = p.getX() + 0.5 - player.getX(), dz = p.getZ() + 0.5 - player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            where = Component.translatable("message.ltwairtransport.tracker_where", dist, compass(dx, dz), p.getX(), p.getY(), p.getZ());
        }
        player.displayClientMessage(Component.literal(info.name() + ": ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(info.status() + " - ").withStyle(ChatFormatting.WHITE))
            .append(where), actionBar);
    }

    private static String compass(double dx, double dz) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return dirs[Math.floorMod(Math.round((float) (yaw / 45.0)), 8)];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("name")) {
            tooltip.add(Component.translatable("tooltip.ltwairtransport.tracker_linked", data.copyTag().getString("name")).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.ltwairtransport.tracker_hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
