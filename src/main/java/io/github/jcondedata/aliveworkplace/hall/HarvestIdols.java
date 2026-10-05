package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Harvest Idol (ROADMAP 30.14). In harvest season (autumn on the village calendar, {@link Seasons}) a crop within
 * {@link #RADIUS} blocks of an idol grows a quarter faster: each time it takes a random tick, one time in
 * {@link #ONE_IN} it takes another. Crops are the block tag {@code aliveworkplace:idol_crops}. Idols don't stack: one in
 * reach is as good as five.
 *
 * <p>Nothing is scanned: each idol has a block entity that puts its place in its dimension's set when its chunk loads
 * (or it's placed) and takes it out when the chunk unloads (or it's broken), so the set always holds the loaded idols and
 * nothing is saved. In harvest season golden sparkles rise from an idol now and then. Off ({@code harvestIdols}): the
 * idols are ornaments.
 */
public final class HarvestIdols {
	public static volatile boolean ENABLED = true;
	/** How far an idol reaches, in blocks (a sphere round it). */
	public static final int RADIUS = 32;
	/** One random tick in this many brings a second: 25% more growth. */
	public static final int ONE_IN = 4;
	/** How often the idols are looked at for sparkles, in ticks. */
	static final int SPARKLE_EVERY = 20;
	/** One look in this many, an idol sparkles. */
	static final int SPARKLE_ONE_IN = 3;
	public static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK, AliveWorkplace.id("idol_crops"));

	/** Per dimension, each loaded idol's place and the block entity standing for it (a reloaded chunk brings a new one). */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Object>> IDOLS = new ConcurrentHashMap<>();
	/** True while the extra tick runs, so it doesn't bring another of its own. */
	private static boolean extra;

	public static void init() {
		Platform.get().onServerStarting(server -> IDOLS.clear());
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % SPARKLE_EVERY == 0) {
				Map<BlockPos, Object> idols = IDOLS.get(level.dimension());
				if (idols != null && !idols.isEmpty() && ENABLED && harvestSeason(level)) {
					for (BlockPos idol : idols.keySet()) {
						sparkle(level, idol, level.random);
					}
				}
			}
		});
	}

	/** The idol {@code owner} (its block entity) came into {@code level}: its chunk loaded, or it was placed. */
	public static void loaded(ServerLevel level, BlockPos idol, Object owner) {
		IDOLS.computeIfAbsent(level.dimension(), d -> new ConcurrentHashMap<>()).put(idol.immutable(), owner);
	}

	/**
	 * The idol {@code owner} left {@code level}: its chunk unloaded, or it was broken. Only its own entry goes: when a chunk
	 * is loaded again over a block entity still there, the new one has already taken the place.
	 */
	public static void unloaded(ServerLevel level, BlockPos idol, Object owner) {
		Map<BlockPos, Object> idols = IDOLS.get(level.dimension());
		if (idols != null) {
			idols.remove(idol, owner);
		}
	}

	/** The loaded idols of {@code level}'s dimension. */
	public static Set<BlockPos> idols(ServerLevel level) {
		Map<BlockPos, Object> idols = IDOLS.get(level.dimension());
		return idols == null ? Set.of() : Set.copyOf(idols.keySet());
	}

	/** Forgets every idol (as when the server stops); they're found again as their chunks load. */
	public static void forget() {
		IDOLS.clear();
	}

	/** Whether it's harvest season (autumn) on the village calendar. */
	public static boolean harvestSeason(ServerLevel level) {
		return Seasons.today(level).season().harvest();
	}

	/** Whether a loaded idol stands within {@link #RADIUS} blocks of {@code pos}. */
	public static boolean near(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Object> idols = IDOLS.get(level.dimension());
		if (idols == null) {
			return false;
		}
		long reach = (long) RADIUS * RADIUS;
		for (BlockPos idol : idols.keySet()) {
			if (idol.distSqr(pos) <= reach) {
				return true;
			}
		}
		return false;
	}

	/**
	 * After a block at {@code pos} took a random tick as {@code state}: if it's a crop near an idol in harvest season, one
	 * time in {@link #ONE_IN} it takes another (as it is now, if it's still a crop). True when it did.
	 */
	public static boolean afterRandomTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		if (extra || !ENABLED) {
			return false;
		}
		Map<BlockPos, Object> idols = IDOLS.get(level.dimension());
		if (idols == null || idols.isEmpty() || !state.is(CROPS) || !harvestSeason(level) || !near(level, pos)
			|| random.nextInt(ONE_IN) != 0) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		if (!now.is(CROPS)) {
			return false;
		}
		extra = true;
		try {
			now.randomTick(level, pos, random);
		} finally {
			extra = false;
		}
		return true;
	}

	/** Now and then (one look in {@link #SPARKLE_ONE_IN}) golden sparkles rise from the idol at {@code idol}; true when they did. */
	public static boolean sparkle(ServerLevel level, BlockPos idol, RandomSource random) {
		if (!ENABLED || !harvestSeason(level) || random.nextInt(SPARKLE_ONE_IN) != 0) {
			return false;
		}
		for (int i = 0; i < 3; i++) {
			double x = idol.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
			double y = idol.getY() + 1.0 + random.nextDouble() * 0.6;
			double z = idol.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
			// Count 0: the speeds are the particle's own; wax-on sparkles are golden and rise at a hundredth of it.
			level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 0, 0, 1, 0, 3 + random.nextDouble() * 2);
		}
		return true;
	}

	private HarvestIdols() {
	}
}
