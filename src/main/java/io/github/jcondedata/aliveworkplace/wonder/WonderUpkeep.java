package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A finished Wonder's upkeep (35.8). Once a day each finished Wonder counts how much of it still stands: every block of
 * every stage's parts, at most {@link #PER_TICK} a tick, and only while all of it is loaded (a chunk unloading mid-count
 * puts the count off). A block stands when the world has it ({@link MaterialRules#matches}: properties that depend on
 * neighbours aside, and copper's weathering and waxing count as the same block, so a dome turned green still stands).
 * Under {@link #STANDING}% (config {@code wonderStanding}) it is damaged: its powers stop, the hall and the stone say so,
 * the chronicle and its owner are told, and the village's builders repair it before anything else ({@link #repair},
 * {@code build/Upkeep}: {@link BuildPlan#repair} per part, whatever {@code builderRepairs} says). Each part they finish
 * counts the Wonder again; back at {@link #STANDING}%, the powers return. The count in progress isn't saved: after a
 * restart it begins again.
 */
public final class WonderUpkeep {
	/** The share (%) of a Wonder that must stand for its powers (config {@code wonderStanding}, 50 to 100). */
	public static volatile int STANDING = 90;
	/** Most blocks counted a tick. */
	public static final int PER_TICK = 4096;
	/** Ticks between looks for a Wonder due its count. */
	static final int LOOK_EVERY = 20;

	/** A count going on in one dimension: the Wonder, its blocks, how far along and how many stand so far. */
	private static final class Count {
		final UUID site;
		final List<Block> blocks;
		int next;
		int standing;

		Count(UUID site, List<Block> blocks) {
			this.site = site;
			this.blocks = blocks;
		}
	}

	/** One block of a Wonder as drawn, turned and mirrored with it, and as its part's unstyled drawing has it (null: the same). */
	public record Block(BlockPos pos, BlockState state, @Nullable BlockState plain) {
	}

	private static final Map<ResourceKey<Level>, Count> COUNTING = new ConcurrentHashMap<>();
	/** Wonders to count again as soon as they may (a repair part was finished), not waiting for the next day. */
	private static final Set<UUID> RECOUNT = ConcurrentHashMap.newKeySet();

	public static void init() {
		Platform.get().onLevelTick(WonderUpkeep::tick);
	}

	/** Every tick: the count going on goes on; every {@link #LOOK_EVERY} ticks with none going, the next Wonder due begins. */
	static void tick(ServerLevel level) {
		if (!Wonders.ENABLED) {
			COUNTING.remove(level.dimension());
			return; // switched off: finished ones keep their blocks, and their counts wait
		}
		Count count = COUNTING.get(level.dimension());
		if (count == null) {
			if (level.getGameTime() % LOOK_EVERY != 0) {
				return;
			}
			count = due(level);
			if (count == null) {
				return;
			}
			COUNTING.put(level.dimension(), count);
		}
		step(level, count);
	}

	/** Counts {@code site} again as soon as it may (its builders finished a repair). */
	public static void recount(UUID site) {
		RECOUNT.add(site);
	}

	/** The next finished Wonder of {@code level} due its count (not counted today, or asked again) and loaded whole; null if none. */
	@Nullable
	private static Count due(ServerLevel level) {
		long today = Chronicle.day(level);
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (!site.done() || site.placement().isEmpty() || site.kept().counted() >= today && !RECOUNT.contains(site.id())) {
				continue;
			}
			if (!loaded(level, site)) {
				continue; // counted once it's all loaded
			}
			List<Block> blocks = blocks(level, site);
			if (!blocks.isEmpty() && loaded(level, blocks)) {
				return new Count(site.id(), blocks);
			}
		}
		return null;
	}

	/** Counts up to {@link #PER_TICK} blocks of {@code count}; the last of them ends it. */
	private static void step(ServerLevel level, Count count) {
		int end = Math.min(count.blocks.size(), count.next + PER_TICK);
		for (; count.next < end; count.next++) {
			Block b = count.blocks.get(count.next);
			if (!level.isLoaded(b.pos())) {
				COUNTING.remove(level.dimension()); // part of it went out of reach: counted again once it's all loaded
				return;
			}
			if (stands(level.getBlockState(b.pos()), b)) {
				count.standing++;
			}
		}
		if (count.next >= count.blocks.size()) {
			COUNTING.remove(level.dimension());
			RECOUNT.remove(count.site);
			counted(level, count.site, count.standing * 100 / count.blocks.size());
		}
	}

	/** Whether {@code world} is the block {@code b} wants (or its part's unstyled one: a village's colours changed since). */
	public static boolean stands(BlockState world, Block b) {
		return MaterialRules.matches(world, b.state()) || b.plain() != null && MaterialRules.matches(world, b.plain());
	}

	/** Counts {@code site} now, all of it at once (the command and tests): its share standing, or -1 when it isn't loaded. */
	public static int countNow(ServerLevel level, UUID id) {
		Optional<WonderSite> site = WonderManager.get(level).site(id);
		if (site.isEmpty() || !site.get().done()) {
			return -1;
		}
		List<Block> blocks = blocks(level, site.get());
		if (blocks.isEmpty() || !loaded(level, blocks)) {
			return -1;
		}
		int standing = 0;
		for (Block b : blocks) {
			if (stands(level.getBlockState(b.pos()), b)) {
				standing++;
			}
		}
		RECOUNT.remove(id);
		int percent = standing * 100 / blocks.size();
		counted(level, id, percent);
		return percent;
	}

	/**
	 * A count of the Wonder {@code id} is done: {@code percent} of it stands. Under {@link #STANDING} it is damaged (its
	 * powers off; the chronicle and its owner told once); at or over it again, repaired.
	 */
	static void counted(ServerLevel level, UUID id, int percent) {
		WonderManager manager = WonderManager.get(level);
		Optional<WonderSite> found = manager.site(id);
		if (found.isEmpty()) {
			return;
		}
		WonderSite site = found.get();
		boolean damaged = percent < STANDING;
		WonderSite.Kept was = site.kept();
		manager.setKept(id, new WonderSite.Kept(percent, Chronicle.day(level), damaged, was.festival()));
		if (damaged == was.damaged()) {
			return;
		}
		Component name = Wonders.name(site.wonder());
		Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, damaged
			? Component.translatable("chronicle.aliveworkplace.wonder.damaged", name, percent)
			: Component.translatable("chronicle.aliveworkplace.wonder.repaired", name));
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		if (owner != null) {
			Chat.system(owner, damaged
				? Component.translatable("message.aliveworkplace.wonder.damaged", name, VillageHalls.name(level, site.hall()), percent, STANDING)
					.withStyle(ChatFormatting.RED)
				: Component.translatable("message.aliveworkplace.wonder.repaired", name, VillageHalls.name(level, site.hall()), percent)
					.withStyle(ChatFormatting.GREEN));
		}
	}

	/** Whether the whole of the finished {@code site}'s outline is in loaded chunks (looked at before its blocks are worked out). */
	private static boolean loaded(ServerLevel level, WonderSite site) {
		Optional<Blueprint> whole = BlueprintLibrary.get(level, WonderBlueprints.idOf(site.wonder()));
		if (whole.isEmpty() || site.placement().isEmpty()) {
			return true; // (no whole drawing to go by: the blocks' own chunks are looked at)
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox box = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(site.placement().get(),
			whole.get().size());
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether every block of {@code blocks} is in a loaded chunk. */
	private static boolean loaded(ServerLevel level, List<Block> blocks) {
		Set<Long> chunks = new java.util.HashSet<>();
		for (Block b : blocks) {
			long chunk = net.minecraft.world.level.ChunkPos.asLong(b.pos().getX() >> 4, b.pos().getZ() >> 4);
			if (chunks.add(chunk) && !level.hasChunk(b.pos().getX() >> 4, b.pos().getZ() >> 4)) {
				return false;
			}
		}
		return true;
	}

	/** One part of a Wonder where it stands: its blueprint id (styled as its stage was) and its placement. */
	public record Part(ResourceLocation structure, ResourceLocation plain, BlueprintData.Placement placement) {
	}

	/** Every part of every stage of the finished {@code site}, placed with the Wonder's turn and mirror at its offset. */
	public static List<Part> parts(ServerLevel level, WonderSite site) {
		Wonder wonder = Wonders.loaded().get(site.wonder());
		if (wonder == null || site.placement().isEmpty()) {
			return List.of();
		}
		BlueprintData.Placement at = site.placement().get();
		String colours = wonder.villageColours() ? VillageBanners.wonderStyle(VillageBanners.of(level, site.hall())) : null;
		List<Part> out = new ArrayList<>();
		for (Wonder.Stage stage : wonder.stages()) {
			for (Wonder.Part part : stage.parts()) {
				BlockPos origin = at.origin().offset(StructureTemplate.transform(part.offset(), at.mirror(), at.rotation(), BlockPos.ZERO));
				out.add(new Part(BlueprintStyles.styled(part.part(), colours), part.part(),
					new BlueprintData.Placement(at.dimension(), origin, at.rotation(), at.mirror())));
			}
		}
		return out;
	}

	/** Every block of the finished {@code site} as drawn, a later stage's block over an earlier stage's (air never counts). */
	public static List<Block> blocks(ServerLevel level, WonderSite site) {
		Map<BlockPos, Block> out = new LinkedHashMap<>();
		for (Part part : parts(level, site)) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, part.structure());
			if (blueprint.isEmpty()) {
				return List.of(); // a part's drawing is missing: nothing to count it against
			}
			Map<BlockPos, BlockState> plain = new java.util.HashMap<>();
			if (!part.plain().equals(part.structure())) {
				BlueprintLibrary.get(level, part.plain()).ifPresent(b -> steps(BuildPlan.create(b, part.placement())).forEach(s -> plain.put(s.pos(), s.state())));
			}
			for (BuildPlan.Step s : steps(BuildPlan.create(blueprint.get(), part.placement()))) {
				out.put(s.pos(), new Block(s.pos(), s.state(), plain.get(s.pos())));
			}
		}
		return List.copyOf(out.values());
	}

	private static List<BuildPlan.Step> steps(BuildPlan plan) {
		List<BuildPlan.Step> out = new ArrayList<>();
		for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!s.state().isAir()) {
					out.add(s);
				}
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- repairs

	/** Whether any Wonder of {@code level} is damaged (an idle builder looks over its village's then). */
	public static boolean anyDamaged(ServerLevel level) {
		if (!Wonders.ENABLED) {
			return false;
		}
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (site.done() && site.kept().damaged()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * {@code builder}, idle at its bench at {@code bench}, repairs its village's damaged Wonder before anything else: a
	 * repair site for each part with blocks missing ({@link BuildPlan#repair}: only the holes, from the stockpile round
	 * the Foundation Stone, nothing players put there touched), the first its job and the rest in its queue. Parts another
	 * builder is repairing already are left to them. Returns the sites (empty: nothing to repair here).
	 */
	public static List<BuildSite> repair(ServerLevel level, Villager builder, BlockPos bench) {
		if (!Wonders.ENABLED) {
			return List.of();
		}
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (!site.done() || !site.kept().damaged() || bench.distSqr(site.hall()) > (double) VillageHalls.RADIUS * VillageHalls.RADIUS) {
				continue;
			}
			List<BuildSite> made = new ArrayList<>();
			int missing = 0;
			UUID owner = site.owner().orElse(new UUID(0L, 0L));
			for (Part part : parts(level, site)) {
				if (sites.all().stream().anyMatch(s -> !s.isDone() && s.placement().equals(part.placement()))) {
					continue; // being repaired already
				}
				Optional<Blueprint> blueprint = BlueprintLibrary.get(level, part.structure());
				int holes = blueprint.map(b -> BuildPlan.repair(b, part.placement(), level).size()).orElse(0);
				if (holes == 0) {
					continue;
				}
				missing += holes;
				BuildSite repair = sites.create(owner, site.ownerName(), part.structure(), part.placement());
				repair.setRepair();
				repair.setWonder(site.id());
				site.stone().ifPresent(repair::setBench);
				repair.setBuilder(builder.getUUID());
				if (made.isEmpty()) {
					ModAttachments.BUILDER_JOB.set(builder, new BuilderJob(repair.id()));
				} else {
					repair.setQueued(true);
				}
				made.add(repair);
			}
			if (!made.isEmpty()) {
				ServerPlayer player = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
				if (player != null) {
					Chat.system(player, Component.translatable("message.aliveworkplace.wonder.repairing", builder.getDisplayName(), Wonders.name(site.wonder()),
						missing).withStyle(ChatFormatting.AQUA));
				}
				return made;
			}
		}
		return List.of();
	}

	/** Forgets the counts going on (tests). */
	public static void forget() {
		COUNTING.clear();
		RECOUNT.clear();
	}

	private WonderUpkeep() {
	}
}
