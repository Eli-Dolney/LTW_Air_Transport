package com.ltw.airtransport.client;

import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.network.AirNetwork;
import com.ltw.airtransport.ship.ShipCollision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Captain controls and clicking on the blocks of a flying ship. */
@EventBusSubscriber(modid = AirTransport.ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static float lastForward, lastVertical;
    private static int lastTurn;
    private static int sinceSent;

    private ClientEvents() {}

    @SubscribeEvent
    static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() && !event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        Vec3 from = player.getEyePosition();
        double range = player.blockInteractionRange();
        Vec3 to = from.add(player.getViewVector(1f).scale(range));
        ShipCollision.Hit hit = ShipCollision.raycast(mc.level, from, to);
        if (hit == null) return;
        HitResult vanilla = mc.hitResult;
        if (vanilla != null && vanilla.getType() != HitResult.Type.MISS
            && vanilla.getLocation().distanceToSqr(from) < hit.distanceSqr()) return;
        event.setCanceled(true);
        if (event.isUseItem()) {
            event.setSwingHand(true);
            PacketDistributor.sendToServer(new AirNetwork.ShipInteract(hit.ship().getId(), hit.box().local()));
        } else {
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        while (ClientSetup.HELM_KEY.consumeClick()) {
            if (player.getVehicle() instanceof AirshipEntity) PacketDistributor.sendToServer(new AirNetwork.OpenHelm());
        }
        if (!(player.getVehicle() instanceof AirshipEntity ship) || mc.screen != null) return;
        BlockPos seat = ship.seatOf(player);
        if (seat == null || !ship.structure().isCaptainSeat(seat)) return;

        float forward = Math.signum(player.input.forwardImpulse);
        float left = player.input.leftImpulse;
        int turn = left > 0 ? -1 : left < 0 ? 1 : 0;
        float vertical = player.input.jumping ? 1 : mc.options.keySprint.isDown() ? -1 : 0;
        sinceSent++;
        boolean changed = forward != lastForward || turn != lastTurn || vertical != lastVertical;
        boolean active = forward != 0 || turn != 0 || vertical != 0;
        if (changed || (active && sinceSent >= 5)) {
            PacketDistributor.sendToServer(new AirNetwork.CaptainInput(ship.getId(), forward, 0, turn, vertical));
            lastForward = forward;
            lastTurn = turn;
            lastVertical = vertical;
            sinceSent = 0;
        }
    }
}
