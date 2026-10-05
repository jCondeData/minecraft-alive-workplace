package io.github.jcondedata.aliveworkplace.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;

/**
 * The City Plan screen (ROADMAP 27.3): the village map with the plan's grid over it, each zone tinted in its kind's
 * colour and named, Keep Clear hatched, build sites going up as outlines; a side panel to add, rename, delete and set
 * up zones, a brush (one cell, or drag an area), an eraser, undo and a legend. Every change is a packet the server
 * checks ({@link CityPlans.Edit}); the server sends the plan back after each.
 */
public class CityPlanScreen extends Screen {
	private static final ResourceLocation MAP_TEXTURE = AliveWorkplace.id("city_plan_map");
	private static final int PAD = 6;
	private static final int ROW = 12;
	private static final int BUTTON_H = 14;
	private static final int PANEL_BG = 0xE0101418;
	private static final int PANEL_EDGE = 0xFF3A4C66;
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
	private int mapX, mapY, mapSize, panelX, panelY, panelW, panelH, listTop, listRows;
	private EditBox nameBox;
	private Button newButton, kindButton, styleButton, renewButton, deleteButton, brushButton, areaButton, eraserButton, undoButton,
		roadButton, wallButton, optionButton, roadStyleButton;
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

	@Override
	protected void init() {
		panelW = Math.max(150, Math.min(190, width * 38 / 100));
		panelX = width - PAD - panelW;
		panelY = PAD;
		panelH = height - 2 * PAD;
		int availW = width - panelW - 3 * PAD;
		int availH = height - 2 * PAD - 12 - 14;
		mapSize = Math.max(CityPlan.GRID, Math.min(availW, availH) / CityPlan.GRID * CityPlan.GRID);
		mapX = PAD + (availW - mapSize) / 2;
		mapY = PAD + 12;

		int x = panelX + 4;
		int w = panelW - 8;
		newButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.new"), b -> newZone())
			.bounds(panelX + panelW - 4 - 44, panelY + 14, 44, BUTTON_H).build());
		listTop = panelY + 14 + BUTTON_H + 2;

		int legendH = 10 + 4 * 9;
		int y = panelY + panelH - 4 - legendH - (BUTTON_H + 2) * 5 - 18;
		listRows = Math.max(1, (y - 2 - listTop) / ROW);

		nameBox = addRenderableWidget(new EditBox(font, x + 1, y, w - 2, BUTTON_H, Component.translatable("screen.aliveworkplace.city_plan.name")));
		nameBox.setMaxLength(CityPlans.MAX_NAME);
		y += BUTTON_H + 4;
		styleButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleStyle()).bounds(x + 18, y, w - 18, BUTTON_H).build());
		y += BUTTON_H + 2;
		kindButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleKind()).bounds(x, y, w, BUTTON_H).build());
		y += BUTTON_H + 2;
		int half = (w - 2) / 2;
		renewButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleRenew()).bounds(x, y, half, BUTTON_H)
			.tooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.renew.tip"))).build());
		deleteButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.delete"), b -> deleteZone())
			.bounds(x + half + 2, y, w - half - 2, BUTTON_H).build());
		y += BUTTON_H + 2;
		int q = (w - 6) / 4;
		brushButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.brush"), b -> setTool(Tool.BRUSH))
			.bounds(x, y, q, BUTTON_H).tooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.brush.tip"))).build());
		areaButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.area"), b -> setTool(Tool.AREA))
			.bounds(x + q + 2, y, q, BUTTON_H).tooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.area.tip"))).build());
		eraserButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.eraser"), b -> setTool(Tool.ERASER))
			.bounds(x + 2 * (q + 2), y, q, BUTTON_H).tooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.eraser.tip"))).build());
		undoButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.undo"), b -> undo())
			.bounds(x + 3 * (q + 2), y, w - 3 * (q + 2), BUTTON_H).build());
		y += BUTTON_H + 2;
		roadButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.road"), b -> setTool(Tool.ROAD))
			.bounds(x, y, q, BUTTON_H).tooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.road.tip"))).build());
		wallButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.city_plan.wall"), b -> setTool(Tool.WALL))
			.bounds(x + q + 2, y, q, BUTTON_H).tooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.wall.tip"))).build());
		optionButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleOption())
			.bounds(x + 2 * (q + 2), y, q, BUTTON_H).build());
		roadStyleButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleRoadStyle())
			.bounds(x + 3 * (q + 2), y, w - 3 * (q + 2), BUTTON_H).build());

		buildTexture();
		refreshWidgets();
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
		undoButton.active = mayEdit && undoSteps > 0;
		brushButton.active = mayEdit && tool != Tool.BRUSH;
		areaButton.active = mayEdit && tool != Tool.AREA;
		eraserButton.active = mayEdit && tool != Tool.ERASER;
		roadButton.active = mayEdit && tool != Tool.ROAD;
		wallButton.active = mayEdit && tool != Tool.WALL;
		optionButton.active = mayEdit && (tool == Tool.ROAD || tool == Tool.WALL);
		roadStyleButton.active = mayEdit && tool == Tool.ROAD;
		if (tool == Tool.WALL) {
			optionButton.setMessage(Component.translatable(wallClosed ? "screen.aliveworkplace.city_plan.wall.closed" : "screen.aliveworkplace.city_plan.wall.open"));
			optionButton.setTooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.wall.closed.tip")));
		} else {
			optionButton.setMessage(Component.translatable("screen.aliveworkplace.city_plan.width." + roadWidth));
			optionButton.setTooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.width.tip")));
		}
		roadStyleButton.setMessage(Component.empty());
		roadStyleButton.setTooltip(Tooltip.create(Component.translatable("screen.aliveworkplace.city_plan.road_style",
			roadStyle.isEmpty() ? Component.translatable("screen.aliveworkplace.city_plan.road_style.zone") : styleTitle(roadStyle))));
		kindButton.setMessage(Component.translatable("screen.aliveworkplace.city_plan.kind",
			zone == null ? Component.literal("-") : kindTitle(zone.kind())));
		styleButton.setMessage(Component.translatable("screen.aliveworkplace.city_plan.style",
			zone == null ? Component.literal("-") : styleTitle(zone.style())));
		renewButton.setMessage(Component.translatable(zone != null && zone.renew() ? "screen.aliveworkplace.city_plan.renew.on"
			: "screen.aliveworkplace.city_plan.renew.off"));
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
		if (mx >= panelX && mx < panelX + panelW && my >= listTop && my < listTop + listRows * ROW) {
			int row = (int) ((my - listTop) / ROW) + scroll;
			if (row < plan.zones().size()) {
				select(row);
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
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
		if (mx >= panelX && mx < panelX + panelW && my >= listTop && my < listTop + listRows * ROW) {
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
		g.fill(mapX - 3, mapY - 3, mapX + mapSize + 3, mapY + mapSize + 3, PANEL_BG);
		g.renderOutline(mapX - 3, mapY - 3, mapSize + 6, mapSize + 6, PANEL_EDGE);
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
		g.renderOutline(panelX, panelY, panelW, panelH, PANEL_EDGE);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		drawn.clear();
		super.render(g, mouseX, mouseY, partialTick);
		renderMap(g, mouseX, mouseY);
		renderPanel(g, mouseX, mouseY);
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
		text(g, title, mapX, mapY - 11, mapSize, 0xFFFFFFFF, false);
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
		List<int[]> boxes = new ArrayList<>();
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
			if (boxes.stream().anyMatch(b -> b[0] < box[2] && box[0] < b[2] && b[1] < box[3] && box[1] < b[3])) {
				continue;
			}
			boxes.add(box);
			g.fill(box[0], box[1], box[2], box[3], 0x90000000);
			text(g, Component.literal(name), x, y, w, 0xFFFFFFFF, false);
		}
		// under the map: what the pointer is over, or why nothing can be changed
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
		text(g, status, mapX, mapY + mapSize + 5, mapSize, 0xFFC8C8C8, false);
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
		int x = panelX + 4;
		int w = panelW - 8;
		text(g, village, x, panelY + 4, w, 0xFFFFE080, false);
		text(g, Component.translatable("screen.aliveworkplace.city_plan.zones", plan.zones().size(), CityPlan.MAX_ZONES), x, panelY + 17,
			w - newButton.getWidth() - 4, 0xFFC8C8C8, true);
		// the zones
		g.fill(x, listTop, x + w, listTop + listRows * ROW, 0x60000000);
		for (int row = 0; row < listRows && row + scroll < plan.zones().size(); row++) {
			int i = row + scroll;
			CityPlan.Zone zone = plan.zones().get(i);
			int y = listTop + row * ROW;
			if (i == selected) {
				g.fill(x, y, x + w, y + ROW, 0x50FFFFFF);
			}
			CityPlans.KindInfo kind = kind(zone.kind());
			g.fill(x + 2, y + 2, x + 10, y + 10, 0xFF000000);
			g.fill(x + 3, y + 3, x + 9, y + 9, 0xFF000000 | (kind == null ? 0x808080 : kind.color()));
			String cells = String.valueOf(zone.cells().cardinality());
			int cw = font.width(cells);
			text(g, Component.literal(cells), x + w - 3 - cw, y + 2, cw, 0xFF909090, true);
			text(g, Component.literal(fit(zone.name(), w - 18 - cw - 4)), x + 13, y + 2, w - 18 - cw - 4, 0xFFFFFFFF, true);
		}
		if (plan.zones().isEmpty()) {
			text(g, Component.translatable("screen.aliveworkplace.city_plan.no_zones"), x + 3, listTop + 2, w - 6, 0xFF909090, true);
		}
		if (plan.zones().size() > listRows) {
			int barH = Math.max(6, listRows * ROW * listRows / plan.zones().size());
			int barY = listTop + (listRows * ROW - barH) * scroll / Math.max(1, plan.zones().size() - listRows);
			g.fill(x + w - 2, barY, x + w, barY + barH, 0xFFA0A0A0);
		}
		// the style's icon beside its button
		CityPlan.Zone zone = zone();
		ItemStack icon = new ItemStack(zone == null ? ModItems.BLUEPRINT : styleIcon(zone.style()));
		g.renderItem(icon, x, styleButton.getY() - 1);
		// the new road's style, as an icon on its button (the zone's own: a path block)
		ItemStack roadIcon = new ItemStack(roadStyle.isEmpty() ? net.minecraft.world.item.Items.DIRT_PATH : styleIcon(roadStyle));
		g.pose().pushPose();
		g.pose().translate(roadStyleButton.getX() + roadStyleButton.getWidth() / 2f - 6, roadStyleButton.getY() + 1, 0);
		g.pose().scale(0.75f, 0.75f, 1f);
		g.renderItem(roadIcon, 0, 0);
		g.pose().popPose();
		// the legend: every kind's colour
		int ly = panelY + panelH - 4 - 4 * 9 - 10;
		text(g, Component.translatable("screen.aliveworkplace.city_plan.legend"), x, ly, w, 0xFFC8C8C8, true);
		int colW = w / 2;
		for (int i = 0; i < Math.min(8, kinds.size()); i++) {
			CityPlans.KindInfo kind = kinds.get(i);
			int kx = x + (i / 4) * colW;
			int ky = ly + 10 + (i % 4) * 9;
			g.fill(kx, ky, kx + 7, ky + 7, 0xFF000000);
			g.fill(kx + 1, ky + 1, kx + 6, ky + 6, 0xFF000000 | kind.color());
			if (!kind.buildable()) {
				g.fill(kx + 1, ky + 5, kx + 2, ky + 6, 0xFF000000);
				g.fill(kx + 3, ky + 3, kx + 4, ky + 4, 0xFF000000);
				g.fill(kx + 5, ky + 1, kx + 6, ky + 2, 0xFF000000);
			}
			text(g, kindTitle(kind.id()), kx + 9, ky, colW - 10, 0xFFE0E0E0, true);
		}
	}

	/** Draws {@code text}; a label that must fit is recorded as it is, a name is cut to its room first. */
	private void text(GuiGraphics g, Component text, int x, int y, int room, int color, boolean mustFit) {
		String s = text.getString();
		if (!mustFit) {
			s = fit(s, room);
		}
		int w = font.width(s);
		g.drawString(font, s, x, y, color, true);
		drawn.add(new Drawn(s, x, y, w, room, mustFit));
	}

	/** {@code s}, cut short with "..." to {@code room} pixels. */
	private String fit(String s, int room) {
		if (font.width(s) <= room) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, room - font.width("..."))) + "...";
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
			if (a instanceof Button && font.width(a.getMessage()) > a.getWidth() - 6) {
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
