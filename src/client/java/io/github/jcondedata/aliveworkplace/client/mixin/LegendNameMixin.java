package io.github.jcondedata.aliveworkplace.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jcondedata.aliveworkplace.client.LegendLookLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A Legend's name over their head is gold (29.4). */
@Mixin(EntityRenderer.class)
abstract class LegendNameMixin {
	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"), index = 1)
	private Component aliveworkplace$gold(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
		return LegendLookLayer.isLegend(entity) ? name.copy().withStyle(ChatFormatting.GOLD) : name;
	}
}
