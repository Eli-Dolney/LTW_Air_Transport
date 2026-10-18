package com.ltw.airtransport.client;

import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.block.EnvelopeBlock;
import com.ltw.airtransport.entity.AirshipEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AirTransport.ID, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping HELM_KEY = new KeyMapping("key.ltwairtransport.helm", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H, "key.categories.ltwairtransport");

    private ClientSetup() {}

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        AirshipEntity.localPlayer = level -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.level() == level ? mc.player : null;
        };
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AirTransport.AIRSHIP.get(), AirshipRenderer::new);
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(AirTransport.BURNER_MENU.get(), BurnerScreen::new);
    }

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent event) {
        event.register(HELM_KEY);
    }

    @SubscribeEvent
    static void blockColors(RegisterColorHandlersEvent.Block event) {
        Block[] envelopes = AirTransport.ENVELOPES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new);
        event.register((state, level, pos, tint) -> tint == 0 && state.getBlock() instanceof EnvelopeBlock env
            ? env.color().getTextureDiffuseColor() | 0xFF000000 : -1, envelopes);
    }

    @SubscribeEvent
    static void itemColors(RegisterColorHandlersEvent.Item event) {
        AirTransport.ENVELOPE_ITEMS.values().forEach(item -> event.register((stack, tint) ->
            tint == 0 && stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof EnvelopeBlock env
                ? env.color().getTextureDiffuseColor() | 0xFF000000 : -1, item.get()));
    }
}
