package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Bringing an old build up to its latest look (ROADMAP 36.21). A build already standing was built from the drawing of
 * its day; when that blueprint has been redrawn since (fixed floating or invisible pieces, new furniture), the Village
 * Hall's Repairs page offers "Refresh to the latest look". A first click shows what changes, a second hands it to the
 * nearest free builder who looks after the build (or, with none free, it waits until one is), who works it as a repair:
 * the older drawing's leftovers come away first (their items go to the store), then the new pieces go in.
 *
 * <p>Which drawing it was built from: the mod ships the older drawings of its blueprints
 * ({@code data/aliveworkplace/blueprint_history/<blueprint>/<version>.nbt}, from {@code tools/blueprints/history.py}),
 * and the one the build still matches best is taken. That drawing is the build's record of what the builder put
 * there: a block standing as it drew it is the builder's; anything else (a block a player put there or changed) stays.
 * Never touched either: containers and what's in them, a bed a villager sleeps in (their claim kept), job blocks.
 */
public final class Refreshes {
	/** What a refresh would change: the older drawing, and what goes in and comes away by kind. */
	public record Change(String from, int[] added, int[] removed) {
		public int total() {
			return added[0] + added[1] + added[2] + removed[0] + removed[1] + removed[2];
		}
	}

	/** The kinds of piece the page counts. */
	public static final int DECORATION = 0, FURNITURE = 1, BLOCK = 2;

	private static final String DIR = "blueprint_history";
	/** An older drawing; {@code file} is the shipped file it was read from (null for one added by hand). */
	private record Old(String key, Blueprint drawing, @Nullable ResourceLocation file) {
	}

	/** A shipped older drawing's file, listed at a reload but not read yet (B1418). */
	private record Shipped(String key, ResourceLocation file, Resource resource) {
	}

	@Nullable
	private static ResourceManager loadedFrom;
	/**
	 * B1418: by blueprint, its shipped older drawings' files in key order, listed (not read) when the resources change: the
	 * history ships over a thousand drawings, and reading them all on the first question froze the server for seconds.
	 */
	private static Map<ResourceLocation, List<Shipped>> shipped = Map.of();
	/** B1418: by blueprint, its shipped older drawings as read, each blueprint's read the first time it is asked about. */
	private static final Map<ResourceLocation, List<Old>> history = new HashMap<>();
	/** B1418: how many shipped drawing files have been read since the mod started (for the tests). */
	private static int filesRead;

	/** B1418: how many shipped drawing files have been read since the mod started (for the tests). */
	public static synchronized int filesRead() {
		return filesRead;
	}

	/**
	 * B1407: by blueprint (a modded library copy's {@code modded/<path>}), the key its older drawing of the day of another
	 * key ships under ({@code blueprint_history/<path>/aliases.json}, from tools/blueprints/history.py): a modded site
	 * mid-build finds the key of the VANILLA drawing it was begun from, and the modded copy of that day shipped once, under
	 * the key of the first commit or release that had it.
	 */
	private static Map<ResourceLocation, Map<String, String>> aliases = Map.of();
	private static final String ALIASES = "aliases.json";
	/** Older drawings added by hand (tests, or a datapack's own code), by blueprint. */
	private static final Map<ResourceLocation, List<Old>> EXTRA = new HashMap<>();

	/** Adds an older drawing of {@code structure} under {@code key} (as a shipped one would be). */
	public static synchronized void addHistory(ResourceLocation structure, String key, Blueprint drawing) {
		EXTRA.computeIfAbsent(structure, k -> new ArrayList<>()).removeIf(o -> o.key().equals(key));
		EXTRA.get(structure).add(new Old(key, drawing, null));
		DRAWN_AS.clear();
		STANDING.clear();
	}

	public static synchronized void removeHistory(ResourceLocation structure) {
		EXTRA.remove(structure);
		STANDING.clear();
		DRAWN_AS.clear();
	}

	/** The older drawings of {@code structure}: those the mod ships, then any added. */
	private static synchronized List<Old> history(MinecraftServer server, ResourceLocation structure) {
		ResourceManager resources = server.getResourceManager();
		if (resources != loadedFrom) {
			loadedFrom = resources;
			shipped = index(resources);
			history.clear();
			aliases = loadAliases(resources);
			STANDING.clear();
			DRAWN_AS.clear();
		}
		List<Old> out = new ArrayList<>(history.computeIfAbsent(structure, Refreshes::read));
		out.addAll(EXTRA.getOrDefault(structure, List.of()));
		return out;
	}

	/** B1407: the key {@code structure}'s older drawing of the day of {@code key} ships under ({@code key} if not aliased). */
	private static synchronized String resolved(MinecraftServer server, ResourceLocation structure, String key) {
		history(server, structure); // (loads the aliases with the history)
		return aliases.getOrDefault(structure, Map.of()).getOrDefault(key, key);
	}

	private static Map<ResourceLocation, Map<String, String>> loadAliases(ResourceManager resources) {
		Map<ResourceLocation, Map<String, String>> out = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : resources.listResources(DIR, id -> id.getPath().endsWith("/" + ALIASES)).entrySet()) {
			String path = e.getKey().getPath();
			String folder = path.substring(DIR.length() + 1, path.length() - ALIASES.length() - 1);
			ResourceLocation structure = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), folder);
			try (java.io.Reader in = e.getValue().openAsReader()) {
				com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(in).getAsJsonObject();
				Map<String, String> keys = out.computeIfAbsent(structure, k -> new HashMap<>());
				for (Map.Entry<String, com.google.gson.JsonElement> alias : json.entrySet()) {
					keys.put(alias.getKey(), alias.getValue().getAsString());
				}
			} catch (Exception ex) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.warn("Couldn't read the older drawings' aliases {}: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	/** The older drawing {@code key} of {@code structure}, as shipped (or added), if there is one. */
	public static Optional<Blueprint> olderDrawing(MinecraftServer server, ResourceLocation structure, String key) {
		return history(server, structure).stream().filter(o -> o.key().equals(key)).findFirst().map(Old::drawing);
	}

	/** The keys of every older drawing of {@code structure}, as shipped (or added), in key order. */
	public static List<String> olderKeys(MinecraftServer server, ResourceLocation structure) {
		return history(server, structure).stream().map(Old::key).toList();
	}

	/** B1418: every shipped older drawing's file by blueprint, in key order: a listing, no file is read. */
	private static Map<ResourceLocation, List<Shipped>> index(ResourceManager resources) {
		Map<ResourceLocation, List<Shipped>> out = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : resources.listResources(DIR, id -> id.getPath().endsWith(".nbt")).entrySet()) {
			String path = e.getKey().getPath().substring(DIR.length() + 1, e.getKey().getPath().length() - ".nbt".length());
			int slash = path.lastIndexOf('/');
			if (slash <= 0) {
				continue;
			}
			ResourceLocation structure = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(0, slash));
			out.computeIfAbsent(structure, k -> new ArrayList<>()).add(new Shipped(path.substring(slash + 1), e.getKey(), e.getValue()));
		}
		out.values().forEach(list -> list.sort(Comparator.comparing(Shipped::key)));
		return out;
	}

	/** B1418: {@code structure}'s shipped older drawings, read from their files (only that blueprint's), in key order. */
	private static List<Old> read(ResourceLocation structure) {
		List<Shipped> files = shipped.getOrDefault(structure, List.of());
		if (files.isEmpty()) {
			return List.of();
		}
		List<Old> out = new ArrayList<>(files.size());
		for (Shipped file : files) {
			filesRead++;
			try (InputStream in = file.resource().open()) {
				CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
				// (with the blueprint's own id, so its basement and features are worked out as for the current drawing)
				Blueprint drawing = StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
					Blueprint.fromStructureNbt(structure, tag, Lookup.lookup(BuiltInRegistries.BLOCK))));
				out.add(new Old(file.key(), drawing, file.file()));
			} catch (Exception ex) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.warn("Couldn't read the older drawing {}: {}", file.file(), ex.toString());
			}
		}
		return List.copyOf(out);
	}

	/** B1418: forgets what was listed and read, as a reload of the resources does (for the tests). */
	public static synchronized void forgetShipped() {
		loadedFrom = null;
		shipped = Map.of();
		history.clear();
	}

	// ---- what changes ------------------------------------------------------------------------------------------------

	/** The building's latest drawing as it would be built (its furniture as it was built with). */
	private static Optional<Blueprint> current(ServerLevel level, BuildSiteManager.Finished f) {
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture = refreshedFurniture(level, f.structure(),
			BuildSiteManager.get(level).furniture(f.placement()));
		return BlueprintLibrary.get(level, f.structure()).map(b -> furniture.apply(b, f.structure()));
	}

	/**
	 * B642: the furniture {@code asBuilt} (the build's saved choice, by the spots file of the day it was furnished)
	 * brought to the blueprint's spots today: the pieces standing on a spot that is still one kept, every other spot of
	 * today's file furnished as a new build would be. A vanilla build stays vanilla.
	 */
	public static io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice refreshedFurniture(ServerLevel level, ResourceLocation structure,
																								io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice asBuilt) {
		if (asBuilt.spots().isEmpty()) {
			return asBuilt;
		}
		List<io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot> today = io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture.of(structure);
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice fresh = io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.choose(today,
			io.github.jcondedata.aliveworkplace.furnish.Furnishings.MODS, io.github.jcondedata.aliveworkplace.furnish.Furnishings.ENABLED, false,
			io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.hasPokeBallDesks(structure));
		return asBuilt.refreshedFor(today, fresh);
	}

	/**
	 * B642: an older drawing as the build was built from it: its own blocks (from the modded library's older copy for a
	 * modded build, never today's copy), with the pieces it was furnished with where they were put.
	 */
	private static Optional<Blueprint> asBuilt(ServerLevel level, ResourceLocation structure, String key,
											   io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		if (LATEST.equals(key)) {
			return latestDrawing(level, structure, furniture).map(furniture::applyAsBuilt);
		}
		ResourceLocation id = historyId(structure, furniture);
		List<Old> olds = history(level.getServer(), id);
		Optional<Old> old = olds.stream().filter(o -> o.key().equals(key)).findFirst();
		if (old.isEmpty()) {
			// B1407: a modded build begun from the vanilla drawing {@code key}: its modded copy of that day ships under another key
			String alias = resolved(level.getServer(), id, key);
			old = olds.stream().filter(o -> o.key().equals(alias)).findFirst();
		}
		return old.map(o -> furniture.applyAsBuilt(o.drawing()));
	}

	/**
	 * B641: the "older drawing" that is today's drawing itself, furnished as the build was, for a build furnished by an
	 * older spots file ({@link io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice#staleFor}): a Terrace planned
	 * before B281 has no piece for the east kitchen's table and chair, which kept their spruce stand-ins, and a refresh
	 * swaps them for the mods' pieces (and takes down the library's crockery that hung over its tables).
	 */
	public static final String LATEST = "latest";

	/** Today's drawing of {@code structure} as the build's library has it (the modded library's copy for a modded build). */
	private static Optional<Blueprint> latestDrawing(ServerLevel level, ResourceLocation structure,
													 io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		Optional<Blueprint> drawn = BlueprintLibrary.get(level, structure);
		return furniture.modded() ? io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.copy(structure).or(() -> drawn) : drawn;
	}

	/** Whose older drawings a build's are: the blueprint's own, or (37.12) its modded library copy's ({@code modded/<path>}). */
	private static ResourceLocation historyId(ResourceLocation structure, io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		return furniture.modded() ? ResourceLocation.fromNamespaceAndPath(structure.getNamespace(),
			io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.PREFIX + structure.getPath()) : structure;
	}

	/** What stays whatever the drawings say: a container (and what's in it), a bed someone sleeps in, a job block, a builder's bench. */
	static boolean kept(ServerLevel level, BlockPos pos, BlockState world) {
		return MaterialRules.isProtected(world, world.getDestroySpeed(level, pos))
			|| world.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH)
			|| world.getBlock() instanceof BedBlock && BuilderWork.someonesBed(level, pos, world)
			|| PoiTypes.forState(world).map(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false);
	}

	/**
	 * The refresh's work from the older drawing {@code from} (an ordinary repair if that drawing is gone). {@code drawn} is
	 * the blueprint as drawn today (unfurnished); {@code furniture} the build's saved choice, as it was built.
	 */
	static BuildPlan plan(ServerLevel level, Blueprint drawn, ResourceLocation structure, String from, BlueprintData.Placement placement,
						  @Nullable io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice built = furniture == null ? io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.VANILLA : furniture;
		Blueprint current = refreshedFurniture(level, structure, built).apply(drawn, structure);
		Optional<Blueprint> was = asBuilt(level, structure, from, built);
		if (was.isEmpty()) {
			return BuildPlan.repair(current, placement, level, BuildSiteManager.get(level).gaveUp(placement));
		}
		return BuildPlan.refresh(current, was.get(), placement, level,
			(pos, state) -> kept(level, pos, state) && !leftoverPiece(level, built, placement, pos, state));
	}

	/**
	 * B1383: whether {@code world} at {@code pos} is a furniture mod's piece that {@code built} (the build's furniture, as
	 * built) put there and that holds nothing: a refresh takes it away like any other piece of the older drawing (B642),
	 * though it has a block entity (a Handcrafted shelf, nightstand or crockery set). One with something in it stays, as a
	 * container does; never a bed someone sleeps in or a job block.
	 */
	public static boolean leftoverPiece(ServerLevel level, @Nullable io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice built,
										BlueprintData.Placement placement, BlockPos pos, BlockState world) {
		if (built == null || built.spots().isEmpty() || BuiltInRegistries.BLOCK.getKey(world.getBlock()).getNamespace().equals("minecraft")
			|| world.getBlock() instanceof BedBlock || PoiTypes.forState(world).isPresent()) {
			return false;
		}
		boolean placed = false;
		for (io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.Placed piece : built.spots().values()) {
			for (Map.Entry<BlockPos, BlockState> cell : piece.cells().entrySet()) {
				if (cell.getValue().getBlock() == world.getBlock() && pos.equals(placement.origin().offset(
					net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(piece.pos().offset(cell.getKey()),
						placement.mirror(), placement.rotation(), BlockPos.ZERO)))) {
					placed = true;
				}
			}
		}
		if (!placed) {
			return false;
		}
		if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container && !container.isEmpty()) {
			return false;
		}
		return !io.github.jcondedata.aliveworkplace.platform.Platform.get().items().hasMatching(level, pos, stack -> !stack.isEmpty());
	}

	/**
	 * What refreshing {@code f} would change, or empty when nothing would: no older drawing of its blueprint is
	 * shipped, the build doesn't stand as one of them drew it (it was built from the latest), or nothing it would touch
	 * is the builder's to change.
	 */
	public static Optional<Change> change(ServerLevel level, BuildSiteManager.Finished f) {
		Optional<Blueprint> current = current(level, f);
		if (current.isEmpty()) {
			return Optional.empty();
		}
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture = BuildSiteManager.get(level).furniture(f.placement());
		List<Old> olds = new ArrayList<>(history(level.getServer(), historyId(f.structure(), furniture)));
		if (furniture.staleFor(io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture.of(f.structure()))) {
			latestDrawing(level, f.structure(), furniture).ifPresent(d -> olds.add(new Old(LATEST, d, null))); // B641
		}
		if (olds.isEmpty()) {
			return Optional.empty(); // (a modded library build: its copy's older drawings aren't shipped yet)
		}
		Old best = best(level, current.get(), olds, f.placement(), furniture);
		BuildSiteManager manager = BuildSiteManager.get(level);
		String begun = manager.refreshBegun(f.placement());
		if (best == null) {
			if (begun != null) {
				manager.setRefreshBegun(f.placement(), null); // (stands as today's drawing now: nothing left to take up)
			}
			return Optional.empty();
		}
		// B1500: a refresh begun and left (its Builder died) goes on from the drawing it was begun from, while the build
		// still stands as that drawing more than as today's. Guessed again from the half-cleared house, another older
		// drawing could match it a little better, and what only the true one drew (its furniture by the front door) was
		// then never taken away, with the new door's place still taken.
		if (begun != null && !begun.equals(best.key())) {
			Map<BlockPos, BlockState> now = BuildPlan.create(current.get(), f.placement()).targets();
			Optional<Old> from = olds.stream().filter(o -> o.key().equals(begun)).findFirst()
				.filter(o -> score(level, BuildPlan.create(furniture.applyAsBuilt(o.drawing()), f.placement()).targets(), now) > 0);
			if (from.isPresent()) {
				best = from.get();
			} else {
				manager.setRefreshBegun(f.placement(), null); // (that drawing is no longer shipped, or the build no longer stands as it)
			}
		}
		Change change = changeFrom(level, f, best.key(), furniture);
		return change.total() == 0 ? Optional.empty() : Optional.of(change);
	}

	/** What refreshing {@code f} from its older drawing {@code from} would change. */
	private static Change changeFrom(ServerLevel level, BuildSiteManager.Finished f, String from,
									 io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		BuildPlan plan = plan(level, BlueprintLibrary.get(level, f.structure()).orElseThrow(), f.structure(), from, f.placement(), furniture);
		int[] added = new int[3];
		int[] removed = new int[3];
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				added[kind(s.state())]++;
			}
		}
		for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.CLEAR)) {
			BlockState world = level.getBlockState(s.pos());
			if (s.state().isAir() && !MaterialRules.isSecondaryHalf(world)) {
				removed[kind(world)]++;
			}
		}
		return new Change(from, added, removed);
	}

	/** The older drawing among {@code olds} the build on {@code placement} stands as better than {@code current}, or null. */
	@Nullable
	private static Old best(ServerLevel level, Blueprint current, List<Old> olds, BlueprintData.Placement placement,
							io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		Map<BlockPos, BlockState> now = BuildPlan.create(current, placement).targets();
		Old best = null;
		int bestScore = 0;
		for (Old old : olds) {
			// B642: as it was built, its pieces where they were put (the spots of their day), not as today's spots would furnish it
			Map<BlockPos, BlockState> was = BuildPlan.create(furniture.applyAsBuilt(old.drawing()), placement).targets();
			int score = score(level, was, now);
			if (score > bestScore) {
				best = old;
				bestScore = score;
			}
		}
		return best;
	}

	// ---- the drawing a standing build was built from (B1369, B1370, B1372) ----------------------------------------------

	/**
	 * B1369, B1370, B1372: the older drawing (its key in the shipped history) the finished build of {@code structure} on
	 * {@code placement} still stands as, better than today's drawing; null when it stands as today's (or no older drawing
	 * is shipped). Once a family's drawing is rebuilt under the same id, a house of the old drawing keeps it until it is
	 * refreshed (tools/blueprints/REBUILD.md): its repairs, its take-down and its upgrade go by this drawing, not today's.
	 */
	@Nullable
	public static String builtFrom(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement,
								   io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		List<Old> olds = history(level.getServer(), historyId(structure, furniture));
		if (olds.isEmpty()) {
			return null; // (most builds: nothing to look at)
		}
		Optional<Blueprint> current = BlueprintLibrary.get(level, structure)
			.map(b -> refreshedFurniture(level, structure, furniture).apply(b, structure));
		if (current.isEmpty()) {
			return null;
		}
		Old best = best(level, current.get(), olds, placement, furniture);
		return best == null ? null : best.key();
	}

	// ---- the drawing a site mid-build was begun from (B1371) -----------------------------------------------------------

	/** B1371: {@link BuildSite#fingerprint} of each older drawing as the library would have read it, by blueprint and key. */
	private static final Map<ResourceLocation, Map<String, Integer>> DRAWN_AS = new HashMap<>();

	/**
	 * B1371: the key of the older drawing of {@code structure} that, as the blueprint library read it when it was today's
	 * drawing, has the fingerprint {@code drawnHash} (what a site begun from it saved, B1049); null if none has. Exact, not
	 * a likeness: a site mid-build when its family's drawing is rebuilt keeps the drawing it was begun from
	 * (tools/blueprints/REBUILD.md), and a blueprint replaced with no older drawing shipped (a Create schematic uploaded
	 * again) still restarts as the new one (B1049).
	 */
	@Nullable
	static synchronized String drawnAs(MinecraftServer server, ResourceLocation structure, int drawnHash) {
		List<Old> olds = history(server, structure);
		if (olds.isEmpty()) {
			return null;
		}
		Map<String, Integer> known = DRAWN_AS.computeIfAbsent(structure, k -> new HashMap<>());
		for (Old old : olds) {
			Integer print = known.get(old.key());
			if (print == null) {
				print = libraryPrint(server, structure, old);
				known.put(old.key(), print);
			}
			if (print == drawnHash) {
				return old.key();
			}
		}
		return null;
	}

	/** The fingerprint of {@code old} read as {@link BlueprintLibrary} reads a drawing: loaded into a structure template first. */
	private static int libraryPrint(MinecraftServer server, ResourceLocation structure, Old old) {
		try {
			CompoundTag tag = null;
			if (old.file() != null) {
				Optional<Resource> file = server.getResourceManager().getResource(old.file());
				if (file.isPresent()) {
					try (InputStream in = file.get().open()) {
						tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
					}
				}
			}
			if (tag == null) {
				tag = io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles.toStructureNbt(old.drawing());
			}
			net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template
				= new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
			template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
			return BuildSite.fingerprint(StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
				Blueprint.fromTemplate(structure, template, Lookup.lookup(BuiltInRegistries.BLOCK)))));
		} catch (Exception ex) {
			return BuildSite.fingerprint(old.drawing()); // (unreadable: as it was read for the history)
		}
	}

	/**
	 * B1371: the older drawing {@code key} of {@code structure} as a build begun from it is built and furnished, with no
	 * fallback to today's drawing.
	 */
	static Optional<Blueprint> olderAsBuilt(ServerLevel level, ResourceLocation structure, String key,
										   io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		return LATEST.equals(key) ? Optional.empty() : asBuilt(level, structure, key, furniture);
	}

	/**
	 * The drawing of {@code structure} the builder works a standing build by: the older drawing {@code from} as the build
	 * was built and furnished from it, or (null, or that drawing no longer shipped) today's, with what it was built with.
	 */
	public static Optional<Blueprint> drawing(ServerLevel level, ResourceLocation structure, @Nullable String from,
											  io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		if (from != null && !from.isEmpty() && !LATEST.equals(from)) {
			Optional<Blueprint> old = asBuilt(level, structure, from, furniture);
			if (old.isPresent()) {
				return old;
			}
		}
		return BlueprintLibrary.get(level, structure).map(b -> furniture.apply(b, structure));
	}

	/** B1373, B1374: what {@link #standingSize} found for one building, and on which tick. */
	private record Standing(long tick, @Nullable Blueprint drawing) {
	}

	private record StandingKey(ResourceLocation structure, BlueprintData.Placement placement) {
	}

	/** How long (ticks) a building's {@link #standingSize} is kept before it is looked at again. */
	private static final int STANDING_TICKS = 40;
	private static final Map<StandingKey, Standing> STANDING = new HashMap<>();

	/**
	 * B1373, B1374: the size of the older drawing the finished building {@code f} still stands as (its box is that size at
	 * its placement), or null when it stands as today's drawing (or no older drawing of its blueprint is shipped): the
	 * Mover, the Move page and its fit check measure a house of an old drawing as it stands.
	 */
	@Nullable
	public static net.minecraft.core.Vec3i standingSize(ServerLevel level, BuildSiteManager.Finished f) {
		return standingDrawing(level, f).map(Blueprint::size).orElse(null);
	}

	/**
	 * B1373, B1374, B1435, B1436: the older drawing the finished building {@code f} still stands as, as it was built and
	 * furnished, or empty when it stands as today's drawing (or no older drawing of its blueprint is shipped). Everything
	 * that reads a finished building's box reads this drawing's when there is one: the Move page as its box, a neighbor's
	 * levelling, the Steward's plot search and the homes as well as today's ({@link #standingBox}). Kept for a couple of
	 * seconds per building, since the fit check and the Move page ask it for every building of the village on each click.
	 */
	public static synchronized Optional<Blueprint> standingDrawing(ServerLevel level, BuildSiteManager.Finished f) {
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture = BuildSiteManager.get(level).furniture(f.placement());
		if (history(level.getServer(), historyId(f.structure(), furniture)).isEmpty()) {
			return Optional.empty(); // (most builds: nothing to look at)
		}
		long now = level.getGameTime();
		StandingKey key = new StandingKey(f.structure(), f.placement());
		Standing known = STANDING.get(key);
		if (known != null && now >= known.tick() && now - known.tick() < STANDING_TICKS) {
			return Optional.ofNullable(known.drawing());
		}
		String from = builtFrom(level, f.structure(), f.placement(), furniture);
		Blueprint drawing = from == null ? null : drawing(level, f.structure(), from, furniture).orElse(null);
		if (STANDING.size() > 1024) {
			STANDING.clear();
		}
		STANDING.put(key, new Standing(now, drawing));
		return Optional.ofNullable(drawing);
	}

	/**
	 * B1435, B1436: {@code today} (the box today's drawing of the finished building {@code f} fills at its placement)
	 * grown to also hold the box of the older drawing it still stands as ({@link #standingDrawing}), its basement too when
	 * {@code basement}. The ground protection round a neighbor's levelling, the Steward's plot search and the homes read a
	 * finished building by it: a house of an old, deeper drawing keeps its back rows and its beds there, and since which
	 * drawing a house stands as is read from its blocks (a guess on a damaged house), they never read less than today's box.
	 */
	public static net.minecraft.world.level.levelgen.structure.BoundingBox standingBox(ServerLevel level, BuildSiteManager.Finished f,
			net.minecraft.world.level.levelgen.structure.BoundingBox today, boolean basement) {
		Optional<Blueprint> old = standingDrawing(level, f);
		if (old.isEmpty()) {
			return today;
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox was
			= io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), old.get().size());
		int down = basement ? old.get().basement() : 0;
		return new net.minecraft.world.level.levelgen.structure.BoundingBox(Math.min(today.minX(), was.minX()),
			Math.min(today.minY(), was.minY() - down), Math.min(today.minZ(), was.minZ()),
			Math.max(today.maxX(), was.maxX()), Math.max(today.maxY(), was.maxY()), Math.max(today.maxZ(), was.maxZ()));
	}

	/**
	 * How much better the build matches {@code was} than {@code now}, over the spots where the two drawings differ: one
	 * for each standing as {@code was} drew it, less one for each standing as {@code now} does.
	 */
	private static int score(ServerLevel level, Map<BlockPos, BlockState> was, Map<BlockPos, BlockState> now) {
		int score = 0;
		java.util.Set<BlockPos> spots = new java.util.HashSet<>(was.keySet());
		spots.addAll(now.keySet());
		for (BlockPos pos : spots) {
			BlockState a = was.get(pos);
			BlockState b = now.get(pos);
			if (a != null && b != null && MaterialRules.matches(a, b)) {
				continue;
			}
			BlockState world = level.getBlockState(pos);
			boolean asWas = a == null || a.isAir() ? world.canBeReplaced() : MaterialRules.matches(world, a);
			boolean asNow = b == null || b.isAir() ? world.canBeReplaced() : MaterialRules.matches(world, b);
			score += (asWas ? 1 : 0) - (asNow ? 1 : 0);
		}
		return score;
	}

	/** Which kind of piece the page counts {@code state} as. */
	static int kind(BlockState state) {
		if (state.hasBlockEntity() || state.getBlock() instanceof BedBlock || PoiTypes.forState(state).isPresent()) {
			return FURNITURE;
		}
		return MaterialRules.classify(state) == MaterialRules.Kind.DECORATION ? DECORATION : BLOCK;
	}

	/** "12 decorations, 3 furniture pieces": the nonzero counts of {@code counts}, or null when all are zero. */
	@Nullable
	public static Component counts(int[] counts) {
		String[] keys = {"decorations", "furniture", "blocks"};
		Component out = null;
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				Component part = io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.refresh." + keys[i], counts[i], counts[i]);
				out = out == null ? part : Component.translatable("screen.aliveworkplace.refresh.and", out, part);
			}
		}
		return out;
	}

	// ---- the builds that can be refreshed, and the button ---------------------------------------------------------------

	/** A build on the page: the finished building, what would change (null while it's being refreshed), and its refresh going on. */
	public record Entry(BuildSiteManager.Finished building, @Nullable Change change, @Nullable BuildSite site, boolean waiting) {
	}

	/** The refresh of {@code placement} going on or queued, if any. */
	@Nullable
	public static BuildSite siteOf(ServerLevel level, BlueprintData.Placement placement) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.isRefresh() && site.placement().equals(placement)) {
				return site;
			}
		}
		return null;
	}

	/** The builds round {@code hall} that can be refreshed, or are being (those going on first, then the waiting, nearest first). */
	public static List<Entry> entries(ServerLevel level, BlockPos hall) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<Entry> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : manager.finishedInVillage(level, hall)) { // B616: the village's claimed area
			BuildSite site = siteOf(level, f.placement());
			if (site != null) {
				out.add(new Entry(f, null, site, false));
				continue;
			}
			if (manager.all().stream().anyMatch(s -> s.placement().equals(f.placement()))) {
				continue; // being repaired or rebuilt: not now
			}
			Optional<Change> change = change(level, f);
			if (change.isPresent()) {
				out.add(new Entry(f, change.get(), null, manager.refreshAsked(f.placement())));
			} else if (manager.refreshAsked(f.placement())) {
				manager.setRefreshAsked(f.placement(), false); // (put right some other way)
			}
		}
		out.sort(Comparator.comparingInt((Entry e) -> e.site() != null ? 0 : e.waiting() ? 1 : 2)
			.thenComparingDouble(e -> e.building().placement().origin().distSqr(hall)));
		return out;
	}

	/** What a click on the button came to. */
	public enum Outcome {
		/** SPLIT (B1110): a helper was taken off another build's crew for it. */
		STARTED, SPLIT, WAITING, NOTHING, NOT_YOURS, GONE;

		public boolean ok() {
			return this == STARTED || this == SPLIT || this == WAITING;
		}
	}

	/**
	 * {@code player} asks for the build at {@code at} to be refreshed: the nearest free builder who looks after it sets
	 * about it now; with none free, it waits (saved) and the first one free takes it up ({@link #takeUp}).
	 */
	public static Outcome refresh(ServerPlayer player, BlockPos at) {
		ServerLevel level = Players.level(player);
		Optional<BuildSiteManager.Finished> f = RepairAsks.building(level, at);
		if (f.isEmpty()) {
			return said(player, null, Outcome.GONE);
		}
		if (!RepairAsks.mayAnswer(level, player, f.get())) {
			return said(player, f.get(), Outcome.NOT_YOURS);
		}
		if (siteOf(level, f.get().placement()) != null) {
			return said(player, f.get(), Outcome.STARTED);
		}
		if (change(level, f.get()).isEmpty()) {
			return said(player, f.get(), Outcome.NOTHING);
		}
		BuildSiteManager.get(level).setRefreshAsked(f.get().placement(), true);
		BlockPos origin = Builders.spot(level, f.get().structure(), f.get().placement()); // B615: measured as placing measures
		// B619: every builder whose bench reaches it, wherever in the village (nearest first)
		List<Villager> builders = Builders.reaching(level, f.get().structure(), f.get().placement(), v -> !ModAttachments.BUILDER_JOB.has(v)
			&& Builders.activeSite(level, v) == null && !Builders.isHelping(v) && Upkeep.looksAfter(level, v, f.get().owner()));
		for (Villager v : builders) {
			if (start(level, v, f.get()) != null) {
				return said(player, f.get(), Outcome.STARTED);
			}
		}
		// B1110: every builder busy: a helper who looks after it is taken off another build's crew (which keeps its lead)
		List<Villager> crews = Builders.reaching(level, f.get().structure(), f.get().placement(), v -> Upkeep.looksAfter(level, v, f.get().owner()));
		Optional<Builders.Split> split = Builders.splitOff(level, crews, origin);
		if (split.isPresent()) {
			if (start(level, split.get().builder(), f.get()) != null) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.refresh_answer.split", split.get().builder().getDisplayName(),
					Blueprints.displayName(split.get().left().structure()), Blueprints.displayName(f.get().structure())).withStyle(ChatFormatting.GREEN));
				return Outcome.SPLIT;
			}
			split.get().undo(level);
		}
		return said(player, f.get(), Outcome.WAITING);
	}

	/** A free builder takes up a refresh waiting for one among the buildings they look after (from {@link Upkeep#look}). */
	@Nullable
	static BuildSite takeUp(ServerLevel level, Villager builder, BlockPos bench) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (manager.refreshAsked(f.placement()) && Upkeep.looksAfter(level, builder, f.owner())
				&& Builders.inReach(level, bench, f.structure(), f.placement())
				&& manager.all().stream().noneMatch(s -> s.placement().equals(f.placement()))) {
				BuildSite site = start(level, builder, f);
				if (site != null) {
					return site;
				}
			}
		}
		return null;
	}

	@Nullable
	private static BuildSite start(ServerLevel level, Villager builder, BuildSiteManager.Finished f) {
		BlockPos bench = Builders.benchPos(builder).orElse(null);
		Optional<Change> change = change(level, f);
		BuildSiteManager manager = BuildSiteManager.get(level);
		if (bench == null || change.isEmpty()) {
			if (change.isEmpty()) {
				manager.setRefreshAsked(f.placement(), false);
			}
			return null;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(f.owner());
		BuildSite site = manager.create(f.owner(), owner != null ? owner.getGameProfile().getName() : "", f.structure(), f.placement());
		site.setFurniture(manager.furniture(f.placement()));
		site.setRefresh(change.get().from());
		manager.setRefreshBegun(f.placement(), change.get().from()); // B1500: kept if he dies, until a refresh finishes
		site.setBuilder(builder.getUUID());
		site.setBench(bench);
		ModAttachments.BUILDER_JOB.set(builder, new BuilderJob(site.id()));
		manager.setRefreshAsked(f.placement(), false);
		if (owner != null) {
			Builders.tell(owner, Component.translatable("message.aliveworkplace.refreshing", builder.getDisplayName(), Blueprints.displayName(f.structure())),
				ChatFormatting.AQUA);
		}
		return site;
	}

	private static Outcome said(ServerPlayer player, @Nullable BuildSiteManager.Finished f, Outcome outcome) {
		Component name = f != null ? Blueprints.displayName(f.structure()) : Component.empty();
		Chat.actionBar(player, switch (outcome) {
			case STARTED, SPLIT -> Component.translatable("message.aliveworkplace.refresh_answer.started", name).withStyle(ChatFormatting.GREEN);
			case WAITING -> Component.translatable("message.aliveworkplace.refresh_answer.waiting", name).withStyle(ChatFormatting.YELLOW);
			case NOTHING -> Component.translatable("message.aliveworkplace.refresh_answer.nothing", name).withStyle(ChatFormatting.GRAY);
			case NOT_YOURS -> Component.translatable("message.aliveworkplace.repair_answer.not_yours", name).withStyle(ChatFormatting.RED);
			case GONE -> Component.translatable("message.aliveworkplace.repair_answer.gone").withStyle(ChatFormatting.RED);
		});
		return outcome;
	}

	private Refreshes() {
	}
}
