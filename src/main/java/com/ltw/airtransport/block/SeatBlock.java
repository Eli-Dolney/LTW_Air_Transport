package com.ltw.airtransport.block;

import com.ltw.airtransport.ship.ShipAssembler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A seat on a ship. The Captain's Chair is the helm: right-clicking it on a parked ship
 * turns the ship into a flying airship and sits you down as captain.
 */
public class SeatBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SeatBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        propertiesCodec(), Codec.BOOL.fieldOf("captain").forGetter(SeatBlock::isCaptain)).apply(i, SeatBlock::new));
    private static final VoxelShape SEAT = Shapes.or(Block.box(1, 0, 1, 15, 8, 15), Block.box(1, 8, 12, 15, 16, 15));
    private final boolean captain;

    public SeatBlock(Properties properties, boolean captain) {
        super(properties);
        this.captain = captain;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean isCaptain() {
        return captain;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** FACING is the way a seated player looks. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SEAT;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!captain) {
            player.displayClientMessage(Component.translatable("message.ltwairtransport.seat_parked"), true);
            return InteractionResult.CONSUME;
        }
        ShipAssembler.boardFromChair((ServerLevel) level, pos, (ServerPlayer) player);
        return InteractionResult.CONSUME;
    }
}
