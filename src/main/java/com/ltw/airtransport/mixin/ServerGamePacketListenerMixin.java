package com.ltw.airtransport.mixin;

import com.ltw.airtransport.ship.ShipCollision;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standing on a flying deck is not "flying": don't kick players riding on airships. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void ltwairtransport$shipCountsAsGround(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ShipCollision.nearShip(entity)) cir.setReturnValue(false);
    }
}
