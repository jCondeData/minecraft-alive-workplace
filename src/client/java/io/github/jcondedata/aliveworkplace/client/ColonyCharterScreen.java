package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
 * Drawn in Copperline (44.3, {@link CityPlanLayout#charter}): the map at the stage's left, the spot and the ring in the
 * column at its right, Done the one copper button, and the whisper line.
 */
public class ColonyCharterScreen extends Screen {
	private static final ResourceLocation MAP_TEXTURE = AliveWorkplace.id("colony_charter_map");
	public static final ResourceLocation MARKS = AliveWorkplace.id("textures/gui/colony_map_marks.png");
	public static final ResourceLocation PARCHMENT = AliveWorkplace.id("textures/gui/colony_map_parchment.png");
	public static final ResourceLocation RING = AliveWorkplace.id("textures/gui/colony_map_ring.png");

	private final boolean mainHand;
	private final BlockPos hall;
	private final Component village;
	private final byte[] colors;
	private final List<Colonies.Mark> marks;
	private Optional<BlockPos> spot;
	private Component message;
	private boolean messageGood = true;
	private DynamicTexture texture;
	private CityPlanLayout.Charter boxes;
	private Button doneButton;
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

	/** The boxes the screen draws in and takes its clicks with. */
	public CityPlanLayout.Charter boxes() {
		return boxes;
	}

	@Override
	protected void init() {
		Component done = Component.translatable("screen.aliveworkplace.colony_map.done");
		boxes = CityPlanLayout.charter(width, height, font.width(title), Math.max(60, font.width(done) + 16));
		mapX = boxes.map()[0];
		mapY = boxes.map()[1];
		mapSize = boxes.map()[2];
		int[] d = boxes.done();
		// The widget takes the click; render draws it in Copperline (addWidget: the game doesn't draw it itself).
		doneButton = addWidget(Button.builder(done, b -> onClose()).bounds(d[0], d[1], d[2], d[3]).build());
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
		if (button == 0 && CopperLayout.inside(boxes.close(), mx, my)) {
			// The lintel's close X (Copperline, 47.1): as Esc.
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
			onClose();
			return true;
		}
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
		int[] win = boxes.win();
		CopperStyle.window(g, win[0], win[1], win[2], win[3]);
		g.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, CopperStyle.VOID);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Component whisper = Component.translatable("screen.aliveworkplace.colony_map.hint");
		boolean overClose = CopperLayout.inside(boxes.close(), mouseX, mouseY);
		int[] plaque = CopperStyle.plaque(g, font, title, boxes.win());
		CopperStyle.lintel(g, font, boxes.win(), plaque, Component.translatable("screen.aliveworkplace.colony_map.lintel", ColonyMap.MIN, ColonyMap.MAX), overClose);
		if (overClose) {
			whisper = Component.translatable("screen.aliveworkplace.colony_map.whisper.close");
		}

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
		// north, as on the Hall's map
		String north = Component.translatable("screen.aliveworkplace.hall.map.north").getString();
		g.fill(mapX + mapSize - font.width(north) - 6, mapY + 2, mapX + mapSize - 2, mapY + 13, 0xC0000000);
		g.drawString(font, north, mapX + mapSize - font.width(north) - 4, mapY + 4, CopperStyle.QUARTZ, false);

		// the column: where the colony goes (a glyph, the sentence in its color), the ring, Done
		int[] sh = boxes.spotHead();
		heading(g, Component.translatable("screen.aliveworkplace.colony_map.spot_head"), sh);
		Component status = message != null ? message
			: spot.isPresent() ? Component.translatable("screen.aliveworkplace.colony_map.spot", VillageHallScreen.where(hall, spot.get()), village)
			: Component.translatable("screen.aliveworkplace.colony_map.pick");
		CopperStyle.Status kind = message != null ? (messageGood ? CopperStyle.Status.GOOD : CopperStyle.Status.BAD)
			: spot.isPresent() ? CopperStyle.Status.GOOD : CopperStyle.Status.INFO;
		int[] sp = boxes.spot();
		CopperStyle.panel(g, sp[0], sp[1], sp[2], sp[3]);
		g.blitSprite(kind.glyph, sp[0] + 4, sp[1] + 5, 8, 8);
		paragraph(g, status, sp[0] + 15, sp[1] + 5, sp[2] - 19, sp[1] + sp[3] - 2, kind.color);
		int[] rh = boxes.ringHead();
		heading(g, Component.translatable("screen.aliveworkplace.colony_map.ring_head"), rh);
		int[] r = boxes.ring();
		paragraph(g, Component.translatable("screen.aliveworkplace.colony_map.legend", ColonyMap.MIN, ColonyMap.MAX, ColonyMap.CLEAR), r[0], r[1], r[2],
			r[1] + r[3], CopperStyle.TUFF);
		boolean overDone = doneButton.isMouseOver(mouseX, mouseY);
		CopperStyle.button(g, font, doneButton.getMessage(), null, doneButton.getX(), doneButton.getY(), doneButton.getWidth(), doneButton.getHeight(),
			CopperStyle.Kind.COPPER, overDone, false, false);
		if (overDone) {
			whisper = Component.translatable("screen.aliveworkplace.colony_map.whisper.done");
		}

		List<Component> hover = overMap(mouseX, mouseY) ? hover(mouseX, mouseY) : List.of();
		if (!hover.isEmpty()) {
			whisper = Component.translatable("screen.aliveworkplace.colony_map.whisper.map");
		}
		int[] w = boxes.whisper();
		CopperStyle.whisper(g, font, whisper, w[0] + 2, w[1] + 3, w[2] - 4, boxes.compact());
		if (hover.size() == 1) {
			CopperStyle.tooltip(g, font, hover.get(0), List.of(), null, null, mouseX + 8, mouseY - 14);
		} else if (hover.size() == 2) {
			ColonyMap.Verdict verdict = verdictAt(mouseX, mouseY);
			CopperStyle.tooltip(g, font, hover.get(0), List.of(), verdict == ColonyMap.Verdict.OK ? CopperStyle.Status.GOOD : CopperStyle.Status.BAD,
				hover.get(1), mouseX + 8, mouseY - 14);
		}
	}

	private ColonyMap.Verdict verdictAt(int mouseX, int mouseY) {
		int x = ColonyMap.toWorld(hall.getX(), mouseX, mapX, mapSize);
		int z = ColonyMap.toWorld(hall.getZ(), mouseY, mapY, mapSize);
		return ColonyMap.verdict(hall, x, z, marks.stream().map(Colonies.Mark::hall).toList());
	}

	/** A heading: Tuff capitals and a rule to its right. */
	private void heading(GuiGraphics g, Component text, int[] box) {
		String t = CopperStyle.trim(font, text.getString().toUpperCase(java.util.Locale.ROOT), box[2]);
		g.drawString(font, t, box[0], box[1] + 1, CopperStyle.TUFF, false);
		int rx = box[0] + font.width(t) + 4;
		if (rx < box[0] + box[2]) {
			g.fill(rx, box[1] + 5, box[0] + box[2], box[1] + 6, CopperStyle.DEEP_600);
		}
	}

	/** {@code text} wrapped to {@code w}, from {@code y} down to {@code bottom}; the last line that fits ends with an ellipsis if more is left. */
	private void paragraph(GuiGraphics g, Component text, int x, int y, int w, int bottom, int color) {
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, w);
		for (int i = 0; i < lines.size() && y + 9 <= bottom; i++) {
			boolean last = i + 1 < lines.size() && y + 19 > bottom;
			if (last) {
				StringBuilder sb = new StringBuilder();
				lines.get(i).accept((index, style, cp) -> {
					sb.appendCodePoint(cp);
					return true;
				});
				g.drawString(font, CopperStyle.trim(font, sb + " ...", w), x, y, color, false);
				return;
			}
			g.drawString(font, lines.get(i), x, y, color, false);
			y += 10;
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
			case OK -> Component.translatable("screen.aliveworkplace.colony_map.ok");
			case TOO_NEAR -> Component.translatable("screen.aliveworkplace.colony_map.too_near", village);
			case TOO_FAR -> Component.translatable("screen.aliveworkplace.colony_map.too_far", village);
			case NEAR_HALL -> Component.translatable("screen.aliveworkplace.colony_map.near_hall");
		});
		return out;
	}

}
