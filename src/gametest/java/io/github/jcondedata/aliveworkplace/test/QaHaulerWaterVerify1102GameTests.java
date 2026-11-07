package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA verification (qa-lane-c-1102-0655) of B722, B783 and B784, written from the bugs' text: a Hauler Golem at work
 * never stays trapped in water it went into (B722: moat level with its banks; B783: surface a block below the banks),
 * takes its load to a Storehouse it can reach and keeps every item, and never sets foot in lava (B784). These cover the
 * corners the fixes' own tests leave out: a moat one deep and three deep, level with its banks or a block below them, an
 * island reached by a one-wide bridge, a mixed load counted item by item, a save and reload while crossing, and lava
 * trenches one and three wide. Every test is its own batch in the huge area, and its timeout is the tick budget the haul
 * must finish in. The corners that fail (water two below its banks, lava a block below the ground, a long detour) are in
 * QaHaulerWaterVerify1102BugTests.
 */
public class QaHaulerWaterVerify1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	/** Standing height on the raised ground: stone at y 1..4, so the rim's top is y 5. */
	static final int TOP = 5;
	private static final int SIZE = 26;

	static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	static int count(GameTestHelper helper, BlockPos chest, Item item) {
		return ((Container) helper.getBlockEntity(chest)).countItem(item);
	}

	static int dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), QaHaulerWaterVerify1102GameTests::loadItem)
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** The items these tests load (laying the ground can knock the test frame's own button off, which is no hauler's). */
	private static boolean loadItem(ItemEntity e) {
		return e.getItem().is(Items.OAK_LOG) || e.getItem().is(Items.COBBLESTONE) || e.getItem().is(Items.DIAMOND);
	}

	static String droppedItems(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), QaHaulerWaterVerify1102GameTests::loadItem)
			.stream().map(e -> e.getItem() + " at " + e.blockPosition()).toList().toString();
	}

	static IronGolem hauler(GameTestHelper helper, BlockPos standOn) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(standOn), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	static void put(GameTestHelper helper, IronGolem golem, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		golem.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		golem.setDeltaMovement(0, 0, 0);
	}

	static IronGolem reloaded(GameTestHelper helper, IronGolem golem) {
		CompoundTag tag = golem.saveWithoutId(new CompoundTag());
		golem.discard();
		IronGolem copy = EntityType.IRON_GOLEM.create(helper.getLevel());
		helper.assertTrue(copy != null, "an iron golem to load into");
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	static void setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		for (int x = 0; x <= SIZE; x++) {
			for (int z = 0; z <= SIZE; z++) {
				for (int y = 1; y < TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
	}

	/**
	 * Fills the column (x, z) with a liquid {@code depth} deep whose surface lies {@code below} blocks under the rim (0 =
	 * level with the banks), air above it, stone under it.
	 */
	static void liquid(GameTestHelper helper, int x, int z, int depth, int below, net.minecraft.world.level.block.Block block) {
		int surface = TOP - 1 - below;
		for (int y = 1; y < TOP; y++) {
			helper.setBlock(new BlockPos(x, y, z), y > surface ? Blocks.AIR : y > surface - depth ? block : Blocks.STONE);
		}
	}

	/** A square moat from 7 to 17 round a 3x3 island (11..13) whose middle (12, 12) holds the Storehouse. */
	static void moat(GameTestHelper helper, int depth, int below) {
		for (int x = 7; x <= 17; x++) {
			for (int z = 7; z <= 17; z++) {
				boolean island = x >= 11 && x <= 13 && z >= 11 && z <= 13;
				if (!island) {
					liquid(helper, x, z, depth, below, Blocks.WATER);
				}
			}
		}
	}

	static BlockPos islandStorehouse(GameTestHelper helper) {
		helper.setBlock(new BlockPos(12, TOP, 12), ModBlocks.STOREHOUSE);
		BlockPos chest = new BlockPos(12, TOP, 11);
		helper.setBlock(chest, Blocks.CHEST);
		return chest;
	}

	/** Every item of the expected load is in the chest, none carried, none dropped. */
	static void delivered(GameTestHelper helper, IronGolem golem, BlockPos chest, Map<Item, Integer> expected, String where) {
		for (Map.Entry<Item, Integer> e : expected.entrySet()) {
			int stored = count(helper, chest, e.getKey());
			helper.assertTrue(stored == e.getValue(), where + ": the Storehouse chest holds " + stored + " of " + e.getValue() + " " + e.getKey()
				+ "; the golem carries " + HaulerGolems.load(golem) + " at " + helper.relativeVec(golem.position()) + " in the test area (in water: " + golem.isInWater()
				+ "), heading for " + HaulerGolems.storehouse(helper.getLevel(), golem));
		}
		helper.assertTrue(carried(golem) == 0, where + ": the golem still carries " + HaulerGolems.load(golem));
		helper.assertTrue(dropped(helper) == 0, where + ": items lie on the ground: " + droppedItems(helper));
	}

	static void islandRun(GameTestHelper helper, int depth, int below, String where) {
		setup(helper);
		moat(helper, depth, below);
		BlockPos chest = islandStorehouse(helper);
		IronGolem golem = hauler(helper, new BlockPos(21, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), where));
	}

	// ---- B722: water level with its banks, one deep and three deep ----

	/** The only Storehouse is on an island in a moat one deep, its water level with the banks: the hauler wades over. */
	//$ gametest_ticks_batch HUGE '1200' '"qaVerify1102MoatShallow"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaVerify1102MoatShallow")
	public void aHaulerWadesAShallowMoatLevelWithItsBanksToTheOnlyStorehouse(GameTestHelper helper) {
		islandRun(helper, 1, 0, "a moat one deep, level with its banks");
	}

	/** The same moat three deep (deeper than the golem is tall to its waist): it swims across and climbs onto the island. */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102MoatDeepLevel"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102MoatDeepLevel")
	public void aHaulerSwimsADeepMoatLevelWithItsBanksToTheOnlyStorehouse(GameTestHelper helper) {
		islandRun(helper, 3, 0, "a moat three deep, level with its banks");
	}

	/**
	 * B858: a new hauler goes straight to work, never off on vanilla's "move back to the village" stroll first. That goal
	 * ranks the same as the work, starts first on about 1 golem in 5 a few sections from a village (an earlier test's, in
	 * CI) and walks it up to 10 blocks toward it, in CI once off the raised ground in the shallow-moat test, where it then
	 * gave its Storehouse up. 45 haulers a few sections from a bell: each must be at work, and none strolling, on every tick
	 * of its first 60.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVerify1102NoStrollFirst"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVerify1102NoStrollFirst")
	public void aNewHaulerGoesToWorkBeforeAnyVanillaStroll(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		// a village two to four sections off (a bell, a meeting point), as an earlier test's left beside this one in CI: the
		// stroll heads for it; none of these golems stands in it
		BlockPos bell = helper.absolutePos(new BlockPos(-24, 2, 14));
		helper.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		// a village counts only claimed points of interest: as if its villagers met at the bell
		helper.assertTrue(helper.getLevel().getPoiManager().take(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING),
			(h, p) -> p.equals(bell), bell, 1).isPresent(), "the bell claimed as a meeting point");
		Leftovers.after(helper, () -> {
			helper.getLevel().getPoiManager().release(bell);
			helper.getLevel().removeBlock(bell, false);
		});
		java.util.List<IronGolem> golems = new java.util.ArrayList<>();
		for (int x = 10; x <= 26; x += 4) {
			for (int z = 2; z <= 26; z += 3) {
				IronGolem golem = hauler(helper, new BlockPos(x, 1, z));
				ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
				golems.add(golem);
			}
		}
		java.lang.reflect.Field field;
		try {
			field = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
			field.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		StringBuilder strolled = new StringBuilder();
		helper.onEachTick(() -> {
			if (helper.getTick() < 4) {
				return; // its goals are first picked in its first ticks
			}
			for (IronGolem golem : golems) {
				java.util.List<net.minecraft.world.entity.ai.goal.Goal> running;
				try {
					running = ((net.minecraft.world.entity.ai.goal.GoalSelector) field.get(golem)).getAvailableGoals().stream()
						.filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal).toList();
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
				boolean working = running.stream().anyMatch(g -> g instanceof io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal);
				boolean strolling = running.stream().anyMatch(g -> g instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal);
				if ((!working || strolling) && strolled.length() < 400) {
					strolled.append(" tick ").append(helper.getTick()).append(" at ").append(helper.relativePos(golem.blockPosition())).append(": ")
						.append(running.stream().map(g -> g.getClass().getSimpleName()).toList()).append(';');
				}
			}
		});
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(strolled.isEmpty(), "haulers not at work, or off on a stroll, in their first ticks:" + strolled);
			// and whenever one stops for a fight, its work takes over from any stroll again, not only when it starts first
			for (IronGolem golem : golems) {
				java.util.Set<net.minecraft.world.entity.ai.goal.WrappedGoal> goals;
				try {
					goals = ((net.minecraft.world.entity.ai.goal.GoalSelector) field.get(golem)).getAvailableGoals();
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
				net.minecraft.world.entity.ai.goal.WrappedGoal work = goals.stream()
					.filter(g -> g.getGoal() instanceof io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal).findFirst().orElseThrow();
				for (net.minecraft.world.entity.ai.goal.WrappedGoal g : goals) {
					if (g.getGoal() instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal) {
						helper.assertTrue(g.getPriority() > work.getPriority(), "a hauler's work (rank " + work.getPriority() + ") gives way to "
							+ g.getGoal().getClass().getSimpleName() + " (rank " + g.getPriority() + ")");
					}
				}
			}
			helper.succeed();
		});
	}

	// ---- B783: surface below the banks ----

	/** A moat only one deep whose surface is a block below the banks: in, across and up the block-high bank onto the island. */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102MoatShallowLow"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102MoatShallowLow")
	public void aHaulerCrossesAOneDeepMoatABlockBelowItsBanks(GameTestHelper helper) {
		islandRun(helper, 1, 1, "a moat one deep, a block below its banks");
	}

	/** Water three deep a block below the rim on all sides of the island, with a one-wide stone bridge from the far bank. */
	//$ gametest_ticks_batch HUGE '1200' '"qaVerify1102Bridge"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaVerify1102Bridge")
	public void aHaulerGetsToAnIslandWithAOneWideBridge(GameTestHelper helper) {
		setup(helper);
		moat(helper, 3, 1);
		for (int x = 14; x <= 17; x++) {
			for (int y = 1; y < TOP; y++) {
				helper.setBlock(new BlockPos(x, y, 12), Blocks.STONE);
			}
		}
		BlockPos chest = islandStorehouse(helper);
		IronGolem golem = hauler(helper, new BlockPos(21, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), "an island with a one-wide bridge"));
	}

	/**
	 * A hauler carrying three kinds of items is pushed into a pool three deep a block below its rim: every item arrives,
	 * counted kind by kind (64 logs, 33 cobblestone, 1 diamond), nothing lost, doubled or dropped.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102MixedLoad"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102MixedLoad")
	public void aHaulerPushedIntoAPoolKeepsAMixedLoadExactly(GameTestHelper helper) {
		setup(helper);
		for (int x = 3; x <= 8; x++) {
			for (int z = 3; z <= 8; z++) {
				liquid(helper, x, z, 3, 1, Blocks.WATER);
			}
		}
		BlockPos chest = new BlockPos(20, TOP, 20);
		helper.setBlock(new BlockPos(19, TOP, 20), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(12, TOP - 1, 5));
		Map<Item, Integer> load = new LinkedHashMap<>();
		load.put(Items.OAK_LOG, 64);
		load.put(Items.COBBLESTONE, 33);
		load.put(Items.DIAMOND, 1);
		ModAttachments.HAULER_LOAD.set(golem, load);
		helper.runAfterDelay(10, () -> put(helper, golem, new BlockPos(5, 2, 5)));
		helper.runAfterDelay(30, () -> helper.assertTrue(golem.isInWater() && carried(golem) == 98,
			"the pushed hauler should be in the pool with its 98 items at tick 30: in water " + golem.isInWater() + ", carrying " + HaulerGolems.load(golem)));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 30, "not yet pushed");
			delivered(helper, golem, chest, load, "a mixed load through a deep pool");
		});
	}

	/**
	 * The save is closed and loaded again the moment the hauler is in the moat on its way to the only Storehouse (three
	 * deep, a block below the banks). The loaded golem carries on, climbs onto the island and stores 64 logs and 10
	 * cobblestone exactly once.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaVerify1102ReloadCrossing"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaVerify1102ReloadCrossing")
	public void aHaulerReloadedWhileCrossingAMoatStillStoresItsLoadOnce(GameTestHelper helper) {
		setup(helper);
		moat(helper, 3, 1);
		BlockPos chest = islandStorehouse(helper);
		IronGolem golem = hauler(helper, new BlockPos(21, TOP - 1, 12));
		Map<Item, Integer> load = Map.of(Items.OAK_LOG, 64, Items.COBBLESTONE, 10);
		ModAttachments.HAULER_LOAD.set(golem, load);
		IronGolem[] now = {golem};
		// B825: it failed only in full suites (about 1 run in 10, never in 230 runs alone), the reloaded golem floating
		// where it went in with no Storehouse to head for. What happened first is kept, so the next failure names it.
		StringBuilder story = new StringBuilder();
		BlockPos storehouse = helper.absolutePos(new BlockPos(12, TOP, 12));
		boolean[] told = new boolean[3];
		helper.onEachTick(() -> {
			if (now[0] == golem && golem.isInWater()) {
				now[0] = reloaded(helper, golem);
				story.append(" reloaded on tick ").append(helper.getTick()).append(" at ").append(helper.relativeVec(now[0].position())).append(';');
				helper.assertTrue(carried(now[0]) == 74, "the reloaded hauler carries " + HaulerGolems.load(now[0]) + " instead of 64 logs and 10 cobblestone");
			}
			IronGolem copy = now[0];
			if (copy == golem) {
				return;
			}
			if (!told[0] && HaulerGolems.storehouse(helper.getLevel(), copy).isEmpty()) {
				told[0] = true;
				story.append(" on tick ").append(helper.getTick()).append(" it had no Storehouse to head for at ").append(helper.relativeVec(copy.position()))
					.append(" (in water ").append(copy.isInWater()).append(", the Storehouse block ")
					.append(helper.getLevel().getBlockState(storehouse).is(ModBlocks.STOREHOUSE) ? "stands" : "is gone").append(", its point of interest ")
					.append(HaulerGolems.storehouse(helper.getLevel(), copy.blockPosition()).isPresent() ? "is there, so it gave the Storehouse up" : "is gone")
					.append("), with ").append(others(helper, copy)).append(" about;");
			}
			if (!told[1] && copy.getTarget() != null) {
				told[1] = true;
				story.append(" on tick ").append(helper.getTick()).append(" it went for ").append(copy.getTarget()).append(';');
			}
			if (!told[2] && copy.isRemoved()) {
				told[2] = true;
				story.append(" on tick ").append(helper.getTick()).append(" it was removed (").append(copy.getRemovalReason()).append(");");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(now[0] != golem, "the hauler never went into the moat (not reloaded yet)");
			delivered(helper, now[0], chest, load, "reloaded while crossing a moat (" + story.toString().trim() + ")");
			helper.assertTrue(golem.isRemoved(), "the golem from before the reload is still about");
		});
	}

	/** The other entities within 16 blocks of {@code golem}, by type and block (the test's own golem left out). */
	private static String others(GameTestHelper helper, IronGolem golem) {
		java.util.List<String> near = helper.getLevel().getEntities(golem, golem.getBoundingBox().inflate(16)).stream()
			.map(e -> e.getType().toShortString() + " at " + e.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList();
		return near.isEmpty() ? "nothing else" : near.toString();
	}

	// ---- B784: lava ----

	/**
	 * A loaded hauler whose Storehouse lies straight across a lava trench ({@code wide} blocks, from z 4 to 20 unless given,
	 * its surface {@code below} blocks under the ground; below -1 makes it a stone wall two high instead) goes round and
	 * stores all 64 logs, and on no tick is it in lava, on fire or hurt.
	 */
	static void lavaRun(GameTestHelper helper, int wide, int below, String where) {
		lavaRun(helper, wide, below, 4, 20, where);
	}

	static void lavaRun(GameTestHelper helper, int wide, int below, int fromZ, int toZ, String where) {
		setup(helper);
		for (int x = 12; x < 12 + wide; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				if (below < 0) {
					for (int y = TOP; y <= TOP + 1; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				} else {
					liquid(helper, x, z, 1, below, Blocks.LAVA);
				}
			}
		}
		BlockPos chest = new BlockPos(21, TOP, 12);
		helper.setBlock(new BlockPos(20, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(5, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		float health = golem.getHealth();
		helper.onEachTick(() -> {
			if (golem.isInLava() || golem.isOnFire() || golem.getHealth() < health) {
				helper.fail(where + ": the hauler went into the lava: in lava " + golem.isInLava() + ", on fire " + golem.isOnFire() + ", health "
					+ golem.getHealth() + " of " + health + " at " + golem.position() + " on tick " + helper.getTick());
			}
		});
		helper.succeedWhen(() -> delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64), where));
	}

	/** Lava trenches 11 long (z 7 to 17), level with the ground, one and three wide; each twice, so a run catches a rare slip. */
	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LavaOneShort"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LavaOneShort")
	public void aHaulerGoesRoundAShortOneWideLavaTrench(GameTestHelper helper) {
		lavaRun(helper, 1, 0, 7, 17, "a one-wide lava trench, 11 long");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LavaThreeShort"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LavaThreeShort")
	public void aHaulerGoesRoundAShortThreeWideLavaTrench(GameTestHelper helper) {
		lavaRun(helper, 3, 0, 7, 17, "a three-wide lava trench, 11 long");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LavaOneShortB"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LavaOneShortB")
	public void aHaulerGoesRoundAShortOneWideLavaTrenchCopy1(GameTestHelper helper) {
		lavaRun(helper, 1, 0, 7, 17, "a one-wide lava trench, 11 long (copy 1)");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qaVerify1102LavaThreeShortB"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaVerify1102LavaThreeShortB")
	public void aHaulerGoesRoundAShortThreeWideLavaTrenchCopy1(GameTestHelper helper) {
		lavaRun(helper, 3, 0, 7, 17, "a three-wide lava trench, 11 long (copy 1)");
	}
}
