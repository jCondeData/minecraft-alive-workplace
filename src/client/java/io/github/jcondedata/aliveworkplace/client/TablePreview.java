package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Blueprint Table's 3D preview of the selected build (26.9): the blocks the server sent in a
 * {@link TablePayloads.Preview}, drawn with Minecraft's block renderer into a box of the screen. It turns
 * slowly by itself, can be dragged round (and tilted), and is scaled so the whole build fits the box.
 */
final class TablePreview {
	private static final float TURN_DEGREES_PER_SECOND = 14f;
	private static final long RESUME_AFTER_DRAG_MS = 2500;

	private record Cell(float x, float y, float z, BlockState state) {
	}

	private final List<Cell> cells = new ArrayList<>();
	private final float sizeX, sizeY, sizeZ, scale;
	private float yaw = 225f;
	private float pitch = 28f;
	private long lastFrame = Util.getMillis();
	private long lastDrag;

	TablePreview(TablePayloads.Preview preview) {
		this.sizeX = preview.sizeX();
		this.sizeY = preview.sizeY();
		this.sizeZ = preview.sizeZ();
		this.scale = preview.scale();
		int[] b = preview.blocks();
		List<BlockState> palette = preview.palette().stream().map(Block::stateById).toList();
		for (int i = 0; i + 3 < b.length; i += 4) {
			if (b[i + 3] >= 0 && b[i + 3] < palette.size()) {
				cells.add(new Cell(b[i] * scale, b[i + 1] * scale, b[i + 2] * scale, palette.get(b[i + 3])));
			}
		}
	}

	int count() {
		return cells.size();
	}

	/** Turns the build by a mouse drag (degrees per pixel), and holds the slow turn for a moment. */
	void drag(double dx, double dy) {
		yaw += (float) dx * 1.2f;
		pitch = Math.max(-10f, Math.min(85f, pitch + (float) dy * 0.8f));
		lastDrag = Util.getMillis();
	}

	/** Draws the build centred in the box at x, y (w × h), clipped to it. */
	void render(GuiGraphics g, int x, int y, int w, int h) {
		long now = Util.getMillis();
		if (now - lastDrag > RESUME_AFTER_DRAG_MS) {
			yaw += (now - lastFrame) / 1000f * TURN_DEGREES_PER_SECOND;
		}
		lastFrame = now;
		yaw %= 360f;
		if (cells.isEmpty()) {
			return;
		}
		// Scaled so the build's bounding sphere fits the box, whichever way it is turned.
		float diagonal = (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ);
		float fit = Math.min(w, h) * 0.95f / Math.max(1f, diagonal);
		BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
		g.enableScissor(x, y, x + w, y + h);
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x + w / 2f, y + h / 2f, 150f);
		pose.scale(fit, -fit, fit);
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.translate(-sizeX / 2f, -sizeY / 2f, -sizeZ / 2f);
		Lighting.setupFor3DItems();
		for (Cell c : cells) {
			pose.pushPose();
			pose.translate(c.x(), c.y(), c.z());
			if (scale != 1f) {
				pose.scale(scale, scale, scale);
			}
			blocks.renderSingleBlock(c.state(), pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		g.flush();
		pose.popPose();
		g.disableScissor();
	}
}
