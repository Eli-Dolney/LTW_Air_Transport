package com.ltw.airtransport.client;

import com.ltw.airtransport.block.BurnerBlock;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.ship.ShipStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws every block of the ship, the water in its tanks, and block entity renderers such as chests, turned with the ship. */
public class AirshipRenderer extends EntityRenderer<AirshipEntity> {
    private final BlockRenderDispatcher blocks;
    private final ShipFluidView fluids = new ShipFluidView();

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
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                fluids.set(ship, structure, origin, light);
                blocks.renderLiquid(local, fluids, new PosedConsumer(buffers.getBuffer(Sheets.translucentCullBlockSheet()), pose.last(), local), state, fluid);
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

    /**
     * The ship's blocks seen from ship-local positions, so the vanilla liquid renderer can find which
     * faces of tank water touch other water and which are open. Tint and light come from the world
     * where the ship is.
     */
    private static final class ShipFluidView implements BlockAndTintGetter {
        private Level level;
        private ShipStructure structure;
        private BlockPos worldPos = BlockPos.ZERO;
        private int light;

        void set(AirshipEntity ship, ShipStructure structure, Vec3 origin, int light) {
            this.level = ship.level();
            this.structure = structure;
            this.worldPos = BlockPos.containing(origin);
            this.light = light;
        }

        @Override
        public BlockState getBlockState(BlockPos local) {
            return structure.blocks().getOrDefault(local, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos local) {
            return getBlockState(local).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos local) {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos local) {
            return layer == LightLayer.SKY ? LightTexture.sky(light) : LightTexture.block(light);
        }

        @Override
        public float getShade(Direction dir, boolean shade) {
            return level.getShade(dir, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos local, ColorResolver resolver) {
            return level.getBlockTint(worldPos, resolver);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }

    /**
     * The liquid renderer writes chunk-section coordinates without a pose; this moves them back to the
     * block's corner, applies the ship's pose and adds the overlay the entity vertex format needs.
     */
    private record PosedConsumer(VertexConsumer out, PoseStack.Pose pose, float dx, float dy, float dz) implements VertexConsumer {
        PosedConsumer(VertexConsumer out, PoseStack.Pose pose, BlockPos local) {
            this(out, pose, local.getX() & 15, local.getY() & 15, local.getZ() & 15);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(pose, x - dx, y - dy, z - dz).setOverlay(OverlayTexture.NO_OVERLAY);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(pose, x, y, z);
            return this;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AirshipEntity ship) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
