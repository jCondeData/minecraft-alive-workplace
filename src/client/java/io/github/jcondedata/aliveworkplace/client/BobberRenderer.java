package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.jcondedata.aliveworkplace.fish.FishingBobber;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** A fisherman's bobber: the vanilla bobber picture, bobbing on the water, and a line back to the rod in their folded arms. */
public class BobberRenderer extends EntityRenderer<FishingBobber> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
	private static final RenderType BOBBER = RenderType.entityCutout(TEXTURE);
	/** Points along the line: it sags a little towards the water. */
	private static final int SEGMENTS = 16;

	public BobberRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(FishingBobber bobber, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		float time = bobber.tickCount + partialTick;
		float bob = bobber.biting() ? -0.2f : Mth.sin(time * 0.12f) * 0.04f;
		poseStack.pushPose();
		poseStack.translate(0, bob, 0);

		poseStack.pushPose();
		poseStack.scale(0.5f, 0.5f, 0.5f);
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		PoseStack.Pose quad = poseStack.last();
		VertexConsumer picture = buffers.getBuffer(BOBBER);
		corner(picture, quad, light, 0, 0, 0, 1);
		corner(picture, quad, light, 1, 0, 1, 1);
		corner(picture, quad, light, 1, 1, 1, 0);
		corner(picture, quad, light, 0, 1, 0, 0);
		poseStack.popPose();

		Villager fisher = bobber.owner();
		if (fisher != null) {
			// The rod sits in the folded arms: in front of the chest, a little to the side.
			float bodyYaw = Mth.lerp(partialTick, fisher.yBodyRotO, fisher.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
			Vec3 side = new Vec3(-forward.z, 0, forward.x);
			Vec3 rodTip = fisher.getPosition(partialTick).add(0, fisher.getBbHeight() * 0.62, 0).add(forward.scale(0.55)).add(side.scale(0.2));
			Vec3 start = bobber.getPosition(partialTick).add(0, bob + 0.1, 0);
			float dx = (float) (rodTip.x - start.x);
			float dy = (float) (rodTip.y - start.y);
			float dz = (float) (rodTip.z - start.z);
			VertexConsumer line = buffers.getBuffer(RenderType.lineStrip());
			PoseStack.Pose pose = poseStack.last();
			for (int i = 0; i <= SEGMENTS; i++) {
				float t = i / (float) SEGMENTS;
				float x = dx * t;
				float y = dy * (t * t + t) * 0.5f + 0.1f; // hangs lower near the water
				float z = dz * t;
				float nt = (i + 1) / (float) SEGMENTS;
				float nx = dx * nt - x;
				float ny = dy * (nt * nt + nt) * 0.5f + 0.1f - y;
				float nz = dz * nt - z;
				float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
				if (length > 0) {
					nx /= length;
					ny /= length;
					nz /= length;
				}
				line.addVertex(pose, x, y, z).setColor(0, 0, 0, 255).setNormal(pose, nx, ny, nz);
			}
		}
		poseStack.popPose();
		super.render(bobber, yaw, partialTick, poseStack, buffers, light);
	}

	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, float y, int u, int v) {
		consumer.addVertex(pose, x - 0.5f, y - 0.5f, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
			.setNormal(pose, 0, 1, 0);
	}

	@Override
	public ResourceLocation getTextureLocation(FishingBobber bobber) {
		return TEXTURE;
	}
}
