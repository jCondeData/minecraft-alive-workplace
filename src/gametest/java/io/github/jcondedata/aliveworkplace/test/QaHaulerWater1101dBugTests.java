package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug tests (lane-b-1101-2138) on B722, after its fix (486b09d74, working golems swim): the fix's own test has a moat
 * whose water is level with its banks. Water whose surface lies a block below the rim (a pool or moat not filled to the
 * brim, the commonest kind) still traps a Hauler Golem: it swims at the surface pressed against the bank, never climbs
 * out, gives up its Storehouse and floats there with its whole load. B722's text: "the golem doesn't path into water
 * it can't leave (or gets out), takes the load to the reachable Storehouse and keeps hauling". Also lava, which must
 * never be crossed: a hauler going round a lava trench sometimes cuts its corner and dips in (on fire, hurt).
 */
public class QaHaulerWater1101dBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** Standing height on the raised ground: stone at y 1..4 (the floor is y 1). */
	private static final int TOP = 5;

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	private static int logs(GameTestHelper helper, BlockPos chest) {
		return ((Container) helper.getBlockEntity(chest)).countItem(Items.OAK_LOG);
	}

	private static int dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), e -> e.getItem().is(Items.OAK_LOG))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static IronGolem hauler(GameTestHelper helper, BlockPos near) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(near), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	private static void put(GameTestHelper helper, IronGolem golem, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		golem.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		golem.setDeltaMovement(0, 0, 0);
	}

	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x <= size; x++) {
			for (int z = 0; z <= size; z++) {
				for (int y = 1; y < TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
	}

	/** Water at y 1..3 (three deep) on a stone bottom, its surface a full block below the rim on every side. */
	private static void pool(GameTestHelper helper, int from, int to) {
		for (int x = from; x <= to; x++) {
			for (int z = from; z <= to; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
				}
				helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
			}
		}
	}

	private static void delivered(GameTestHelper helper, IronGolem golem, BlockPos chest, int expected, String where) {
		int stored = logs(helper, chest);
		helper.assertTrue(stored == expected, where + ": the Storehouse chest holds " + stored + " of " + expected + " logs; the golem carries "
			+ carried(golem) + " at " + golem.position() + " (in water: " + golem.isInWater() + "), heading for "
			+ HaulerGolems.storehouse(helper.getLevel(), golem));
		helper.assertTrue(carried(golem) == 0, where + ": the golem still carries " + carried(golem) + " after storing " + stored);
		helper.assertTrue(dropped(helper) == 0, where + ": " + dropped(helper) + " logs lie on the ground");
	}

	/**
	 * A loaded hauler on its way to the Storehouse is pushed into a pool three deep whose surface is a block below the
	 * rim on every side. It swims up, climbs out and stores the whole load: 64 logs in the chest, none carried or dropped.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugPool"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugPool")
	public void aHaulerPushedIntoADeepPoolMidHaulClimbsOutAndStoresTheWholeLoad(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ground(helper, 22);
		pool(helper, 3, 8);
		BlockPos chest = new BlockPos(19, TOP, 18);
		helper.setBlock(new BlockPos(18, TOP, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(12, TOP - 1, 5));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(10, () -> put(helper, golem, new BlockPos(5, 1, 5)));
		helper.runAfterDelay(40, () -> helper.assertTrue(golem.isInWater() && carried(golem) == 64,
			"the pushed hauler should be in the pool with its 64 logs at tick 40: in water " + golem.isInWater() + ", carrying " + carried(golem)));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 40, "not yet pushed");
			delivered(helper, golem, chest, 64, "pushed into a deep pool");
		});
	}

	/**
	 * The only Storehouse stands on an island in a moat three deep with steep banks; the loaded hauler starts on the far
	 * bank. With nowhere else to take the load, it must cross the water and store all 64 logs on the island.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerWaterBugIsland"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerWaterBugIsland")
	public void aHaulerCrossesAMoatToTheOnlyStorehouseOnAnIsland(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ground(helper, 22);
		pool(helper, 2, 12);
		for (int x = 6; x <= 8; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 1; y < TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		BlockPos chest = new BlockPos(7, TOP, 6);
		helper.setBlock(new BlockPos(7, TOP, 7), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(17, TOP - 1, 7));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1, () -> helper.assertTrue(golem.getY() >= helper.absolutePos(BlockPos.ZERO).getY() + TOP - 0.01
			&& !golem.isInWater(), "the hauler should start dry on the far bank, not at " + golem.position()));
		helper.succeedWhen(() -> delivered(helper, golem, chest, 64, "the only Storehouse, across a moat"));
	}

	/**
	 * Lava is not water: a loaded hauler whose Storehouse lies straight across a lava trench must go round it on dry land
	 * and never set foot in the lava (no burning, no lava contact on any tick), and still store all 64 logs. It cuts the
	 * trench's far corner and dips into the lava (on fire, 96 of 100 health) in about 1 run in 6 (2 of 12 copies in one
	 * run, and 5 of 13 with 486b09d74 reverted, so it predates B722's fix); the copies below make a run catch it.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava")
	public void aHaulerGoesRoundLavaInsteadOfThroughIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ground(helper, 22);
		for (int x = 10; x <= 11; x++) {
			for (int z = 6; z <= 18; z++) {
				for (int y = 1; y < TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == TOP - 1 ? Blocks.LAVA : Blocks.STONE);
				}
			}
		}
		BlockPos chest = new BlockPos(18, TOP, 13);
		helper.setBlock(new BlockPos(17, TOP, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(4, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		float health = golem.getHealth();
		helper.onEachTick(() -> {
			if (golem.isInLava() || golem.isOnFire() || golem.getHealth() < health) {
				helper.fail("the hauler went into the lava: in lava " + golem.isInLava() + ", on fire " + golem.isOnFire() + ", health "
					+ golem.getHealth() + " of " + health + " at " + golem.position());
			}
		});
		helper.succeedWhen(() -> delivered(helper, golem, chest, 64, "round a lava trench"));
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava1"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava1")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy1(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava2"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava2")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy2(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava3"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava3")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy3(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava4"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava4")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy4(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava5"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava5")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy5(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava6"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava6")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy6(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava7"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava7")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy7(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava8"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava8")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy8(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}

	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterBugLava9"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterBugLava9")
	public void aHaulerGoesRoundLavaInsteadOfThroughItCopy9(GameTestHelper helper) {
		aHaulerGoesRoundLavaInsteadOfThroughIt(helper);
	}
}
