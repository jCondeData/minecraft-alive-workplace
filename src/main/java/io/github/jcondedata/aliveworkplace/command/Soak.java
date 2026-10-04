package io.github.jcondedata.aliveworkplace.command;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.MaterialLedger;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.StallWatch;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.grower.TreeGrower;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /workplace soak}: the builder soak test (roadmap 23.1). Raises hilly, forested ground east and south of where
 * it's run, then puts 10 builders to work on the whole starter set (every tier-1 build), with the materials only in
 * chests by their benches and no player help, and watches for 2 in-game days (or the days given). When every build is done, or the 2 days
 * are up, it logs one {@code Soak result:} line: builds finished, stalls ({@link StallWatch}), and every item whose
 * count doesn't add up ({@link MaterialLedger}: stocked + gained − built in − dropped ≠ left in containers and bags). Only registered with
 * {@code -Daliveworkplace.benchmark=true} (the pack test's measuring modes), never on a normal server.
 */
public final class Soak {
	public static final int BUILDERS = 10;
	/** One in-game day; the soak runs 2 by default ({@code /workplace soak <days>} for more). */
	public static final long DAY = 24000;
	public static final int DAYS = 2;
	/** Each builder has a row: a chest pad and bench at its west end, then its builds 18 blocks apart. */
	static final int ROW = 26;
	static final int BUILD_STEP = 18;
	static final int COLUMNS = 2;
	static final int COLUMN = 70;
	/** The words the result line starts with, for log searches. */
	public static final String RESULT = "Soak result:";

	/** One soak in progress. */
	private record Run(ServerLevel level, long start, long limit, BlockPos from, BlockPos to, List<Planned> builds,
					   List<BlockPos> chests, List<Villager> builders, Map<Item, Integer> stocked, int stallsBefore) {
	}

	private record Planned(BuildSite site, BuildPlan plan) {
	}

	@Nullable
	private static Run running;

	public static void init() {
		if (!Boolean.getBoolean("aliveworkplace.benchmark")) {
			return;
		}
		Platform.get().onRegisterCommands(dispatcher -> dispatcher.register(
			Commands.literal("workplace").then(Commands.literal("soak")
				.requires(s -> s.hasPermission(4))
				.executes(ctx -> run(ctx.getSource(), DAYS))
				.then(Commands.argument("days", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 30))
					.executes(ctx -> run(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "days")))))));
		Platform.get().onServerTick(Soak::tick);
	}

	static int run(CommandSourceStack source, int days) {
		String text = begin(source.getLevel(), BlockPos.containing(source.getPosition()), days);
		source.sendSuccess(() -> Component.literal(text), true);
		return running == null ? 0 : running.builds().size();
	}

	/**
	 * Starts a soak at {@code origin} for {@code days} in-game days and returns (and logs) its summary line. The command
	 * and the time-lapse scene (SCENE=soak) both start here; the scene ends it with {@link #finish()}.
	 */
	public static String begin(ServerLevel level, BlockPos origin, int days) {
		Rules.set(level, ModGameRules.FREE_MATERIALS, false, level.getServer());
		Rules.set(level, GameRules.RULE_DAYLIGHT, true, level.getServer());
		level.setDayTime(1000);
		Run run = start(level, origin, RandomSource.create(23_1L), days * DAY);
		running = run;
		int total = run.stocked().values().stream().mapToInt(Integer::intValue).sum();
		String text = "Soak: " + run.builders().size() + " builders, " + run.builds().size() + " builds, " + total + " items in "
			+ run.chests().size() + " chests, " + days + " days";
		AliveWorkplace.LOG.info(text);
		return text;
	}

	/** Builds finished so far and builds in the soak, or {0, 0} when none is running. */
	public static int[] progress() {
		Run run = running;
		if (run == null) {
			return new int[] {0, 0};
		}
		BuildSiteManager manager = BuildSiteManager.get(run.level());
		int finished = (int) run.builds().stream().filter(p -> manager.get(p.site().id()) == null).count();
		return new int[] {finished, run.builds().size()};
	}

	/** Ends the running soak now: logs and returns its result line, or null when none is running. */
	@Nullable
	public static String finish() {
		Run run = running;
		if (run == null) {
			return null;
		}
		String result = result(run, true);
		AliveWorkplace.LOG.info(result);
		running = null;
		return result;
	}

	/** Lays out the ground, the builders, their chests and builds; public for the soak GameTest and scene. */
	static Run start(ServerLevel level, BlockPos origin, RandomSource random, long limit) {
		List<StarterBlueprints.Entry> builds = StarterBlueprints.ALL.stream()
			.filter(e -> BlueprintUpgrades.baseOf(e.id()).isEmpty()).toList();
		int rows = (BUILDERS + COLUMNS - 1) / COLUMNS;
		int perBuilder = (builds.size() + BUILDERS - 1) / BUILDERS;
		terrain(level, origin, COLUMNS * COLUMN, rows * ROW, random);
		List<Planned> planned = new ArrayList<>();
		List<BlockPos> chests = new ArrayList<>();
		List<Villager> builders = new ArrayList<>();
		Map<Item, Integer> stocked = new TreeMap<>(Soak::byId);
		for (int b = 0; b < BUILDERS; b++) {
			int x = origin.getX() + (b % COLUMNS) * COLUMN + 8;
			int z = origin.getZ() + (b / COLUMNS) * ROW;
			BlockPos bench = pad(level, x, z + 6);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
			if (builder == null) {
				continue;
			}
			builder.setPersistenceRequired();
			Builders.employ(level, builder, bench);
			builders.add(builder);
			Map<Item, Integer> mine = new TreeMap<>(Soak::byId);
			for (int k = 0; k < perBuilder; k++) {
				int index = b + k * BUILDERS;
				if (index >= builds.size()) {
					break;
				}
				BlockPos at = top(level, x + 5 + k * BUILD_STEP, z);
				BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()), at, Rotation.NONE, Mirror.NONE);
				BuildSite site = k == 0
					? Builders.start(level, builder, null, builds.get(index).id(), placement)
					: Builders.enqueue(level, builder, null, builds.get(index).id(), placement);
				BuildPlan plan = site.plan(level);
				if (plan == null) {
					continue;
				}
				planned.add(new Planned(site, plan));
				plan.materials().forEach((item, n) -> mine.merge(item, n, Integer::sum));
			}
			mine.forEach((item, n) -> stocked.merge(item, n, Integer::sum));
			chests.addAll(stock(level, bench, mine));
		}
		// Count from here: everything builders gain, build in or drop is in the ledger.
		MaterialLedger.start();
		BlockPos from = origin.offset(-4, -8, -4);
		BlockPos to = origin.offset(COLUMNS * COLUMN + 4, 40, rows * ROW + 4);
		return new Run(level, level.getGameTime(), limit, from, to, planned, chests, builders, stocked, StallWatch.stalls());
	}

	/** Rolling hills of grass over dirt and stone, with oak and birch woods on them. */
	static void terrain(ServerLevel level, BlockPos origin, int width, int depth, RandomSource random) {
		int base = origin.getY();
		for (int dx = -4; dx < width + 4; dx++) {
			for (int dz = -4; dz < depth + 4; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int h = base + height(dx, dz);
				for (int y = base - 6; y <= base + 30; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					level.setBlock(pos, y < h - 3 ? Blocks.STONE.defaultBlockState()
						: y < h ? Blocks.DIRT.defaultBlockState()
						: y == h ? Blocks.GRASS_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		for (int dx = 0; dx < width; dx += 5) {
			for (int dz = 0; dz < depth; dz += 5) {
				if (random.nextInt(3) == 0) {
					continue;
				}
				int x = origin.getX() + dx + random.nextInt(3);
				int z = origin.getZ() + dz + random.nextInt(3);
				BlockPos pos = new BlockPos(x, base + height(x - origin.getX(), z - origin.getZ()) + 1, z);
				boolean birch = random.nextInt(3) == 0;
				level.setBlock(pos, (birch ? Blocks.BIRCH_SAPLING : Blocks.OAK_SAPLING).defaultBlockState(), 2);
				(birch ? TreeGrower.BIRCH : TreeGrower.OAK).growTree(level, level.getChunkSource().getGenerator(), pos,
					level.getBlockState(pos), random);
			}
		}
	}

	/** Hills about 9 blocks from top to bottom, a few long waves so the slopes stay walkable. */
	static int height(int dx, int dz) {
		return Math.round(3.5f * Mth.sin(dx / 11f) + 2.5f * Mth.cos(dz / 9f) + 1.5f * Mth.sin((dx + dz) / 6f));
	}

	/** Levels a small pad for the bench and its chests and returns the bench's spot on it. */
	private static BlockPos pad(ServerLevel level, int x, int z) {
		BlockPos ground = top(level, x, z);
		for (int dx = -7; dx <= 1; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				for (int dy = -3; dy <= 6; dy++) {
					BlockPos pos = new BlockPos(x + dx, ground.getY() + dy, z + dz);
					level.setBlockAndUpdate(pos, dy < -1 ? Blocks.DIRT.defaultBlockState()
						: dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		return ground;
	}

	/** Fills chests west of the bench (all within the bench's reach) with exactly {@code items}. */
	private static List<BlockPos> stock(ServerLevel level, BlockPos bench, Map<Item, Integer> items) {
		List<ItemStack> stacks = new ArrayList<>();
		items.forEach((item, n) -> {
			int left = n;
			while (left > 0) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				stacks.add(new ItemStack(item, take));
				left -= take;
			}
		});
		List<BlockPos> out = new ArrayList<>();
		int next = 0;
		// Two layers of single chests, a gap between columns so none of them join into double chests.
		for (int layer = 0; layer < 2 && next < stacks.size(); layer++) {
			for (int dx = -2; dx >= -6 && next < stacks.size(); dx -= 2) {
				for (int dz = -6; dz <= 6 && next < stacks.size(); dz++) {
					BlockPos pos = bench.offset(dx, layer, dz);
					level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
					if (level.getBlockEntity(pos) instanceof Container chest) {
						for (int slot = 0; slot < chest.getContainerSize() && next < stacks.size(); slot++) {
							chest.setItem(slot, stacks.get(next++));
						}
						out.add(pos);
					}
				}
			}
		}
		if (next < stacks.size()) {
			AliveWorkplace.LOG.warn("Soak: {} stacks didn't fit in the chests by the bench at {}", stacks.size() - next, bench.toShortString());
		}
		return out;
	}

	private static BlockPos top(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static void tick(MinecraftServer server) {
		Run run = running;
		if (run == null || run.level().getGameTime() % 200 != 0) {
			return;
		}
		String result = result(run, false);
		if (result != null) {
			AliveWorkplace.LOG.info(result);
			running = null;
		}
	}

	/** The result line when the soak is over (or {@code force}), else null. */
	@Nullable
	static String result(Run run, boolean force) {
		ServerLevel level = run.level();
		BuildSiteManager manager = BuildSiteManager.get(level);
		int finished = 0;
		List<String> unfinished = new ArrayList<>();
		for (Planned p : run.builds()) {
			// A finished site leaves the list (nobody cancels in the soak). Its start-time plan isn't the yardstick: the
			// builder plans again as the ground changes (trees felled, slopes levelled).
			if (manager.get(p.site().id()) == null) {
				finished++;
			} else {
				unfinished.add(p.site().structure().getPath());
			}
		}
		long ticks = level.getGameTime() - run.start();
		if (!force && finished < run.builds().size() && ticks < run.limit()) {
			return null;
		}
		MaterialLedger.stop();
		// Left: every container in the soak's ground (builders may put things in a storehouse or chest they built) and bag.
		Map<Item, Integer> left = new TreeMap<>(Soak::byId);
		for (int cx = run.from().getX() >> 4; cx <= run.to().getX() >> 4; cx++) {
			for (int cz = run.from().getZ() >> 4; cz <= run.to().getZ() >> 4; cz++) {
				for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (be instanceof Container box && inside(be.getBlockPos(), run)) {
						for (int i = 0; i < box.getContainerSize(); i++) {
							ItemStack stack = box.getItem(i);
							if (!stack.isEmpty()) {
								left.merge(stack.getItem(), stack.getCount(), Integer::sum);
							}
						}
					}
				}
			}
		}
		for (Villager builder : run.builders()) {
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
			for (ItemStack stack : bag.stacks()) {
				if (!stack.isEmpty()) {
					left.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		return RESULT + " " + finished + "/" + run.builds().size() + " builds finished in " + ticks + " ticks ("
			+ String.format(java.util.Locale.ROOT, "%.1f", ticks / (double) DAY) + " days); "
			+ (StallWatch.stalls() - run.stallsBefore()) + " stalls; items off: " + itemsOff(run.stocked(), MaterialLedger.gained(),
				MaterialLedger.used(), MaterialLedger.dropped(), left)
			+ (unfinished.isEmpty() ? "" : "; unfinished: " + String.join(", ", unfinished));
	}

	private static boolean inside(BlockPos pos, Run run) {
		return pos.getX() >= run.from().getX() && pos.getX() <= run.to().getX() && pos.getY() >= run.from().getY()
			&& pos.getY() <= run.to().getY() && pos.getZ() >= run.from().getZ() && pos.getZ() <= run.to().getZ();
	}

	/**
	 * Every item whose count doesn't add up: stocked + gained − used − dropped should be what is left in containers and
	 * bags. More means duplicated (+), less means lost (−). "none" when everything adds up.
	 */
	public static String itemsOff(Map<Item, Integer> stocked, Map<Item, Integer> gained, Map<Item, Integer> used,
						   Map<Item, Integer> dropped, Map<Item, Integer> left) {
		Map<Item, Integer> expected = new TreeMap<>(Soak::byId);
		stocked.forEach((item, n) -> expected.merge(item, n, Integer::sum));
		gained.forEach((item, n) -> expected.merge(item, n, Integer::sum));
		used.forEach((item, n) -> expected.merge(item, -n, Integer::sum));
		dropped.forEach((item, n) -> expected.merge(item, -n, Integer::sum));
		left.keySet().forEach(item -> expected.putIfAbsent(item, 0));
		List<String> off = new ArrayList<>();
		expected.forEach((item, want) -> {
			int now = left.getOrDefault(item, 0);
			if (now != want) {
				off.add(BuiltInRegistries.ITEM.getKey(item).getPath() + " " + (now > want ? "+" : "") + (now - want));
			}
		});
		return off.isEmpty() ? "none" : String.join(", ", off);
	}

	private static int byId(Item a, Item b) {
		return BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b));
	}

	private Soak() {
	}
}
