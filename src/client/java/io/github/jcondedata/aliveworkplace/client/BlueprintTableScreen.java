package io.github.jcondedata.aliveworkplace.client;

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
	private static final int PANEL_BG = 0xE0101418;
	private static final int PANEL_EDGE = 0xFF3A4C66;
	private static final int LIST_BG = 0x80000000;
	private static final int ROW = 22;

	private enum Mode { LIBRARY, FILES }

	private final BlockPos table;
	private List<TablePayloads.Entry> entries;
	private boolean canUpload;
	private final Map<ResourceLocation, List<TablePayloads.Material>> details = new HashMap<>();
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

	public BlueprintTableScreen(TablePayloads.Open open) {
		super(Component.translatable("block.aliveworkplace.blueprint_table"));
		this.table = open.table();
		this.entries = open.entries();
		this.canUpload = open.canUpload();
	}

	public BlockPos table() {
		return table;
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
		panelW = Math.min(width - 16, 420);
		panelH = Math.min(height - 16, 250);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		int listW = Math.min(180, panelW / 2 - 12);
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
		takeButton.active = selectedId != null;
		uploadButton.active = !lib && selectedFile != null && !uploading && canUpload;
	}

	@Override
	public void tick() {
		updateVisibility();
	}

	// --- actions ---------------------------------------------------------------------------

	/** Selects a library entry and asks the server for its materials. Also used by the screenshot harness. */
	public void select(ResourceLocation id) {
		selectedId = id;
		for (LibraryList.Row row : library.children()) {
			if (row.entry.id().equals(id)) {
				library.setSelected(row);
				library.scrollTo(row);
			}
		}
		if (!details.containsKey(id)) {
			ClientPlayNetworking.send(new TablePayloads.RequestDetails(id));
		}
	}

	private void take() {
		if (selectedId != null) {
			ClientPlayNetworking.send(new TablePayloads.Take(table, selectedId));
		}
	}

	private void upload() {
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
		g.drawString(font, title, left + 8, top + 8, 0xFFFFD66B, false);
		Component sub = mode == Mode.LIBRARY
			? Component.translatable("screen.aliveworkplace.table.library_count", entries.size())
			: Component.translatable("screen.aliveworkplace.table.files_title");
		g.drawString(font, sub, left + panelW - 8 - font.width(sub), top + 8, 0xFF9AA8BA, false);

		int dx = library.getX() + library.getWidth() + 10;
		int dw = left + panelW - 8 - dx;
		int dy = top + 24;
		if (mode == Mode.LIBRARY) {
			renderDetails(g, dx, dy, dw, mouseX, mouseY);
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
		g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, PANEL_EDGE);
		g.fill(left, top, left + panelW, top + panelH, PANEL_BG);
	}

	private void renderDetails(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
		TablePayloads.Entry entry = selectedEntry();
		if (entry == null) {
			g.drawWordWrap(font, Component.translatable("screen.aliveworkplace.table.empty"), x, y, w, 0xFFAAAAAA);
			return;
		}
		g.drawString(font, Blueprints.displayName(entry.id()), x, y, 0xFFFFFFFF, false);
		y += 12;
		g.drawString(font, Component.translatable("screen.aliveworkplace.table.size", entry.sizeX(), entry.sizeY(), entry.sizeZ(), entry.blocks()),
			x, y, 0xFF9AA8BA, false);
		y += 10;
		g.drawString(font, Component.literal(entry.id().toString()), x, y, 0xFF5E6B7C, false);
		y += 14;
		g.drawString(font, Component.translatable("screen.aliveworkplace.table.materials"), x, y, 0xFFFFD66B, false);
		y += 11;

		List<TablePayloads.Material> materials = details.get(entry.id());
		if (materials == null) {
			g.drawString(font, Component.translatable("screen.aliveworkplace.table.loading"), x, y, 0xFF777777, false);
			return;
		}
		int cell = 34;
		int cols = Math.max(1, w / cell);
		int bottom = top + panelH - 46;
		int maxRows = Math.max(1, (bottom - y) / 20);
		int shown = Math.min(materials.size(), cols * maxRows);
		if (shown < materials.size()) {
			shown = Math.max(0, shown - 1); // leave room for the "+N more" cell
		}
		ItemStack hovered = null;
		for (int i = 0; i < shown; i++) {
			TablePayloads.Material m = materials.get(i);
			int cx = x + (i % cols) * cell;
			int cy = y + (i / cols) * 20;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(m.item()));
			g.renderItem(stack, cx, cy);
			String count = m.count() >= 1000 ? (m.count() / 1000) + "k" : String.valueOf(m.count());
			g.drawString(font, count, cx + 17, cy + 5, 0xFFE8E8E8, true);
			if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
				hovered = stack;
			}
		}
		if (shown < materials.size()) {
			int cx = x + (shown % cols) * cell;
			int cy = y + (shown / cols) * 20;
			g.drawString(font, "+" + (materials.size() - shown), cx + 2, cy + 5, 0xFF9AA8BA, false);
		}
		if (hovered != null) {
			g.renderTooltip(font, hovered, mouseX, mouseY);
		}
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
		if (selectedId == null) {
			return null;
		}
		for (TablePayloads.Entry e : entries) {
			if (e.id().equals(selectedId)) {
				return e;
			}
		}
		return null;
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
			g.fill(getX(), getY(), getX() + width, getY() + height, LIST_BG);
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
				g.drawString(font, font.plainSubstrByWidth(Blueprints.displayName(entry.id()).getString(), width - 4), x + 2, y + 2, 0xFFFFFFFF, false);
				String size = entry.sizeX() + "×" + entry.sizeY() + "×" + entry.sizeZ();
				String source = entry.id().getPath().startsWith("uploads/") ? " · " + Component.translatable("screen.aliveworkplace.table.uploaded").getString() : "";
				g.drawString(font, size + source, x + 2, y + 12, 0xFF7F8C9E, false);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				select(entry.id());
				return true;
			}

			@Override
			public Component getNarration() {
				return Blueprints.displayName(entry.id());
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
			g.fill(getX(), getY(), getX() + width, getY() + height, LIST_BG);
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
				g.drawString(font, font.plainSubstrByWidth(path.getFileName().toString(), width - 4), x + 2, y + 2, 0xFFFFFFFF, false);
				g.drawString(font, Math.max(1, size / 1024) + " KB", x + 2, y + 12, 0xFF7F8C9E, false);
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
