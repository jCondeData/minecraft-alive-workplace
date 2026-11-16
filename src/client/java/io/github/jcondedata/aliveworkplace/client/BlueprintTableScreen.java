package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableSearch;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout.View;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Blueprint Table: a list of every blueprint on the server with its size and materials, a button
 * to take a copy, and an "upload" view listing build files in the player's own blueprints folder. A search box above
 * the list (36.10) narrows it by name, id or job as the player types ({@link TableSearch}). The Stored tab (36.13) lists the
 * bought blueprints kept in this table, with how many of each, to store more in it or take one back out.
 *
 * <p>Drawn in Copperline like the Blueprint Book board (48.1, {@link BlueprintBookLayout}): the copper plaque, the lintel
 * with the Library and Stored tabs, the style rail, the list with its NAME and SIZE heading, the build's 3D preview, size
 * and materials (each slot framed in Redstone when the player doesn't carry it, with the word beside), one copper button
 * per view and the whisper line saying what the thing under the mouse does. The buttons, tabs and search box are the
 * game's own widgets taking the clicks and keys; the screen draws them in Copperline's parts.
 */
public class BlueprintTableScreen extends Screen {
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
	private boolean draggingPreview;
	private View mode = View.LIBRARY;
	@Nullable
	private ResourceLocation selectedId;
	@Nullable
	private Path selectedFile;
	@Nullable
	private ResourceLocation selectAfterRefresh;
	private Component status = Component.empty();
	@Nullable
	private CopperStyle.Status statusKind;
	private boolean uploading;

	private BlueprintBookLayout.Boxes boxes;
	private int left, top, panelW, panelH;
	/** The search box over the library (36.10), what's typed in it and the entries it lets through, in the list's order. */
	private EditBox search;
	private String query = "";
	private List<TablePayloads.Entry> shown = List.of();
	/** The rows scrolled past in each list. */
	private int libScroll, storedScroll, filesScroll;
	private List<Path> fileItems = List.of();
	private Button takeButton, uploadViewButton, uploadButton, folderButton, backButton;
	/** The full materials list over the right side: opened by "+N" or "All materials", closed by Esc or Back. */
	private boolean allMaterials;
	private Button allMaterialsButton, allBackButton;
	private int allScroll;
	/** Materials scrolled past in the short row of slots, with the mouse wheel. */
	private int gridScroll;
	private int gridX, gridY, gridW, gridH, moreX, moreY, moreW;
	private static final int ALL_ROW = 18;
	/** The blueprints stored in this table (36.13), how many it holds at most, and the one picked in the Stored tab. */
	private List<TablePayloads.Stored> stored;
	private int capacity;
	@Nullable
	private ItemStack selectedStored;
	private Button libraryTab, storedTab, takeOutButton, storeHeldButton, storeAllButton;
	/** The empty library's button (Upload a File...), where it was drawn, or null. */
	@Nullable
	private int[] emptyAction;
	/** What the whisper line says about the thing under the mouse this frame. */
	private Component whisper = Component.empty();

	public BlueprintTableScreen(TablePayloads.Open open) {
		super(Component.translatable("block.aliveworkplace.blueprint_table"));
		this.table = open.table();
		this.entries = open.entries();
		this.canUpload = open.canUpload();
		this.stored = open.stored();
		this.capacity = open.capacity();
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
		this.stored = open.stored();
		this.capacity = open.capacity();
		if (search != null) {
			fillStored();
			fillLibrary();
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
		statusKind = result.ok() ? CopperStyle.Status.GOOD : CopperStyle.Status.BAD;
		if (result.ok() && result.id().isPresent()) {
			mode = View.LIBRARY;
			selectAfterRefresh = result.id().get();
			updateVisibility();
		}
	}

	// --- layout ----------------------------------------------------------------------------

	private int[] tabWidths() {
		return new int[]{font.width(Component.translatable("screen.aliveworkplace.table.tab_library")) + 12,
			font.width(Component.translatable("screen.aliveworkplace.table.tab_stored", 999)) + 12};
	}

	private int[] buttonWidths(View view) {
		String[] keys = BlueprintBookLayout.buttons(view);
		int[] out = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = Math.max(44, font.width(Component.translatable(keys[i])) + 12);
		}
		return out;
	}

	/** The rail's widest label: "As drawn" and the selected build's styles. */
	private int railLabelWidth() {
		int w = font.width(Component.translatable("style.aliveworkplace.as_drawn"));
		for (TablePayloads.StyleChoice c : selectedStyles()) {
			w = Math.max(w, font.width(c.title()));
		}
		return Math.max(w, font.width(Component.translatable("screen.aliveworkplace.table.rail_style")));
	}

	/** Lays the view out again (the rail's width follows the selected build's styles) and puts the widgets in their boxes. */
	private void layout() {
		boxes = BlueprintBookLayout.of(width, height, font.width(title), railLabelWidth(), tabWidths(), mode, buttonWidths(mode));
		left = boxes.win()[0];
		top = boxes.win()[1];
		panelW = boxes.win()[2];
		panelH = boxes.win()[3];
		int[] s = boxes.search();
		search.setPosition(s[0] + 14, s[1] + 3);
		search.setWidth(s[2] - 18);
		// The game's box doesn't cut its hint, so it is cut here with "..." to the well.
		search.setHint(Component.literal(CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.table.search_hint").getString(), s[2] - 20))
			.withStyle(st -> st.withColor(CopperStyle.OFF_TEXT & 0xFFFFFF)));
		int[][] tabs = boxes.tabs();
		place(libraryTab, tabs[0]);
		place(storedTab, tabs[1]);
		Button[] row = mode == View.LIBRARY ? new Button[]{takeButton, uploadViewButton}
			: mode == View.STORED ? new Button[]{takeOutButton, storeHeldButton, storeAllButton}
			: new Button[]{uploadButton, folderButton, backButton};
		for (int i = 0; i < row.length; i++) {
			place(row[i], boxes.buttons()[i]);
		}
	}

	private static void place(Button b, int[] box) {
		b.setPosition(box[0], box[1]);
		b.setWidth(box[2]);
		b.setHeight(box[3]);
	}

	@Override
	protected void init() {
		// The search box (36.10) over the library: drawn without the game's border, in a Copperline well.
		search = new EditBox(font, 0, 0, 100, 10, Component.translatable("screen.aliveworkplace.table.search"));
		search.setBordered(false);
		search.setTextColor(CopperStyle.QUARTZ);
		search.setMaxLength(TableSearch.MAX_LENGTH);
		search.setHint(Component.translatable("screen.aliveworkplace.table.search_hint").withStyle(s -> s.withColor(CopperStyle.OFF_TEXT & 0xFFFFFF)));
		search.setValue(query);
		search.setResponder(this::onSearch);
		addRenderableWidget(search);
		fillLibrary();
		fillFiles();
		fillStored();

		// The widgets take the clicks; render draws them in Copperline (addWidget: the game doesn't draw them itself).
		libraryTab = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.tab_library"), b -> showLibrary()).bounds(0, 0, 40, 13).build());
		storedTab = addWidget(Button.builder(storedTabLabel(), b -> showStored()).bounds(0, 0, 40, 13).build());
		takeButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.take"), b -> take()).bounds(0, 0, 44, 14).build());
		uploadViewButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.upload_view"), b -> {
			mode = View.FILES;
			fillFiles();
			updateVisibility();
		}).bounds(0, 0, 44, 14).build());
		uploadButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.upload"), b -> upload()).bounds(0, 0, 44, 14).build());
		folderButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.open_folder"), b -> {
			Util.getPlatform().openFile(folder().toFile());
		}).bounds(0, 0, 44, 14).build());
		backButton = addWidget(Button.builder(Component.translatable("gui.back"), b -> {
			mode = View.LIBRARY;
			updateVisibility();
		}).bounds(0, 0, 44, 14).build());
		takeOutButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.take_out"), b -> takeOut()).bounds(0, 0, 44, 14).build());
		storeHeldButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.store_held"), b -> store(false)).bounds(0, 0, 44, 14).build());
		storeAllButton = addWidget(Button.builder(Component.translatable("screen.aliveworkplace.table.store_all"), b -> store(true)).bounds(0, 0, 44, 14).build());
		// Placed in render (it sits beside the "Materials" heading).
		Component all = Component.translatable("screen.aliveworkplace.table.all_materials");
		allMaterialsButton = addWidget(Button.builder(all, b -> showAllMaterials()).bounds(0, 0, font.width(all) + 10, 12).build());
		Component back = Component.translatable("gui.back");
		allBackButton = addWidget(Button.builder(back, b -> closeAllMaterials()).bounds(0, 0, font.width(back) + 12, 14).build());

		if (selectedId != null) {
			select(selectedId);
		} else if (!shown.isEmpty()) {
			select(shown.get(0).id());
		}
		updateVisibility();
		setInitialFocus(search); // typing searches straight away
	}

	/** Lists the entries the search lets through (36.10). */
	private void fillLibrary() {
		shown = TableSearch.filter(entries, query);
		libScroll = clampScroll(libScroll, shown.size(), BlueprintBookLayout.ROW);
	}

	private int clampScroll(int scroll, int count, int rowH) {
		if (boxes == null) {
			return Math.max(0, scroll);
		}
		return Math.max(0, Math.min(scroll, count - BlueprintBookLayout.visibleRows(boxes.list(), rowH)));
	}

	/** The box changed: narrow the list, and if the selected build is no longer listed, pick the first that is. */
	private void onSearch(String value) {
		query = value;
		fillLibrary();
		ResourceLocation next = TableSearch.selection(shown, selectedId);
		if (next != null) {
			select(next);
		}
	}

	/** Types {@code text} into the search box as a player would (for the screenshot harness). */
	public void search(String text) {
		search.setValue(text);
	}

	/** What's in the search box (for the screenshot harness). */
	public String query() {
		return query;
	}

	/** How many builds the library lists now, and how many it holds (for the screenshot harness). */
	public int shownCount() {
		return shown.size();
	}

	public int entryCount() {
		return entries.size();
	}

	private void updateVisibility() {
		boolean lib = mode == View.LIBRARY;
		boolean st = mode == View.STORED;
		boolean files = mode == View.FILES;
		search.visible = lib;
		takeOutButton.visible = st;
		storeHeldButton.visible = st;
		storeAllButton.visible = st;
		takeOutButton.active = selectedStoredEntry() != null;
		storeHeldButton.active = minecraft.player != null && TableLibrary.storable(minecraft.player.getMainHandItem()) && storedCount() < capacity;
		storeAllButton.active = minecraft.player != null && carriesStorable() && storedCount() < capacity;
		libraryTab.active = mode != View.LIBRARY;
		storedTab.active = !st;
		storedTab.setMessage(storedTabLabel());
		takeButton.visible = lib;
		uploadViewButton.visible = lib;
		uploadViewButton.active = canUpload;
		uploadButton.visible = files;
		folderButton.visible = files;
		backButton.visible = files;
		TablePayloads.Entry chosen = selectedEntry();
		takeButton.active = chosen != null && chosen.unlocked(); // a locked or "???" build isn't handed out (26.10)
		// The full list is of the build as shown, in the chosen style (26.11).
		List<TablePayloads.Material> materials = selectedId == null ? null : details.get(shownId());
		if (allMaterials && (!lib || materials == null)) {
			allMaterials = false;
		}
		allMaterialsButton.visible = lib && !allMaterials && materials != null && !materials.isEmpty();
		allBackButton.visible = allMaterials;
		uploadButton.active = files && selectedFile != null && !uploading && canUpload;
		layout();
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
		int i = indexOf(id);
		if (i >= 0 && boxes != null) {
			int rows = BlueprintBookLayout.visibleRows(boxes.list(), BlueprintBookLayout.ROW);
			if (i < libScroll) {
				libScroll = i;
			} else if (i >= libScroll + rows) {
				libScroll = i - rows + 1;
			}
		}
		TablePayloads.Entry entry = entry(id);
		if (!details.containsKey(id) && (entry == null || !entry.hidden())) {
			ClientPlayNetworking.send(new TablePayloads.RequestDetails(id));
		}
	}

	private int indexOf(ResourceLocation id) {
		for (int i = 0; i < shown.size(); i++) {
			if (shown.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** Opens the full materials list of the selected build (also used by the screenshot harness). */
	public void showAllMaterials() {
		if (mode == View.LIBRARY && selectedId != null && details.containsKey(shownId())) {
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

	// --- the Stored tab (36.13) ---------------------------------------------------------------

	private Component storedTabLabel() {
		return Component.translatable("screen.aliveworkplace.table.tab_stored", storedCount());
	}

	/** Blueprints stored in the table, in all (for the screenshot harness too). */
	public int storedCount() {
		return stored.stream().mapToInt(TablePayloads.Stored::count).sum();
	}

	/** Different blueprints the Stored tab lists (for the screenshot harness). */
	public int storedKinds() {
		return stored.size();
	}

	/** The Stored tab's rows, keeping the pick if it's still stored (else the first). */
	private void fillStored() {
		TablePayloads.Stored keep = selectedStoredEntry();
		selectedStored = keep != null ? keep.stack() : stored.isEmpty() ? null : stored.get(0).stack();
		storedScroll = clampScroll(storedScroll, stored.size(), BlueprintBookLayout.STORED_ROW);
	}

	@Nullable
	private TablePayloads.Stored selectedStoredEntry() {
		if (selectedStored == null) {
			return null;
		}
		for (TablePayloads.Stored s : stored) {
			if (ItemStack.isSameItemSameComponents(s.stack(), selectedStored)) {
				return s;
			}
		}
		return null;
	}

	/** The picked stored blueprint's name, or "" (for the screenshot harness). */
	public String selectedStoredName() {
		TablePayloads.Stored s = selectedStoredEntry();
		return s == null ? "" : s.stack().getHoverName().getString();
	}

	/** How many of the picked stored blueprint the table holds, or 0 (for the screenshot harness). */
	public int selectedStoredCount() {
		TablePayloads.Stored s = selectedStoredEntry();
		return s == null ? 0 : s.count();
	}

	private boolean carriesStorable() {
		var inventory = minecraft.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (TableLibrary.storable(inventory.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	/** Switches to the library (tab button; also for the screenshot harness). */
	public void showLibrary() {
		mode = View.LIBRARY;
		updateVisibility();
	}

	/** Switches to the Stored tab (tab button; also for the screenshot harness). */
	public void showStored() {
		mode = View.STORED;
		allMaterials = false;
		fillStored();
		requestStoredPreview();
		updateVisibility();
	}

	/** Whether the Stored tab is showing (for the screenshot harness). */
	public boolean storedOpen() {
		return mode == View.STORED;
	}

	/** Picks a stored blueprint in the Stored tab. */
	private void selectStored(TablePayloads.Stored entry) {
		selectedStored = entry.stack();
		requestStoredPreview();
	}

	/** The build a stored blueprint is for (as styled), if it is one the Table can draw. */
	@Nullable
	private static ResourceLocation buildOf(ItemStack stack) {
		return io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(stack).map(d -> d.structure())
			.or(() -> io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem.plan(stack)
				.map(p -> io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.idOf(p.wonder())))
			.orElse(null);
	}

	private void requestStoredPreview() {
		TablePayloads.Stored entry = selectedStoredEntry();
		ResourceLocation id = entry == null ? null : buildOf(entry.stack());
		if (id != null && !previews.containsKey(id) && !details.containsKey(id)) {
			ClientPlayNetworking.send(new TablePayloads.RequestDetails(id));
		}
	}

	/** Takes one of the picked stored blueprint out of the table (button action; public for the screenshot harness). */
	public void takeOut() {
		TablePayloads.Stored entry = selectedStoredEntry();
		if (entry != null) {
			ClientPlayNetworking.send(new TablePayloads.TakeStored(table, entry.stack()));
		}
	}

	/** Stores the blueprint in hand, or with {@code all} every blueprint carried (button actions; public for the screenshot harness). */
	public void store(boolean all) {
		ClientPlayNetworking.send(new TablePayloads.Store(table, all));
	}

	// --- the upload view -------------------------------------------------------------------------

	private void fillFiles() {
		List<Path> found = new ArrayList<>();
		try (Stream<Path> stream = Files.list(folder())) {
			stream.filter(Files::isRegularFile).filter(p -> BlueprintImporter.hasSupportedExtension(p.getFileName().toString()))
				.sorted().forEach(found::add);
		} catch (IOException ignored) {
			// empty list
		}
		fileItems = found;
		filesScroll = clampScroll(filesScroll, found.size(), BlueprintBookLayout.ROW);
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
			statusKind = CopperStyle.Status.BAD;
			return;
		}
		if (bytes.length > BlueprintFiles.MAX_FILE_BYTES) {
			status = Component.translatable("message.aliveworkplace.import.error.file_too_big", bytes.length / 1024, BlueprintFiles.MAX_FILE_BYTES / 1024);
			statusKind = CopperStyle.Status.BAD;
			return;
		}
		String name = selectedFile.getFileName().toString();
		for (int offset = 0; offset < bytes.length; offset += TablePayloads.UPLOAD_CHUNK) {
			byte[] chunk = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + TablePayloads.UPLOAD_CHUNK));
			ClientPlayNetworking.send(new TablePayloads.UploadChunk(table, name, bytes.length, offset, chunk));
		}
		uploading = true;
		status = Component.translatable("screen.aliveworkplace.table.uploading", name);
		statusKind = CopperStyle.Status.INFO;
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
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		CopperStyle.window(g, left, top, panelW, panelH);
		if (mode == View.LIBRARY) {
			// The search well, under the game's box (drawn without its border).
			int[] s = boxes.search();
			g.blitSprite(search.isFocused() ? CopperStyle.WELL_FOCUS : CopperStyle.WELL, s[0], s[1], s[2], s[3]);
			CopperStyle.glyph(g, "search", s[0] + 4, s[1] + 3);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		whisper = Component.empty();
		emptyAction = null;
		long unlocked = entries.stream().filter(TablePayloads.Entry::unlocked).count();
		Component sub = mode == View.STORED ? Component.translatable("screen.aliveworkplace.table.stored_count", storedCount(), capacity)
			: mode == View.LIBRARY
			? !query.isBlank() ? Component.translatable("screen.aliveworkplace.table.search_count", shown.size(), entries.size())
			: unlocked < entries.size() ? Component.translatable("screen.aliveworkplace.table.unlocked_count", unlocked, entries.size())
				: Component.translatable("screen.aliveworkplace.table.library_count", entries.size())
			: Component.translatable("screen.aliveworkplace.table.files_title");
		boolean overClose = CopperLayout.inside(boxes.close(), mouseX, mouseY);
		int[] plaque = CopperStyle.plaque(g, font, title, boxes.win());
		CopperStyle.lintel(g, font, boxes.win(), plaque, Component.empty(), overClose);
		int[] lt = boxes.lintelText();
		if (lt[2] > 8) {
			String text = CopperStyle.trim(font, sub.getString(), lt[2]);
			g.drawString(font, text, lt[0] + lt[2] - font.width(text), lt[1] + 4, CopperStyle.TUFF, false);
		}
		if (overClose) {
			whisper = Component.translatable("screen.aliveworkplace.table.whisper.close");
		}
		drawTab(g, libraryTab, mode == View.LIBRARY, mouseX, mouseY);
		drawTab(g, storedTab, mode == View.STORED, mouseX, mouseY);

		renderRail(g, mouseX, mouseY);
		int[] d = boxes.detail();
		ItemStack hoveredItem = null;
		if (mode == View.LIBRARY) {
			renderLibraryList(g, mouseX, mouseY);
			if (allMaterials) {
				renderAllMaterials(g, d[0], d[1], d[2], d[3], mouseX, mouseY, partialTick);
			} else {
				hoveredItem = renderDetails(g, d[0], d[1], d[2], d[3], mouseX, mouseY);
			}
		} else if (mode == View.STORED) {
			renderStoredList(g, mouseX, mouseY);
			renderStored(g, d[0], d[1], d[2], d[3]);
		} else {
			renderFilesList(g, mouseX, mouseY);
			renderFileHelp(g, d[0], d[1], d[2], d[3]);
		}
		renderButtons(g, mouseX, mouseY);
		if (allMaterialsButton.visible) {
			drawButton(g, allMaterialsButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY);
		}
		if (allBackButton.visible) {
			drawButton(g, allBackButton, CopperStyle.Kind.NEUTRAL, mouseX, mouseY);
		}

		// The whisper line: what the thing under the mouse does, else the last upload's word (its glyph, the sentence in its color).
		int[] w = boxes.whisper();
		int wy = w[1] + 3;
		if (whisper.getString().isEmpty() && !status.getString().isEmpty() && statusKind != null) {
			CopperStyle.whisper(g, font, Component.empty(), w[0] + 2, wy, w[2] - 4, boxes.compact());
			g.blitSprite(statusKind.glyph, w[0] + 2, wy, 8, 8);
			int room = w[2] - 4 - 12 - (font.width(Component.translatable("screen.aliveworkplace.copperline.key.esc")) + 6
				+ (boxes.compact() ? 0 : 4 + font.width(Component.translatable("screen.aliveworkplace.copperline.key.close")))) - 6;
			g.drawString(font, CopperStyle.trim(font, status.getString(), room), w[0] + 14, wy, statusKind.color, false);
		} else {
			CopperStyle.whisper(g, font, whisper, w[0] + 2, wy, w[2] - 4, boxes.compact());
		}
		if (hoveredItem != null) {
			g.renderTooltip(font, hoveredItem, mouseX, mouseY);
		}
	}

	private void drawTab(GuiGraphics g, Button tab, boolean open, int mouseX, int mouseY) {
		boolean over = tab.isMouseOver(mouseX, mouseY);
		CopperStyle.tab(g, tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight(), open, over && !open);
		String text = CopperStyle.trim(font, tab.getMessage().getString(), tab.getWidth() - 6);
		g.drawString(font, text, tab.getX() + (tab.getWidth() - font.width(text)) / 2, tab.getY() + 3, open || over ? CopperStyle.QUARTZ : CopperStyle.TUFF, open);
	}

	private void drawButton(GuiGraphics g, Button b, CopperStyle.Kind kind, int mouseX, int mouseY) {
		boolean over = b.active && b.isMouseOver(mouseX, mouseY);
		CopperStyle.button(g, font, b.getMessage(), null, b.getX(), b.getY(), b.getWidth(), b.getHeight(), kind, over, false, !b.active);
	}

	/** The view's row of buttons: the first copper, the rest neutral; each says in the whisper line what it does. */
	private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
		Button[] row = mode == View.LIBRARY ? new Button[]{takeButton, uploadViewButton}
			: mode == View.STORED ? new Button[]{takeOutButton, storeHeldButton, storeAllButton}
			: new Button[]{uploadButton, folderButton, backButton};
		String[] keys = BlueprintBookLayout.buttons(mode);
		for (int i = 0; i < row.length; i++) {
			drawButton(g, row[i], i == BlueprintBookLayout.COPPER ? CopperStyle.Kind.COPPER : CopperStyle.Kind.NEUTRAL, mouseX, mouseY);
			if (row[i].isMouseOver(mouseX, mouseY)) {
				whisper = Component.translatable("screen.aliveworkplace.table.whisper." + keys[i].substring(keys[i].lastIndexOf('.') + 1));
			}
		}
	}

	/** The style rail (26.11): as drawn and each style the selected build takes, the chosen one copper-marked. */
	private void renderRail(GuiGraphics g, int mouseX, int mouseY) {
		int[] rail = boxes.rail();
		if (rail[2] <= 0) {
			return; // the Stored tab and the upload view have no rail
		}
		g.fill(rail[0], rail[1], rail[0] + rail[2], rail[1] + rail[3], CopperStyle.DEEP_800);
		if (!boxes.compact()) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.table.rail_style").getString().toUpperCase(java.util.Locale.ROOT),
				rail[2] - 8), rail[0] + 4, rail[1] + 3, CopperStyle.TUFF, false);
		}
		if (mode != View.LIBRARY || selectedEntry() == null || !selectedEntry().unlocked()) {
			return;
		}
		List<TablePayloads.StyleChoice> choices = selectedStyles();
		int[][] cells = BlueprintBookLayout.styleEntries(boxes, choices.size() + 1);
		for (int i = 0; i < cells.length; i++) {
			TablePayloads.StyleChoice choice = i == 0 ? null : choices.get(i - 1);
			String name = choice == null ? "" : choice.name();
			Component label = choice == null ? Component.translatable("style.aliveworkplace.as_drawn") : choice.title();
			ItemStack icon = new ItemStack(choice == null ? net.minecraft.world.item.Items.SPRUCE_LOG : BuiltInRegistries.ITEM.get(choice.icon()));
			boolean over = !allMaterials && CopperLayout.inside(cells[i], mouseX, mouseY);
			CopperStyle.railEntry(g, font, cells[i], null, icon, label, 0, name.equals(style), over, boxes.compact());
			if (over) {
				whisper = Component.translatable("screen.aliveworkplace.table.whisper.style", label);
			}
		}
	}

	/** The list's well, its scroll bar, and the rows from {@code scroll}; returns the rows that fit. */
	private int listFrame(GuiGraphics g, int count, int scroll, int rowH) {
		int[] l = boxes.list();
		CopperStyle.well(g, l[0], l[1], l[2], l[3]);
		int rows = BlueprintBookLayout.visibleRows(l, rowH);
		// In pixels: the thumb is as tall as the rows shown are of all of them.
		CopperStyle.scrollbar(g, l[0] + l[2] - 5, l[1] + 1, l[3] - 2, scroll * rowH, Math.max(0, count - rows) * rowH, count * rowH, false);
		return rows;
	}

	private void renderLibraryList(GuiGraphics g, int mouseX, int mouseY) {
		int[] h = boxes.header();
		g.drawString(font, Component.translatable("screen.aliveworkplace.table.col_name").getString().toUpperCase(java.util.Locale.ROOT), h[0] + 4, h[1] + 2, CopperStyle.TUFF, false);
		String sizeHead = Component.translatable("screen.aliveworkplace.table.col_size").getString().toUpperCase(java.util.Locale.ROOT);
		g.drawString(font, sizeHead, h[0] + h[2] - 8 - font.width(sizeHead), h[1] + 2, CopperStyle.TUFF, false);
		CopperStyle.headerRow(g, h[0], h[1], h[2]);
		int[] l = boxes.list();
		int rowH = BlueprintBookLayout.ROW;
		libScroll = clampScroll(libScroll, shown.size(), rowH);
		int rows = listFrame(g, shown.size(), libScroll, rowH);
		if (entries.isEmpty()) {
			// The empty state (no blueprints on the server): what to do, and the button that does it.
			boolean over = emptyAction != null && CopperLayout.inside(emptyAction, mouseX, mouseY);
			emptyAction = CopperStyle.empty(g, font, "buildings", Component.translatable("screen.aliveworkplace.table.empty_title"),
				Component.translatable("screen.aliveworkplace.table.empty_line"), canUpload ? Component.translatable("screen.aliveworkplace.table.upload_view") : null,
				l[0] + 2, l[1] + 2, l[2] - 4, l[3] - 4, over);
			return;
		}
		if (shown.isEmpty() && !query.isBlank()) {
			// Nothing matches (36.10): said in the empty list, with what was typed.
			Component none = Component.translatable("screen.aliveworkplace.table.no_match", query.trim());
			int lw = l[2] - 12;
			int ly = l[1] + 8;
			for (var line : font.split(none, lw)) {
				g.drawString(font, line, l[0] + 6, ly, CopperStyle.TUFF, false);
				ly += 10;
			}
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.table.no_match_help").getString(), lw),
				l[0] + 6, ly + 4, CopperStyle.OFF_TEXT, false);
			return;
		}
		int at = BlueprintBookLayout.rowAt(l, rowH, libScroll, shown.size(), mouseX, mouseY);
		int rowW = l[2] - 7;
		for (int k = 0; k < rows && libScroll + k < shown.size(); k++) {
			int i = libScroll + k;
			TablePayloads.Entry e = shown.get(i);
			int x = l[0] + 1;
			int y = l[1] + 1 + k * rowH;
			boolean sel = e.id().equals(selectedId);
			CopperStyle.rowBand(g, x, y, rowW, rowH, i % 2 == 1, i == at, sel);
			int tx = x + 4;
			int right = x + rowW - 3;
			if (e.hidden()) {
				g.drawString(font, Component.translatable("screen.aliveworkplace.table.hidden_name"), tx, y + 2, CopperStyle.OFF_TEXT, false);
			} else {
				String size = e.locked() ? "" : e.sizeX() + "×" + e.sizeZ();
				int sw = size.isEmpty() ? 0 : font.width(size) + 4;
				if (e.locked()) {
					CopperStyle.glyph(g, "lock", tx, y + 2);
					tx += 10;
				}
				String name = Blueprints.displayName(e.id()).getString();
				if (e.id().getPath().startsWith("uploads/")) {
					name += " · " + Component.translatable("screen.aliveworkplace.table.uploaded").getString();
				}
				g.drawString(font, CopperStyle.trim(font, name, right - sw - tx), tx, y + 2, e.locked() ? CopperStyle.OFF_TEXT : CopperStyle.QUARTZ, sel);
				if (!size.isEmpty()) {
					g.drawString(font, size, right - font.width(size), y + 2, sel ? CopperStyle.QUARTZ : CopperStyle.TUFF, false);
				}
			}
			if (i == at) {
				whisper = e.hidden() || e.locked() ? Component.translatable(e.hint()) : Component.translatable("screen.aliveworkplace.table.whisper.list");
			}
		}
	}

	private void renderStoredList(GuiGraphics g, int mouseX, int mouseY) {
		int[] l = boxes.list();
		int rowH = BlueprintBookLayout.STORED_ROW;
		storedScroll = clampScroll(storedScroll, stored.size(), rowH);
		int rows = listFrame(g, stored.size(), storedScroll, rowH);
		if (stored.isEmpty()) {
			CopperStyle.empty(g, font, "buildings", Component.translatable("screen.aliveworkplace.table.stored_none"),
				Component.translatable("screen.aliveworkplace.table.stored_empty_line"), null, l[0] + 2, l[1] + 2, l[2] - 4, l[3] - 4, false);
			return;
		}
		int at = BlueprintBookLayout.rowAt(l, rowH, storedScroll, stored.size(), mouseX, mouseY);
		int rowW = l[2] - 7;
		TablePayloads.Stored picked = selectedStoredEntry();
		for (int k = 0; k < rows && storedScroll + k < stored.size(); k++) {
			int i = storedScroll + k;
			TablePayloads.Stored s = stored.get(i);
			int x = l[0] + 1;
			int y = l[1] + 1 + k * rowH;
			boolean sel = s == picked;
			CopperStyle.rowBand(g, x, y, rowW, rowH, i % 2 == 1, i == at, sel);
			g.renderItem(s.stack(), x + 3, y + 2);
			g.drawString(font, CopperStyle.trim(font, s.stack().getHoverName().getString(), rowW - 26), x + 22, y + 2, CopperStyle.QUARTZ, sel);
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.table.stored_row", s.count()).getString(), rowW - 26),
				x + 22, y + 11, CopperStyle.TUFF, false);
		}
	}

	private void renderFilesList(GuiGraphics g, int mouseX, int mouseY) {
		int[] l = boxes.list();
		int rowH = BlueprintBookLayout.ROW;
		filesScroll = clampScroll(filesScroll, fileItems.size(), rowH);
		int rows = listFrame(g, fileItems.size(), filesScroll, rowH);
		int at = BlueprintBookLayout.rowAt(l, rowH, filesScroll, fileItems.size(), mouseX, mouseY);
		int rowW = l[2] - 7;
		for (int k = 0; k < rows && filesScroll + k < fileItems.size(); k++) {
			int i = filesScroll + k;
			Path p = fileItems.get(i);
			int x = l[0] + 1;
			int y = l[1] + 1 + k * rowH;
			boolean sel = p.equals(selectedFile);
			CopperStyle.rowBand(g, x, y, rowW, rowH, i % 2 == 1, i == at, sel);
			long size;
			try {
				size = Files.size(p);
			} catch (IOException e) {
				size = 0;
			}
			String kb = Math.max(1, size / 1024) + " KB";
			int right = x + rowW - 3;
			g.drawString(font, CopperStyle.trim(font, p.getFileName().toString(), right - font.width(kb) - 4 - (x + 4)), x + 4, y + 2, CopperStyle.QUARTZ, sel);
			g.drawString(font, kb, right - font.width(kb), y + 2, CopperStyle.TUFF, false);
		}
	}

	/** The selected build: its 3D preview, name and tags, size, jobs and beds, and a row of material slots. Returns the slot item under the mouse. */
	@Nullable
	private ItemStack renderDetails(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
		int bottom = y + h;
		gridW = 0;
		TablePayloads.Entry entry = selectedEntry();
		if (entry == null) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.empty"), x, y, w, CopperStyle.TUFF);
			return null;
		}
		if (entry.hidden()) {
			// A rare build not unlocked yet (26.10): like a hidden achievement, nothing but how to come by it.
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.hidden_name"), x, y, CopperStyle.OFF_TEXT, true);
			y += 12;
			g.blitSprite(CopperStyle.Status.RARE.glyph, x, y, 8, 8);
			g.drawWordWrap(font, Component.translatable(entry.hint()), x + 10, y, w - 10, CopperStyle.AMETHYST);
			y += 9 * font.split(Component.translatable(entry.hint()), w - 10).size() + 4;
			CopperStyle.well(g, x, y, w, Math.min(60, bottom - y));
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.hidden_help"), x + 4, y + 6, w - 8, CopperStyle.TUFF);
			return null;
		}
		List<Component> lines = facts.getOrDefault(shownId(), List.of());
		TablePreview shownPreview = previews.get(shownId());
		int below = shownPreview == null ? 0 : shownPreview.below();
		Component size = below > 0
			? io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.table.size_below", entry.blocks(), entry.sizeX(), entry.sizeY(), entry.sizeZ(), entry.blocks(), below)
			: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.table.size", entry.blocks(), entry.sizeX(), entry.sizeY(), entry.sizeZ(), entry.blocks());
		boolean compact = boxes.compact();
		// At 320x180 the size takes one line (cut with "...") and the id line goes, so the slots keep their room.
		int sizeLines = compact ? 1 : Math.min(2, font.split(size, w).size());
		boolean idLine = !compact || !entry.hint().isEmpty();
		int factLines = 0;
		for (Component line : lines) {
			factLines += font.split(line, w).size();
		}
		if (compact) {
			factLines = Math.min(factLines, 1);
		}
		// Under the preview: name (12), size, facts, the hint or id (10), "Materials" (11), a row of slots (18) and its word (10).
		int under = 12 + 10 * sizeLines + 9 * factLines + (idLine ? 11 : 0) + 11 + BlueprintBookLayout.SLOT + 11;
		// The build in 3D (26.9) at the top, as tall as the room left.
		previewX = x;
		previewY = y;
		previewW = w;
		previewH = Math.max(30, Math.min(130, h - under - 4));
		CopperStyle.well(g, previewX, previewY, previewW, previewH);
		if (shownPreview == null) {
			Component loading = Component.translatable("screen.aliveworkplace.table.preview_loading");
			g.drawString(font, loading, x + (w - font.width(loading)) / 2, y + previewH / 2 - 4, CopperStyle.TUFF, false);
		} else {
			g.enableScissor(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + previewH - 1);
			shownPreview.render(g, previewX + 1, previewY + 1, previewW - 2, previewH - 2);
			g.disableScissor();
			if (entry.locked()) {
				// A locked build's preview, dimmed (26.10), over whatever depth the 3D drawing left.
				g.pose().pushPose();
				g.pose().translate(0, 0, 400);
				g.fill(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + previewH - 1, 0x99101010);
				g.pose().popPose();
			}
			if (overPreview(mouseX, mouseY) && shownPreview.count() > 0) {
				whisper = Component.translatable("screen.aliveworkplace.table.preview_drag");
			}
		}
		y += previewH + 4;
		// The name in Quartz with a shadow, its tag (LOCKED, or the style it's shown in) at the right.
		String tag = entry.locked() ? Component.translatable("screen.aliveworkplace.table.tag_locked").getString().toUpperCase(java.util.Locale.ROOT)
			: style.isEmpty() ? "" : selectedStyles().stream().filter(c -> c.name().equals(style)).findFirst().map(c -> c.title().getString().toUpperCase(java.util.Locale.ROOT)).orElse("");
		int tagW = tag.isEmpty() ? 0 : font.width(tag) + 6;
		g.drawString(font, CopperStyle.trim(font, Blueprints.displayName(entry.id()).getString(), w - tagW - 4), x, y, entry.locked() ? CopperStyle.OFF_TEXT : CopperStyle.QUARTZ, true);
		if (!tag.isEmpty()) {
			CopperStyle.chip(g, font, tag, x + w - tagW, y - 2, entry.locked() ? CopperStyle.LANTERN : CopperStyle.COPPER_LIGHT);
		}
		y += 12;
		if (sizeLines == 1) {
			g.drawString(font, CopperStyle.trim(font, size.getString(), w), x, y, CopperStyle.TUFF, false);
			y += 10;
		} else {
			int sy = y;
			for (var line : font.split(size, w)) {
				if (y >= sy + 10 * sizeLines) {
					break;
				}
				g.drawString(font, line, x, y, CopperStyle.TUFF, false);
				y += 10;
			}
		}
		// What jobs it brings and its beds (36.6a); at 320x180 only the first line, cut with "...".
		int budget = factLines;
		for (Component line : lines) {
			for (var part : font.split(line, w)) {
				if (budget-- <= 0) {
					break;
				}
				if (compact && font.split(line, w).size() > 1) {
					g.drawString(font, CopperStyle.trim(font, line.getString(), w), x, y, CopperStyle.QUARTZ, false);
				} else {
					g.drawString(font, part, x, y, CopperStyle.QUARTZ, false);
				}
				y += 9;
			}
		}
		if (!entry.hint().isEmpty()) {
			// Locked (or a Wonder, raised from its plan): how to get it, with its glyph and its word in Lantern.
			Component how = entry.locked()
				? Component.translatable("screen.aliveworkplace.table.locked_hint", Component.translatable(entry.hint()))
				: Component.translatable(entry.hint());
			CopperStyle.glyph(g, entry.locked() ? "lock" : "info", x, y);
			g.drawString(font, CopperStyle.trim(font, how.getString(), w - 10), x + 10, y, entry.locked() ? CopperStyle.LANTERN : CopperStyle.LAPIS, false);
			y += 11;
		} else if (idLine) {
			g.drawString(font, CopperStyle.trim(font, entry.id().toString(), w), x, y, CopperStyle.OFF_TEXT, false);
			y += 11;
		}
		TableStyle.heading(g, font, Component.translatable("screen.aliveworkplace.table.materials"), x, y);
		allMaterialsButton.setPosition(x + w - allMaterialsButton.getWidth(), y - 2);
		y += 11;
		List<TablePayloads.Material> materials = details.get(shownId());
		if (materials == null) {
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.loading"), x, y + 5, CopperStyle.TUFF, false);
			return null;
		}
		return renderSlots(g, materials, x, y, w, mouseX, mouseY);
	}

	/**
	 * One row of material slots, scrolled with the wheel, "+N" at its end opening the full list; a slot the player doesn't
	 * carry enough of is framed in Redstone, and the line under the row says so with the warning glyph (or the good one).
	 */
	@Nullable
	private ItemStack renderSlots(GuiGraphics g, List<TablePayloads.Material> materials, int x, int y, int w, int mouseX, int mouseY) {
		int step = BlueprintBookLayout.SLOT + 2;
		int cols = Math.max(1, (w + 2) / step);
		// With more than fit, the row keeps room at its end for the "+N" button, as wide as its widest label.
		int moreBtn = font.width("+" + materials.size()) + 8;
		int fit = materials.size() <= cols ? cols : Math.max(1, (w - moreBtn + 2) / step);
		gridScroll = Math.max(0, Math.min(gridScroll, materials.size() - fit));
		gridX = x;
		gridY = y;
		gridW = w;
		gridH = BlueprintBookLayout.SLOT;
		int first = gridScroll;
		int count = Math.min(materials.size() - first, fit);
		moreX = -1;
		ItemStack hovered = null;
		for (int k = 0; k < count; k++) {
			TablePayloads.Material m = materials.get(first + k);
			int cx = x + k * step;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(m.item()));
			boolean short_ = carried(m.item()) < m.count();
			g.blitSprite(short_ ? CopperStyle.WELL_ERROR : CopperStyle.WELL, cx, y, 18, 18);
			g.renderItem(stack, cx + 1, y + 1);
			String n = countText(m.count());
			g.pose().pushPose();
			g.pose().translate(0, 0, 200);
			g.drawString(font, n, cx + 18 - font.width(n), y + 10, CopperStyle.QUARTZ, true);
			g.pose().popPose();
			if (mouseX >= cx && mouseX < cx + 18 && mouseY >= y && mouseY < y + 18) {
				hovered = stack;
			}
		}
		if (first + count < materials.size()) {
			int cx = x + count * step;
			moreX = cx;
			moreY = y;
			String more = "+" + (materials.size() - first - count);
			moreW = Math.min(x + w - cx, font.width(more) + 8);
			boolean over = mouseX >= cx && mouseX < cx + moreW && mouseY >= y && mouseY < y + 18;
			CopperStyle.button(g, font, Component.literal(more), null, cx, y, moreW, 18, CopperStyle.Kind.NEUTRAL, over, false, false);
			if (over) {
				whisper = Component.translatable("screen.aliveworkplace.table.whisper.all_materials");
			}
		}
		// The materials' word: all carried (good), or which isn't (warning), never the color alone.
		List<String> missing = new ArrayList<>();
		for (TablePayloads.Material m : materials) {
			if (carried(m.item()) < m.count()) {
				missing.add(new ItemStack(BuiltInRegistries.ITEM.get(m.item())).getHoverName().getString());
			}
		}
		int ly = y + 21;
		CopperStyle.Status st = missing.isEmpty() ? CopperStyle.Status.GOOD : CopperStyle.Status.WARNING;
		Component word = missing.isEmpty() ? Component.translatable("screen.aliveworkplace.table.carried_all")
			: missing.size() == 1 ? Component.translatable("screen.aliveworkplace.table.carried_missing_one", missing.get(0))
			: Component.translatable("screen.aliveworkplace.table.carried_missing_more", missing.get(0));
		g.blitSprite(st.glyph, x, ly, 8, 8);
		g.drawString(font, CopperStyle.trim(font, word.getString(), w - 10), x + 10, ly, st.color, false);
		return hovered;
	}

	/** How many of {@code item} the player carries. */
	private int carried(ResourceLocation item) {
		if (minecraft == null || minecraft.player == null) {
			return 0;
		}
		var inv = minecraft.player.getInventory();
		var it = BuiltInRegistries.ITEM.get(item);
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(it)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Every material of the selected build, biggest first: icon, count and name, scrolled with the wheel. */
	private void renderAllMaterials(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
		List<TablePayloads.Material> materials = details.get(shownId());
		CopperStyle.panel(g, x, y, w, h);
		if (materials == null) {
			// Another build was just picked and its list hasn't arrived yet (a click here used to crash the client).
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.loading"), x + 4, y + 5, CopperStyle.TUFF, false);
			return;
		}
		int total = 0;
		for (TablePayloads.Material m : materials) {
			total += m.count();
		}
		allBackButton.setPosition(x + w - allBackButton.getWidth() - 3, y + 3);
		g.drawString(font, CopperStyle.trim(font, io.github.jcondedata.aliveworkplace.work.Words.countedTwo("screen.aliveworkplace.table.all_materials_title",
			materials.size(), total, materials.size(), total).getString(), w - allBackButton.getWidth() - 12), x + 4, y + 6, CopperStyle.COPPER_LIGHT, false);
		int listY = y + 20;
		int listH = h - 22;
		int maxScroll = Math.max(0, materials.size() * ALL_ROW - listH);
		allScroll = Math.max(0, Math.min(allScroll, maxScroll));
		int countW = 0;
		for (TablePayloads.Material m : materials) {
			countW = Math.max(countW, font.width(String.valueOf(m.count())));
		}
		g.enableScissor(x + 1, listY, x + w - 1, listY + listH);
		for (int i = allScroll / ALL_ROW; i < materials.size(); i++) {
			int ry = listY + i * ALL_ROW - allScroll;
			if (ry > listY + listH) {
				break;
			}
			TablePayloads.Material m = materials.get(i);
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(m.item()));
			CopperStyle.rowBand(g, x + 2, ry, w - 10, ALL_ROW, i % 2 == 1, false, false);
			g.renderItem(stack, x + 4, ry + 1);
			String count = String.valueOf(m.count());
			g.drawString(font, count, x + 24 + countW - font.width(count), ry + 5, CopperStyle.QUARTZ, false);
			int nameX = x + 30 + countW;
			boolean short_ = carried(m.item()) < m.count();
			int glyphW = 10;
			g.drawString(font, CopperStyle.trim(font, stack.getHoverName().getString(), x + w - 10 - glyphW - nameX), nameX, ry + 5, CopperStyle.TUFF, false);
			g.blitSprite((short_ ? CopperStyle.Status.WARNING : CopperStyle.Status.GOOD).glyph, x + w - 8 - glyphW, ry + 5, 8, 8);
		}
		g.disableScissor();
		CopperStyle.scrollbar(g, x + w - 6, listY, listH, allScroll, maxScroll, materials.size() * ALL_ROW, false);
	}

	private boolean overGrid(double mx, double my) {
		return mode == View.LIBRARY && !allMaterials && gridW > 0 && mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int[] d = boxes.detail();
		if (allMaterials && mx >= d[0]) {
			allScroll -= (int) Math.round(scrollY * ALL_ROW * 2);
			return true;
		}
		if (overGrid(mx, my) && selectedId != null && details.containsKey(shownId())) {
			gridScroll = Math.max(0, gridScroll - (int) Math.signum(scrollY));
			return true;
		}
		if (CopperLayout.inside(boxes.list(), mx, my)) {
			int step = -(int) Math.signum(scrollY);
			switch (mode) {
				case LIBRARY -> libScroll = clampScroll(libScroll + step, shown.size(), BlueprintBookLayout.ROW);
				case STORED -> storedScroll = clampScroll(storedScroll + step, stored.size(), BlueprintBookLayout.STORED_ROW);
				case FILES -> filesScroll = clampScroll(filesScroll + step, fileItems.size(), BlueprintBookLayout.ROW);
			}
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

	/** The Stored tab's right side (36.13): the picked blueprint, how many are stored, its build in 3D, and what storing is. */
	private void renderStored(GuiGraphics g, int x, int y, int w, int h) {
		int bottom = y + h;
		Component help = Component.translatable("screen.aliveworkplace.table.stored_help");
		int helpLines = Math.min(boxes.compact() ? 2 : 4, font.split(help, w).size());
		int helpH = 9 * helpLines;
		if (stored.isEmpty()) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.stored_empty"), x, y, w, CopperStyle.QUARTZ);
			drawLines(g, help, x, bottom - helpH, w, helpLines);
			return;
		}
		TablePayloads.Stored entry = selectedStoredEntry();
		if (entry == null) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.stored_pick"), x, y, w, CopperStyle.QUARTZ);
			return;
		}
		g.renderItem(entry.stack(), x, y);
		g.drawString(font, CopperStyle.trim(font, entry.stack().getHoverName().getString(), w - 20), x + 20, y, CopperStyle.QUARTZ, true);
		g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.table.stored_here", entry.count()).getString(), w - 20),
			x + 20, y + 9, CopperStyle.COPPER_LIGHT, false);
		y += 20;
		// The blueprint's own lines (size, style, where it's placed), as its tooltip has them.
		List<Component> lines = entry.stack().getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
			net.minecraft.world.item.TooltipFlag.Default.NORMAL);
		int max = boxes.compact() ? 2 : 4;
		for (int i = 1; i < lines.size() && i <= max; i++) {
			g.drawString(font, CopperStyle.trim(font, lines.get(i).getString(), w), x, y, CopperStyle.TUFF, false);
			y += 10;
		}
		y += 2;
		ResourceLocation id = buildOf(entry.stack());
		TablePreview preview = id == null ? null : previews.get(id);
		int ph = Math.min(120, bottom - helpH - 6 - y);
		if (preview != null && ph >= 30) {
			CopperStyle.well(g, x, y, w, ph);
			g.enableScissor(x + 1, y + 1, x + w - 1, y + ph - 1);
			preview.render(g, x + 1, y + 1, w - 2, ph - 2);
			g.disableScissor();
		}
		drawLines(g, help, x, bottom - helpH, w, helpLines);
	}

	/** {@code text} wrapped to {@code w}, at most {@code max} lines, the last cut with "..." if there is more. */
	private void drawLines(GuiGraphics g, Component text, int x, int y, int w, int max) {
		var split = font.split(text, w);
		for (int i = 0; i < split.size() && i < max; i++) {
			if (i == max - 1 && split.size() > max) {
				g.drawString(font, lastLine(text, w, max), x, y, CopperStyle.TUFF, false);
			} else {
				g.drawString(font, split.get(i), x, y, CopperStyle.TUFF, false);
			}
			y += 9;
		}
	}

	/** The {@code max}th line of {@code text} wrapped to {@code w}, with the rest cut off and "..." at its end. */
	private String lastLine(Component text, int w, int max) {
		var lines = font.getSplitter().splitLines(text.getString(), w, net.minecraft.network.chat.Style.EMPTY);
		StringBuilder rest = new StringBuilder();
		for (int i = max - 1; i < lines.size(); i++) {
			rest.append(i > max - 1 ? " " : "").append(lines.get(i).getString().strip());
		}
		String r = rest.toString();
		return font.width(r) <= w ? r : font.plainSubstrByWidth(r, Math.max(0, w - font.width("…"))) + "…";
	}

	private void renderFileHelp(GuiGraphics g, int x, int y, int w, int h) {
		int max = Math.max(1, (h - 14) / 9);
		drawLines(g, Component.translatable("screen.aliveworkplace.table.files_help"), x, y, w, max);
		if (selectedFile != null) {
			g.drawString(font, CopperStyle.trim(font, Component.translatable("screen.aliveworkplace.table.selected_file", selectedFile.getFileName().toString()).getString(), w),
				x, y + h - 10, CopperStyle.QUARTZ, false);
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
		return mode == View.LIBRARY && previewW > 0 && mx >= previewX && mx < previewX + previewW && my >= previewY && my < previewY + previewH;
	}

	private void click() {
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && CopperLayout.inside(boxes.close(), mx, my)) {
			// The lintel's close X (Copperline, 47.1): as Esc.
			click();
			onClose();
			return true;
		}
		if (allMaterials && allBackButton.mouseClicked(mx, my, button)) {
			return true;
		}
		if (button == 0 && emptyAction != null && CopperLayout.inside(emptyAction, mx, my) && canUpload) {
			click();
			mode = View.FILES;
			fillFiles();
			updateVisibility();
			return true;
		}
		// The style rail (26.11); covered while the full list is open.
		if (button == 0 && mode == View.LIBRARY && !allMaterials && selectedEntry() != null && selectedEntry().unlocked()) {
			List<TablePayloads.StyleChoice> choices = selectedStyles();
			int i = CopperLayout.at(BlueprintBookLayout.styleEntries(boxes, choices.size() + 1), mx, my);
			if (i >= 0) {
				chooseStyle(i == 0 ? "" : choices.get(i - 1).name());
				click();
				return true;
			}
		}
		if (button == 0) {
			int[] l = boxes.list();
			switch (mode) {
				case LIBRARY -> {
					int i = BlueprintBookLayout.rowAt(l, BlueprintBookLayout.ROW, libScroll, shown.size(), mx, my);
					if (i >= 0) {
						select(shown.get(i).id());
						return true;
					}
				}
				case STORED -> {
					int i = BlueprintBookLayout.rowAt(l, BlueprintBookLayout.STORED_ROW, storedScroll, stored.size(), mx, my);
					if (i >= 0) {
						selectStored(stored.get(i));
						return true;
					}
				}
				case FILES -> {
					int i = BlueprintBookLayout.rowAt(l, BlueprintBookLayout.ROW, filesScroll, fileItems.size(), mx, my);
					if (i >= 0) {
						selectedFile = fileItems.get(i);
						return true;
					}
				}
			}
		}
		if (button == 0 && overGrid(mx, my) && moreX >= 0 && mx >= moreX && mx < moreX + moreW && my >= moreY && my < moreY + 18) {
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

	/** For the screenshot harness: switch to the upload view. */
	public void showFiles() {
		mode = View.FILES;
		fillFiles();
		if (!fileItems.isEmpty()) {
			selectedFile = fileItems.get(0);
		}
		updateVisibility();
	}

	/** The boxes as laid out now (for the screenshot harness's checks). */
	public BlueprintBookLayout.Boxes boxes() {
		return boxes;
	}

	/** The view's buttons as laid out, the copper one first (for the screenshot harness's checks). */
	public List<Button> viewButtons() {
		return mode == View.LIBRARY ? List.of(takeButton, uploadViewButton)
			: mode == View.STORED ? List.of(takeOutButton, storeHeldButton, storeAllButton)
			: List.of(uploadButton, folderButton, backButton);
	}
}
