package com.ltw.airtransport.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ltw.airtransport.ship.ShipCollision;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets entities stand on and bump into airship blocks. */
@Mixin(Entity.class)
abstract class EntityCollideMixin {
    @WrapOperation(method = "collide", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> ltwairtransport$shipCollisions(Level level, Entity entity, AABB box, Operation<List<VoxelShape>> original) {
        return ShipCollision.withShipShapes((Entity) (Object) this, box, original.call(level, entity, box));
    }
}
