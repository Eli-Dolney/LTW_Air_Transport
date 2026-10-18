package com.ltw.airtransport.client;

import com.ltw.airtransport.block.BurnerBlock;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.ship.ShipStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws every block of the ship (and block entity renderers such as chests), turned with the ship. */
public class AirshipRenderer extends EntityRenderer<AirshipEntity> {
    private final BlockRenderDispatcher blocks;

    public AirshipRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        blocks = ctx.getBlockRenderDispatcher();
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(AirshipEntity ship, Frustum frustum, double x, double y, double z) {
        return ship.shouldRenderAtSqrDistance(ship.distanceToSqr(x, y, z)) && frustum.isVisible(ship.getBoundingBoxForCulling());
    }

    @Override
    public void render(AirshipEntity ship, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        ShipStructure structure = ship.structure();
        if (structure.size() == 0) return;
        float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        Vec3 origin = ship.getPosition(partialTick);
        BlockEntityRenderDispatcher beDispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        Map<BlockPos, BlockEntity> blockEntities = ship.blockEntities();
        boolean burning = ship.isBurning();
        BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        for (Map.Entry<BlockPos, BlockState> e : structure.blocks().entrySet()) {
            BlockPos local = e.getKey();
            BlockState state = e.getValue();
            if (burning && state.getBlock() instanceof BurnerBlock) state = state.setValue(BurnerBlock.LIT, true);
            Vec3 center = origin.add(ShipStructure.rotate(local.getX(), local.getY() + 0.5, local.getZ(), yaw));
            lightPos.set(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
            int light = LevelRenderer.getLightColor(ship.level(), lightPos);
            if (state.getLightEmission(ship.level(), lightPos) > 0) light = LightTexture.FULL_BRIGHT;

            pose.pushPose();
            pose.translate(local.getX() - 0.5, local.getY(), local.getZ() - 0.5);
            if (state.getRenderShape() == RenderShape.MODEL) {
                blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
            BlockEntity be = blockEntities.get(local);
            if (be != null) {
                BlockEntityRenderer<BlockEntity> renderer = beDispatcher.getRenderer(be);
                if (renderer != null) {
                    try {
                        renderer.render(be, partialTick, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                    } catch (RuntimeException ignored) {
                        // Some renderers expect their block to be in the world; skip them rather than crash.
                    }
                }
            }
            pose.popPose();
        }
        pose.popPose();
        super.render(ship, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AirshipEntity ship) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
