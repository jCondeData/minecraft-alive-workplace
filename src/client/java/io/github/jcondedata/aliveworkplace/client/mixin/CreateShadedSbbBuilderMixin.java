package io.github.jcondedata.aliveworkplace.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B231 (owner report, 2026-10-19): with shaders on, Flywheel falls back to "off" and Create draws a spinning
 * contraption (the Windmill's sails, ROADMAP 39.4) through Ponder's {@code ShadedBlockSbbBuilder}. That builder only
 * takes whole quads ({@code putBulkData}); Sodium's renderer, for a block whose model another mod wraps (Continuity's
 * connected or emissive textures, as in the Cobbleverse pack), writes the quad one vertex at a time instead, and every
 * per-vertex call throws "ShadedBlockSbbBuilder only supports putBulkData!", crashing the client of anyone near a
 * turning windmill. This hands each per-vertex call to the builder's own {@code BufferBuilder}, as the shaded
 * geometry it collects (Ponder's {@code unwrap(true)}), so the quad is drawn like any other.
 *
 * <p>Create is optional: {@link Pseudo} skips this when Ponder isn't installed. Ponder isn't on our compile classpath,
 * so the target and its methods are named as strings, unremapped: the Mojang names for the dev runtime and the
 * intermediary ones ({@code method_…}) for a real game; each injector matches whichever the runtime has.
 */
@Pseudo
@Mixin(targets = "net.createmod.catnip.render.ShadedBlockSbbBuilder", remap = false)
public abstract class CreateShadedSbbBuilderMixin {
	/** Ponder's: marks what follows as shaded geometry and returns the builder's {@code BufferBuilder}. */
	@Shadow(remap = false)
	public abstract BufferBuilder unwrap(boolean shade);

	@Inject(method = {"addVertex", "method_22912"}, at = @At("HEAD"), cancellable = true, remap = false)
	private void aliveworkplace$addVertex(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(unwrap(true).addVertex(x, y, z));
	}

	@Inject(method = {"setColor", "method_1336"}, at = @At("HEAD"), cancellable = true, remap = false)
	private void aliveworkplace$setColor(int red, int green, int blue, int alpha, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(unwrap(true).setColor(red, green, blue, alpha));
	}

	@Inject(method = {"setUv", "method_22913"}, at = @At("HEAD"), cancellable = true, remap = false)
	private void aliveworkplace$setUv(float u, float v, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(unwrap(true).setUv(u, v));
	}

	@Inject(method = {"setUv1", "method_60796"}, at = @At("HEAD"), cancellable = true, remap = false)
	private void aliveworkplace$setUv1(int u, int v, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(unwrap(true).setUv1(u, v));
	}

	@Inject(method = {"setUv2", "method_22921"}, at = @At("HEAD"), cancellable = true, remap = false)
	private void aliveworkplace$setUv2(int u, int v, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(unwrap(true).setUv2(u, v));
	}

	@Inject(method = {"setNormal", "method_22914"}, at = @At("HEAD"), cancellable = true, remap = false)
	private void aliveworkplace$setNormal(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(unwrap(true).setNormal(x, y, z));
	}
}
