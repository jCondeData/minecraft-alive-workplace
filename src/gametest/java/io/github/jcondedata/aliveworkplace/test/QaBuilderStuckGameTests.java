package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (qa-lane-b-1027-0338), ROADMAP 23.2 "Stuck recovery, proven": "Builders stuck on water, lava, holes, fences, doors,
 * their own scaffolding, or in unloaded chunks ... the recovery (hop, re-path, step back) never breaks a placed block."
 *
 * <p>The chaos test ({@link BuilderChaosGameTests}) springs mild traps a villager could mostly walk out of: a pool sunk
 * one block into the floor, a 2-high ring, a fence ring, a room with a wooden door it can open, lava three blocks off.
 * These are the harder versions of the same cases, each one a trap the builder cannot walk out of, so only the recovery
 * gets it out: a 3-deep hole, a room shut by an iron door (villagers can't open one), a 3-deep well of water with sheer
 * sides, an island in a lava moat, a fence pen with a shut gate, and the 3-deep hole with a server restart (every chunk unloaded: sites and builder
 * saved and loaded) right after it fell in. Each builds the test hut with every material in the chest, and must:
 * <ul>
 *   <li>be out of the trap (or building from inside it) within {@link #ESCAPE_TICKS};</li>
 *   <li>never go {@link #STALL_TICKS} on shift without the site moving on;</li>
 *   <li>never break a block of the build once placed, nor a block of the trap (getting out is a hop, a re-path or a step
 *   back, never digging);</li>
 *   <li>finish the hut whole with nothing skipped, alive.</li>
 * </ul>
 */
public class QaBuilderStuckGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);
	/** Where the builder is trapped: clear of the hut (x 6-10, z 6-10) by at least one block, even for a 5x5 moat. */
	private static final BlockPos TRAP = new BlockPos(3, 2, 14);
	private static final int ESCAPE_TICKS = 600;
	private static final int STALL_TICKS = 1200;
	/** Blocks placed when the builder is trapped (again) mid-job: the hut has 82, so early, halfway and near the end. */
	private static final int[] MID_JOB = {12, 35, 60};

	/** The builder at the bottom of a 3-deep hole (it can't jump out) before the job starts, and twice more: out each time, and builds. */
	//$ gametest_ticks_batch AREA '4000' '"qaStuckPit"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStuckPit")
	public void aBuilderAtTheBottomOfAThreeDeepHoleGetsOutAndFinishes(GameTestHelper helper) {
		trapped(helper, "a 3-deep hole", new int[]{0, 35, 60}, 1, 3, false, QaBuilderStuckGameTests::pit);
	}

	/** Shut in a roofed stone room by an iron door (no villager opens one), three times mid-job: out without breaking it, and builds. */
	//$ gametest_ticks_batch AREA '4000' '"qaStuckIronDoor"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStuckIronDoor")
	public void aBuilderShutInByAnIronDoorMidJobGetsOutAndFinishes(GameTestHelper helper) {
		trapped(helper, "a room shut by an iron door", MID_JOB, 1, 3, false, QaBuilderStuckGameTests::ironDoorRoom);
	}

	/** Dropped into a 3-deep well of water with sheer sides two blocks above the water, three times mid-job: out, and builds. */
	//$ gametest_ticks_batch AREA '4000' '"qaStuckWell"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStuckWell")
	public void aBuilderInADeepWellOfWaterMidJobGetsOutAndFinishes(GameTestHelper helper) {
		trapped(helper, "a 3-deep well of water", MID_JOB, 1, 5, false, QaBuilderStuckGameTests::well);
	}

	/** On a one-block island in a two-wide moat of lava, three times mid-job: out without burning to death, and builds. */
	//$ gametest_ticks_batch AREA '4000' '"qaStuckLava"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStuckLava")
	public void aBuilderOnAnIslandInALavaMoatMidJobGetsOutAndFinishes(GameTestHelper helper) {
		trapped(helper, "an island in a lava moat", MID_JOB, 2, 0, false, QaBuilderStuckGameTests::lavaMoat);
	}

	/** Penned in by a fence with a shut gate (villagers don't open gates), three times mid-job: out over nothing broken, and builds. */
	//$ gametest_ticks_batch AREA '4000' '"qaStuckPen"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStuckPen")
	public void aBuilderPennedInByAShutFenceGateMidJobGetsOutAndFinishes(GameTestHelper helper) {
		trapped(helper, "a pen with a shut gate", MID_JOB, 1, 2, false, QaBuilderStuckGameTests::pen);
	}

	/**
	 * Three times mid-job, in the 3-deep hole, and then the server restarts (its chunks unload: every site and the builder saved and
	 * loaded, the builder as a new entity): it still gets out and finishes.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaStuckPitRestart"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaStuckPitRestart")
	public void aBuilderInAHoleThroughARestartGetsOutAndFinishes(GameTestHelper helper) {
		trapped(helper, "a 3-deep hole, then a restart", MID_JOB, 1, 3, true, QaBuilderStuckGameTests::pit);
	}

	// ------------------------------------------------------------------------------------------------- the traps

	/** A 3-high stone ring round the trap spot, open above: the bottom of a hole three deep. */
	private static void pit(GameTestHelper helper, Map<BlockPos, BlockState> trap) {
		ring(helper, trap, 3, Blocks.STONE.defaultBlockState());
	}

	/** A 2-high stone ring with a roof, its side towards the hut an iron door, shut. */
	private static void ironDoorRoom(GameTestHelper helper, Map<BlockPos, BlockState> trap) {
		ring(helper, trap, 2, Blocks.STONE.defaultBlockState());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(helper, trap, TRAP.offset(dx, 2, dz), Blocks.STONE.defaultBlockState());
			}
		}
		BlockPos door = TRAP.north(); // towards the hut
		BlockState iron = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, false);
		put(helper, trap, door, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(helper, trap, door.above(), iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A ring of oak fence round the trap spot, its side towards the hut a fence gate, shut. */
	private static void pen(GameTestHelper helper, Map<BlockPos, BlockState> trap) {
		ring(helper, trap, 1, Blocks.OAK_FENCE.defaultBlockState());
		put(helper, trap, TRAP.north(), Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH)
			.setValue(FenceGateBlock.OPEN, false));
	}

	/** A 1x1 well, three blocks of water deep, its glass sides going on two blocks above the water. */
	private static void well(GameTestHelper helper, Map<BlockPos, BlockState> trap) {
		ring(helper, trap, 5, Blocks.GLASS.defaultBlockState());
		for (int dy = 0; dy < 3; dy++) {
			put(helper, trap, TRAP.above(dy), Blocks.WATER.defaultBlockState());
		}
	}

	/** The floor round the trap spot, two blocks each way, turned to lava: the builder stands on the one stone left. */
	private static void lavaMoat(GameTestHelper helper, Map<BlockPos, BlockState> trap) {
		BlockPos floor = TRAP.below();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx != 0 || dz != 0) {
					put(helper, trap, floor.offset(dx, 0, dz), Blocks.LAVA.defaultBlockState());
				}
			}
		}
	}

	private static void ring(GameTestHelper helper, Map<BlockPos, BlockState> trap, int height, BlockState block) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int dy = 0; dy < height; dy++) {
					put(helper, trap, TRAP.offset(dx, dy, dz), block);
				}
			}
		}
	}

	private static void put(GameTestHelper helper, Map<BlockPos, BlockState> trap, BlockPos rel, BlockState state) {
		BlockPos abs = helper.absolutePos(rel);
		helper.getLevel().setBlock(abs, state, 2);
		trap.put(abs.immutable(), state);
	}

	// ------------------------------------------------------------------------------------------------- the run

	/**
	 * Builds the test hut, trapping the builder at {@link #TRAP} each time {@code placed} blocks are down. It is out once
	 * its feet are more than {@code radius} blocks (flat) from the trap spot or {@code top} or more blocks above it.
	 */
	private static void trapped(GameTestHelper helper, String what, int[] when, int radius, int top, boolean restart,
								BiConsumer<GameTestHelper, Map<BlockPos, BlockState>> spring) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		GameRules rules = level.getGameRules();
		boolean free = rules.getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = rules.getInt(ModGameRules.BUILD_DELAY);
		boolean help = rules.getBoolean(ModGameRules.BUILDERS_HELP);
		boolean daylight = rules.getBoolean(GameRules.RULE_DAYLIGHT);
		boolean fire = rules.getBoolean(GameRules.RULE_DOFIRETICK);
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
		rules.getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(GameRules.RULE_DAYLIGHT).set(daylight, level.getServer());
			rules.getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer());
		});

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack[] stock = {new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i]);
		}
		Villager first = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, first, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN),
			Rotation.NONE, Mirror.NONE);
		UUID id = Builders.start(level, first, null, TEST_HUT, placement).id();
		BuildPlan plan = BuildSiteManager.get(level).get(id).plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		Map<BlockPos, BlockState> wanted = new HashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				wanted.put(step.pos(), step.state());
				if (step.secondaryPos() != null) {
					wanted.put(step.secondaryPos(), step.secondaryState());
				}
			}
		}

		Villager[] builder = {first};
		Map<BlockPos, BlockState> trap = new LinkedHashMap<>();
		Set<BlockPos> placed = new HashSet<>();
		List<String> log = new ArrayList<>();
		int[] ticks = {0, -1, 0}; // ticks, the tick it was last trapped (or last placed a block inside), ticks on shift without progress
		long[] marks = {Long.MIN_VALUE}; // the last mark seen
		int[] traps = {0, 0}; // traps sprung, traps got out of
		int[] placedInside = {0};
		boolean[] escaped = {true};
		String[] failed = {null};
		int[] lastSkipped = {0};
		int[] lastPlaced = {0};

		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(id);
			if (site == null || failed[0] != null) {
				return;
			}
			int t = ++ticks[0];
			Villager v = builder[0];
			long mark = TerrainStallGameTests.mark(site);
			lastSkipped[0] = site.skipped();
			lastPlaced[0] = site.placed();
			// The trap (again), once the job is far enough along and the builder got out of the last one.
			if (escaped[0] && traps[0] < when.length && site.placed() >= when[traps[0]]) {
				if (trap.isEmpty()) {
					spring.accept(helper, trap);
				}
				BlockPos at = helper.absolutePos(TRAP);
				v.getNavigation().stop();
				v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				ticks[1] = t;
				placedInside[0] = site.placed();
				traps[0]++;
				escaped[0] = false;
				log.add(t + ": trapped in " + what + " (stage " + site.stage() + ", " + site.placed() + " placed)");
				if (restart) {
					var registries = level.registryAccess();
					level.getDataStorage().set(BuildSiteManager.NAME,
						BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
					CompoundTag tag = new CompoundTag();
					helper.assertTrue(v.save(tag), "the builder wasn't saved");
					v.discard();
					Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
					helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
					level.addFreshEntity(loaded);
					builder[0] = (Villager) loaded;
					log.add(t + ": restarted");
				}
				return;
			}
			if (traps[0] == 0) {
				return;
			}
			// Out of the trap within ESCAPE_TICKS (or, while still inside, placing a block at least that often).
			if (!escaped[0]) {
				// (Absolute positions: GameTestHelper.relativePos turns an unrotated test's positions round on 1.21.1.)
				BlockPos feet = v.blockPosition();
				BlockPos spot = helper.absolutePos(TRAP);
				boolean wide = Math.max(Math.abs(feet.getX() - spot.getX()), Math.abs(feet.getZ() - spot.getZ())) > radius;
				if (wide || top > 0 && feet.getY() - spot.getY() >= top) {
					escaped[0] = true;
					traps[1]++;
					log.add(t + ": out after " + (t - ticks[1]) + " ticks, at " + helper.relativeVec(v.position()));
				} else if (site.placed() != placedInside[0]) {
					placedInside[0] = site.placed();
					ticks[1] = t; // building from inside: not stuck
				} else if (t - ticks[1] > ESCAPE_TICKS) {
					failed[0] = what + " held the builder " + ESCAPE_TICKS + " ticks (at " + helper.relativeVec(v.position()) + ", stage "
						+ site.stage() + ", status " + site.status() + "); " + log;
					return;
				}
			}
			// Never long on shift without progress.
			boolean onShift = !v.isSleeping() && v.getBrain().isActive(Activity.WORK);
			if (mark != marks[0] || !onShift) {
				marks[0] = mark;
				ticks[2] = 0;
			} else if (++ticks[2] > STALL_TICKS) {
				failed[0] = "no progress for " + STALL_TICKS + " ticks on shift: stage " + site.stage() + ", " + site.placed()
					+ " placed, builder at " + helper.relativeVec(v.position()) + "; " + log;
				return;
			}
			// Nothing of the build, once placed, and nothing of the trap is ever broken.
			if (t % 5 == 0) {
				for (Map.Entry<BlockPos, BlockState> e : wanted.entrySet()) {
					if (MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue())) {
						placed.add(e.getKey());
					} else if (placed.contains(e.getKey())) {
						failed[0] = "the placed " + e.getValue() + " at " + helper.relativePos(e.getKey()) + " is now "
							+ level.getBlockState(e.getKey()) + " (stage " + site.stage() + "); " + log;
						return;
					}
				}
				for (Map.Entry<BlockPos, BlockState> e : trap.entrySet()) {
					BlockState now = level.getBlockState(e.getKey());
					if (!now.is(e.getValue().getBlock())) {
						failed[0] = "the trap's " + e.getValue().getBlock() + " at " + helper.relativePos(e.getKey()) + " is now " + now
							+ " (stage " + site.stage() + "); " + log;
						return;
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			if (failed[0] != null) {
				helper.fail(failed[0]);
			}
			Villager v = builder[0];
			helper.assertTrue(traps[0] == when.length, "the builder was trapped " + traps[0] + " time(s), not " + when.length + ": " + log);
			helper.assertTrue(traps[1] == traps[0], "the builder got out of " + traps[1] + " of " + traps[0] + " traps: " + log);
			helper.assertTrue(v.isAlive(), "the builder died: " + v.getRemovalReason() + " / " + v.getLastDamageSource() + "; " + log);
			BuildSite site = BuildSiteManager.get(level).get(id);
			helper.assertTrue(site == null, "still building: " + (site == null ? "" : "stage " + site.stage() + ", status " + site.status()
				+ ", " + site.placed() + " placed, builder at " + helper.relativeVec(v.position())) + "; " + log);
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList() + "; " + log);
			helper.assertTrue(lastSkipped[0] == 0, lastSkipped[0] + " step(s) skipped; " + log);
			for (Map.Entry<BlockPos, BlockState> e : trap.entrySet()) {
				helper.assertTrue(level.getBlockState(e.getKey()).is(e.getValue().getBlock()), "the trap's " + e.getValue().getBlock() + " at "
					+ helper.relativePos(e.getKey()) + " is gone: " + level.getBlockState(e.getKey()));
			}
			AliveWorkplace.LOG.info("[qa stuck] {}: finished in {} ticks, {} placed: {}", what, ticks[0], lastPlaced[0], log);
		});
	}
}
