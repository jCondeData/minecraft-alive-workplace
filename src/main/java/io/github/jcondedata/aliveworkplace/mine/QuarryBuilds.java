package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * The Quarry build (ROADMAP 36.12): a pit the Miner works like a farm. When a Quarry stands finished, the Miner whose
 * job block is its shed's blast furnace takes its pit as their quarry (no marker needed) whenever they have no other.
 * What they dig out grows back slowly from the bottom up ({@link #regrow}), in a weighted mix ({@link #pick}): mostly
 * stone, some coal and copper, now and then iron, redstone and gold, rarely a diamond, deepslate in the lower layers. So
 * the Miner digs down, waits at the shed while a layer grows back, and digs again; given days, a pit dug to the bottom
 * fills up again. Only air in the pit grows back: a block a player put there stays, and nothing outside the pit is
 * touched. {@code quarryBuild} switches it off (the build isn't offered, nothing grows back, no Miner is given a pit);
 * {@code quarryRegrowth} says how many blocks grow back in a Minecraft day.
 */
public final class QuarryBuilds {
	public static final ResourceLocation BLUEPRINT = AliveWorkplace.id("quarry");
	/** The pit's corners in the blueprint's own coordinates (y 0 sits on the clicked block): the ground under the rim (tools/blueprints/quarry.py). */
	public static final BlockPos PIT_MIN = new BlockPos(1, -10, 2);
	public static final BlockPos PIT_MAX = new BlockPos(8, -1, 10);
	/** The shed's blast furnace, the Miner's job block, in the blueprint's coordinates. */
	public static final BlockPos FURNACE = new BlockPos(14, 1, 7);
	public static final int TICKS_PER_DAY = 24000;
	/** How often the pit grows back what's due, and how often finished Quarries look for their Miner (ticks). */
	static final int GROW_EVERY = 20;
	static final int HIRE_EVERY = 100;
	/** At most this many blocks grow back in one go (a pit left unloaded for days catches up over a few seconds, not at once). */
	static final int AT_ONCE = 32;

	/** The config's {@code quarryBuild}. */
	public static boolean ENABLED = true;
	/** The config's {@code quarryRegrowth}: blocks that grow back in a Minecraft day (0: none). */
	public static int PER_DAY = 100;

	public static void init() {
		Platform.get().onLevelTick(QuarryBuilds::tick);
	}

	static void tick(ServerLevel level) {
		if (!ENABLED) {
			return;
		}
		long now = level.getGameTime();
		if (now % HIRE_EVERY == 0) {
			hire(level);
		}
		if (now % GROW_EVERY == 0) {
			for (QuarrySite site : List.copyOf(QuarrySiteManager.get(level).all())) {
				if (site.refills()) {
					regrow(level, site, now);
				}
			}
		}
	}

	/** The pit of the Quarry built at {@code placement}, in the world. */
	public static BoundingBox pit(BlueprintData.Placement placement) {
		return BoundingBox.fromCorners(toWorld(placement, PIT_MIN), toWorld(placement, PIT_MAX));
	}

	/** Where the shed's blast furnace of the Quarry built at {@code placement} stands. */
	public static BlockPos furnace(BlueprintData.Placement placement) {
		return toWorld(placement, FURNACE);
	}

	private static BlockPos toWorld(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	public static boolean isQuarry(ResourceLocation structure) {
		return BLUEPRINT.equals(BlueprintStyles.base(structure));
	}

	/**
	 * Gives each finished Quarry without a Miner at work in it to the Miner whose job block is its blast furnace, if
	 * they have no other quarry. Returns how many were given out.
	 */
	public static int hire(ServerLevel level) {
		QuarrySiteManager manager = QuarrySiteManager.get(level);
		int hired = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			if (!isQuarry(f.structure())) {
				continue;
			}
			BlockPos origin = f.placement().origin();
			if (manager.all().stream().anyMatch(s -> origin.equals(s.refillBuild()))) {
				continue;
			}
			BlockPos furnace = furnace(f.placement());
			if (!level.isLoaded(furnace)) {
				continue;
			}
			Optional<Villager> miner = level.getEntitiesOfClass(Villager.class, new AABB(furnace).inflate(48),
					v -> v.isAlive() && Miners.isMiner(v) && Builders.benchPos(v).map(furnace::equals).orElse(false)
						&& Miners.activeSite(level, v) == null)
				.stream().findFirst();
			if (miner.isPresent()) {
				Miners.startQuarryBuild(level, miner.get(), f.owner(), pit(f.placement()), origin);
				hired++;
			}
		}
		return hired;
	}

	/**
	 * Grows back what is due in {@code site}'s pit since it last grew: one block every day / {@code quarryRegrowth}
	 * ticks, at most {@link #AT_ONCE} in one go (the rest next time). Nothing is banked while the pit is full.
	 */
	public static int regrow(ServerLevel level, QuarrySite site, long now) {
		if (site.lastGrowth() == QuarrySite.NEVER || PER_DAY <= 0) {
			site.setLastGrowth(now);
			return 0;
		}
		long every = Math.max(1, TICKS_PER_DAY / PER_DAY);
		long due = (now - site.lastGrowth()) / every;
		if (due <= 0) {
			return 0;
		}
		BoundingBox box = site.box();
		if (!level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) || !level.isLoaded(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
			return 0;
		}
		int want = (int) Math.min(due, AT_ONCE);
		int grown = grow(level, site, want);
		site.setLastGrowth(grown < want ? now : site.lastGrowth() + want * every);
		return grown;
	}

	/**
	 * Grows back up to {@code n} blocks in {@code site}'s pit, from the bottom up: each into the lowest open air (not
	 * where someone stands), picked by {@link #pick} with a random fixed by the site and the spot. Returns how many grew.
	 */
	public static int grow(ServerLevel level, QuarrySite site, int n) {
		BoundingBox box = site.box();
		List<BlockPos> grownAt = new ArrayList<>();
		for (int y = box.minY(); y <= box.maxY() && grownAt.size() < n; y++) {
			boolean deep = deep(box, y);
			for (int z = box.minZ(); z <= box.maxZ() && grownAt.size() < n; z++) {
				for (int x = box.minX(); x <= box.maxX() && grownAt.size() < n; x++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir() || !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos)).isEmpty()) {
						continue;
					}
					RandomSource random = RandomSource.create(site.id().getLeastSignificantBits() ^ pos.asLong() * 31 ^ site.grownTotal() * 7919L);
					level.setBlock(pos, pick(random, deep), Block.UPDATE_ALL);
					site.countGrown();
					grownAt.add(pos);
				}
			}
		}
		return grownAt.size();
	}

	/** The lower third of the pit, and anything below the world's y 0, grows back as deepslate. */
	static boolean deep(BoundingBox box, int y) {
		return y < 0 || y - box.minY() < box.getYSpan() / 3;
	}

	/**
	 * A block of the pit's rock, out of 1000: stone 820, coal 80, copper 50, iron 25, redstone 10, gold 10, diamond 5;
	 * each the deepslate kind when {@code deep}.
	 */
	public static BlockState pick(RandomSource random, boolean deep) {
		int roll = random.nextInt(1000);
		Block block;
		if (roll < 820) {
			block = deep ? Blocks.DEEPSLATE : Blocks.STONE;
		} else if (roll < 900) {
			block = deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
		} else if (roll < 950) {
			block = deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
		} else if (roll < 975) {
			block = deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
		} else if (roll < 985) {
			block = deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
		} else if (roll < 995) {
			block = deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
		} else {
			block = deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
		}
		return block.defaultBlockState();
	}

	/** Blocks that grow back in the pit before the Miner starts on it again: a layer's worth. */
	public static int restartAfter(QuarrySite site) {
		return site.box().getXSpan() * site.box().getZSpan();
	}

	/** Days for a dug-out pit of {@code blocks} to grow back full at {@code perDay} (infinite with 0). */
	public static double daysToRefill(int blocks, int perDay) {
		return perDay <= 0 ? Double.POSITIVE_INFINITY : blocks / (double) perDay;
	}

	private QuarryBuilds() {
	}
}
