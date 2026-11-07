package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ranch.RancherWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-c-1107-1853) for B979, written from the spec: a Rancher, Shepherd or Herder only feeds two ready animals
 * of one kind that can find each other once in love (vanilla looks for a partner within 8 blocks). He picks the two ready
 * ones standing closest together, only when they're within 7 blocks; no food is spent on a pair that drifted apart before
 * the first was fed; when the second walks off or dies after the first was fed, the nearest ready one within 8 blocks of
 * the first is fed instead, or none. Animals are held still (no AI) so the distances are the test's, not their wandering.
 */
public class QaRanchPairs1107cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TROUGH = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);
	/** The loom or smoker of a Shepherd or Herder, and its chest. */
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos STATION_CHEST = new BlockPos(2, 2, 4);

	/**
	 * Two horses exactly 7 blocks apart are the furthest pair the spec still feeds: both get a golden carrot, and once let
	 * go they find each other (within vanilla's 8) and have a foal. 2 of the 4 carrots are left.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaRanchPairSevenApart"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaRanchPairSevenApart")
	public void rancherFeedsAPairSevenApartAndTheyHaveAFoal(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse west = stillHorse(helper, new BlockPos(3, 2, 4));
		Horse east = stillHorse(helper, new BlockPos(10, 2, 4));
		helper.assertTrue(Math.abs(west.distanceTo(east) - 7.0) < 1e-6, "the horses stand " + west.distanceTo(east) + " apart, not 7");
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		boolean[] letGo = {false};
		helper.onEachTick(() -> {
			if (!letGo[0] && west.isInLove() && east.isInLove()) {
				letGo[0] = true;
				west.setNoAi(false);
				east.setNoAi(false);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(letGo[0], "the pair 7 apart wasn't fed (in love: " + west.isInLove() + "/" + east.isInLove() + ")");
			long foals = helper.getEntities(EntityType.HORSE).stream().filter(Horse::isBaby).count();
			helper.assertTrue(foals >= 1, "no foal from the pair 7 apart");
			int left = carrots(helper);
			helper.assertTrue(left == 2, "golden carrots left: " + left + " (expected 2 of 4)");
		});
	}

	/**
	 * Two horses 5 and 5 blocks apart on the diagonal stand 7.07 apart: just over the 7, so for 600 ticks neither gets a
	 * carrot. Then one is set 6.4 away and both are fed: it was only the distance.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchPairDiagonal"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchPairDiagonal")
	public void rancherSkipsAPairJustOverSevenOnTheDiagonal(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(4, 2, 4));
		Horse b = stillHorse(helper, new BlockPos(9, 2, 9));
		helper.assertTrue(a.distanceTo(b) > 7.05 && a.distanceTo(b) < 7.1, "the horses stand " + a.distanceTo(b) + " apart");
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		boolean[] moved = {false};
		helper.onEachTick(() -> {
			if (!moved[0] && (a.isInLove() || b.isInLove())) {
				helper.fail("a horse of a pair 7.07 apart was fed");
			}
		});
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(carrots(helper) == 4, "golden carrots left while 7.07 apart: " + carrots(helper));
			moved[0] = true;
			teleport(helper, b, new BlockPos(9, 2, 8));
			helper.assertTrue(a.distanceTo(b) < 6.5, "moved to " + a.distanceTo(b) + " apart");
			helper.succeedWhen(() -> {
				helper.assertTrue(a.isInLove() && b.isInLove(), "the pair 6.4 apart wasn't fed: " + a.isInLove() + "/" + b.isInLove());
				helper.assertTrue(carrots(helper) == 2, "golden carrots left: " + carrots(helper) + " (expected 2)");
			});
		});
	}

	/**
	 * Three ready horses all within 7 of each other: A (spawned first) is 6 from B and 6.7 from C, B and C 3 apart. The
	 * closest two, B and C, are fed; A never is. 2 of 4 carrots are left.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchPairClosest"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchPairClosest")
	public void rancherPicksTheClosestOfThree(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(3, 2, 5));
		Horse b = stillHorse(helper, new BlockPos(9, 2, 5));
		Horse c = stillHorse(helper, new BlockPos(9, 2, 8));
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		helper.onEachTick(() -> {
			if (a.isInLove()) {
				helper.fail("horse A (6 and 6.7 from the others) was fed instead of the pair 3 apart");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(b.isInLove() && c.isInLove(), "the pair 3 apart wasn't fed: " + b.isInLove() + "/" + c.isInLove());
			helper.assertTrue(carrots(helper) == 2, "golden carrots left: " + carrots(helper) + " (expected 2)");
		});
	}

	/**
	 * Right next to ready horse A stand a foal and a horse resting after breeding; the only other ready horse D is 6 away.
	 * The closest READY pair is A and D: they're fed, the foal and the resting one never are.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchPairNotReady"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchPairNotReady")
	public void rancherPassesOverAFoalAndOneResting(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(4, 2, 6));
		Horse foal = stillHorse(helper, new BlockPos(5, 2, 6));
		foal.setAge(-24000);
		Horse resting = stillHorse(helper, new BlockPos(4, 2, 8));
		resting.setAge(6000);
		Horse d = stillHorse(helper, new BlockPos(10, 2, 6));
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		helper.onEachTick(() -> {
			if (foal.isInLove() || resting.isInLove()) {
				helper.fail("fed a horse that can't breed: foal " + foal.isInLove() + ", resting " + resting.isInLove());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(a.isInLove() && d.isInLove(), "the ready pair 6 apart wasn't fed: " + a.isInLove() + "/" + d.isInLove());
			helper.assertTrue(carrots(helper) == 2, "golden carrots left: " + carrots(helper) + " (expected 2)");
		});
	}

	/**
	 * A pair 4 apart is picked and the carrots leave the chest; before either is fed, one is moved 12 away. Neither is
	 * ever fed and all 4 carrots end up back in the chest.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRanchPairDrifts"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRanchPairDrifts")
	public void rancherSpendsNothingWhenThePairDriftsApartBeforeFeeding(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(4, 2, 4));
		Horse b = stillHorse(helper, new BlockPos(4, 2, 8));
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		boolean[] moved = {false};
		helper.onEachTick(() -> {
			if (a.isInLove() || b.isInLove()) {
				helper.fail("a horse was fed though its mate had walked 12 away (moved: " + moved[0] + ")");
			}
			if (!moved[0] && carrots(helper) < 4) {
				moved[0] = true;
				teleport(helper, b, new BlockPos(16, 2, 4));
			}
		});
		helper.runAtTickTime(1000, () -> {
			helper.assertTrue(moved[0], "the rancher never took the carrots for the pair 4 apart");
			helper.assertTrue(carrots(helper) == 4, "golden carrots left: " + carrots(helper) + " (expected all 4 back)");
			helper.succeed();
		});
	}

	/**
	 * After the first of a pair 3 apart is fed, its mate dies. Horse C, ready and about 5 from either, is fed instead;
	 * horse D, over 8 from both, never is. 2 of 4 carrots are left.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchPairMateDies"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchPairMateDies")
	public void rancherFeedsTheNearestThirdWhenTheMateDies(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(5, 2, 5));
		Horse b = stillHorse(helper, new BlockPos(5, 2, 8));
		Horse c = stillHorse(helper, new BlockPos(10, 2, 6));
		Horse d = stillHorse(helper, new BlockPos(12, 2, 1));
		helper.assertTrue(d.distanceTo(a) > 8 && d.distanceTo(b) > 8 && c.distanceTo(a) < 8 && c.distanceTo(b) < 8, "set-up distances");
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		Horse[] fed = {null};
		helper.onEachTick(() -> {
			if (fed[0] == null && (a.isInLove() || b.isInLove())) {
				fed[0] = a.isInLove() ? a : b;
				Horse mate = fed[0] == a ? b : a;
				helper.assertTrue(!mate.isInLove(), "both of the pair were fed in one tick");
				mate.kill();
			}
			if (d.isInLove()) {
				helper.fail("horse D, over 8 blocks from the one in love, was fed");
			}
			if (fed[0] == null && c.isInLove()) {
				helper.fail("horse C was fed before the pair 3 apart");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fed[0] != null, "neither of the pair 3 apart was fed");
			helper.assertTrue(c.isInLove(), "horse C (the nearest ready one) wasn't fed after the mate died");
			helper.assertTrue(carrots(helper) == 2, "golden carrots left: " + carrots(helper) + " (expected 2)");
		});
	}

	/**
	 * After the first of a pair 3 apart is fed, its mate is moved 12 away; the only other ready horse is over 8 from the
	 * one in love. Nobody else is fed: 1 carrot was spent (on the first), 3 are back in the chest.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaRanchPairMateLeaves"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaRanchPairMateLeaves")
	public void rancherFeedsNoOtherWhenTheMateWalksOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(3, 2, 3));
		Horse b = stillHorse(helper, new BlockPos(3, 2, 6));
		Horse far = stillHorse(helper, new BlockPos(16, 2, 4));
		helper.assertTrue(far.distanceTo(a) > 8 && far.distanceTo(b) > 8, "set-up distances");
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		Horse[] fed = {null};
		helper.onEachTick(() -> {
			if (fed[0] == null && (a.isInLove() || b.isInLove())) {
				fed[0] = a.isInLove() ? a : b;
				Horse mate = fed[0] == a ? b : a;
				helper.assertTrue(!mate.isInLove(), "both of the pair were fed in one tick");
				teleport(helper, mate, new BlockPos(3, 2, 18));
			}
			if (fed[0] != null) {
				Horse mate = fed[0] == a ? b : a;
				if (mate.isInLove() || far.isInLove()) {
					helper.fail("fed a horse that can't reach the one in love: walked-off mate " + mate.isInLove() + ", far one " + far.isInLove());
				}
			}
		});
		helper.runAtTickTime(1200, () -> {
			helper.assertTrue(fed[0] != null, "neither of the pair 3 apart was fed");
			Horse mate = fed[0] == a ? b : a;
			helper.assertTrue(fed[0].distanceTo(mate) > 8, "the mate is only " + fed[0].distanceTo(mate) + " away");
			helper.assertTrue(carrots(helper) == 3, "golden carrots left: " + carrots(helper) + " (expected 3: one fed, one brought back)");
			helper.succeed();
		});
	}

	/**
	 * The Shepherd, through the same pairing: two sheep 8 apart get no wheat for 600 ticks; moved to 6 apart, both are fed.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchPairShepherd"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchPairShepherd")
	public void shepherdFeedsSheepOnlyOnceTheyStandClose(GameTestHelper helper) {
		Leftovers.clear(helper);
		Sheep a = helper.spawn(EntityType.SHEEP, new BlockPos(6, 2, 8));
		Sheep b = helper.spawn(EntityType.SHEEP, new BlockPos(14, 2, 8));
		for (Sheep s : List.of(a, b)) {
			s.setNoAi(true);
			s.setSheared(true); // nothing to shear: pairing is all there is to do
		}
		Container chest = station(helper, Blocks.LOOM, new ItemStack(Items.WHEAT, 4));
		Villager shepherd = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		farThenNear(helper, a, b, new BlockPos(12, 2, 8), () -> chest.countItem(Items.WHEAT), "wheat");
	}

	/**
	 * The Herder (a butcher), through the same pairing: two cows 8 apart, and a pig right beside one of them. For 600
	 * ticks nobody is fed (the cows are too far apart, and a cow and a pig are never a pair). Then the far cow is moved 6
	 * from the other: the two cows are fed wheat, the pig never gets a carrot.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchPairHerder"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchPairHerder")
	public void herderPairsOnlyCowsThatCanMeetNeverACowAndAPig(GameTestHelper helper) {
		Leftovers.clear(helper);
		Cow a = helper.spawn(EntityType.COW, new BlockPos(6, 2, 8));
		Cow b = helper.spawn(EntityType.COW, new BlockPos(14, 2, 8));
		Pig pig = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 9));
		for (Animal animal : List.<Animal>of(a, b, pig)) {
			animal.setNoAi(true);
		}
		Container chest = station(helper, Blocks.SMOKER, new ItemStack(Items.WHEAT, 4), new ItemStack(Items.CARROT, 4));
		Villager herder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), herder, helper.absolutePos(STATION), PoiTypes.BUTCHER, VillagerProfession.BUTCHER);
		helper.onEachTick(() -> {
			if (pig.isInLove()) {
				helper.fail("the lone pig (beside a cow) was fed");
			}
		});
		farThenNear(helper, a, b, new BlockPos(12, 2, 8), () -> chest.countItem(Items.WHEAT), "wheat");
	}

	/** {@code a} and {@code b} start 8 apart with 4 food: unfed for 600 ticks, then {@code b} is moved to {@code near} and both are fed, using 2. */
	private static void farThenNear(GameTestHelper helper, Animal a, Animal b, BlockPos near, java.util.function.IntSupplier food, String what) {
		helper.assertTrue(Math.abs(a.distanceTo(b) - 8.0) < 1e-6, "the two stand " + a.distanceTo(b) + " apart, not 8");
		boolean[] moved = {false};
		helper.onEachTick(() -> {
			if (!moved[0] && (a.isInLove() || b.isInLove())) {
				helper.fail("one of a pair 8 apart was fed");
			}
		});
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(food.getAsInt() == 4, what + " left while 8 apart: " + food.getAsInt() + " (expected 4)");
			moved[0] = true;
			teleport(helper, b, near);
			helper.succeedWhen(() -> {
				helper.assertTrue(a.isInLove() && b.isInLove(), "the pair 6 apart wasn't fed: " + a.isInLove() + "/" + b.isInLove());
				helper.assertTrue(food.getAsInt() == 2, what + " left: " + food.getAsInt() + " (expected 2 of 4)");
			});
		});
	}

	private static Container station(GameTestHelper helper, net.minecraft.world.level.block.Block block, ItemStack... food) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, block);
		helper.setBlock(STATION_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(STATION_CHEST);
		for (int i = 0; i < food.length; i++) {
			chest.setItem(i, food[i]);
		}
		return chest;
	}

	private static void teleport(GameTestHelper helper, Animal animal, BlockPos to) {
		Vec3 v = helper.absoluteVec(Vec3.atBottomCenterOf(to));
		animal.teleportTo(v.x, v.y, v.z);
	}

	private static int carrots(GameTestHelper helper) {
		Container chest = helper.getBlockEntity(CHEST);
		return chest.countItem(Items.GOLDEN_CARROT);
	}

	/** A tamed horse that stays where it's put (no AI). */
	private static Horse stillHorse(GameTestHelper helper, BlockPos pos) {
		Horse horse = helper.spawn(EntityType.HORSE, pos);
		horse.setTamed(true);
		horse.setNoAi(true);
		return horse;
	}

	private static Villager rancher(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(TROUGH, ModBlocks.FEED_TROUGH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		helper.assertTrue(RancherWork.isRancher(villager), "not a rancher");
		return villager;
	}
}
