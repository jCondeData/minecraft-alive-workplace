package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server side of the Blueprint Table: library listing, materials, handing out blueprints, uploads. */
public final class TableServer {
	private static final double MAX_DISTANCE = 8.0;
	private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

	private record Upload(String fileName, byte[] data, int received) {
	}

	public static void init() {
		Platform.get().clientbound(TablePayloads.Open.TYPE, TablePayloads.Open.CODEC);
		Platform.get().clientbound(TablePayloads.Details.TYPE, TablePayloads.Details.CODEC);
		Platform.get().clientbound(TablePayloads.Preview.TYPE, TablePayloads.Preview.CODEC);
		Platform.get().clientbound(TablePayloads.UploadResult.TYPE, TablePayloads.UploadResult.CODEC);

		Platform.get().serverbound(TablePayloads.RequestDetails.TYPE, TablePayloads.RequestDetails.CODEC, (payload, player) -> details(player, payload.id()));
		Platform.get().serverbound(TablePayloads.Take.TYPE, TablePayloads.Take.CODEC, (payload, player) -> take(player, payload.table(), payload.id()));
		Platform.get().serverbound(TablePayloads.Store.TYPE, TablePayloads.Store.CODEC, (payload, player) -> TableLibrary.store(player, payload.table(), payload.all()));
		Platform.get().serverbound(TablePayloads.TakeStored.TYPE, TablePayloads.TakeStored.CODEC,
			(payload, player) -> TableLibrary.takeOut(player, payload.table(), payload.stack()));
		Platform.get().serverbound(TablePayloads.UploadChunk.TYPE, TablePayloads.UploadChunk.CODEC, (payload, player) -> upload(player, payload));
		Platform.get().onPlayerLeave(player -> UPLOADS.remove(player.getUUID()));
	}

	/** Opens (or refreshes) the table screen for {@code player}. */
	public static void open(ServerPlayer player, BlockPos table) {
		ServerLevel level = Players.level(player);
		// Create's uploaded schematics join the library first (39.6), and the player hears what came of each new one.
		CreateSchematics.sync(level.getServer()).forEach(player::sendSystemMessage);
		Platform.get().send(player, openPayload(player, table));
	}

	/** What {@link #open} sends: the library, and the blueprints stored in this table (36.13). */
	public static TablePayloads.Open openPayload(ServerPlayer player, BlockPos table) {
		ServerLevel level = Players.level(player);
		return new TablePayloads.Open(table, listing(level, BlueprintUnlocks.village(level, table)), canUpload(player),
			TableLibrary.stored(TableLibrary.at(player, table)), BlueprintTableBlockEntity.CAPACITY);
	}

	/** The listing of a Table far from any village. */
	public static List<TablePayloads.Entry> listing(MinecraftServer server) {
		return listing(server.overworld(), null);
	}

	/**
	 * The listing of a Table in the village round {@code hall} (null: none). With {@code blueprintUnlocks} on (26.10) the
	 * unlocked builds come first, then the locked ones with how to get them, then a "???" for each rare build not unlocked
	 * yet (a stand-in id, no size: nothing about it is sent).
	 */
	public static List<TablePayloads.Entry> listing(ServerLevel level, @Nullable BlockPos hall) {
		MinecraftServer server = level.getServer();
		List<ResourceLocation> ids = new ArrayList<>(BlueprintLibrary.list(server, false));
		if (BlueprintUnlocks.ENABLED) {
			ids.addAll(BlueprintUnlocks.rareExtras(server));
		}
		List<TablePayloads.Entry> entries = new ArrayList<>();
		List<String> hidden = new ArrayList<>();
		for (ResourceLocation id : ids) {
			BlueprintUnlocks.State state = BlueprintUnlocks.state(level, hall, id);
			if (state == BlueprintUnlocks.State.HIDDEN) {
				hidden.add(BlueprintUnlocks.hint(id));
				continue;
			}
			String hint = state == BlueprintUnlocks.State.UNLOCKED ? "" : BlueprintUnlocks.hint(id);
			BlueprintLibrary.get(server, id).ifPresent(bp -> entries.add(new TablePayloads.Entry(id, bp.size().getX(), bp.size().getY(),
				bp.size().getZ(), bp.solidBlockCount(), state.ordinal(), hint, listedJobs(id, bp))));
		}
		// Unlocked first (26.10); our own blueprints first, then everything else alphabetically.
		entries.sort(Comparator.comparing((TablePayloads.Entry e) -> e.locked())
			.thenComparing(e -> !e.id().getNamespace().equals(AliveWorkplace.MOD_ID) || e.id().getPath().contains("/"))
			.thenComparing(e -> e.id().toString()));
		java.util.Collections.sort(hidden);
		for (int i = 0; i < hidden.size(); i++) {
			entries.add(new TablePayloads.Entry(AliveWorkplace.id("hidden/" + i), 0, 0, 0, 0, BlueprintUnlocks.State.HIDDEN.ordinal(), hidden.get(i)));
		}
		return entries;
	}

	public static List<TablePayloads.Material> materials(Blueprint blueprint) {
		BuildPlan plan = BuildPlan.create(blueprint, new BlueprintData.Placement(ResourceLocation.withDefaultNamespace("overworld"),
			BlockPos.ZERO, Rotation.NONE, Mirror.NONE));
		// Chipped/Rechiseled variants are listed as the plain block they are made from.
		Map<Item, Integer> grouped = new java.util.LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			grouped.merge(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(e.getKey()), e.getValue(), Integer::sum);
		}
		List<TablePayloads.Material> out = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : grouped.entrySet()) {
			out.add(new TablePayloads.Material(BuiltInRegistries.ITEM.getKey(e.getKey()), e.getValue()));
		}
		out.sort(Comparator.comparingInt(TablePayloads.Material::count).reversed());
		return out;
	}

	public static boolean canUpload(ServerPlayer player) {
		return player.hasPermissions(2) || Rules.on(Players.level(player), ModGameRules.ALLOW_UPLOADS);
	}

	private static void details(ServerPlayer player, ResourceLocation id) {
		ServerLevel level = Players.level(player);
		details(level, BlueprintUnlocks.village(level, player.blockPosition()), id).ifPresent(d -> {
			Platform.get().send(player, d);
			preview(level.getServer(), id, BlueprintUnlocks.ENABLED).ifPresent(p -> Platform.get().send(player, p));
		});
	}

	/**
	 * What the Table sends for {@code id} (as drawn, or styled, 26.11) at a Table in the village round {@code hall}: its
	 * materials and, for an unlocked build, the styles it can be built in. Empty for a "???" (26.10), and for a style the
	 * build doesn't take or a locked build in a style.
	 */
	public static Optional<TablePayloads.Details> details(ServerLevel level, @Nullable BlockPos hall, ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		BlueprintUnlocks.State state = BlueprintUnlocks.state(level, hall, base);
		if (state == BlueprintUnlocks.State.HIDDEN) {
			return Optional.empty(); // a "???" (26.10): no materials, no preview
		}
		MinecraftServer server = level.getServer();
		boolean open = state == BlueprintUnlocks.State.UNLOCKED;
		if (!id.equals(base) && (!open || !styleTaken(server, id))) {
			return Optional.empty();
		}
		return BlueprintLibrary.get(server, id).map(bp -> {
			CachedMaterials cached = cachedMaterials(id, bp);
			return new TablePayloads.Details(id, cached.materials(), open ? styles(server, base) : List.of(), cached.jobs().jobs(), cached.jobs().beds());
		});
	}

	// --- styles (26.11) ----------------------------------------------------------------------

	private record CachedStyles(Blueprint blueprint, List<TablePayloads.StyleChoice> styles) {
	}
	private static final Map<ResourceLocation, CachedStyles> STYLES = java.util.Collections.synchronizedMap(new HashMap<>());
	private record CachedMaterials(Blueprint blueprint, java.util.BitSet jobsOn, List<TablePayloads.Material> materials, BuildJobs.Summary jobs) {
	}
	private static final Map<ResourceLocation, CachedMaterials> MATERIALS = java.util.Collections.synchronizedMap(
		new java.util.LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<ResourceLocation, CachedMaterials> eldest) {
				return size() > PREVIEW_CACHE_SIZE;
			}
		});

	/**
	 * The styles {@code base} can be built in at the Table, in the style screen's order: those that swap any of its
	 * blocks (Apricorn only with Cobblemon, as {@link BlueprintStyles#all} has it). None for a Wonder, which keeps its own
	 * palette (as in the style screen), or a build the Table doesn't know. Worked out once per build.
	 */
	public static List<TablePayloads.StyleChoice> styles(MinecraftServer server, ResourceLocation base) {
		if (io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.wonderOf(base).isPresent() || BlueprintStyles.parse(base).isPresent()) {
			return List.of();
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(server, base);
		if (blueprint.isEmpty()) {
			return List.of();
		}
		CachedStyles cached = STYLES.get(base);
		if (cached != null && cached.blueprint() == blueprint.get()) {
			return cached.styles();
		}
		List<TablePayloads.StyleChoice> out = new ArrayList<>();
		for (BlueprintStyles.Style style : BlueprintStyles.all()) {
			if (BlueprintStyles.changes(style, blueprint.get())) {
				out.add(new TablePayloads.StyleChoice(style.name(), style.title(), BuiltInRegistries.ITEM.getKey(style.iconItem())));
			}
		}
		List<TablePayloads.StyleChoice> styles = List.copyOf(out);
		STYLES.put(base, new CachedStyles(blueprint.get(), styles));
		return styles;
	}

	/** Whether a styled id is its build in a style the Table offers for it (true for an id that isn't styled). */
	public static boolean styleTaken(MinecraftServer server, ResourceLocation id) {
		Optional<BlueprintStyles.Styled> styled = BlueprintStyles.parse(id);
		return styled.isEmpty() || styles(server, styled.get().base()).stream().anyMatch(s -> s.name().equals(styled.get().style()));
	}

	private record CachedJobs(Blueprint blueprint, java.util.BitSet jobsOn, List<ResourceLocation> jobs) {
	}

	/**
	 * Which jobs can be had in this game right now ({@link io.github.jcondedata.aliveworkplace.work.Stations#available}),
	 * by registry id: the job lists below are kept only while this stays the same, so a config switch flipped by a
	 * reload (physicians, tailors...) reaches the listing and the details alike (bug B570: a listing kept from before
	 * the flip left the Physician out while the details named it).
	 */
	private static java.util.BitSet jobsOn() {
		java.util.BitSet on = new java.util.BitSet();
		for (net.minecraft.world.entity.npc.VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
			if (io.github.jcondedata.aliveworkplace.work.Stations.available(p)) {
				on.set(BuiltInRegistries.VILLAGER_PROFESSION.getId(p));
			}
		}
		return on;
	}
	/** Every listed build's jobs (36.10), small enough to keep them all. */
	private static final Map<ResourceLocation, CachedJobs> JOBS = java.util.Collections.synchronizedMap(new HashMap<>());

	/**
	 * The jobs a listed build brings (36.10), each once, in the order {@link BuildJobs#of} finds them: what the Table's
	 * search matches "fisherman" or "smith" against. Worked out once per build and kept.
	 */
	public static List<ResourceLocation> listedJobs(ResourceLocation id, Blueprint blueprint) {
		java.util.BitSet on = jobsOn();
		CachedJobs cached = JOBS.get(id);
		if (cached != null && cached.blueprint() == blueprint && cached.jobsOn().equals(on)) {
			return cached.jobs();
		}
		CachedMaterials materials = MATERIALS.get(id);
		BuildJobs.Summary summary = materials != null && materials.blueprint() == blueprint && materials.jobsOn().equals(on) ? materials.jobs() : BuildJobs.of(blueprint);
		List<ResourceLocation> jobs = summary.jobs().stream().flatMap(b -> b.jobs().stream()).distinct().toList();
		JOBS.put(id, new CachedJobs(blueprint, on, jobs));
		return jobs;
	}

	/** A build's materials and its job blocks and beds (36.6a), worked out once per build. */
	private static CachedMaterials cachedMaterials(ResourceLocation id, Blueprint blueprint) {
		java.util.BitSet on = jobsOn();
		CachedMaterials cached = MATERIALS.get(id);
		if (cached != null && cached.blueprint() == blueprint && cached.jobsOn().equals(on)) {
			return cached;
		}
		cached = new CachedMaterials(blueprint, on, List.copyOf(materials(blueprint)), BuildJobs.of(blueprint));
		MATERIALS.put(id, cached);
		return cached;
	}

	// --- 3D preview (26.9) -------------------------------------------------------------------

	/** Most cells a preview sends; bigger builds are drawn in coarser cells until they fit. */
	public static final int PREVIEW_CAP = 4096;
	private static final int PREVIEW_MAX_SCALE = 16;
	private static final int PREVIEW_CACHE_SIZE = 48;
	private record CachedPreview(Blueprint blueprint, TablePayloads.Preview preview) {
	}
	private static final Map<ResourceLocation, CachedPreview> PREVIEWS = java.util.Collections.synchronizedMap(
		new java.util.LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<ResourceLocation, CachedPreview> eldest) {
				return size() > PREVIEW_CACHE_SIZE;
			}
		});

	/**
	 * The 3D preview of a blueprint the Table lists (26.9), or empty for an id it doesn't: unknown, sold by villagers
	 * only, or of an expansion that isn't open. Worked out once per blueprint and kept.
	 */
	public static Optional<TablePayloads.Preview> preview(MinecraftServer server, ResourceLocation id) {
		return preview(server, id, false);
	}

	/** {@link #preview}, with {@code rare} also of the builder-only homes and the Wonders (listed once unlocked, 26.10). */
	public static Optional<TablePayloads.Preview> preview(MinecraftServer server, ResourceLocation id, boolean rare) {
		ResourceLocation base = BlueprintStyles.base(id); // a styled id (26.11) previews its build in that style
		boolean wonder = rare && io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.wonderOf(base).isPresent();
		if (!rare && BlueprintLibrary.soldOnly(base) || !StarterBlueprints.offered(base) || BlueprintLibrary.isWorldgenPiece(base) && !wonder
			|| !styleTaken(server, id)) {
			return Optional.empty();
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(server, id);
		if (blueprint.isEmpty()) {
			return Optional.empty();
		}
		CachedPreview cached = PREVIEWS.get(id);
		if (cached != null && cached.blueprint() == blueprint.get()) {
			return Optional.of(cached.preview());
		}
		TablePayloads.Preview preview = buildPreview(id, blueprint.get());
		PREVIEWS.put(id, new CachedPreview(blueprint.get(), preview));
		return Optional.of(preview);
	}

	/** Whether a preview draws this block: not air, nothing invisible (barriers, light, fluids), no structure-block helpers. */
	public static boolean previewShows(net.minecraft.world.level.block.state.BlockState state) {
		return !state.isAir() && state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE
			&& !state.is(net.minecraft.world.level.block.Blocks.JIGSAW) && !state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK);
	}

	/**
	 * The preview's cells: each cell of {@code scale}³ blocks shows its highest drawn block, and a cell whose six
	 * neighbours are all solid is left out (no one sees it). The scale grows until the cells fit {@link #PREVIEW_CAP}.
	 */
	public static TablePayloads.Preview buildPreview(ResourceLocation id, Blueprint blueprint) {
		return buildPreview(id, blueprint, 1);
	}

	/** {@link #buildPreview}, trying cells of {@code fromScale} blocks first (tests check a coarse preview of a small build). */
	public static TablePayloads.Preview buildPreview(ResourceLocation id, Blueprint blueprint, int fromScale) {
		List<Blueprint.Entry> shown = new ArrayList<>();
		int below = 0; // a basement's depth (36.1): StarterBlueprints.withBasement puts its blocks under y = 0
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (previewShows(e.state())) {
				shown.add(e);
				below = Math.max(below, -e.pos().getY());
			}
		}
		for (int scale = Math.max(1, fromScale); ; scale++) {
			Map<Long, Blueprint.Entry> cells = new HashMap<>();
			for (Blueprint.Entry e : shown) {
				// Floor division, so a cellar's first layer (y = -1) never folds into the ground floor's cell (36.1).
				long key = BlockPos.asLong(Math.floorDiv(e.pos().getX(), scale), Math.floorDiv(e.pos().getY(), scale), Math.floorDiv(e.pos().getZ(), scale));
				Blueprint.Entry old = cells.get(key);
				if (old == null || e.pos().getY() > old.pos().getY()) {
					cells.put(key, e);
				}
			}
			List<Long> visible = new ArrayList<>();
			for (Map.Entry<Long, Blueprint.Entry> c : cells.entrySet()) {
				BlockPos cell = BlockPos.of(c.getKey());
				boolean hidden = true;
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					Blueprint.Entry n = cells.get(cell.relative(d).asLong());
					if (n == null || !n.state().canOcclude()) {
						hidden = false;
						break;
					}
				}
				if (!hidden) {
					visible.add(c.getKey());
				}
			}
			if (visible.size() <= PREVIEW_CAP || scale >= PREVIEW_MAX_SCALE) {
				// Sorted bottom-up so the packet (and what the client draws) is the same every time.
				visible.sort(Comparator.comparingInt((Long k) -> BlockPos.getY(k)).thenComparingInt(k -> BlockPos.getX(k)).thenComparingInt(k -> BlockPos.getZ(k)));
				int count = Math.min(visible.size(), PREVIEW_CAP);
				Map<net.minecraft.world.level.block.state.BlockState, Integer> index = new HashMap<>();
				List<Integer> palette = new ArrayList<>();
				int[] blocks = new int[count * 4];
				for (int i = 0; i < count; i++) {
					long key = visible.get(i);
					net.minecraft.world.level.block.state.BlockState state = cells.get(key).state();
					Integer idx = index.get(state);
					if (idx == null) {
						idx = palette.size();
						index.put(state, idx);
						palette.add(net.minecraft.world.level.block.Block.getId(state));
					}
					blocks[i * 4] = BlockPos.getX(key);
					blocks[i * 4 + 1] = BlockPos.getY(key);
					blocks[i * 4 + 2] = BlockPos.getZ(key);
					blocks[i * 4 + 3] = idx;
				}
				return new TablePayloads.Preview(id, blueprint.size().getX(), blueprint.size().getY(), blueprint.size().getZ(), below, scale, palette, blocks);
			}
		}
	}

	static boolean atTable(ServerPlayer player, BlockPos table) {
		return player.level().getBlockState(table).is(ModBlocks.BLUEPRINT_TABLE)
			&& player.position().distanceToSqr(Vec3.atCenterOf(table)) <= MAX_DISTANCE * MAX_DISTANCE;
	}

	/**
	 * Hands out a blueprint; a styled id (26.11) hands it out set to that style, as the style screen would. Returns false
	 * (and tells the player why) if they can't have it.
	 */
	public static boolean take(ServerPlayer player, BlockPos table, ResourceLocation styledId) {
		if (!atTable(player, table)) {
			return false;
		}
		ResourceLocation id = BlueprintStyles.base(styledId);
		Optional<Blueprint> blueprint = BlueprintLibrary.get(player.level().getServer(), id);
		if (blueprint.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.blueprint.unknown", id.toString()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!BlueprintUnlocks.ENABLED && BlueprintLibrary.soldOnly(id)) {
			// Not listed (34.15a, 34.15b): only a builder sells it, so the Table won't hand it out to a request for it either.
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.sold_only", Blueprints.displayName(id)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!StarterBlueprints.offered(id)) {
			// Not listed (B119): its expansion isn't open, so a request for it by id is refused as an unknown blueprint.
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.blueprint.unknown", id.toString()).withStyle(ChatFormatting.RED));
			return false;
		}
		ServerLevel level = Players.level(player);
		switch (BlueprintUnlocks.state(level, BlueprintUnlocks.village(level, table), id)) {
			case HIDDEN -> {
				// A "???" (26.10): refused without its name.
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.locked_hidden").withStyle(ChatFormatting.YELLOW));
				return false;
			}
			case LOCKED -> {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.locked", Blueprints.displayName(id),
					Component.translatable(BlueprintUnlocks.hint(id))).withStyle(ChatFormatting.YELLOW));
				return false;
			}
			case VIEW_ONLY -> {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.wonder_only", Blueprints.displayName(id)).withStyle(ChatFormatting.YELLOW));
				return false;
			}
			default -> {
			}
		}
		if (!styleTaken(player.level().getServer(), styledId)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.style_unsupported", Blueprints.displayName(id)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!player.getAbilities().instabuild) {
			Inventory inventory = player.getInventory();
			int slot = -1;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).is(ModItems.BLANK_BLUEPRINT)) {
					slot = i;
					break;
				}
			}
			if (slot < 0) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.need_blank").withStyle(ChatFormatting.YELLOW));
				return false;
			}
			inventory.removeItem(slot, 1);
		}
		// The same blueprint the style screen makes: its structure is the styled id.
		player.getInventory().placeItemBackInInventory(BlueprintItem.create(styledId, blueprint.get().size()));
		player.level().playSound(null, table, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.took", Blueprints.displayName(styledId)));
		return true;
	}

	/** Handles one upload packet (public for tests). */
	public static void upload(ServerPlayer player, TablePayloads.UploadChunk chunk) {
		UUID uuid = player.getUUID();
		if (!canUpload(player) || !atTable(player, chunk.table())) {
			UPLOADS.remove(uuid);
			reply(player, false, Component.translatable("message.aliveworkplace.table.upload_denied"), null);
			return;
		}
		if (chunk.totalBytes() <= 0 || chunk.totalBytes() > BlueprintFiles.MAX_FILE_BYTES) {
			reply(player, false, Component.translatable("message.aliveworkplace.import.error.file_too_big",
				chunk.totalBytes() / 1024, BlueprintFiles.MAX_FILE_BYTES / 1024), null);
			return;
		}
		if (!BlueprintImporter.hasSupportedExtension(chunk.fileName())) {
			reply(player, false, Component.translatable("message.aliveworkplace.import.error.unknown_format"), null);
			return;
		}
		Upload current = UPLOADS.get(uuid);
		if (chunk.offset() == 0) {
			current = new Upload(chunk.fileName(), new byte[chunk.totalBytes()], 0);
		} else if (current == null || current.data().length != chunk.totalBytes() || current.received() != chunk.offset()
			|| !current.fileName().equals(chunk.fileName())) {
			UPLOADS.remove(uuid);
			reply(player, false, Component.translatable("message.aliveworkplace.table.upload_broken"), null);
			return;
		}
		if (chunk.offset() + chunk.data().length > chunk.totalBytes()) {
			UPLOADS.remove(uuid);
			reply(player, false, Component.translatable("message.aliveworkplace.table.upload_broken"), null);
			return;
		}
		System.arraycopy(chunk.data(), 0, current.data(), chunk.offset(), chunk.data().length);
		int received = chunk.offset() + chunk.data().length;
		if (received < chunk.totalBytes()) {
			UPLOADS.put(uuid, new Upload(current.fileName(), current.data(), received));
			return;
		}
		UPLOADS.remove(uuid);
		try {
			String folder = "uploads/" + BlueprintImporter.sanitizeFolder(player.getGameProfile().getName());
			BlueprintImporter.Imported imported = BlueprintImporter.importBytes(player.level().getServer(), folder, current.fileName(), current.data());
			reply(player, true, imported.summary(), imported.id());
			open(player, chunk.table());
		} catch (BlueprintFormatException e) {
			reply(player, false, Component.translatable("message.aliveworkplace.import.failed", current.fileName(), e.toComponent()), null);
		}
	}

	private static void reply(ServerPlayer player, boolean ok, Component message, ResourceLocation id) {
		Platform.get().send(player, new TablePayloads.UploadResult(ok, message, Optional.ofNullable(id)));
	}

	private TableServer() {
	}
}
