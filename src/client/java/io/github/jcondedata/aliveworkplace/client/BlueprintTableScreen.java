package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Blueprint Table: a list of every blueprint on the server with its size and materials, a button
 * to take a copy, and an "upload" view listing build files in the player's own blueprints folder.
 */
public class BlueprintTableScreen extends Screen {
	private static final int LIST_BG = TableStyle.LIST_BG;
	private static final int ROW = TableStyle.ROW;

	private enum Mode { LIBRARY, FILES }

	private final BlockPos table;
	private List<TablePayloads.Entry> entries;
	private boolean canUpload;
	private final Map<ResourceLocation, List<TablePayloads.Material>> details = new HashMap<>();
	/** What each build brings, "Jobs: ..." and "Beds: N" (36.6a), from its details; by blueprint as shown. */
	private final Map<ResourceLocation, List<Component>> facts = new HashMap<>();
	/** 3D previews by blueprint (26.9), sent with the details; kept while the screen is open. */
	private final Map<ResourceLocation, TablePreview> previews = new HashMap<>();
	private int previewX, previewY, previewW, previewH;
	/** The styles each build can be built in, by build (26.11), from its details. */
	private final Map<ResourceLocation, List<TablePayloads.StyleChoice>> styles = new HashMap<>();
	/** The style chosen for the selected build ("" as drawn). */
	private String style = "";
	private static final int STYLE_CELL = TableStyle.CELL;
	/** Where the style row was drawn (its first cell; cells sit side by side), and how many cells: as drawn, then the styles. */
	private int styleRowX, styleRowY, styleCells;
	@Nullable
	private Component hoveredStyle;
	private boolean draggingPreview;
	private Mode mode = Mode.LIBRARY;
	@Nullable
	private ResourceLocation selectedId;
	@Nullable
	private Path selectedFile;
	@Nullable
	private ResourceLocation selectAfterRefresh;
	private Component status = Component.empty();
	private int statusColor = 0xFFAAAAAA;
	private boolean uploading;

	private int left, top, panelW, panelH;
	private LibraryList library;
	private FileList files;
	private Button takeButton, uploadViewButton, uploadButton, folderButton, backButton;
	/** The full materials list over the right side: opened by "+N" or "All materials", closed by Esc or Back. */
	private boolean allMaterials;
	private Button allMaterialsButton, allBackButton;
	private int allScroll;
	/** Rows of the short materials grid scrolled past with the mouse wheel. */
	private int gridScroll;
	private int gridX, gridY, gridW, gridH, moreX, moreY, moreW;
	private static final int ALL_ROW = 18;

	public BlueprintTableScreen(TablePayloads.Open open) {
		super(Component.translatable("block.aliveworkplace.blueprint_table"));
		this.table = open.table();
		this.entries = open.entries();
		this.canUpload = open.canUpload();
	}

	public BlockPos table() {
		return table;
	}

	/** The selected library entry, if any (for the screenshot harness). */
	@Nullable
	public ResourceLocation selected() {
		return selectedId;
	}

	// --- packets ---------------------------------------------------------------------------

	public void refresh(TablePayloads.Open open) {
		this.entries = open.entries();
		this.canUpload = open.canUpload();
		if (library != null) {
			library.fill();
			if (selectAfterRefresh != null) {
				select(selectAfterRefresh);
				selectAfterRefresh = null;
			}
		}
	}

	public void setDetails(TablePayloads.Details payload) {
		details.put(payload.id(), payload.materials());
		facts.put(payload.id(), io.github.jcondedata.aliveworkplace.table.BuildJobs.lines(payload.jobs(), payload.beds()));
		styles.put(BlueprintStyles.base(payload.id()), payload.styles());
	}

	/** The selected build as shown: in the chosen style, if any. */
	@Nullable
	private ResourceLocation shownId() {
		return selectedId == null ? null : BlueprintStyles.styled(selectedId, style);
	}

	/** The styles the selected build offers (none for a locked or "???" build, 26.10). */
	private List<TablePayloads.StyleChoice> selectedStyles() {
		TablePayloads.Entry entry = selectedEntry();
		return entry == null || !entry.unlocked() ? List.of() : styles.getOrDefault(entry.id(), List.of());
	}

	/** Chooses a style for the selected build ("" as drawn) and asks for its preview and materials. Also used by the screenshot harness. */
	public void chooseStyle(String name) {
		if (!name.isEmpty() && selectedStyles().stream().noneMatch(s -> s.name().equals(name))) {
			return;
		}
		if (!name.equals(style)) {
			gridScroll = 0; // another style is another list
			allScroll = 0;
		}
		style = name;
		ResourceLocation shown = shownId();
		if (shown != null && !details.containsKey(shown)) {
			ClientPlayNetworking.send(new TablePayloads.RequestDetails(shown));
		}
	}

	/** The chosen style ("" as drawn; for the screenshot harness). */
	public String style() {
		return style;
	}

	public void setPreview(TablePayloads.Preview payload) {
		previews.put(payload.id(), new TablePreview(payload));
	}

	/** Cells in the selected build's preview, or -1 while none has arrived (for the screenshot harness). */
	public int previewCells() {
		TablePreview preview = selectedId == null ? null : previews.get(shownId());
		return preview == null ? -1 : preview.count();
	}

	/** Blocks of the selected build's preview under the ground (a basement's depth, 36.1), or -1 while none has arrived (for the screenshot harness). */
	public int previewBelow() {
		TablePreview preview = selectedId == null ? null : previews.get(shownId());
		return preview == null ? -1 : preview.below();
	}

	/** Turns the selected build's preview as a mouse drag would (for the screenshot harness). */
	public void dragPreview(double dx, double dy) {
		TablePreview preview = selectedId == null ? null : previews.get(shownId());
		if (preview != null) {
			preview.drag(dx, dy);
		}
	}

	public void onUploadResult(TablePayloads.UploadResult result) {
		uploading = false;
		status = result.message();
		statusColor = result.ok() ? 0xFF7CFC7C : 0xFFFF7070;
		if (result.ok() && result.id().isPresent()) {
			mode = Mode.LIBRARY;
			selectAfterRefresh = result.id().get();
			updateVisibility();
		}
	}

	// --- layout ----------------------------------------------------------------------------

	@Override
	protected void init() {
		panelW = TableStyle.panelWidth(width);
		panelH = TableStyle.panelHeight(height);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		int listW = TableStyle.listWidth(panelW);
		int listTop = top + 22;
		int listH = panelH - 22 - 34;

		library = new LibraryList(minecraft, listW, listH, listTop);
		library.setX(left + 8);
		library.fill();
		addRenderableWidget(library);

		files = new FileList(minecraft, listW, listH, listTop);
		files.setX(left + 8);
		files.fill();
		addRenderableWidget(files);

		int by = top + panelH - 28;
		int bw = (panelW - 24) / 2;
		takeButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.take"), b -> take())
			.bounds(left + 8, by, bw, 20).build());
		uploadViewButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.upload_view"), b -> {
			mode = Mode.FILES;
			files.fill();
			updateVisibility();
		}).bounds(left + 16 + bw, by, bw, 20).build());

		int bw3 = (panelW - 32) / 3;
		uploadButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.upload"), b -> upload())
			.bounds(left + 8, by, bw3, 20).build());
		folderButton = addRenderableWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.open_folder"), b -> {
			Util.getPlatform().openFile(folder().toFile());
		}).bounds(left + 16 + bw3, by, bw3, 20).build());
		backButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> {
			mode = Mode.LIBRARY;
			updateVisibility();
		}).bounds(left + 24 + 2 * bw3, by, bw3, 20).build());
		// Placed in render (it sits beside the "Materials" heading, below the preview).
		Component all = Component.translatable("screen.aliveworkplace.table.all_materials");
		allMaterialsButton = addRenderableWidget(Button.builder(all, b -> showAllMaterials())
			.bounds(0, 0, font.width(all) + 10, 12).build());
		// Drawn after the panel (not with the other widgets) so the panel can't cover it.
		Component back = Component.translatable("gui.back");
		allBackButton = addWidget(Button.builder(back, b -> closeAllMaterials()).bounds(0, 0, font.width(back) + 12, 14).build());

		if (selectedId != null) {
			select(selectedId);
		} else if (!entries.isEmpty()) {
			select(entries.get(0).id());
		}
		updateVisibility();
	}

	private void updateVisibility() {
		boolean lib = mode == Mode.LIBRARY;
		library.visible = lib;
		takeButton.visible = lib;
		uploadViewButton.visible = lib;
		uploadViewButton.active = canUpload;
		files.visible = !lib;
		uploadButton.visible = !lib;
		folderButton.visible = !lib;
		backButton.visible = !lib;
		TablePayloads.Entry chosen = selectedEntry();
		takeButton.active = chosen != null && chosen.unlocked(); // a locked or "???" build isn't handed out (26.10)
		// The full list is of the build as shown, in the chosen style (26.11).
		List<TablePayloads.Material> materials = selectedId == null ? null : details.get(shownId());
		if (allMaterials && (!lib || materials == null)) {
			allMaterials = false;
		}
		allMaterialsButton.visible = lib && !allMaterials && materials != null && !materials.isEmpty();
		allBackButton.visible = allMaterials;
		uploadButton.active = !lib && selectedFile != null && !uploading && canUpload;
	}

	@Override
	public void tick() {
		updateVisibility();
	}

	// --- actions ---------------------------------------------------------------------------

	/** Selects a library entry and asks the server for its materials. Also used by the screenshot harness. */
	public void select(ResourceLocation id) {
		if (!id.equals(selectedId)) {
			style = "";
			gridScroll = 0;
			allScroll = 0;
		}
		selectedId = id;
		for (LibraryList.Row row : library.children()) {
			if (row.entry.id().equals(id)) {
				library.setSelected(row);
				library.scrollTo(row);
			}
		}
		TablePayloads.Entry entry = entry(id);
		if (!details.containsKey(id) && (entry == null || !entry.hidden())) {
			ClientPlayNetworking.send(new TablePayloads.RequestDetails(id));
		}
	}

	/** Opens the full materials list of the selected build (also used by the screenshot harness). */
	public void showAllMaterials() {
		if (mode == Mode.LIBRARY && selectedId != null && details.containsKey(shownId())) {
			allMaterials = true;
			allScroll = 0;
			updateVisibility();
		}
	}

	public void closeAllMaterials() {
		allMaterials = false;
		updateVisibility();
	}

	/** Whether the full materials list is open (for the screenshot harness). */
	public boolean allMaterialsOpen() {
		return allMaterials;
	}

	/** Materials known for the selected build, or -1 while none has arrived (for the screenshot harness). */
	public int materialCount() {
		List<TablePayloads.Material> m = selectedId == null ? null : details.get(shownId());
		return m == null ? -1 : m.size();
	}

	/** The selected build's job and bed lines as the pane shows them, one per line, or "" when it has neither (36.6a; for the screenshot harness). */
	public String selectedFacts() {
		List<Component> lines = selectedId == null ? null : facts.get(shownId());
		return lines == null ? "" : String.join("\n", lines.stream().map(Component::getString).toList());
	}

	private void take() {
		if (selectedId != null) {
			ClientPlayNetworking.send(new TablePayloads.Take(table, shownId()));
		}
	}

	/** Uploads the selected file (button action; public for the screenshot harness). */
	public void upload() {
		if (selectedFile == null || uploading) {
			return;
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(selectedFile);
		} catch (IOException e) {
			status = Component.translatable("screen.aliveworkplace.table.read_failed", selectedFile.getFileName().toString());
			statusColor = 0xFFFF7070;
			return;
		}
		if (bytes.length > BlueprintFiles.MAX_FILE_BYTES) {
			status = Component.translatable("message.aliveworkplace.import.error.file_too_big", bytes.length / 1024, BlueprintFiles.MAX_FILE_BYTES / 1024);
			statusColor = 0xFFFF7070;
			return;
		}
		String name = selectedFile.getFileName().toString();
		for (int offset = 0; offset < bytes.length; offset += TablePayloads.UPLOAD_CHUNK) {
			byte[] chunk = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + TablePayloads.UPLOAD_CHUNK));
			ClientPlayNetworking.send(new TablePayloads.UploadChunk(table, name, bytes.length, offset, chunk));
		}
		uploading = true;
		status = Component.translatable("screen.aliveworkplace.table.uploading", name);
		statusColor = 0xFFE0E0E0;
	}

	/** {@code .minecraft/blueprints} (or the instance folder in launchers like CurseForge). */
	public static Path folder() {
		Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("blueprints");
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
			// shown as an empty list
		}
		return dir;
	}

	// --- rendering -------------------------------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		long unlocked = entries.stream().filter(TablePayloads.Entry::unlocked).count();
		Component sub = mode == Mode.LIBRARY
			? unlocked < entries.size() ? Component.translatable("screen.aliveworkplace.table.unlocked_count", unlocked, entries.size())
				: Component.translatable("screen.aliveworkplace.table.library_count", entries.size())
			: Component.translatable("screen.aliveworkplace.table.files_title");
		TableStyle.header(g, font, title, sub, left, top, panelW);

		int dx = library.getX() + library.getWidth() + 10;
		int dw = left + panelW - 8 - dx;
		int dy = top + 24;
		if (mode == Mode.LIBRARY && allMaterials) {
			renderAllMaterials(g, dx, top + 22, dw, top + panelH - 34 - (top + 22), mouseX, mouseY, partialTick);
		} else if (mode == Mode.LIBRARY) {
			renderDetails(g, dx, dy, dw, mouseX, mouseY);
			if (hoveredStyle != null) {
				g.renderTooltip(font, List.of(hoveredStyle.getVisualOrderText(), Component.translatable("screen.aliveworkplace.table.style_hint")
					.withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
			}
		} else {
			renderFileHelp(g, dx, dy, dw);
		}
		if (!status.getString().isEmpty()) {
			int sy = top + panelH - 42;
			for (var line : font.split(status, panelW - 16)) {
				g.drawString(font, line, left + 8, sy, statusColor, false);
				sy += 10;
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		TableStyle.panel(g, left, top, panelW, panelH);
	}

	private void renderDetails(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
		styleCells = 0;
		hoveredStyle = null;
		TablePayloads.Entry entry = selectedEntry();
		if (entry == null) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.empty"), x, y, w, 0xFFAAAAAA);
			return;
		}
		if (entry.hidden()) {
			// A rare build not unlocked yet (26.10): like a hidden achievement, nothing but how to come by it.
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.hidden_name"), x, y, 0xFF6A6A6A, false);
			y += 12;
			g.drawWordWrap(font, Component.translatable(entry.hint()), x, y, w, 0xFFB08850);
			y += 9 * font.split(Component.translatable(entry.hint()), w).size() + 4;
			g.fill(x, y, x + w, y + 60, 0xC0000000);
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.hidden_help"), x + 4, y + 6, w - 8, 0xFF777777);
			return;
		}
		g.drawString(font, font.plainSubstrByWidth(Blueprints.displayName(entry.id()).getString(), w), x, y, entry.locked() ? 0xFF8A8A8A : 0xFFFFFFFF, false);
		y += 12;
		TablePreview shownPreview = previews.get(shownId());
		int below = shownPreview == null ? 0 : shownPreview.below();
		if (below > 0) { // a basement (36.1): wrapped, as "and N below ground" can run past the pane
			Component size = Component.translatable("screen.aliveworkplace.table.size_below", entry.sizeX(), entry.sizeY(), entry.sizeZ(), entry.blocks(), below);
			g.drawWordWrap(font, size, x, y, w, 0xFF9AA8BA);
			y += 10 * font.split(size, w).size();
		} else {
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.size", entry.sizeX(), entry.sizeY(), entry.sizeZ(), entry.blocks()),
				x, y, 0xFF9AA8BA, false);
			y += 10;
		}
		// What jobs it brings and its beds (36.6a), wrapped; a build with neither shows nothing here.
		List<Component> lines = facts.getOrDefault(shownId(), List.of());
		if (!lines.isEmpty()) {
			y += 1;
			for (Component line : lines) {
				g.drawWordWrap(font, line, x, y, w, 0xFFC8C0A8);
				y += 9 * font.split(line, w).size();
			}
			y += 2;
		}
		if (!entry.hint().isEmpty()) {
			// Locked (or a Wonder, raised from its plan): how to get it, where the id goes for the rest.
			Component how = entry.locked()
				? Component.translatable("screen.aliveworkplace.table.locked_hint", Component.translatable(entry.hint()))
				: Component.translatable(entry.hint());
			g.drawString(font, font.plainSubstrByWidth(how.getString(), w), x, y, 0xFFE0A050, false);
		} else {
			g.drawString(font, font.plainSubstrByWidth(entry.id().toString(), w), x, y, 0xFF5E6B7C, false);
		}
		y += 12;
		// The build in 3D (26.9), above its materials; it keeps room for two rows of them.
		previewX = x;
		previewY = y;
		previewW = w;
		List<TablePayloads.StyleChoice> choices = selectedStyles();
		int styleRow = choices.isEmpty() ? 0 : STYLE_CELL + 3;
		previewH = Math.max(40, Math.min(120, top + panelH - 46 - y - 11 - 40 - 4 - styleRow));
		g.fill(previewX, previewY, previewX + previewW, previewY + previewH, LIST_BG);
		TablePreview preview = previews.get(shownId());
		if (preview == null) {
			Component loading = Component.translatable("screen.aliveworkplace.table.preview_loading");
			g.drawString(font, loading, x + (w - font.width(loading)) / 2, y + previewH / 2 - 4, 0xFF777777, false);
		} else {
			preview.render(g, previewX, previewY, previewW, previewH);
			if (entry.locked()) {
				// A locked build's preview, dimmed (26.10), over whatever depth the 3D drawing left.
				g.pose().pushPose();
				g.pose().translate(0, 0, 400);
				g.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0x99101010);
				g.pose().popPose();
			}
			if (preview.count() > 0) {
				g.drawString(font, Component.translatable("screen.aliveworkplace.table.preview_drag"), x + 3, y + previewH - 10, 0xFF5E6B7C, false);
			}
		}
		y += previewH + 4;
		if (!choices.isEmpty()) {
			// The styles (26.11): as drawn, then each style the build takes; the chosen one framed in gold, its name beside.
			styleRowX = x;
			styleRowY = y;
			styleCells = Math.min(choices.size() + 1, Math.max(1, (w - 60) / STYLE_CELL));
			Component chosen = Component.translatable("style.aliveworkplace.as_drawn");
			for (int i = 0; i < styleCells; i++) {
				TablePayloads.StyleChoice choice = i == 0 ? null : choices.get(i - 1);
				String name = choice == null ? "" : choice.name();
				Component title = choice == null ? Component.translatable("style.aliveworkplace.as_drawn") : choice.title();
				int cx = x + i * STYLE_CELL;
				boolean on = name.equals(style);
				boolean over = mouseX >= cx && mouseX < cx + STYLE_CELL - 2 && mouseY >= y && mouseY < y + STYLE_CELL - 2;
				TableStyle.cell(g, cx, y, on, over);
				ItemStack icon = new ItemStack(choice == null ? net.minecraft.world.item.Items.SPRUCE_LOG : BuiltInRegistries.ITEM.get(choice.icon()));
				g.renderItem(icon, cx + 1, y + 1);
				if (on) {
					chosen = title;
				}
				if (over) {
					hoveredStyle = title;
				}
			}
			int tx = x + styleCells * STYLE_CELL + 2;
			g.drawString(font, font.plainSubstrByWidth(chosen.getString(), x + w - tx), tx, y + 5, TableStyle.TITLE, false);
			y += styleRow;
		}
		TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.table.materials"), x, y);
		allMaterialsButton.setPosition(x + w - allMaterialsButton.getWidth(), y - 2);
		y += 11;

		List<TablePayloads.Material> materials = details.get(shownId());
		if (materials == null) {
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.loading"), x, y, 0xFF777777, false);
			return;
		}
		// Each cell is an icon and its count beside it, as wide as the widest count needs (3-digit counts used to touch
		// the next icon).
		int countWidth = font.width("99");
		for (TablePayloads.Material m : materials) {
			countWidth = Math.max(countWidth, font.width(countText(m.count())));
		}
		int cell = 16 + 2 + countWidth + 5;
		int cols = Math.max(1, w / cell);
		int bottom = top + panelH - 46;
		int maxRows = Math.max(1, (bottom - y) / 20);
		// The mouse wheel over the grid scrolls it a row at a time; "+N" counts what's after the last cell shown.
		int totalRows = (materials.size() + cols - 1) / cols;
		gridScroll = Math.max(0, Math.min(gridScroll, totalRows - maxRows));
		gridX = x;
		gridY = y;
		gridW = w;
		gridH = maxRows * 20;
		int first = gridScroll * cols;
		int shown = Math.min(materials.size() - first, cols * maxRows);
		if (first + shown < materials.size()) {
			shown = Math.max(0, shown - 1); // leave room for the "+N more" cell
		}
		moreX = -1;
		ItemStack hovered = null;
		for (int k = 0; k < shown; k++) {
			TablePayloads.Material m = materials.get(first + k);
			int cx = x + (k % cols) * cell;
			int cy = y + (k / cols) * 20;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(m.item()));
			g.renderItem(stack, cx, cy);
			g.drawString(font, countText(m.count()), cx + 18, cy + 5, 0xFFE8E8E8, true);
			if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
				hovered = stack;
			}
		}
		if (first + shown < materials.size()) {
			int cx = x + (shown % cols) * cell;
			int cy = y + (shown / cols) * 20;
			moreX = cx;
			moreY = cy;
			moreW = cell;
			String more = "+" + (materials.size() - first - shown);
			boolean over = mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + 16;
			g.drawString(font, more, cx + 2, cy + 5, over ? 0xFFFFFFFF : 0xFF9AA8BA, false);
			if (over) {
				g.fill(cx + 2, cy + 14, cx + 2 + font.width(more), cy + 15, 0xFFFFFFFF);
			}
		}
		if (hovered != null) {
			g.renderTooltip(font, hovered, mouseX, mouseY);
		}
	}

	/** Every material of the selected build, biggest first: icon, count and name, scrolled with the wheel. */
	private void renderAllMaterials(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
		List<TablePayloads.Material> materials = details.get(shownId());
		g.fill(x - 4, y, x + w + 4, y + h, TableStyle.OVERLAY_BG);
		TableStyle.rule(g, x - 4, y, w + 8);
		TableStyle.rule(g, x - 4, y + h - 1, w + 8);
		if (materials == null) {
			// Another build was just picked and its list hasn't arrived yet (a click here used to crash the client).
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.loading"), x, y + 5, 0xFF777777, false);
			return;
		}
		int total = 0;
		for (TablePayloads.Material m : materials) {
			total += m.count();
		}
		g.drawString(font, Component.translatable("screen.aliveworkplace.table.all_materials_title", materials.size(), total), x, y + 5, TableStyle.TITLE, false);
		allBackButton.setPosition(x + w - allBackButton.getWidth(), y + 2);
		int listY = y + 18;
		int listH = h - 20;
		int maxScroll = Math.max(0, materials.size() * ALL_ROW - listH);
		allScroll = Math.max(0, Math.min(allScroll, maxScroll));
		int countW = 0;
		for (TablePayloads.Material m : materials) {
			countW = Math.max(countW, font.width(String.valueOf(m.count())));
		}
		g.enableScissor(x - 4, listY, x + w + 4, listY + listH);
		for (int i = allScroll / ALL_ROW; i < materials.size(); i++) {
			int ry = listY + i * ALL_ROW - allScroll;
			if (ry > listY + listH) {
				break;
			}
			TablePayloads.Material m = materials.get(i);
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(m.item()));
			if (i % 2 == 1) {
				g.fill(x - 2, ry, x + w - 6, ry + ALL_ROW, 0x20FFFFFF);
			}
			g.renderItem(stack, x, ry + 1);
			String count = String.valueOf(m.count());
			g.drawString(font, count, x + 20 + countW - font.width(count), ry + 5, 0xFFE8E8E8, false);
			int nameX = x + 26 + countW;
			g.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), x + w - 8 - nameX), nameX, ry + 5, 0xFFCCCCCC, false);
		}
		g.disableScissor();
		TableStyle.thinScrollbar(g, x + w - 3, listY, listH, allScroll, maxScroll, materials.size() * ALL_ROW);
		allBackButton.render(g, mouseX, mouseY, partialTick);
	}

	private boolean overGrid(double mx, double my) {
		return mode == Mode.LIBRARY && !allMaterials && gridW > 0 && mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (allMaterials && mx >= library.getX() + library.getWidth()) {
			allScroll -= (int) Math.round(scrollY * ALL_ROW * 2);
			return true;
		}
		if (overGrid(mx, my) && selectedId != null && details.containsKey(shownId())) {
			gridScroll = Math.max(0, gridScroll - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (allMaterials && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			closeAllMaterials();
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	private static String countText(int count) {
		return count >= 1000 ? (count / 1000) + "k" : String.valueOf(count);
	}

	private void renderFileHelp(GuiGraphics g, int x, int y, int w) {
		g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.files_help"), x, y, w, 0xFFCCCCCC);
		if (selectedFile != null) {
			int fy = top + panelH - 70;
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.selected_file", selectedFile.getFileName().toString()),
				x, fy, 0xFFFFFFFF, false);
		}
	}

	@Nullable
	private TablePayloads.Entry selectedEntry() {
		return selectedId == null ? null : entry(selectedId);
	}

	@Nullable
	private TablePayloads.Entry entry(ResourceLocation id) {
		for (TablePayloads.Entry e : entries) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	private boolean overPreview(double mx, double my) {
		return mode == Mode.LIBRARY && previewW > 0 && mx >= previewX && mx < previewX + previewW && my >= previewY && my < previewY + previewH;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (allMaterials && allBackButton.mouseClicked(mx, my, button)) {
			return true;
		}
		// The style row is covered while the full list is open (styleCells keeps the last drawn row).
		if (button == 0 && mode == Mode.LIBRARY && !allMaterials && styleCells > 0 && my >= styleRowY && my < styleRowY + STYLE_CELL - 2
			&& mx >= styleRowX && mx < styleRowX + styleCells * STYLE_CELL) {
			int i = (int) ((mx - styleRowX) / STYLE_CELL);
			List<TablePayloads.StyleChoice> choices = selectedStyles();
			if (i <= choices.size()) {
				chooseStyle(i == 0 ? "" : choices.get(i - 1).name());
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
			}
			return true;
		}
		if (button == 0 && overGrid(mx, my) && moreX >= 0 && mx >= moreX && mx < moreX + moreW && my >= moreY && my < moreY + 16) {
			showAllMaterials();
			return true;
		}
		if (button == 0 && !allMaterials && overPreview(mx, my) && selectedId != null && previews.containsKey(shownId())) {
			draggingPreview = true;
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingPreview) {
			dragPreview(dx, dy);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (draggingPreview) {
			draggingPreview = false;
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// --- lists -----------------------------------------------------------------------------

	private class LibraryList extends ObjectSelectionList<LibraryList.Row> {
		LibraryList(Minecraft mc, int width, int height, int y) {
			super(mc, width, height, y, ROW);
		}

		void fill() {
			clearEntries();
			for (TablePayloads.Entry e : entries) {
				addEntry(new Row(e));
			}
		}

		@Override
		public int getRowWidth() {
			return width - 12;
		}

		@Override
		protected int getScrollbarPosition() {
			return getX() + width - 6;
		}

		@Override
		protected void renderListBackground(GuiGraphics g) {
			TableStyle.listBackground(g, getX(), getY(), width, height);
		}

		@Override
		protected void renderListSeparators(GuiGraphics g) {
		}

		void scrollTo(Row row) {
			ensureVisible(row);
		}

		class Row extends ObjectSelectionList.Entry<Row> {
			final TablePayloads.Entry entry;

			Row(TablePayloads.Entry entry) {
				this.entry = entry;
			}

			@Override
			public void render(GuiGraphics g, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
				if (entry.hidden()) {
					// "???" (26.10): a dark row with no name.
					g.fill(x, y - 1, x + width - 2, y + height - 1, 0x60000000);
					g.drawString(font, Component.translatable("screen.aliveworkplace.table.hidden_name"), x + 2, y + 1, 0xFF5A5A5A, false);
					g.drawString(font, font.plainSubstrByWidth(Component.translatable(entry.hint()).getString(), width - 4), x + 2, y + 10, 0xFF4A4A4A, false);
					return;
				}
				if (entry.locked()) {
					// Greyed out, with how to get it (26.10).
					g.drawString(font, font.plainSubstrByWidth(Blueprints.displayName(entry.id()).getString(), width - 4), x + 2, y + 1, 0xFF8A8A8A, false);
					g.drawString(font, font.plainSubstrByWidth(Component.translatable(entry.hint()).getString(), width - 4), x + 2, y + 10, 0xFF8C6A3C, false);
					return;
				}
				g.drawString(font, font.plainSubstrByWidth(Blueprints.displayName(entry.id()).getString(), width - 4), x + 2, y + 1, 0xFFFFFFFF, false);
				String size = entry.sizeX() + "×" + entry.sizeY() + "×" + entry.sizeZ();
				String source = entry.id().getPath().startsWith("uploads/") ? " · " + Component.translatable("screen.aliveworkplace.table.uploaded").getString() : "";
				g.drawString(font, size + source, x + 2, y + 10, 0xFF7F8C9E, false);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				select(entry.id());
				return true;
			}

			@Override
			public Component getNarration() {
				return entry.hidden() ? Component.translatable("screen.aliveworkplace.table.hidden_name") : Blueprints.displayName(entry.id());
			}
		}
	}

	private class FileList extends ObjectSelectionList<FileList.Row> {
		FileList(Minecraft mc, int width, int height, int y) {
			super(mc, width, height, y, ROW);
		}

		void fill() {
			clearEntries();
			List<Path> found = new ArrayList<>();
			try (Stream<Path> stream = Files.list(folder())) {
				stream.filter(Files::isRegularFile).filter(p -> BlueprintImporter.hasSupportedExtension(p.getFileName().toString()))
					.sorted().forEach(found::add);
			} catch (IOException ignored) {
				// empty list
			}
			for (Path p : found) {
				addEntry(new Row(p));
			}
		}

		@Override
		public int getRowWidth() {
			return width - 12;
		}

		@Override
		protected int getScrollbarPosition() {
			return getX() + width - 6;
		}

		@Override
		protected void renderListBackground(GuiGraphics g) {
			TableStyle.listBackground(g, getX(), getY(), width, height);
		}

		@Override
		protected void renderListSeparators(GuiGraphics g) {
		}

		class Row extends ObjectSelectionList.Entry<Row> {
			final Path path;
			final long size;

			Row(Path path) {
				this.path = path;
				long s;
				try {
					s = Files.size(path);
				} catch (IOException e) {
					s = 0;
				}
				this.size = s;
			}

			@Override
			public void render(GuiGraphics g, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
				g.drawString(font, font.plainSubstrByWidth(path.getFileName().toString(), width - 4), x + 2, y + 1, 0xFFFFFFFF, false);
				g.drawString(font, Math.max(1, size / 1024) + " KB", x + 2, y + 10, 0xFF7F8C9E, false);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				FileList.this.setSelected(this);
				selectedFile = path;
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal(path.getFileName().toString());
			}
		}
	}

	/** For the screenshot harness: switch to the upload view. */
	public void showFiles() {
		mode = Mode.FILES;
		files.fill();
		if (!files.children().isEmpty()) {
			files.setSelected(files.children().get(0));
			selectedFile = files.children().get(0).path;
		}
		updateVisibility();
	}
}
