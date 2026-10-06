package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Old houses, found and measured (ROADMAP 27.20). In the zones whose "renew old houses" switch is on, the Steward looks
 * for houses no builder built: a bed or a workstation (by its point of interest) that isn't in a finished build
 * ({@link Homes#building}), and the house round it, measured by a flood fill over built blocks (not terrain, plants or
 * natural trees), at most {@link #MAX_BLOCKS} blocks and {@link #MAX_X}×{@link #MAX_Y}×{@link #MAX_Z}. Each hall's
 * survey reads at most {@link #BLOCKS_PER_TICK} blocks a tick, all halls of a level together.
 *
 * <p>Keyed on blocks, never on the structure: a house counts as an old village house, plain or ruined, only when
 * {@link #VILLAGE_SHARE} or more of its blocks are in {@link #VILLAGE_HOUSE_BLOCKS}, it holds no container and no block
 * entity but beds, bells, signs, banners, campfires and job blocks, no part of it is in the {@link PlayerBuilt} ledger
 * (27.19), and it lies wholly inside renew zones of the hall's plan. A survey is kept in memory, done again after a day
 * or once the plan's zones change; nothing of it is saved.
 */
public final class OldHouses {
	/** The blocks of vanilla's five kinds of village, with the cobwebs, mossy and cracked blocks of abandoned ones; packs add theirs. */
	public static final TagKey<Block> VILLAGE_HOUSE_BLOCKS = TagKey.create(Registries.BLOCK, AliveWorkplace.id("village_house_blocks"));
	public static final int MAX_BLOCKS = 2000;
	public static final int MAX_X = 20;
	public static final int MAX_Y = 16;
	public static final int MAX_Z = 20;
	/** The most blocks all of a level's surveys read in one tick. */
	public static final int BLOCKS_PER_TICK = 256;
	/** The share of a house's blocks that must be village blocks. */
	public static final float VILLAGE_SHARE = 0.85f;
	/** A finished survey is done again after this long (a day). */
	public static final long RESURVEY_TICKS = 24000L;
	/** The most blocks one block of the fill reads: its 6 neighbours, and for a log among them, that log's 6 (natural leaves?). */
	private static final int MOST_READS_PER_BLOCK = 6 + 6 * 6;

	/** What a measured house is. */
	public enum Verdict {
		/** An old village house the builders may renew. */
		RENEWABLE,
		/** Bigger than {@link #MAX_BLOCKS} blocks or {@link #MAX_X}×{@link #MAX_Y}×{@link #MAX_Z}: not a house. */
		TOO_BIG,
		/** Part of it is in a chunk that isn't loaded. */
		UNLOADED,
		/** It holds a chest or another block entity that isn't a bed, bell, sign, banner, campfire or job block. */
		CONTAINER,
		/** Less than {@link #VILLAGE_SHARE} of its blocks are village blocks. */
		NOT_VILLAGE,
		/** A player built or broke something in it (the {@link PlayerBuilt} ledger). */
		PLAYER_BUILT,
		/** It reaches out of the renew zones. */
		OUTSIDE_ZONE
	}

	/** One house: the bed or workstation it was found by, its box, its blocks, how many are village blocks, and the verdict. */
	public record House(BlockPos seed, BoundingBox box, int blocks, int villageBlocks, Verdict verdict) {
		public boolean renewable() {
			return verdict == Verdict.RENEWABLE;
		}

		/** Measured whole: a house, whether or not it can be renewed (not one too big or unloaded). */
		public boolean found() {
			return verdict != Verdict.TOO_BIG && verdict != Verdict.UNLOADED;
		}
	}

	private static final Map<ServerLevel, Map<BlockPos, Survey>> SURVEYS = new WeakHashMap<>();
	private static final Map<ServerLevel, Integer> LAST_TICK = new WeakHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			long cost = StewardCost.start(); // 27.22
			try {
				tick(level);
			} finally {
				StewardCost.stop(cost, "old-house survey");
			}
		});
	}

	/** Whether any zone of the hall's plan has its renew switch on. */
	public static boolean renewing(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.plan().zones().stream().anyMatch(CityPlan.Zone::renew);
	}

	/**
	 * The hall's survey: the one going or done if it is less than a day old and the plan's zones are as they were, else a
	 * new one. Empty if the hall is gone or no zone renews.
	 */
	public static Optional<Survey> request(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || !renewing(level, hall)) {
			return Optional.empty();
		}
		int signature = entity.plan().zones().hashCode();
		Map<BlockPos, Survey> surveys = SURVEYS.computeIfAbsent(level, l -> new HashMap<>());
		Survey survey = surveys.get(hall);
		if (survey == null || survey.signature != signature || survey.done() && level.getGameTime() - survey.started >= RESURVEY_TICKS) {
			Survey next = new Survey(level, hall.immutable(), entity.plan(), signature);
			if (survey != null && survey.done()) {
				next.previous = survey.houses; // the desk keeps showing the last count while the new one runs
			}
			survey = next;
			surveys.put(hall.immutable(), survey);
		}
		return Optional.of(survey);
	}

	/** The houses of the hall's last finished survey (empty while the first runs). */
	public static Optional<List<House>> result(ServerLevel level, BlockPos hall) {
		Survey survey = SURVEYS.getOrDefault(level, Map.of()).get(hall);
		if (survey == null) {
			return Optional.empty();
		}
		return survey.done() ? Optional.of(survey.houses) : Optional.ofNullable(survey.previous);
	}

	/** Forgets the hall's survey (tests, and a hall broken). */
	public static void forget(ServerLevel level, BlockPos hall) {
		Map<BlockPos, Survey> surveys = SURVEYS.get(level);
		if (surveys != null) {
			surveys.remove(hall);
		}
	}

	/** Blocks the level's surveys read in its last tick (never more than {@link #BLOCKS_PER_TICK}). */
	public static int blocksLastTick(ServerLevel level) {
		return LAST_TICK.getOrDefault(level, 0);
	}

	static void tick(ServerLevel level) {
		Map<BlockPos, Survey> surveys = SURVEYS.get(level);
		int left = BLOCKS_PER_TICK;
		if (surveys != null) {
			surveys.values().removeIf(s -> !(level.getBlockEntity(s.hall) instanceof VillageHallBlockEntity));
			for (Survey survey : surveys.values()) {
				if (left <= 0) {
					break;
				}
				if (!survey.done()) {
					left -= survey.step(left);
				}
			}
		}
		LAST_TICK.put(level, BLOCKS_PER_TICK - left);
	}

	// ---- what the fill walks over ----------------------------------------------------------------------------------

	/** Ground, plants and water: never part of a house (dirt paths and farmland included, so a village's lanes don't join its houses). */
	static boolean natural(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
			return true;
		}
		if (state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
			return true;
		}
		if (Plots.clearable(state)) {
			return true;
		}
		if (Plots.ground(state)) {
			// sandstone and terracotta are terrain, but also what desert and savanna houses are built of
			return !state.is(VILLAGE_HOUSE_BLOCKS) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD);
		}
		return false;
	}

	/** A block entity an old house may hold: a bed, bell, sign, banner, campfire or a job block. */
	static boolean allowedBlockEntity(BlockState state) {
		return state.is(BlockTags.BEDS) || state.is(Blocks.BELL) || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.BANNERS)
			|| state.is(BlockTags.CAMPFIRES) || PoiTypes.forState(state).map(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false);
	}

	/** Measures one house by a flood fill from {@code seed}, a step at a time. */
	public static final class Measure {
		private final ServerLevel level;
		private final BlockPos seed;
		private final Long2ObjectOpenHashMap<BlockState> read = new Long2ObjectOpenHashMap<>();
		private final LongSet accepted = new LongOpenHashSet();
		/** Blocks the fill has decided on (in the house or not); the read cache also holds blocks only looked at round a log. */
		private final LongSet visited = new LongOpenHashSet();
		private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		private int minX, minY, minZ, maxX, maxY, maxZ;
		private int village;
		private boolean container;
		private Verdict stopped;
		private boolean started;
		private int reads;

		public Measure(ServerLevel level, BlockPos seed) {
			this.level = level;
			this.seed = seed.immutable();
			minX = maxX = seed.getX();
			minY = maxY = seed.getY();
			minZ = maxZ = seed.getZ();
		}

		public boolean done() {
			return started && (stopped != null || queue.isEmpty());
		}

		/** Blocks read so far. */
		public int reads() {
			return reads;
		}

		LongSet accepted() {
			return accepted;
		}

		/** Reads at most {@code budget} blocks; returns how many it read. */
		public int step(int budget) {
			int before = reads;
			if (!started) {
				if (budget < 1) {
					return 0;
				}
				started = true;
				BlockState state = read(seed);
				if (state == null) {
					stopped = Verdict.UNLOADED;
				} else if (natural(state)) {
					stopped = Verdict.TOO_BIG; // (nothing built here)
				} else {
					visited.add(seed.asLong());
					accept(seed.asLong(), state);
				}
			}
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			while (stopped == null && !queue.isEmpty() && budget - (reads - before) >= MOST_READS_PER_BLOCK) {
				long pos = queue.dequeueLong();
				for (Direction d : Direction.values()) {
					at.set(pos).move(d);
					long n = at.asLong();
					if (!visited.add(n)) {
						continue;
					}
					BlockState state = read(at);
					if (state == null) {
						stopped = Verdict.UNLOADED;
						break;
					}
					if (natural(state) || state.is(BlockTags.LOGS) && naturalTree(at)) {
						continue;
					}
					accept(n, state);
					if (stopped != null) {
						break;
					}
				}
			}
			return reads - before;
		}

		/** A log touching leaves that grew is a natural tree's. */
		private boolean naturalTree(BlockPos log) {
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (Direction d : Direction.values()) {
				at.setWithOffset(log, d);
				BlockState state = read(at);
				if (state != null && state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT)
					&& !state.getValue(LeavesBlock.PERSISTENT)) {
					return true;
				}
			}
			return false;
		}

		private BlockState read(BlockPos pos) {
			long key = pos.asLong();
			BlockState state = read.get(key);
			if (state != null) {
				return state;
			}
			if (!level.isLoaded(pos)) {
				return null;
			}
			state = level.getBlockState(pos);
			reads++;
			read.put(key, state);
			return state;
		}

		private void accept(long pos, BlockState state) {
			accepted.add(pos);
			queue.enqueue(pos);
			int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
			if (state.is(VILLAGE_HOUSE_BLOCKS)) {
				village++;
			}
			if (state.hasBlockEntity() && !allowedBlockEntity(state)) {
				container = true;
			}
			if (accepted.size() > MAX_BLOCKS || maxX - minX + 1 > MAX_X || maxY - minY + 1 > MAX_Y || maxZ - minZ + 1 > MAX_Z) {
				stopped = Verdict.TOO_BIG;
			}
		}

		public BoundingBox box() {
			return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
		}

		/** The house as measured, judged for the hall at {@code hall} with {@code plan} (call once {@link #done}). */
		public House house(BlockPos hall, CityPlan plan) {
			BoundingBox box = box();
			Verdict verdict;
			if (stopped != null) {
				verdict = stopped;
			} else if (container) {
				verdict = Verdict.CONTAINER;
			} else if (village < VILLAGE_SHARE * accepted.size()) {
				verdict = Verdict.NOT_VILLAGE;
			} else if (PlayerBuilt.get(level).marked(box)) {
				verdict = Verdict.PLAYER_BUILT;
			} else if (!insideRenewZones(level, hall, plan, box)) {
				verdict = Verdict.OUTSIDE_ZONE;
			} else {
				verdict = Verdict.RENEWABLE;
			}
			return new House(seed, box, accepted.size(), village, verdict);
		}
	}

	/** Whether every column of {@code box} lies in a renew zone of the hall's plan, and nearer that hall than any other. */
	static boolean insideRenewZones(ServerLevel level, BlockPos hall, CityPlan plan, BoundingBox box) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos p = new BlockPos(x, box.minY(), z);
				if (!plan.zoneAt(hall, p).map(CityPlan.Zone::renew).orElse(false)) {
					return false;
				}
			}
		}
		for (BlockPos corner : List.of(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.minY(), box.maxZ()),
			new BlockPos(box.minX(), box.minY(), box.maxZ()), new BlockPos(box.maxX(), box.minY(), box.minZ()))) {
			Optional<BlockPos> nearest = VillageHalls.nearest(level, corner);
			if (nearest.isPresent() && !nearest.get().equals(hall)) {
				return false;
			}
		}
		return true;
	}

	/** One hall's survey: its beds and workstations in renew zones, nearest the hall first, each house measured in turn. */
	public static final class Survey {
		private final ServerLevel level;
		private final BlockPos hall;
		private final CityPlan plan;
		private final int signature;
		private final long started;
		private final ArrayDeque<BlockPos> seeds = new ArrayDeque<>();
		private final LongSet claimed = new LongOpenHashSet();
		private final List<House> houses = new ArrayList<>();
		private List<House> previous;
		private Measure current;
		private boolean seeded;
		private int reads;

		Survey(ServerLevel level, BlockPos hall, CityPlan plan, int signature) {
			this.level = level;
			this.hall = hall;
			this.plan = plan;
			this.signature = signature;
			this.started = level.getGameTime();
		}

		public boolean done() {
			return seeded && current == null && seeds.isEmpty();
		}

		/** Every house measured so far, renewable or not. */
		public List<House> houses() {
			return List.copyOf(houses);
		}

		/** Blocks this survey has read. */
		public int reads() {
			return reads;
		}

		int step(int budget) {
			if (!seeded) {
				seeded = true;
				int reach = CityPlan.half() * 3 / 2;
				level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME) || h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE),
						p -> plan.zoneAt(hall, p).map(CityPlan.Zone::renew).orElse(false), hall, reach, PoiManager.Occupancy.ANY)
					.map(BlockPos::immutable)
					.sorted(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(hall)).thenComparingLong(BlockPos::asLong))
					.forEach(seeds::add);
			}
			int used = 0;
			while (used < budget) {
				if (current == null) {
					BlockPos seed = next();
					if (seed == null) {
						break;
					}
					current = new Measure(level, seed);
				}
				int n = current.step(budget - used);
				used += n;
				if (current.done()) {
					House house = current.house(hall, plan);
					claimed.addAll(current.accepted());
					if (house.found()) {
						houses.add(house);
					}
					current = null;
				} else if (n == 0) {
					break; // too little left this tick for the next block's reads
				}
			}
			reads += used;
			return used;
		}

		/** The next bed or workstation not in a house measured already, nor in a finished build. */
		private BlockPos next() {
			while (!seeds.isEmpty()) {
				BlockPos seed = seeds.poll();
				if (claimed.contains(seed.asLong()) || !level.isLoaded(seed)) {
					continue;
				}
				if (Homes.building(level, seed).isPresent()) {
					continue; // a builder built it
				}
				return seed;
			}
			return null;
		}
	}

	private OldHouses() {
	}
}
