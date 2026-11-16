package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.ui.CityPlanLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;

/**
 * The City Plan screen (ROADMAP 27.3): the village map with the plan's grid over it, each zone tinted in its kind's
 * colour and named, Keep Clear hatched, build sites going up as outlines; a side panel to add, rename, delete and set
 * up zones, a brush (one cell, or drag an area), an eraser, undo and a legend. Every change is a packet the server
 * checks ({@link CityPlans.Edit}); the server sends the plan back after each. Drawn in Copperline (44.3,
 * {@link CityPlanLayout}): the tools and Undo on the rail, the map and its paint as before, New the one copper button,
 * and the whisper line saying what the thing under the mouse does.
 */
public class CityPlanScreen extends Screen {
	private static final ResourceLocation MAP_TEXTURE = AliveWorkplace.id("city_plan_map");
	private static final int ROW = CityPlanLayout.ROW;
	/** Vanilla's empty map colour, for land nobody has seen. */
	private static final int PARCHMENT = 0xFFD6BE96;

	public enum Tool { BRUSH, AREA, ERASER, ROAD, WALL }

	/** Two clicks this close in time (ms) and place (pixels) are a double-click: it ends a road or the wall line. */
	private static final long DOUBLE_CLICK_MS = 300;

	private final BlockPos hall;
	private final Component village;
	private final int cellSize;
	private final boolean mayEdit;
	private final byte[] colors;
	private final List<CityPlans.Mark> marks;
	private final List<CityPlans.Outline> outlines;
	private final List<CityPlans.KindInfo> kinds;
	private final List<CityPlans.StyleInfo> styles;
	private CityPlan plan;
	private int undoSteps;
	private int selected = -1;
	private int scroll;
	private Tool tool = Tool.BRUSH;

	// a stroke being drawn
	private final BitSet stroke = new BitSet();
	private boolean stroking;
	private boolean erasing;
	private int areaStart = -1;
	private int areaEnd = -1;

	// a road or the wall line being drawn (27.4): its points as offsets from the hall
	private final List<BlockPos> line = new ArrayList<>();
	private int roadWidth = CityPlan.Road.STREET;
	/** The new road's style: "" for that of the zone it starts in. */
	private String roadStyle = "";
	private boolean wallClosed = true;
	private long lastClick;
	private double lastClickX, lastClickY;

	private DynamicTexture texture;
	private int mapX, mapY, mapSize, listTop, listRows;
	private CityPlanLayout.Plan boxes;
	private EditBox nameBox;
	private Button newButton, kindButton, styleButton, renewButton, deleteButton, optionButton, roadStyleButton;
	/** What the whisper line says about the thing under the mouse this frame. */
	private Component whisper = Component.empty();
	/** Every text drawn last frame (its box, and whether it had to fit whole), for the GUI-scale check (24.4). */
	private final List<Drawn> drawn = new ArrayList<>();

	private record Drawn(String text, int x, int y, int width, int room, boolean mustFit) {
	}

	public CityPlanScreen(CityPlans.Open open) {
		super(Component.translatable("screen.aliveworkplace.city_plan.title", open.village()));
		this.hall = open.hall();
		this.village = open.village();
		this.cellSize = open.cellSize();
		this.mayEdit = open.mayEdit();
		this.colors = open.colors();
		this.marks = open.marks();
		this.outlines = open.outlines();
		this.kinds = open.kinds();
		this.styles = open.styles();
		this.plan = open.plan();
		this.undoSteps = open.undo();
		this.selected = plan.zones().isEmpty() ? -1 : 0;
	}

	public BlockPos hall() {
		return hall;
	}

	public CityPlan plan() {
		return plan;
	}

	public int selected() {
		return selected;
	}

	/** The server's plan after a change. */
	public void sync(CityPlans.Sync sync) {
		int before = plan.zones().size();
		plan = sync.plan();
		undoSteps = sync.undo();
		if (plan.zones().size() > before) {
			selected = plan.zones().size() - 1; // the zone just added
		}
		if (selected >= plan.zones().size()) {
			selected = plan.zones().size() - 1;
		}
		refreshWidgets();
	}

	// --- layout ------------------------------------------------------------------------------------------------------

	/** The rail's items, in {@link CityPlanLayout#RAIL}'s order. */
	private static final net.minecraft.world.item.Item[] RAIL_ITEMS = {Items.BRUSH, Items.MAP, Items.SPONGE, Items.DIRT_PATH,
		Items.STONE_BRICK_WALL, Items.CLOCK};
	private static final Tool[] RAIL_TOOLS = {Tool.BRUSH, Tool.AREA, Tool.ERASER, Tool.ROAD, Tool.WALL};

	private static Component railLabel(int i) {
		return Component.translatable("screen.aliveworkplace.city_plan." + CityPlanLayout.RAIL[i]);
	}

	/** The boxes the screen draws in and takes its clicks with (also for the scene and its checks). */
	public CityPlanLayout.Plan boxes() {
		return boxes;
	}

	@Override
	protected void init() {
		int railLabelW = 0;
		for (int i = 0; i < CityPlanLayout.RAIL.length; i++) {
			railLabelW = Math.max(railLabelW, font.width(railLabel(i)));
		}
		Component newLabel = Component.translatable("screen.aliveworkplace.city_plan.new");
		boxes = CityPlanLayout.plan(width, height, font.width(title), railLabelW, Math.max(44, font.width(newLabel) + 12));
		mapX = boxes.map()[0];
		mapY = boxes.map()[1];
		mapSize = boxes.map()[2];
		listTop = boxes.list()[1];
		listRows = boxes.listRows();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, plan.zones().size() - listRows)));

		// The widgets take the clicks; render draws them in Copperline (addWidget: the game doesn't draw them itself).
		newButton = addWidget(place(Button.builder(newLabel, b -> newZone()), boxes.newButton()));
		int[] n = boxes.name();
		nameBox = addRenderableWidget(new EditBox(font, n[0] + 4, n[1] + 3, n[2] - 8, 10, Component.translatable("screen.aliveworkplace.city_plan.name")));
		nameBox.setBordered(false);
		nameBox.setTextColor(CopperStyle.QUARTZ);
		nameBox.setMaxLength(CityPlans.MAX_NAME);
		styleButton = addWidget(place(Button.builder(Component.empty(), b -> cycleStyle()), boxes.style()));
		kindButton = addWidget(place(Button.builder(Component.empty(), b -> cycleKind()), boxes.kind()));
		renewButton = addWidget(place(Button.builder(Component.empty(), b -> toggleRenew()), boxes.renew()));
		deleteButton = addWidget(place(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.delete"), b -> deleteZone()), boxes.delete()));
		optionButton = addWidget(place(Button.builder(Component.empty(), b -> cycleOption()), boxes.option()));
		roadStyleButton = addWidget(place(Button.builder(Component.empty(), b -> cycleRoadStyle()), boxes.roadStyle()));

		buildTexture();
		refreshWidgets();
	}

	private static Button place(Button.Builder builder, int[] box) {
		return builder.bounds(box[0], box[1], box[2], box[3]).build();
	}

	private void buildTexture() {
		if (texture == null) {
			NativeImage image = new NativeImage(CityPlans.MAP, CityPlans.MAP, false);
			for (int z = 0; z < CityPlans.MAP; z++) {
				for (int x = 0; x < CityPlans.MAP; x++) {
					int packed = colors.length == CityPlans.MAP * CityPlans.MAP ? colors[x + z * CityPlans.MAP] & 0xFF : 0;
					int abgr = packed == 0 ? abgr(PARCHMENT) : MapColor.getColorFromPackedId(packed);
					image.setPixelRGBA(x, z, abgr | 0xFF000000);
				}
			}
			texture = new DynamicTexture(image);
			minecraft.getTextureManager().register(MAP_TEXTURE, texture);
		}
	}

	private static int abgr(int argb) {
		return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
	}

	@Override
	public void removed() {
		commitName();
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

	private void refreshWidgets() {
		if (nameBox == null) {
			return;
		}
		// the list follows the chosen zone, so the settings under it never edit a row scrolled out of sight
		if (selected >= 0 && listRows > 0) {
			if (selected < scroll) {
				scroll = selected;
			} else if (selected >= scroll + listRows) {
				scroll = selected - listRows + 1;
			}
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, plan.zones().size() - listRows)));
		CityPlan.Zone zone = zone();
		boolean has = zone != null && mayEdit;
		nameBox.setEditable(has);
		nameBox.active = has;
		if (zone != null && !nameBox.isFocused()) {
			nameBox.setValue(zone.name());
		} else if (zone == null) {
			nameBox.setValue("");
		}
		kindButton.active = has;
		styleButton.active = has;
		renewButton.active = has;
		deleteButton.active = has;
		newButton.active = mayEdit && plan.zones().size() < CityPlan.MAX_ZONES && !kinds.isEmpty();
		optionButton.active = mayEdit && (tool == Tool.ROAD || tool == Tool.WALL);
		roadStyleButton.active = mayEdit && tool == Tool.ROAD;
		if (tool == Tool.WALL) {
			optionButton.setMessage(Component.translatable(wallClosed ? "screen.aliveworkplace.city_plan.wall.closed" : "screen.aliveworkplace.city_plan.wall.open"));
		} else {
			optionButton.setMessage(Component.translatable("screen.aliveworkplace.city_plan.width." + roadWidth));
		}
		roadStyleButton.setMessage(Component.empty());
		kindButton.setMessage(Component.translatable("screen.aliveworkplace.city_plan.kind",
			zone == null ? Component.literal("-") : kindTitle(zone.kind())));
		styleButton.setMessage(Component.translatable("screen.aliveworkplace.city_plan.style",
			zone == null ? Component.literal("-") : styleTitle(zone.style())));
		renewButton.setMessage(Component.translatable(zone != null && zone.renew() ? "screen.aliveworkplace.city_plan.renew.on"
			: "screen.aliveworkplace.city_plan.renew.off"));
	}

	/** Whether the rail's entry {@code i} can be clicked now (a tool not already chosen; Undo with steps to undo). */
	private boolean railOn(int i) {
		return mayEdit && (i == CityPlanLayout.UNDO ? undoSteps > 0 : RAIL_TOOLS[i] != tool);
	}

	// --- what the buttons do ---------------------------------------------------------------------------------------

	private CityPlan.Zone zone() {
		return selected >= 0 && selected < plan.zones().size() ? plan.zones().get(selected) : null;
	}

	private void send(CityPlans.Edit edit) {
		if (mayEdit) {
			ClientPlayNetworking.send(edit);
		}
	}

	private void newZone() {
		commitName();
		CityPlans.KindInfo kind = kinds.stream().filter(k -> plan.zones().stream().noneMatch(z -> z.kind().equals(k.id()))).findFirst()
			.orElse(kinds.get(0));
		long same = plan.zones().stream().filter(z -> z.kind().equals(kind.id())).count();
		String name = kindTitle(kind.id()).getString() + (same > 0 ? " " + (same + 1) : "");
		send(CityPlans.Edit.addZone(hall, kind.id(), name, ""));
	}

	private void deleteZone() {
		if (zone() != null) {
			send(CityPlans.Edit.removeZone(hall, selected));
		}
	}

	private void cycleKind() {
		CityPlan.Zone zone = zone();
		if (zone == null || kinds.isEmpty()) {
			return;
		}
		int i = 0;
		while (i < kinds.size() && !kinds.get(i).id().equals(zone.kind())) {
			i++;
		}
		String next = kinds.get((i + (hasShiftDown() ? kinds.size() - 1 : 1)) % kinds.size()).id();
		send(CityPlans.Edit.editZone(hall, selected, next, currentName(zone), zone.style(), zone.renew()));
	}

	private void cycleStyle() {
		CityPlan.Zone zone = zone();
		if (zone == null) {
			return;
		}
		List<String> names = new ArrayList<>();
		names.add("");
		styles.forEach(s -> names.add(s.name()));
		int i = Math.max(0, names.indexOf(zone.style()));
		String next = names.get((i + (hasShiftDown() ? names.size() - 1 : 1)) % names.size());
		send(CityPlans.Edit.editZone(hall, selected, zone.kind(), currentName(zone), next, zone.renew()));
	}

	private void toggleRenew() {
		CityPlan.Zone zone = zone();
		if (zone != null) {
			send(CityPlans.Edit.editZone(hall, selected, zone.kind(), currentName(zone), zone.style(), !zone.renew()));
		}
	}

	private String currentName(CityPlan.Zone zone) {
		String typed = nameBox == null ? "" : nameBox.getValue().strip();
		return typed.isEmpty() ? zone.name() : typed;
	}

	/** Sends the name typed into the box, if it changed. */
	private void commitName() {
		CityPlan.Zone zone = zone();
		if (zone != null && nameBox != null && !nameBox.getValue().strip().isEmpty() && !nameBox.getValue().strip().equals(zone.name())) {
			send(CityPlans.Edit.editZone(hall, selected, zone.kind(), nameBox.getValue().strip(), zone.style(), zone.renew()));
		}
	}

	public void setTool(Tool next) {
		if (next != tool) {
			line.clear();
		}
		tool = next;
		refreshWidgets();
	}

	public Tool tool() {
		return tool;
	}

	/** The road or wall line being drawn: its points so far, as offsets from the hall. */
	public List<BlockPos> line() {
		return List.copyOf(line);
	}

	private void cycleOption() {
		if (tool == Tool.WALL) {
			wallClosed = !wallClosed;
		} else {
			roadWidth = roadWidth == CityPlan.Road.LANE ? CityPlan.Road.STREET : roadWidth == CityPlan.Road.STREET ? CityPlan.Road.AVENUE : CityPlan.Road.LANE;
		}
		refreshWidgets();
	}

	private void cycleRoadStyle() {
		List<String> names = new ArrayList<>();
		names.add("");
		styles.forEach(st -> names.add(st.name()));
		int i = Math.max(0, names.indexOf(roadStyle));
		roadStyle = names.get((i + (hasShiftDown() ? names.size() - 1 : 1)) % names.size());
		refreshWidgets();
	}

	/** Adds a point to the road or wall line being drawn; at {@link CityPlan#MAX_ROAD_POINTS} the line ends by itself. */
	public void addPoint(BlockPos offset) {
		if (!line.isEmpty() && line.get(line.size() - 1).equals(offset)) {
			return;
		}
		line.add(offset);
		if (line.size() >= CityPlan.MAX_ROAD_POINTS) {
			finishLine();
		}
	}

	/** Sends the road or wall line drawn so far (two points at least) and starts afresh. */
	public void finishLine() {
		if (line.size() >= 2) {
			commitName();
			if (tool == Tool.WALL) {
				send(CityPlans.Edit.wall(hall, List.copyOf(line), wallClosed));
			} else {
				send(CityPlans.Edit.addRoad(hall, List.copyOf(line), roadWidth, roadStyle));
			}
		}
		line.clear();
	}

	/** The road nearest the pointer, within a few pixels on the map, or -1. */
	private int roadUnder(double mx, double my) {
		int best = -1;
		double bestD = 5;
		for (int i = 0; i < plan.roads().size(); i++) {
			List<BlockPos> pts = plan.roads().get(i).points();
			for (int j = 0; j + 1 < pts.size(); j++) {
				double d = segmentDistance(mx, my, toMapX(pts.get(j).getX()), toMapY(pts.get(j).getZ()), toMapX(pts.get(j + 1).getX()), toMapY(pts.get(j + 1).getZ()));
				if (d < bestD) {
					bestD = d;
					best = i;
				}
			}
		}
		return best;
	}

	private static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax, dy = by - ay;
		double len = dx * dx + dy * dy;
		double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len));
		return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
	}

	/** The block (as an offset from the hall) under map pixel {@code mx}, {@code my}. */
	private BlockPos offsetAt(double mx, double my) {
		int half = cellSize * CityPlan.GRID / 2;
		int dx = (int) Math.floor((mx - mapX) * (2.0 * half) / mapSize) - half;
		int dz = (int) Math.floor((my - mapY) * (2.0 * half) / mapSize) - half;
		return new BlockPos(Math.max(-half, Math.min(half - 1, dx)), 0, Math.max(-half, Math.min(half - 1, dz)));
	}

	public void select(int index) {
		commitName();
		selected = index;
		if (nameBox != null) {
			nameBox.setFocused(false);
		}
		refreshWidgets();
	}

	private void undo() {
		commitName();
		send(CityPlans.Edit.undo(hall));
	}

	// --- input ---------------------------------------------------------------------------------------------------------

	private int cellUnder(double mx, double my) {
		if (mx < mapX || my < mapY || mx >= mapX + mapSize || my >= mapY + mapSize) {
			return -1;
		}
		int per = mapSize / CityPlan.GRID;
		return (int) ((mx - mapX) / per) + (int) ((my - mapY) / per) * CityPlan.GRID;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && CopperLayout.inside(boxes.close(), mx, my)) {
			// The lintel's close X (Copperline, 47.1): as Esc.
			click();
			onClose();
			return true;
		}
		int rail = button == 0 ? CopperLayout.at(boxes.railEntries(), mx, my) : -1;
		if (rail >= 0) {
			if (railOn(rail)) {
				click();
				if (rail == CityPlanLayout.UNDO) {
					undo();
				} else {
					setTool(RAIL_TOOLS[rail]);
				}
			}
			return true;
		}
		int cell = cellUnder(mx, my);
		if (cell >= 0 && mayEdit && (tool == Tool.ROAD || tool == Tool.WALL)) {
			if (button == 1) {
				if (!line.isEmpty()) {
					line.clear(); // right-click drops the line being drawn
				} else if (tool == Tool.ROAD && roadUnder(mx, my) >= 0) {
					commitName();
					send(CityPlans.Edit.removeRoad(hall, roadUnder(mx, my)));
				} else if (tool == Tool.WALL && plan.wall().isPresent()) {
					commitName();
					send(CityPlans.Edit.wall(hall, List.of(), true));
				}
				return true;
			}
			if (button == 0) {
				long now = net.minecraft.Util.getMillis();
				boolean doubled = now - lastClick < DOUBLE_CLICK_MS && Math.abs(mx - lastClickX) < 4 && Math.abs(my - lastClickY) < 4;
				lastClick = now;
				lastClickX = mx;
				lastClickY = my;
				if (doubled && !line.isEmpty()) {
					finishLine();
					lastClick = 0;
				} else {
					addPoint(offsetAt(mx, my));
				}
				return true;
			}
		}
		if (cell >= 0 && mayEdit && (button == 0 || button == 1)) {
			erasing = button == 1 || tool == Tool.ERASER;
			if (!erasing && zone() == null) {
				return true; // nothing to paint with yet
			}
			stroke.clear();
			if (tool == Tool.AREA && !erasing) {
				areaStart = cell;
				areaEnd = cell;
			} else {
				stroke.set(cell);
			}
			stroking = true;
			return true;
		}
		int row = button == 0 ? CityPlanLayout.rowAt(boxes, scroll, plan.zones().size(), mx, my) : -1;
		if (row >= 0) {
			select(row);
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	private void click() {
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (stroking) {
			int cell = cellUnder(Math.max(mapX, Math.min(mapX + mapSize - 1, mx)), Math.max(mapY, Math.min(mapY + mapSize - 1, my)));
			if (areaStart >= 0) {
				areaEnd = cell;
			} else if (cell >= 0) {
				stroke.set(cell);
			}
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (stroking) {
			stroking = false;
			if (areaStart >= 0) {
				stroke.or(area(areaStart, areaEnd));
				areaStart = -1;
				areaEnd = -1;
			}
			if (!stroke.isEmpty()) {
				commitName();
				send(erasing ? CityPlans.Edit.erase(hall, (BitSet) stroke.clone()) : CityPlans.Edit.paint(hall, selected, (BitSet) stroke.clone()));
			}
			stroke.clear();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	/** Every cell in the rectangle between cells {@code a} and {@code b}. */
	public static BitSet area(int a, int b) {
		BitSet out = new BitSet();
		if (a < 0 || b < 0) {
			return out;
		}
		int x0 = Math.min(a % CityPlan.GRID, b % CityPlan.GRID);
		int x1 = Math.max(a % CityPlan.GRID, b % CityPlan.GRID);
		int z0 = Math.min(a / CityPlan.GRID, b / CityPlan.GRID);
		int z1 = Math.max(a / CityPlan.GRID, b / CityPlan.GRID);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				out.set(x + z * CityPlan.GRID);
			}
		}
		return out;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (CopperLayout.inside(boxes.list(), mx, my)) {
			scroll = Math.max(0, Math.min(Math.max(0, plan.zones().size() - listRows), scroll - (int) Math.signum(sy)));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (nameBox != null && nameBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
			commitName();
			nameBox.setFocused(false);
			return true;
		}
		if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !line.isEmpty()) {
			finishLine();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && !line.isEmpty()) {
			line.clear();
			return true;
		}
		if (key == GLFW.GLFW_KEY_Z && hasControlDown() && (nameBox == null || !nameBox.isFocused())) {
			undo();
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	// --- drawing -------------------------------------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int[] win = boxes.win();
		CopperStyle.window(g, win[0], win[1], win[2], win[3]);
		// The name field's well, under the game's box (drawn without its border).
		int[] n = boxes.name();
		g.blitSprite(nameBox.isFocused() ? CopperStyle.WELL_FOCUS : nameBox.active ? CopperStyle.WELL : CopperStyle.BUTTON_OFF, n[0], n[1], n[2], n[3]);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		drawn.clear();
		whisper = Component.empty();
		super.render(g, mouseX, mouseY, partialTick);
		boolean overClose = CopperLayout.inside(boxes.close(), mouseX, mouseY);
		int[] plaque = CopperStyle.plaque(g, font, title, boxes.win());
		CopperStyle.lintel(g, font, boxes.win(), plaque, mayEdit
			? Component.translatable("screen.aliveworkplace.city_plan.tool", railLabel(java.util.Arrays.asList(RAIL_TOOLS).indexOf(tool)))
			: Component.translatable("screen.aliveworkplace.city_plan.view_only_short"), overClose);
		renderRail(g, mouseX, mouseY);
		renderMap(g, mouseX, mouseY);
		renderPanel(g, mouseX, mouseY);
		if (overClose) {
			whisper = Component.translatable("screen.aliveworkplace.city_plan.whisper.close");
		}
		int[] w = boxes.whisper();
		CopperStyle.whisper(g, font, whisper, w[0] + 2, w[1] + 3, w[2] - 4, boxes.compact());
	}

	/** The tools and Undo, the chosen tool copper-marked, Undo with its steps as the count. */
	private void renderRail(GuiGraphics g, int mouseX, int mouseY) {
		int[] rail = boxes.rail();
		g.fill(rail[0], rail[1], rail[0] + rail[2], rail[1] + rail[3], CopperStyle.DEEP_800);
		if (!boxes.compact()) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.city_plan.tools").getString().toUpperCase(Locale.ROOT),
				rail[2] - 8), rail[0] + 4, rail[1] + 3, CopperStyle.TUFF, false);
		}
		int[][] entries = boxes.railEntries();
		for (int i = 0; i < entries.length; i++) {
			boolean undoRow = i == CityPlanLayout.UNDO;
			boolean current = !undoRow && RAIL_TOOLS[i] == tool;
			boolean over = CopperLayout.inside(entries[i], mouseX, mouseY);
			if (undoRow && !boxes.compact()) { // a rule between the tools and Undo
				g.fill(entries[i][0] + 2, entries[i][1] - 1, entries[i][0] + entries[i][2] - 2, entries[i][1], CopperStyle.DEEP_600);
			}
			CopperStyle.railEntry(g, font, entries[i], null, new ItemStack(RAIL_ITEMS[i]), railLabel(i), undoRow ? undoSteps : 0, current,
				over && railOn(i), boxes.compact());
			if (!railOn(i) && !current) { // off: dimmed
				g.fill(entries[i][0], entries[i][1], entries[i][0] + entries[i][2], entries[i][1] + entries[i][3], 0x9016171D);
			}
			if (over) {
				whisper = Component.translatable("screen.aliveworkplace.city_plan.whisper." + CityPlanLayout.RAIL[i]);
			}
		}
	}

	private void drawButton(GuiGraphics g, Button b, CopperStyle.Kind kind, int mouseX, int mouseY, String whisperKey, Object... args) {
		boolean over = b.isMouseOver(mouseX, mouseY);
		CopperStyle.button(g, font, b.getMessage(), null, b.getX(), b.getY(), b.getWidth(), b.getHeight(), kind, over && b.active, false, !b.active);
		if (over && whisperKey != null) {
			whisper = Component.translatable(whisperKey, args);
		}
	}

	/** A heading over a list or a group: Tuff capitals and a rule to its right. */
	private void heading(GuiGraphics g, Component text, int x, int y, int w) {
		String t = CopperStyle.trim(font, text.getString().toUpperCase(Locale.ROOT), w);
		g.drawString(font, t, x, y, CopperStyle.TUFF, false);
		drawn.add(new Drawn(t, x, y, font.width(t), w, true));
		int rx = x + font.width(t) + 4;
		if (rx < x + w) {
			g.fill(rx, y + 4, x + w, y + 5, CopperStyle.DEEP_600);
		}
	}

	private int[] zoneOfCells() {
		int[] of = new int[CityPlan.GRID * CityPlan.GRID];
		java.util.Arrays.fill(of, -1);
		for (int i = plan.zones().size() - 1; i >= 0; i--) {
			BitSet cells = plan.zones().get(i).cells();
			for (int c = cells.nextSetBit(0); c >= 0 && c < of.length; c = cells.nextSetBit(c + 1)) {
				of[c] = i;
			}
		}
		return of;
	}

	private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
		g.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, CopperStyle.VOID);
		g.blit(MAP_TEXTURE, mapX, mapY, mapSize, mapSize, 0f, 0f, CityPlans.MAP, CityPlans.MAP, CityPlans.MAP, CityPlans.MAP);
		int per = mapSize / CityPlan.GRID;
		int[] of = zoneOfCells();
		// the strokes being drawn show as they will be
		BitSet pending = (BitSet) stroke.clone();
		if (areaStart >= 0) {
			pending.or(area(areaStart, areaEnd));
		}
		for (int c = 0; c < of.length; c++) {
			int zone = pending.get(c) ? (erasing ? -1 : selected) : of[c];
			int x = mapX + (c % CityPlan.GRID) * per;
			int y = mapY + (c / CityPlan.GRID) * per;
			if (zone >= 0) {
				CityPlans.KindInfo kind = kind(plan.zones().get(zone).kind());
				int tint = kind == null ? 0x808080 : kind.tint();
				g.fill(x, y, x + per, y + per, (pending.get(c) ? 0xA0000000 : 0x70000000) | tint);
				if (kind != null && !kind.buildable()) { // Keep Clear: hatched
					for (int py = 0; py < per; py++) {
						for (int px = 0; px < per; px++) {
							if (((x + px) + (y + py)) % 4 == 0) {
								g.fill(x + px, y + py, x + px + 1, y + py + 1, 0xE0000000 | (kind.color() & 0xFFFFFF));
							}
						}
					}
				}
				int edge = 0xFF000000 | (kind == null ? 0x808080 : kind.color());
				int cx = c % CityPlan.GRID;
				int cz = c / CityPlan.GRID;
				if (cz == 0 || of[c - CityPlan.GRID] != zone) {
					g.fill(x, y, x + per, y + 1, edge);
				}
				if (cz == CityPlan.GRID - 1 || of[c + CityPlan.GRID] != zone) {
					g.fill(x, y + per - 1, x + per, y + per, edge);
				}
				if (cx == 0 || of[c - 1] != zone) {
					g.fill(x, y, x + 1, y + per, edge);
				}
				if (cx == CityPlan.GRID - 1 || of[c + 1] != zone) {
					g.fill(x + per - 1, y, x + per, y + per, edge);
				}
			} else if (pending.get(c)) {
				g.fill(x, y, x + per, y + per, 0x60000000);
			}
		}
		// the grid: a faint line every cell, a stronger one every eight
		for (int i = 0; i <= CityPlan.GRID; i++) {
			int color = i % 8 == 0 ? 0x50FFFFFF : 0x22FFFFFF;
			g.fill(mapX + i * per, mapY, mapX + i * per + 1, mapY + mapSize, color);
			g.fill(mapX, mapY + i * per, mapX + mapSize, mapY + i * per + 1, color);
		}
		// build sites going up (white) and the Steward's proposals (yellow), dashed
		for (CityPlans.Outline o : outlines) {
			dashed(g, toMapX(o.minDx()), toMapY(o.minDz()), toMapX(o.maxDx() + 1), toMapY(o.maxDz() + 1), o.proposal() ? 0xFFFFE060 : 0xFFFFFFFF);
		}
		// the roads (27.4): a dirt-brown band as wide as the road, then the wall line in stone grey
		double scale = mapSize / (double) (cellSize * CityPlan.GRID);
		for (CityPlan.Road road : plan.roads()) {
			polyline(g, road.points(), false, Math.max(1, (int) Math.round(road.width() * scale)), 0xFF000000 | ROAD_COLOR);
		}
		plan.wall().ifPresent(w -> polyline(g, w.points(), w.closed(), Math.max(2, (int) Math.round(scale * 1.5)), 0xFF000000 | WALL_COLOR));
		if (!line.isEmpty()) {
			List<BlockPos> drawing = new ArrayList<>(line);
			if (cellUnder(mouseX, mouseY) >= 0) {
				drawing.add(offsetAt(mouseX, mouseY));
			}
			int color = tool == Tool.WALL ? 0xC0000000 | WALL_COLOR : 0xC0000000 | ROAD_COLOR;
			int thick = tool == Tool.WALL ? Math.max(2, (int) Math.round(scale * 1.5)) : Math.max(1, (int) Math.round(roadWidth * scale));
			polyline(g, drawing, false, thick, color);
			for (BlockPos p : line) {
				g.fill(toMapX(p.getX()) - 1, toMapY(p.getZ()) - 1, toMapX(p.getX()) + 2, toMapY(p.getZ()) + 2, 0xFFFFFFFF);
			}
		}
		// the hall and the finished buildings' banners
		for (CityPlans.Mark m : marks) {
			int x = toMapX(m.dx());
			int y = toMapY(m.dz());
			int r = m.hall() ? 3 : 2;
			g.fill(x - r - 1, y - r - 1, x + r + 1, y + r + 1, 0xFF000000);
			g.fill(x - r, y - r, x + r, y + r, 0xFF000000 | (DyeColor.byId(m.color()).getTextureDiffuseColor() & 0xFFFFFF));
		}
		// zone names, each at its middle, left out where it would cover another
		List<int[]> labels = new ArrayList<>();
		for (int i = 0; i < plan.zones().size(); i++) {
			BitSet cells = plan.zones().get(i).cells();
			if (cells.isEmpty()) {
				continue;
			}
			long sx = 0, sz = 0;
			int minX = CityPlan.GRID, maxX = 0;
			for (int c = cells.nextSetBit(0); c >= 0; c = cells.nextSetBit(c + 1)) {
				sx += c % CityPlan.GRID;
				sz += c / CityPlan.GRID;
				minX = Math.min(minX, c % CityPlan.GRID);
				maxX = Math.max(maxX, c % CityPlan.GRID);
			}
			int n = cells.cardinality();
			int room = Math.max(per * 3, (maxX - minX + 1) * per);
			String name = fit(plan.zones().get(i).name(), room);
			int w = font.width(name);
			int x = mapX + (int) ((sx * per) / n) + per / 2 - w / 2;
			int y = mapY + (int) ((sz * per) / n) + per / 2 - 4;
			x = Math.max(mapX + 1, Math.min(mapX + mapSize - w - 1, x));
			y = Math.max(mapY + 1, Math.min(mapY + mapSize - 9, y));
			int[] box = {x - 1, y - 1, x + w + 1, y + 9};
			if (labels.stream().anyMatch(b -> b[0] < box[2] && box[0] < b[2] && b[1] < box[3] && box[1] < b[3])) {
				continue;
			}
			labels.add(box);
			g.fill(box[0], box[1], box[2], box[3], 0x90000000);
			text(g, Component.literal(name), x, y, w, CopperStyle.QUARTZ, false);
		}
		// north, as on the Hall's map
		String north = Component.translatable("screen.aliveworkplace.hall.map.north").getString();
		g.fill(mapX + mapSize - font.width(north) - 6, mapY + 2, mapX + mapSize - 2, mapY + 13, 0xC0000000);
		g.drawString(font, north, mapX + mapSize - font.width(north) - 4, mapY + 4, CopperStyle.QUARTZ, false);
		// the road and wall options under the map
		drawButton(g, optionButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY, tool == Tool.WALL ? "screen.aliveworkplace.city_plan.whisper.wall_closed"
			: "screen.aliveworkplace.city_plan.whisper.width");
		drawButton(g, roadStyleButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY, "screen.aliveworkplace.city_plan.road_style",
			roadStyle.isEmpty() ? Component.translatable("screen.aliveworkplace.city_plan.road_style.zone") : styleTitle(roadStyle));
		ItemStack roadIcon = new ItemStack(roadStyle.isEmpty() ? Items.DIRT_PATH : styleIcon(roadStyle));
		g.pose().pushPose();
		g.pose().translate(roadStyleButton.getX() + roadStyleButton.getWidth() / 2f - 6, roadStyleButton.getY() + 1, 0);
		g.pose().scale(0.75f, 0.75f, 1f);
		g.renderItem(roadIcon, 0, 0);
		g.pose().popPose();
		if (!roadStyleButton.active) {
			g.fill(roadStyleButton.getX(), roadStyleButton.getY(), roadStyleButton.getRight(), roadStyleButton.getBottom(), 0x9016171D);
		}
		// the whisper line: what the pointer is over on the map; off the map, why nothing can be changed or what to do
		if (!CopperLayout.inside(boxes.map(), mouseX, mouseY) && (!whisper.getString().isEmpty() || CopperLayout.inside(boxes.side(), mouseX, mouseY))) {
			return; // the rail and the column say their own
		}
		Component status;
		int cell = cellUnder(mouseX, mouseY);
		if (!mayEdit) {
			status = Component.translatable("screen.aliveworkplace.city_plan.view_only");
		} else if (tool == Tool.ROAD || tool == Tool.WALL) {
			status = Component.translatable(tool == Tool.WALL ? "screen.aliveworkplace.city_plan.wall.hint" : "screen.aliveworkplace.city_plan.road.hint",
				plan.drawnRoads(), CityPlan.MAX_ROADS);
		} else if (cell >= 0 && of[cell] >= 0) {
			CityPlan.Zone z = plan.zones().get(of[cell]);
			status = Component.translatable("screen.aliveworkplace.city_plan.cell", z.name(), kindTitle(z.kind()), styleTitle(z.style()));
		} else if (plan.zones().isEmpty()) {
			status = Component.translatable("screen.aliveworkplace.city_plan.start");
		} else {
			status = Component.translatable("screen.aliveworkplace.city_plan.hint");
		}
		whisper = status;
	}

	private int toMapX(int dx) {
		int half = cellSize * CityPlan.GRID / 2;
		return mapX + (int) Math.floor((dx + half) * (double) mapSize / (2 * half));
	}

	private int toMapY(int dz) {
		int half = cellSize * CityPlan.GRID / 2;
		return mapY + (int) Math.floor((dz + half) * (double) mapSize / (2 * half));
	}

	private static final int ROAD_COLOR = 0xC9A26B;
	private static final int WALL_COLOR = 0x6E6E6E;

	/** {@code points} (offsets from the hall) as a line {@code thick} pixels wide, kept to the map. */
	private void polyline(GuiGraphics g, List<BlockPos> points, boolean closed, int thick, int color) {
		int n = points.size();
		int r0 = -(thick - 1) / 2, r1 = thick / 2 + 1;
		for (int i = 0; i + 1 < n || (closed && n > 2 && i < n); i++) {
			BlockPos a = points.get(i);
			BlockPos b = points.get((i + 1) % n);
			int ax = toMapX(a.getX()), ay = toMapY(a.getZ()), bx = toMapX(b.getX()), by = toMapY(b.getZ());
			int steps = Math.max(1, Math.max(Math.abs(bx - ax), Math.abs(by - ay)));
			for (int s = 0; s <= steps; s++) {
				int x = ax + Math.round((bx - ax) * s / (float) steps);
				int y = ay + Math.round((by - ay) * s / (float) steps);
				int x0 = Math.max(mapX, x + r0), y0 = Math.max(mapY, y + r0), x1 = Math.min(mapX + mapSize, x + r1), y1 = Math.min(mapY + mapSize, y + r1);
				if (x0 < x1 && y0 < y1) {
					g.fill(x0, y0, x1, y1, color);
				}
			}
		}
	}

	private void dashed(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		x0 = Math.max(mapX, x0);
		y0 = Math.max(mapY, y0);
		x1 = Math.min(mapX + mapSize, x1);
		y1 = Math.min(mapY + mapSize, y1);
		for (int x = x0; x < x1; x += 4) {
			g.fill(x, y0, Math.min(x + 2, x1), y0 + 1, color);
			g.fill(x, y1 - 1, Math.min(x + 2, x1), y1, color);
		}
		for (int y = y0; y < y1; y += 4) {
			g.fill(x0, y, x0 + 1, Math.min(y + 2, y1), color);
			g.fill(x1 - 1, y, x1, Math.min(y + 2, y1), color);
		}
	}

	private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
		int[] head = boxes.zonesHead();
		int x = head[0];
		int w = boxes.side()[2];
		heading(g, Component.translatable("screen.aliveworkplace.city_plan.zones", plan.zones().size(), CityPlan.MAX_ZONES), x, head[1] + 3, head[2]);
		drawButton(g, newButton, CopperStyle.Kind.COPPER, mouseX, mouseY, "screen.aliveworkplace.city_plan.whisper.new");
		// the zones
		int[] l = boxes.list();
		CopperStyle.well(g, l[0], l[1], l[2], l[3]);
		int rowW = l[2] - 7;
		for (int row = 0; row < listRows && row + scroll < plan.zones().size(); row++) {
			int i = row + scroll;
			CityPlan.Zone zone = plan.zones().get(i);
			int y = listTop + 1 + row * ROW;
			boolean over = CopperLayout.inside(new int[]{x + 1, y, rowW, ROW}, mouseX, mouseY);
			CopperStyle.rowBand(g, x + 1, y, rowW, ROW, row % 2 == 1, over, i == selected);
			if (over) {
				whisper = Component.translatable("screen.aliveworkplace.city_plan.whisper.list");
			}
			CityPlans.KindInfo kind = kind(zone.kind());
			g.fill(x + 5, y + 2, x + 13, y + 10, CopperStyle.VOID);
			g.fill(x + 6, y + 3, x + 12, y + 9, 0xFF000000 | (kind == null ? 0x808080 : kind.color()));
			String cells = String.valueOf(zone.cells().cardinality());
			int cw = font.width(cells);
			g.drawString(font, cells, x + rowW - 2 - cw, y + 2, CopperStyle.TUFF, false);
			drawn.add(new Drawn(cells, x + rowW - 2 - cw, y + 2, cw, cw, true));
			text(g, Component.literal(zone.name()), x + 16, y + 2, rowW - 16 - cw - 6, CopperStyle.QUARTZ, false);
		}
		if (plan.zones().isEmpty()) {
			text(g, Component.translatable("screen.aliveworkplace.city_plan.no_zones"), x + 5, listTop + 3, l[2] - 10, CopperStyle.TUFF, false);
		}
		CopperStyle.scrollbar(g, l[0] + l[2] - 5, l[1] + 1, l[3] - 2, scroll, Math.max(0, plan.zones().size() - listRows), plan.zones().size() * ROW,
			CopperLayout.inside(l, mouseX, mouseY));
		// the chosen zone
		if (nameBox.isMouseOver(mouseX, mouseY)) {
			whisper = Component.translatable("screen.aliveworkplace.city_plan.whisper.name");
		}
		CityPlan.Zone zone = zone();
		ItemStack icon = new ItemStack(zone == null ? ModItems.BLUEPRINT : styleIcon(zone.style()));
		g.renderItem(icon, x, styleButton.getY() - 1);
		drawButton(g, styleButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY, "screen.aliveworkplace.city_plan.whisper.style");
		drawButton(g, kindButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY, "screen.aliveworkplace.city_plan.whisper.kind");
		drawButton(g, renewButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY, "screen.aliveworkplace.city_plan.renew.tip");
		// Delete: neutral with the bad glyph, Redstone only under the mouse, so it never outshouts the copper New
		boolean overDelete = deleteButton.active && deleteButton.isMouseOver(mouseX, mouseY);
		CopperStyle.button(g, font, deleteButton.getMessage(), deleteButton.active ? "bad" : null, deleteButton.getX(), deleteButton.getY(),
			deleteButton.getWidth(), deleteButton.getHeight(), overDelete ? CopperStyle.Kind.DANGER : CopperStyle.Kind.NEUTRAL, overDelete, false,
			!deleteButton.active);
		if (deleteButton.isMouseOver(mouseX, mouseY)) {
			whisper = Component.translatable("screen.aliveworkplace.city_plan.whisper.delete");
		}
		// the legend: every kind's colour
		int[] legend = boxes.legend();
		if (legend[3] <= 0) {
			return;
		}
		int ly = legend[1];
		heading(g, Component.translatable("screen.aliveworkplace.city_plan.legend"), x, ly, w);
		int colW = w / 2;
		for (int i = 0; i < Math.min(8, kinds.size()); i++) {
			CityPlans.KindInfo kind = kinds.get(i);
			int kx = x + (i / 4) * colW;
			int ky = ly + 12 + (i % 4) * 9;
			g.fill(kx, ky, kx + 7, ky + 7, CopperStyle.VOID);
			g.fill(kx + 1, ky + 1, kx + 6, ky + 6, 0xFF000000 | kind.color());
			if (!kind.buildable()) {
				g.fill(kx + 1, ky + 5, kx + 2, ky + 6, 0xFF000000);
				g.fill(kx + 3, ky + 3, kx + 4, ky + 4, 0xFF000000);
				g.fill(kx + 5, ky + 1, kx + 6, ky + 2, 0xFF000000);
			}
			text(g, kindTitle(kind.id()), kx + 9, ky, colW - 10, CopperStyle.TUFF, false);
		}
	}

	/** Draws {@code text}; a label that must fit is recorded as it is, a name is cut to its room first. */
	private void text(GuiGraphics g, Component text, int x, int y, int room, int color, boolean mustFit) {
		String s = text.getString();
		if (!mustFit) {
			s = fit(s, room);
		}
		int w = font.width(s);
		g.drawString(font, s, x, y, color, false);
		drawn.add(new Drawn(s, x, y, w, room, mustFit));
	}

	/** {@code s}, cut short with an ellipsis to {@code room} pixels. */
	private String fit(String s, int room) {
		return CopperStyle.trim(font, s, room);
	}

	// --- the GUI-scale check (24.4) ------------------------------------------------------------------------------------

	/**
	 * What would look broken at this GUI scale: a label wider than its room, a button's words wider than the button,
	 * two texts or two widgets on top of each other, or anything off the screen. Empty when the screen is fine.
	 */
	public List<String> layoutProblems() {
		List<String> out = new ArrayList<>();
		for (Drawn d : drawn) {
			if (d.mustFit() && d.width() > d.room()) {
				out.add("'" + d.text() + "' is " + d.width() + " wide in " + d.room());
			}
			if (d.x() < 0 || d.y() < 0 || d.x() + d.width() > width || d.y() + 9 > height) {
				out.add("'" + d.text() + "' is off the screen");
			}
		}
		for (int i = 0; i < drawn.size(); i++) {
			for (int j = i + 1; j < drawn.size(); j++) {
				Drawn a = drawn.get(i);
				Drawn b = drawn.get(j);
				if (a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + 8 && b.y() < a.y() + 8) {
					out.add("'" + a.text() + "' overlaps '" + b.text() + "'");
				}
			}
		}
		List<AbstractWidget> widgets = new ArrayList<>();
		children().forEach(c -> {
			if (c instanceof AbstractWidget wd) {
				widgets.add(wd);
			}
		});
		for (int i = 0; i < widgets.size(); i++) {
			AbstractWidget a = widgets.get(i);
			// the style and kind buttons name a style or kind and cut it with an ellipsis (Copperline); the others must fit
			if (a instanceof Button && a != styleButton && a != kindButton && font.width(a.getMessage()) > a.getWidth() - 6) {
				out.add("button '" + a.getMessage().getString() + "' is " + font.width(a.getMessage()) + " wide in " + a.getWidth());
			}
			if (a.getX() < 0 || a.getY() < 0 || a.getRight() > width || a.getBottom() > height) {
				out.add("'" + a.getMessage().getString() + "' is off the screen");
			}
			for (int j = i + 1; j < widgets.size(); j++) {
				AbstractWidget b = widgets.get(j);
				if (a.getX() < b.getRight() && b.getX() < a.getRight() && a.getY() < b.getBottom() && b.getY() < a.getBottom()) {
					out.add("'" + a.getMessage().getString() + "' overlaps '" + b.getMessage().getString() + "'");
				}
			}
			for (Drawn d : drawn) {
				if (d.x() < a.getRight() && a.getX() < d.x() + d.width() && d.y() < a.getBottom() && a.getY() < d.y() + 8) {
					out.add("'" + d.text() + "' is under '" + a.getMessage().getString() + "'");
				}
			}
		}
		if (listRows < 3) {
			out.add("only " + listRows + " zone rows fit");
		}
		return out;
	}

	// --- names ---------------------------------------------------------------------------------------------------------

	private CityPlans.KindInfo kind(String id) {
		for (CityPlans.KindInfo k : kinds) {
			if (k.id().equals(id)) {
				return k;
			}
		}
		return null;
	}

	static Component kindTitle(String id) {
		String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
		return Component.translatableWithFallback("zone.aliveworkplace." + id.replace(':', '.'),
			name.isEmpty() ? id : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1));
	}

	private Component styleTitle(String style) {
		if (style.isEmpty()) {
			return Component.translatable("screen.aliveworkplace.city_plan.as_drawn");
		}
		return styles.stream().filter(s -> s.name().equals(style)).findFirst().map(CityPlans.StyleInfo::title).orElse(Component.literal(style));
	}

	private net.minecraft.world.item.Item styleIcon(String style) {
		return styles.stream().filter(s -> s.name().equals(style)).findFirst()
			.map(s -> BuiltInRegistries.ITEM.get(s.icon())).orElse(ModItems.BLUEPRINT);
	}
}
