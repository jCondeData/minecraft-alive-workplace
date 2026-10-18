package io.github.jcondedata.aliveworkplace.command;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /workplace furnished}: the pack test's furnished builds (roadmap 37.11). On flat ground where it's run, a
 * builder builds the Starter Cottage and another the Inn, each from barrels by its bench holding exactly what its plan
 * costs, with the furniture mods tried in the order the config's {@code furnitureMods} gives (the pack test puts Lucky's
 * Cozy Home first). It logs one {@code Furnished:} line per build when they start (the pieces each mod got) and, when
 * both are done or the days are up, one {@code Furnished result:} line: each build finished or not, its skipped steps,
 * its blocks not as planned, the furniture pieces not standing, and the pieces of each mod. Only registered with
 * {@code -Daliveworkplace.benchmark=true} (the pack test's measuring modes), never on a normal server; the furniture
 * itself also needs Milestone 37's gate open ({@code -Daliveworkplace.shots=true} until 2.1).
 */
public final class FurnishedPackTest {
	/** The words the result line starts with, for log searches. */
	public static final String RESULT = "Furnished result:";
	/** The builds, west to east. */
	static final List<ResourceLocation> BUILDS = List.of(AliveWorkplace.id("starter_cottage"), AliveWorkplace.id("inn"));
	/** Each build's lot: its bench and barrels at the west end, the build 8 blocks east of the bench. */
	static final int LOT = 36;
	static final int DEPTH = 28;
	/** At most this many in-game days (the command's argument changes it). */
	static final int DAYS = 4;

	private record Planned(BuildSite site, BuildPlan plan, FurnitureChoice choice, Villager builder) {
	}

	private record Run(ServerLevel level, long start, long limit, List<Planned> builds, List<String> order) {
	}

	@Nullable
	private static Run running;

	public static void init() {
		if (!Boolean.getBoolean("aliveworkplace.benchmark")) {
			return;
		}
		Platform.get().onRegisterCommands(dispatcher -> dispatcher.register(
			Commands.literal("workplace").then(Commands.literal("furnished")
				.requires(s -> s.hasPermission(4))
				.executes(ctx -> run(ctx.getSource(), DAYS))
				.then(Commands.argument("days", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 30))
					.executes(ctx -> run(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "days")))))));
		Platform.get().onServerTick(FurnishedPackTest::tick);
	}

	static int run(CommandSourceStack source, int days) {
		ServerLevel level = source.getLevel();
		BlockPos origin = BlockPos.containing(source.getPosition());
		Rules.set(level, ModGameRules.FREE_MATERIALS, false, level.getServer());
		Rules.set(level, ModGameRules.BUILDERS_HELP, false, level.getServer());
		// Always day: builders work by day, and the test is about what they build, not when.
		Rules.set(level, GameRules.RULE_DAYLIGHT, false, level.getServer());
		level.setDayTime(2000);
		ground(level, origin);
		List<String> order = List.copyOf(Furnishings.MODS);
		List<Planned> builds = new ArrayList<>();
		for (int i = 0; i < BUILDS.size(); i++) {
			Planned planned = start(level, origin.offset(i * LOT, 0, 0), BUILDS.get(i));
			if (planned != null) {
				builds.add(planned);
				AliveWorkplace.LOG.info("Furnished: {} with {}, furnished builds {}: {} pieces ({}), {} items in barrels",
					BUILDS.get(i).getPath(), String.join(", ", order), Furnishings.ENABLED ? "on" : "off",
					planned.choice().spots().size(), byMod(planned.choice()),
					planned.plan().materials().values().stream().mapToInt(Integer::intValue).sum());
			} else {
				AliveWorkplace.LOG.warn("Furnished: {} couldn't be started", BUILDS.get(i).getPath());
			}
		}
		running = new Run(level, level.getGameTime(), days * Soak.DAY, builds, order);
		String text = "Furnished: " + builds.size() + " of " + BUILDS.size() + " builds started, for at most " + days + " days";
		AliveWorkplace.LOG.info(text);
		source.sendSuccess(() -> Component.literal(text), true);
		return builds.size();
	}

	/** Flat grass over dirt and stone, cleared 30 blocks up, under both lots. */
	private static void ground(ServerLevel level, BlockPos origin) {
		for (int dx = -12; dx < BUILDS.size() * LOT; dx++) {
			for (int dz = -12; dz < DEPTH; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				level.getChunk(x >> 4, z >> 4);
				for (int dy = -6; dy <= 30; dy++) {
					BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
					level.setBlock(pos, dy < -3 ? Blocks.STONE.defaultBlockState()
						: dy < -1 ? Blocks.DIRT.defaultBlockState()
						: dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** A bench at the lot's west end, its builder, the build 8 blocks east, and barrels of exactly its materials. */
	@Nullable
	private static Planned start(ServerLevel level, BlockPos lot, ResourceLocation id) {
		BlockPos bench = lot.offset(-8, 0, 2);
		level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
		if (builder == null) {
			return null;
		}
		builder.setPersistenceRequired();
		Builders.employ(level, builder, bench);
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()), lot, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		// The furniture is chosen at the first plan, from furnitureMods, and saved on the site.
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			return null;
		}
		List<ItemStack> stacks = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			for (int left = n; left > 0; left -= item.getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		// Barrels west of the bench, two layers of three rows: up to 54 barrels, all within the bench's reach.
		int next = 0;
		for (int b = 0; next < stacks.size() && b < 54; b++) {
			BlockPos pos = bench.offset(-2 - (b / 9) % 3, b / 27, -4 + b % 9);
			level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof Container barrel) {
				for (int slot = 0; slot < barrel.getContainerSize() && next < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(next++).copy());
				}
			}
		}
		if (next < stacks.size()) {
			AliveWorkplace.LOG.warn("Furnished: {} stacks of {} didn't fit in the barrels", stacks.size() - next, id.getPath());
		}
		return new Planned(site, plan, site.furniture(), builder);
	}

	private static void tick(MinecraftServer server) {
		Run run = running;
		if (run == null || run.level().getGameTime() % 200 != 0) {
			return;
		}
		BuildSiteManager manager = BuildSiteManager.get(run.level());
		boolean allDone = run.builds().stream().allMatch(p -> manager.get(p.site().id()) == null);
		if (!allDone && run.level().getGameTime() - run.start() < run.limit()) {
			return;
		}
		running = null;
		AliveWorkplace.LOG.info(result(run, manager));
	}

	/** The result line: per build, finished or not, skipped steps, blocks not as planned, pieces not standing, pieces by mod. */
	static String result(Run run, BuildSiteManager manager) {
		ServerLevel level = run.level();
		List<String> parts = new ArrayList<>();
		for (Planned p : run.builds()) {
			BuildSite site = p.site();
			boolean finished = manager.get(site.id()) == null;
			int wrong = p.plan().unfinished(level).size();
			int notStanding = 0;
			TreeSet<String> cozy = new TreeSet<>();
			for (FurnitureChoice.Placed placed : p.choice().spots().values()) {
				boolean standing = true;
				for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
					if (cell.getValue().isAir()) {
						continue;
					}
					BlockState there = level.getBlockState(site.placement().origin().offset(placed.pos()).offset(cell.getKey()));
					if (!there.is(cell.getValue().getBlock())) {
						standing = false;
					} else if (placed.mod().equals("cozyhome")) {
						cozy.add(BuiltInRegistries.BLOCK.getKey(there.getBlock()).getPath());
					}
				}
				if (!standing) {
					notStanding++;
				}
			}
			parts.add(site.structure().getPath() + " " + (finished ? "finished" : "unfinished (" + site.stage() + ", missing "
				+ site.missing().size() + " kinds)") + ", " + site.skipped() + " skipped, " + wrong + " blocks wrong, " + notStanding
				+ " pieces not standing, pieces " + byMod(p.choice()) + (cozy.isEmpty() ? "" : ", Cozy Home blocks " + String.join(" ", cozy)));
		}
		long ticks = level.getGameTime() - run.start();
		return RESULT + " " + String.join("; ", parts) + "; order " + String.join(", ", run.order()) + "; in " + ticks + " ticks";
	}

	/** "cozyhome 9, handcrafted 5, ..." for a choice, or "none". */
	static String byMod(FurnitureChoice choice) {
		Map<String, Integer> count = new TreeMap<>();
		choice.spots().values().forEach(placed -> count.merge(placed.mod(), 1, Integer::sum));
		if (count.isEmpty()) {
			return "none";
		}
		List<String> out = new ArrayList<>();
		count.forEach((mod, n) -> out.add(mod + " " + n));
		return String.join(", ", out);
	}

	private FurnishedPackTest() {
	}
}
