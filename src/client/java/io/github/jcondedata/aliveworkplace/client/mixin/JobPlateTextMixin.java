package io.github.jcondedata.aliveworkplace.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jcondedata.aliveworkplace.work.JobPlate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A working villager's name plate reads its job and level ("Fisherman, Journeyman", or "Dara — Fisherman, Journeyman"
 * with a name of its own; B221). Composed as it's drawn: the villager's saved name is never touched.
 */
@Mixin(EntityRenderer.class)
abstract class JobPlateTextMixin {
	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"), index = 1)
	private Component aliveworkplace$jobPlate(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
		return JobPlate.plate(entity, name);
	}
}
