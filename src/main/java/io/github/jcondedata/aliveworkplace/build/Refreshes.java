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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
	private record Old(String key, Blueprint drawing) {
	}

	@Nullable
	private static ResourceManager loadedFrom;
	private static Map<ResourceLocation, List<Old>> history = Map.of();
	/** Older drawings added by hand (tests, or a datapack's own code), by blueprint. */
	private static final Map<ResourceLocation, List<Old>> EXTRA = new HashMap<>();

	/** Adds an older drawing of {@code structure} under {@code key} (as a shipped one would be). */
	public static synchronized void addHistory(ResourceLocation structure, String key, Blueprint drawing) {
		EXTRA.computeIfAbsent(structure, k -> new ArrayList<>()).removeIf(o -> o.key().equals(key));
		EXTRA.get(structure).add(new Old(key, drawing));
	}

	public static synchronized void removeHistory(ResourceLocation structure) {
		EXTRA.remove(structure);
	}

	/** The older drawings of {@code structure}: those the mod ships, then any added. */
	private static synchronized List<Old> history(MinecraftServer server, ResourceLocation structure) {
		ResourceManager resources = server.getResourceManager();
		if (resources != loadedFrom) {
			loadedFrom = resources;
			history = load(resources);
		}
		List<Old> out = new ArrayList<>(history.getOrDefault(structure, List.of()));
		out.addAll(EXTRA.getOrDefault(structure, List.of()));
		return out;
	}

	/** The older drawing {@code key} of {@code structure}, as shipped (or added), if there is one. */
	public static Optional<Blueprint> olderDrawing(MinecraftServer server, ResourceLocation structure, String key) {
		return history(server, structure).stream().filter(o -> o.key().equals(key)).findFirst().map(Old::drawing);
	}

	private static Map<ResourceLocation, List<Old>> load(ResourceManager resources) {
		Map<ResourceLocation, List<Old>> out = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : resources.listResources(DIR, id -> id.getPath().endsWith(".nbt")).entrySet()) {
			String path = e.getKey().getPath().substring(DIR.length() + 1, e.getKey().getPath().length() - ".nbt".length());
			int slash = path.lastIndexOf('/');
			if (slash <= 0) {
				continue;
			}
			ResourceLocation structure = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(0, slash));
			try (InputStream in = e.getValue().open()) {
				CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
				// (with the blueprint's own id, so its basement and features are worked out as for the current drawing)
				Blueprint drawing = StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
					Blueprint.fromStructureNbt(structure, tag, Lookup.lookup(BuiltInRegistries.BLOCK))));
				out.computeIfAbsent(structure, k -> new ArrayList<>()).add(new Old(path.substring(slash + 1), drawing));
			} catch (Exception ex) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.warn("Couldn't read the older drawing {}: {}", e.getKey(), ex.toString());
			}
		}
		out.values().forEach(list -> list.sort(Comparator.comparing(Old::key)));
		return out;
	}

	// ---- what changes ------------------------------------------------------------------------------------------------

	/** The building's latest drawing as it would be built (its furniture as it was built with). */
	private static Optional<Blueprint> current(ServerLevel level, BuildSiteManager.Finished f) {
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture = BuildSiteManager.get(level).furniture(f.placement());
		return BlueprintLibrary.get(level, f.structure()).map(b -> furniture.apply(b, f.structure()));
	}

	/** What stays whatever the drawings say: a container (and what's in it), a bed someone sleeps in, a job block, a builder's bench. */
	static boolean kept(ServerLevel level, BlockPos pos, BlockState world) {
		return MaterialRules.isProtected(world, world.getDestroySpeed(level, pos))
			|| world.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH)
			|| world.getBlock() instanceof BedBlock && BuilderWork.someonesBed(level, pos, world)
			|| PoiTypes.forState(world).map(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false);
	}

	/** The refresh's work from the older drawing {@code from} (an ordinary repair if that drawing is gone). */
	static BuildPlan plan(ServerLevel level, Blueprint current, ResourceLocation structure, String from, BlueprintData.Placement placement,
						  @Nullable io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture) {
		Optional<Old> old = history(level.getServer(), structure).stream().filter(o -> o.key().equals(from)).findFirst();
		if (old.isEmpty()) {
			return BuildPlan.repair(current, placement, level, BuildSiteManager.get(level).gaveUp(placement));
		}
		Blueprint was = furniture == null ? old.get().drawing() : furniture.apply(old.get().drawing(), structure);
		return BuildPlan.refresh(current, was, placement, level, (pos, state) -> kept(level, pos, state));
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
		List<Old> olds = history(level.getServer(), f.structure());
		if (olds.isEmpty()) {
			return Optional.empty();
		}
		io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture = BuildSiteManager.get(level).furniture(f.placement());
		Map<BlockPos, BlockState> now = BuildPlan.create(current.get(), f.placement()).targets();
		Old best = null;
		int bestScore = 0;
		for (Old old : olds) {
			Map<BlockPos, BlockState> was = BuildPlan.create(furniture.apply(old.drawing(), f.structure()), f.placement()).targets();
			int score = score(level, was, now);
			if (score > bestScore) {
				best = old;
				bestScore = score;
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		BuildPlan plan = plan(level, current.get(), f.structure(), best.key(), f.placement(), furniture);
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
		Change change = new Change(best.key(), added, removed);
		return change.total() == 0 ? Optional.empty() : Optional.of(change);
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
		STARTED, WAITING, NOTHING, NOT_YOURS, GONE;

		public boolean ok() {
			return this == STARTED || this == WAITING;
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
		List<Villager> builders = new ArrayList<>(level.getEntities(EntityType.VILLAGER, new AABB(origin).inflate(Builders.MAX_SITE_DISTANCE * 2),
			v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent() && !ModAttachments.BUILDER_JOB.has(v)
				&& Builders.activeSite(level, v) == null && !Builders.isHelping(v) && Upkeep.looksAfter(level, v, f.get().owner())
				&& Builders.inReach(level, Builders.benchPos(v).get(), f.get().structure(), f.get().placement())));
		builders.sort(Comparator.comparingDouble(v -> v.distanceToSqr(origin.getX(), origin.getY(), origin.getZ())));
		for (Villager v : builders) {
			if (start(level, v, f.get()) != null) {
				return said(player, f.get(), Outcome.STARTED);
			}
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
			case STARTED -> Component.translatable("message.aliveworkplace.refresh_answer.started", name).withStyle(ChatFormatting.GREEN);
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
