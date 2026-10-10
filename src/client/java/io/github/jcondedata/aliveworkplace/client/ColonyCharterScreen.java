package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/**
 * The Colony Charter's map (ROADMAP 33.8): the 2,048 blocks round the mother village's hall, north up, drawn like a
 * vanilla map where the server had the land loaded and as plain parchment elsewhere; a banner for every village with
 * a hall (the mother village's red, in the middle), the ring a colony may go in (two dashed circles, 256 and 1,024
 * blocks out), a small dashed circle round every other hall (128 blocks) and a red cross on the spot chosen. Pointing
 * at the map says how far and which way the place is and whether a colony can go there; a click asks the server to
 * choose it ({@link Colonies.Choose}), which answers with the spot and a line of text. The sums are {@link ColonyMap}'s.
 */
public class ColonyCharterScreen extends Screen {
	private static final ResourceLocation MAP_TEXTURE = AliveWorkplace.id("colony_charter_map");
	public static final ResourceLocation MARKS = AliveWorkplace.id("textures/gui/colony_map_marks.png");
	public static final ResourceLocation PARCHMENT = AliveWorkplace.id("textures/gui/colony_map_parchment.png");
	public static final ResourceLocation RING = AliveWorkplace.id("textures/gui/colony_map_ring.png");
	private static final int PAD = 6;
	private static final int FRAME_BG = 0xE0101418;
	private static final int FRAME_EDGE = 0xFF3A4C66;

	private final boolean mainHand;
	private final BlockPos hall;
	private final Component village;
	private final byte[] colors;
	private final List<Colonies.Mark> marks;
	private Optional<BlockPos> spot;
	private Component message;
	private boolean messageGood = true;
	private DynamicTexture texture;
	private int mapX;
	private int mapY;
	private int mapSize;

	public ColonyCharterScreen(Colonies.Open open) {
		super(Component.translatable("screen.aliveworkplace.colony_map.title", open.village()));
		this.mainHand = open.mainHand();
		this.hall = open.hall();
		this.village = open.village();
		this.colors = open.colors();
		this.marks = open.marks();
		this.spot = open.spot();
	}

	/** The server's answer to a click: the spot now and what to say under the map. */
	public void answer(Colonies.Answer answer) {
		spot = answer.spot();
		message = answer.message();
		messageGood = answer.ok();
	}

	public Optional<BlockPos> spot() {
		return spot;
	}

	public BlockPos hall() {
		return hall;
	}

	/** Where the map is drawn: left, top and size in screen pixels (the scene and tests point at it). */
	public int[] mapBox() {
		return new int[] {mapX, mapY, mapSize};
	}

	@Override
	protected void init() {
		int room = Math.min(width - 2 * PAD, height - 2 * PAD - 14 - 26 - 22);
		mapSize = Math.max(96, Math.min(2 * ColonyMap.PIXELS, room));
		mapX = (width - mapSize) / 2;
		mapY = PAD + 14;
		addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.colony_map.done"), b -> onClose())
			.bounds(width / 2 - 40, mapY + mapSize + 28, 80, 18).build());
		if (texture == null) {
			NativeImage image = new NativeImage(ColonyMap.PIXELS, ColonyMap.PIXELS, true);
			for (int z = 0; z < ColonyMap.PIXELS; z++) {
				for (int x = 0; x < ColonyMap.PIXELS; x++) {
					int packed = colors.length == ColonyMap.PIXELS * ColonyMap.PIXELS ? colors[x + z * ColonyMap.PIXELS] & 0xFF : 0;
					// land the server didn't have loaded stays clear: the parchment under the map shows there
					image.setPixelRGBA(x, z, packed == 0 ? 0 : MapColor.getColorFromPackedId(packed) | 0xFF000000);
				}
			}
			texture = new DynamicTexture(image);
			minecraft.getTextureManager().register(MAP_TEXTURE, texture);
		}
	}

	@Override
	public void removed() {
		if (texture != null) {
			minecraft.getTextureManager().release(MAP_TEXTURE);
			texture = null;
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private boolean overMap(double mx, double my) {
		return mx >= mapX && my >= mapY && mx < mapX + mapSize && my < mapY + mapSize;
	}

	private int screenX(int worldX) {
		return (int) Math.floor(ColonyMap.toScreen(hall.getX(), worldX, mapX, mapSize));
	}

	private int screenY(int worldZ) {
		return (int) Math.floor(ColonyMap.toScreen(hall.getZ(), worldZ, mapY, mapSize));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && overMap(mx, my)) {
			int x = ColonyMap.toWorld(hall.getX(), mx, mapX, mapSize);
			int z = ColonyMap.toWorld(hall.getZ(), my, mapY, mapSize);
			if (ClientPlayNetworking.canSend(Colonies.Choose.TYPE)) {
				ClientPlayNetworking.send(new Colonies.Choose(mainHand, x, z));
			}
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		g.fill(mapX - 3, mapY - 3, mapX + mapSize + 3, mapY + mapSize + 3, FRAME_BG);
		g.renderOutline(mapX - 3, mapY - 3, mapSize + 6, mapSize + 6, FRAME_EDGE);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		line(g, title, mapY - 12, 0xFFFFFFFF);
		RenderSystem.enableBlend();
		g.blit(PARCHMENT, mapX, mapY, 0f, 0f, mapSize, mapSize, 16, 16); // tiled under the whole map
		g.blit(MAP_TEXTURE, mapX, mapY, mapSize, mapSize, 0f, 0f, ColonyMap.PIXELS, ColonyMap.PIXELS, ColonyMap.PIXELS, ColonyMap.PIXELS);
		g.blit(RING, mapX, mapY, mapSize, mapSize, 0f, 0f, 128, 128, 128, 128);
		g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
		int circle = Math.round(17f * mapSize / ColonyMap.PIXELS);
		for (Colonies.Mark m : marks) { // no colony within 128 blocks of another hall
			g.blit(MARKS, screenX(m.hall().getX()) - circle / 2, screenY(m.hall().getZ()) - circle / 2, circle, circle, 32f, 0f, 17, 17, 64, 32);
		}
		for (Colonies.Mark m : marks) {
			g.blit(MARKS, screenX(m.hall().getX()) - 2, screenY(m.hall().getZ()) - 7, 8, 8, 8f, 0f, 8, 8, 64, 32);
		}
		g.blit(MARKS, screenX(hall.getX()) - 2, screenY(hall.getZ()) - 7, 8, 8, 0f, 0f, 8, 8, 64, 32);
		spot.ifPresent(p -> g.blit(MARKS, screenX(p.getX()) - 3, screenY(p.getZ()) - 3, 8, 8, 16f, 0f, 8, 8, 64, 32));
		g.disableScissor();
		RenderSystem.disableBlend();

		Component status = message != null ? message
			: spot.isPresent() ? Component.translatable("screen.aliveworkplace.colony_map.spot", VillageHallScreen.where(hall, spot.get()), village)
			: Component.translatable("screen.aliveworkplace.colony_map.hint");
		int statusColor = message != null ? (messageGood ? 0xFF7CFC7C : 0xFFFF7070) : spot.isPresent() ? 0xFFFF7070 : 0xFFFFFFFF;
		line(g, status, mapY + mapSize + 6, statusColor);
		line(g, Component.translatable("screen.aliveworkplace.colony_map.legend", ColonyMap.MIN, ColonyMap.MAX, ColonyMap.CLEAR), mapY + mapSize + 17, 0xFFA0A0A0);

		if (overMap(mouseX, mouseY)) {
			g.renderComponentTooltip(font, hover(mouseX, mouseY), mouseX, mouseY);
		}
	}

	/** What pointing at the map says: a village's name on its banner, otherwise how far and which way, and whether a colony can go there. */
	private List<Component> hover(int mouseX, int mouseY) {
		List<Component> out = new ArrayList<>();
		if (Math.abs(mouseX - screenX(hall.getX())) <= 4 && Math.abs(mouseY - screenY(hall.getZ()) + 3) <= 5) {
			out.add(village);
			return out;
		}
		for (Colonies.Mark m : marks) {
			if (Math.abs(mouseX - screenX(m.hall().getX())) <= 4 && Math.abs(mouseY - screenY(m.hall().getZ()) + 3) <= 5) {
				out.add(m.name());
				return out;
			}
		}
		int x = ColonyMap.toWorld(hall.getX(), mouseX, mapX, mapSize);
		int z = ColonyMap.toWorld(hall.getZ(), mouseY, mapY, mapSize);
		out.add(VillageHallScreen.where(hall, new BlockPos(x, hall.getY(), z)));
		ColonyMap.Verdict verdict = ColonyMap.verdict(hall, x, z, marks.stream().map(Colonies.Mark::hall).toList());
		out.add(switch (verdict) {
			case OK -> Component.translatable("screen.aliveworkplace.colony_map.ok").withStyle(ChatFormatting.GREEN);
			case TOO_NEAR -> Component.translatable("screen.aliveworkplace.colony_map.too_near", village).withStyle(ChatFormatting.RED);
			case TOO_FAR -> Component.translatable("screen.aliveworkplace.colony_map.too_far", village).withStyle(ChatFormatting.RED);
			case NEAR_HALL -> Component.translatable("screen.aliveworkplace.colony_map.near_hall").withStyle(ChatFormatting.RED);
		});
		return out;
	}

	/** A line of text centred on the screen at {@code y}, shrunk to fit its width if it must be. */
	private void line(GuiGraphics g, Component text, int y, int color) {
		int w = font.width(text);
		int room = width - 8;
		if (w <= room) {
			g.drawCenteredString(font, text, width / 2, y, color);
			return;
		}
		float scale = room / (float) w;
		g.pose().pushPose();
		g.pose().translate(width / 2f, y, 0);
		g.pose().scale(scale, scale, 1f);
		g.drawCenteredString(font, text, 0, 0, color);
		g.pose().popPose();
	}
}
