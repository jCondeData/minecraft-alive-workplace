package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bee.BeekeeperWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane c, 2026-11-23), from the spec, not the code: README "Beekeepers" ("Put a lit campfire under a hive and the
 * bees stay calm; otherwise they fly out when it's harvested (they don't go for villagers). With flowers in the chests
 * they plant them round any hive with fewer than four near it, and feed pairs of bees flowers to breed until there are
 * three bees a hive"; "the beehives and bee nests within 16 blocks") and the wiki's villager-jobs rows. Each boundary
 * from both sides: three flowers near a hive get one more, four get none; two bees breed to three, three and one don't;
 * no flowers (or only a sapling and wheat) in the chest plants and feeds nothing; flowers go only on ground they can
 * grow on and never replace a block; a hive 16 blocks away is tended and one 17 away isn't; a lit campfire keeps the
 * bees in and calm, an unlit one or none lets them out, and they never hurt the Beekeeper.
 */
public class QaBeekeeperFlowers1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos APIARY = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HIVE = new BlockPos(10, 3, 10);

	// --- Flowers -----------------------------------------------------------------------------------------------------

	/**
	 * "fewer than four near it", the low side: a hive with three flowers right by it gets exactly one more, taken from
	 * the chest (8 dandelions become 7), and no more after that.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaBee1123cThree"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBee1123cThree")
	public void aHiveWithThreeFlowersGetsExactlyOneMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		hive(helper, HIVE, 0, true);
		for (BlockPos p : List.of(HIVE.offset(-2, -1, 0), HIVE.offset(2, -1, 0), HIVE.offset(0, -1, 2))) {
			helper.setBlock(p, Blocks.POPPY);
		}
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		Container chest = helper.getBlockEntity(CHEST);
		AtomicLong reached = new AtomicLong(-1);
		helper.onEachTick(() -> {
			if (reached.get() < 0 && flowers(helper) >= 4) {
				reached.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached.get() >= 0, "the fourth flower never came: " + flowers(helper) + " flowers, chest " + chest.countItem(Items.DANDELION));
			helper.assertTrue(helper.getTick() >= reached.get() + 600, "watching for extra flowers");
			helper.assertTrue(flowers(helper) == 4 && chest.countItem(Items.DANDELION) == 7,
				"600 ticks after the fourth flower: " + flowers(helper) + " flowers, " + chest.countItem(Items.DANDELION) + " dandelions in the chest");
		});
	}

	/** "fewer than four near it", the high side: four flowers already by the hive, nothing is planted or taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaBee1123cFour"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaBee1123cFour")
	public void aHiveWithFourFlowersGetsNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		hive(helper, HIVE, 0, true);
		fourFlowers(helper, HIVE);
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		Container chest = helper.getBlockEntity(CHEST);
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(flowers(helper) == 4 && chest.countItem(Items.DANDELION) == 8,
				"a hive with four flowers got more: " + flowers(helper) + " flowers, " + chest.countItem(Items.DANDELION) + " dandelions left");
			helper.succeed();
		});
	}

	/**
	 * Flowers go on ground they can grow on, never in the air or over a block. The grass round the hive is half
	 * covered with cobblestone and oak slabs; every flower that appears stands on grass or dirt and can survive there,
	 * none of the blocks is replaced, and every dandelion is accounted for (in the chest, in his hands or planted).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaBee1123cGround"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBee1123cGround")
	public void flowersGoOnlyOnFreeGroundAndReplaceNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		hive(helper, HIVE, 0, true);
		List<BlockPos> covered = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(HIVE.offset(-4, -1, -4), HIVE.offset(4, -1, 4))) {
			if ((p.getX() + p.getZ()) % 2 == 0 && !p.equals(HIVE.below())) {
				covered.add(p.immutable());
				helper.setBlock(p, p.getX() % 3 == 0 ? Blocks.OAK_SLAB : Blocks.COBBLESTONE);
			}
		}
		Villager keeper = beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(flowers(helper) >= 4, "only " + flowers(helper) + " flowers planted round the half-covered hive");
			for (BlockPos p : covered) {
				BlockState s = helper.getBlockState(p);
				helper.assertTrue(s.is(Blocks.OAK_SLAB) || s.is(Blocks.COBBLESTONE), "the block at " + p + " was replaced by " + s);
			}
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(21, 10, 21))) {
				BlockState s = helper.getBlockState(p);
				if (s.is(BlockTags.FLOWERS) && !s.is(Blocks.POPPY)) {
					BlockState under = helper.getBlockState(p.below());
					helper.assertTrue(under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.DIRT), "a flower at " + p + " stands on " + under);
					helper.assertTrue(s.canSurvive(helper.getLevel(), helper.absolutePos(p)), "a flower at " + p + " can't survive there");
				}
			}
			int held = ModAttachmentsView.bagDandelions(keeper) + (keeper.getMainHandItem().is(Items.DANDELION) ? keeper.getMainHandItem().getCount() : 0);
			helper.assertTrue(chest.countItem(Items.DANDELION) + held + flowers(helper) == 8,
				"dandelions lost: chest " + chest.countItem(Items.DANDELION) + ", carried " + held + ", planted " + flowers(helper));
		});
	}

	/**
	 * "With flowers in the chests": with none (only an oak sapling and wheat), two bees by a hive with no flowers
	 * near: after 1200 ticks nothing is planted (no flower, no sapling), no bee is born and the chest is as it was.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaBee1123cNoFlowers"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaBee1123cNoFlowers")
	public void withoutFlowersNothingIsPlantedOrBred(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		hive(helper, HIVE, 0, true);
		bees(helper, HIVE, 2);
		beekeeper(helper, new ItemStack(Items.OAK_SAPLING, 4), new ItemStack(Items.WHEAT, 8));
		Container chest = helper.getBlockEntity(CHEST);
		helper.runAfterDelay(1200, () -> {
			int saplings = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(21, 10, 21))) {
				saplings += helper.getBlockState(p).is(BlockTags.SAPLINGS) ? 1 : 0;
			}
			helper.assertTrue(flowers(helper) == 0 && saplings == 0, "planted without flowers: " + flowers(helper) + " flowers, " + saplings + " saplings");
			helper.assertTrue(beeCount(helper, HIVE) == 2 && babies(helper) == 0, "bees bred without flowers: " + beeCount(helper, HIVE) + " bees");
			helper.assertTrue(chest.countItem(Items.OAK_SAPLING) == 4 && chest.countItem(Items.WHEAT) == 8,
				"chest changed: saplings " + chest.countItem(Items.OAK_SAPLING) + ", wheat " + chest.countItem(Items.WHEAT));
			clearBees(helper);
			helper.succeed();
		});
	}

	/**
	 * Each hive gets its own count: two hives on grass, both about 13 blocks from the Apiary and too far apart to share
	 * flowers; each ends with four or more flowers near it.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaBee1123cTwoHives"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaBee1123cTwoHives")
	public void twoHivesEachGetTheirOwnFourFlowers(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 7), new BlockPos(21, 1, 21))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		BlockPos a = new BlockPos(5, 3, 14);
		BlockPos b = new BlockPos(15, 3, 5);
		hive(helper, a, 0, true);
		hive(helper, b, 0, true);
		beekeeper(helper, new ItemStack(Items.DANDELION, 16));
		helper.succeedWhen(() -> {
			int na = flowersNear(helper, a);
			int nb = flowersNear(helper, b);
			helper.assertTrue(na >= 4 && nb >= 4, "flowers near the hives: " + na + " and " + nb);
		});
	}

	// --- Breeding ----------------------------------------------------------------------------------------------------

	/**
	 * "feed pairs of bees flowers to breed until there are three bees a hive": two bees by a flowered hive and
	 * dandelions in the chest become three (a baby is born), and 600 ticks later still three.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaBee1123cBreed"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaBee1123cBreed")
	public void twoBeesAreBredToThreeAndNoMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		hive(helper, HIVE, 0, true);
		fourFlowers(helper, HIVE);
		bees(helper, HIVE, 2);
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		AtomicLong third = new AtomicLong(-1);
		AtomicInteger most = new AtomicInteger();
		helper.onEachTick(() -> {
			int n = beeCount(helper, HIVE);
			most.set(Math.max(most.get(), n));
			if (third.get() < 0 && n >= 3) {
				third.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(third.get() >= 0, "two bees were never bred: " + beeCount(helper, HIVE) + " bees");
			helper.assertTrue(helper.getTick() >= third.get() + 600, "watching for more bees");
			helper.assertTrue(most.get() == 3 && beeCount(helper, HIVE) == 3, "bees after breeding: now " + beeCount(helper, HIVE) + ", most " + most.get());
			clearBees(helper);
		});
	}

	/** "pairs of bees": one bee alone by a flowered hive is never bred and no flower is wasted on it. */
	//$ gametest_ticks_batch AREA '1400' '"qaBee1123cOneBee"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaBee1123cOneBee")
	public void oneBeeAloneIsNotFed(GameTestHelper helper) {
		Leftovers.clear(helper);
		grass(helper);
		hive(helper, HIVE, 0, true);
		fourFlowers(helper, HIVE);
		Bee bee = bees(helper, HIVE, 1).get(0);
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		Container chest = helper.getBlockEntity(CHEST);
		AtomicInteger loved = new AtomicInteger();
		helper.onEachTick(() -> loved.addAndGet(bee.isInLove() ? 1 : 0));
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(beeCount(helper, HIVE) == 1 && babies(helper) == 0, "one bee bred: " + beeCount(helper, HIVE) + " bees");
			helper.assertTrue(loved.get() == 0 && chest.countItem(Items.DANDELION) == 8,
				"a flower fed to a lone bee: in love for " + loved.get() + " ticks, " + chest.countItem(Items.DANDELION) + " dandelions left");
			clearBees(helper);
			helper.succeed();
		});
	}

	// --- Range -------------------------------------------------------------------------------------------------------

	/** "the beehives and bee nests within 16 blocks": a full hive exactly 16 blocks from his Apiary is harvested. */
	//$ gametest_ticks_batch HUGE '2400' '"qaBee1123cAt16"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaBee1123cAt16")
	public void aHiveSixteenBlocksAwayIsHarvested(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos hive = APIARY.offset(16, 0, 0);
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1 && helper.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL) == 0,
			"the hive 16 blocks away: honey level " + helper.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL) + ", honey bottles " + chest.countItem(Items.HONEY_BOTTLE)));
	}

	/** The other side: a full hive 17 blocks from his Apiary is left alone for 1500 ticks. */
	//$ gametest_ticks_batch HUGE '1700' '"qaBee1123cAt17"'
	@GameTest(template = HUGE, timeoutTicks = 1700, batch = "qaBee1123cAt17")
	public void aHiveSeventeenBlocksAwayIsLeftAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos hive = APIARY.offset(17, 0, 0);
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		Container chest = helper.getBlockEntity(CHEST);
		helper.runAfterDelay(1500, () -> {
			helper.assertTrue(helper.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL) == 5 && chest.countItem(Items.HONEY_BOTTLE) == 0,
				"the hive 17 blocks away was harvested: honey bottles " + chest.countItem(Items.HONEY_BOTTLE));
			helper.succeed();
		});
	}

	// --- Calm bees ---------------------------------------------------------------------------------------------------

	/**
	 * "Put a lit campfire under a hive and the bees stay calm": a full hive with two bees in it over a lit campfire is
	 * harvested; no bee is ever angry and the Beekeeper keeps all his health. (That the bees also stay in the hive, as the
	 * README's "otherwise they fly out" says, is QaBeekeeperFlowers1123cBugTests: they come out today.)
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaBee1123cCalm"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBee1123cCalm")
	public void aLitCampfireKeepsTheBeesCalm(GameTestHelper helper) {
		Leftovers.clear(helper);
		hive(helper, HIVE, 5, true);
		occupy(helper, HIVE, 2);
		Villager keeper = beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		HarvestWatch watch = watch(helper, HIVE, keeper);
		helper.succeedWhen(() -> {
			helper.assertTrue(watch.harvestedAt.get() >= 0, "the hive was never harvested");
			helper.assertTrue(helper.getTick() >= watch.harvestedAt.get() + 200, "watching the bees");
			helper.assertTrue(watch.angryTicks.get() == 0, "a bee was angry for " + watch.angryTicks.get() + " ticks over a lit campfire");
			helper.assertTrue(keeper.getHealth() == keeper.getMaxHealth(), "the Beekeeper was hurt: " + keeper.getHealth());
			clearBees(helper);
		});
	}

	/**
	 * "otherwise they fly out when it's harvested (they don't go for villagers)": no campfire, two bees in a full hive;
	 * on the harvest both come out, and for 300 ticks after no bee targets or stings the Beekeeper.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaBee1123cNoFire"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBee1123cNoFire")
	public void withoutACampfireTheBeesFlyOutButLeaveTheBeekeeperAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		hive(helper, HIVE, 5, false);
		occupy(helper, HIVE, 2);
		Villager keeper = beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		HarvestWatch watch = watch(helper, HIVE, keeper);
		helper.succeedWhen(() -> calmButOut(helper, watch, keeper, 300));
	}

	/** A campfire that isn't lit is no campfire (the spec says a lit one): the bees fly out, and leave him alone. */
	//$ gametest_ticks_batch AREA '1800' '"qaBee1123cUnlit"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBee1123cUnlit")
	public void anUnlitCampfireLetsTheBeesOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		hive(helper, HIVE, 5, false);
		helper.setBlock(HIVE.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		occupy(helper, HIVE, 2);
		Villager keeper = beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		HarvestWatch watch = watch(helper, HIVE, keeper);
		helper.succeedWhen(() -> calmButOut(helper, watch, keeper, 200));
	}

	// --- Helpers -----------------------------------------------------------------------------------------------------

	private static void calmButOut(GameTestHelper helper, HarvestWatch watch, Villager keeper, int after) {
		helper.assertTrue(watch.harvestedAt.get() >= 0, "the hive was never harvested");
		helper.assertTrue(watch.occupantsAfter.get() == 0, "bees stayed in an unsedated hive on the harvest: " + watch.occupantsAfter.get());
		helper.assertTrue(helper.getTick() >= watch.harvestedAt.get() + after, "watching the bees");
		helper.assertTrue(watch.targetTicks.get() == 0, "a bee went for the Beekeeper for " + watch.targetTicks.get() + " ticks");
		helper.assertTrue(keeper.isAlive() && keeper.getHealth() == keeper.getMaxHealth(), "the Beekeeper was stung: " + keeper.getHealth());
		clearBees(helper);
	}

	/** Watches the hive's honey level drop to 0 and records the bees still inside at that tick and any anger after. */
	private static HarvestWatch watch(GameTestHelper helper, BlockPos hive, Villager keeper) {
		HarvestWatch w = new HarvestWatch();
		helper.onEachTick(() -> {
			BlockState s = helper.getLevel().getBlockState(helper.absolutePos(hive));
			if (w.harvestedAt.get() < 0 && s.is(Blocks.BEEHIVE) && s.getValue(BeehiveBlock.HONEY_LEVEL) == 0) {
				w.harvestedAt.set(helper.getTick());
				w.occupantsAfter.set(((BeehiveBlockEntity) helper.getBlockEntity(hive)).getOccupantCount());
			}
			for (Bee b : allBees(helper)) {
				w.angryTicks.addAndGet(b.isAngry() ? 1 : 0);
				w.targetTicks.addAndGet(b.getTarget() == keeper ? 1 : 0);
			}
		});
		return w;
	}

	private static final class HarvestWatch {
		final AtomicLong harvestedAt = new AtomicLong(-1);
		final AtomicInteger occupantsAfter = new AtomicInteger(-1);
		final AtomicInteger angryTicks = new AtomicInteger();
		final AtomicInteger targetTicks = new AtomicInteger();
	}

	private static Villager beekeeper(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(APIARY, ModBlocks.APIARY);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(APIARY), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		helper.assertTrue(BeekeeperWork.isBeekeeper(villager), "setup: not a beekeeper");
		return villager;
	}

	/** Grass over the floor, apart from the corner where the Apiary and chest stand. */
	private static void grass(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 1, 4), new BlockPos(20, 1, 20))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
	}

	private static void hive(GameTestHelper helper, BlockPos hive, int honey, boolean litCampfire) {
		helper.setBlock(hive.below(), litCampfire ? Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true) : Blocks.GRASS_BLOCK.defaultBlockState());
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, honey));
	}

	/** Four poppies on the grass right round the hive (two blocks out on each side). */
	private static void fourFlowers(GameTestHelper helper, BlockPos hive) {
		for (BlockPos p : List.of(hive.offset(-2, -1, 0), hive.offset(2, -1, 0), hive.offset(0, -1, 2), hive.offset(0, -1, -2))) {
			helper.setBlock(p, Blocks.POPPY);
		}
	}

	/** Adult bees flying by the hive, which is their home. */
	private static List<Bee> bees(GameTestHelper helper, BlockPos hive, int n) {
		List<Bee> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Bee bee = helper.spawn(EntityType.BEE, hive.offset(i - 1, 2, 1));
			bee.setAge(0);
			bee.setHivePos(helper.absolutePos(hive));
			bee.setPersistenceRequired();
			out.add(bee);
		}
		return out;
	}

	/**
	 * Puts {@code n} adult bees inside the hive, settled in for far longer than the test (so they come out only when
	 * something sends them out, not on their own after vanilla's 600 ticks).
	 */
	private static void occupy(GameTestHelper helper, BlockPos hive, int n) {
		BeehiveBlockEntity be = helper.getBlockEntity(hive);
		for (Bee bee : bees(helper, hive, n)) {
			BeehiveBlockEntity.Occupant o = BeehiveBlockEntity.Occupant.of(bee);
			be.storeBee(new BeehiveBlockEntity.Occupant(o.entityData(), 0, 100000));
			bee.discard();
		}
		helper.assertTrue(be.getOccupantCount() == n, "setup: " + be.getOccupantCount() + " bees in the hive");
	}

	/** Bees that leave the test (released, or out foraging) would fly into later ones and be counted there. */
	private static void clearBees(GameTestHelper helper) {
		for (Bee b : helper.getLevel().getEntitiesOfClass(Bee.class, helper.getBounds().inflate(48))) {
			b.discard();
		}
	}

	private static List<Bee> allBees(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Bee.class, helper.getBounds().inflate(20), Bee::isAlive);
	}

	/** The bees flying round the test plus those in the hive. */
	private static int beeCount(GameTestHelper helper, BlockPos hive) {
		BeehiveBlockEntity be = helper.getBlockEntity(hive);
		return allBees(helper).size() + be.getOccupantCount();
	}

	private static int babies(GameTestHelper helper) {
		int n = 0;
		for (Bee b : allBees(helper)) {
			n += b.isBaby() ? 1 : 0;
		}
		return n;
	}

	private static int flowers(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(21, 10, 21))) {
			n += helper.getBlockState(p).is(BlockTags.FLOWERS) ? 1 : 0;
		}
		return n;
	}

	/** Flowers within four blocks of the hive sideways, from three below to two above (a player's "near"). */
	private static int flowersNear(GameTestHelper helper, BlockPos hive) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(hive.offset(-4, -3, -4), hive.offset(4, 2, 4))) {
			n += helper.getBlockState(p).is(BlockTags.FLOWERS) ? 1 : 0;
		}
		return n;
	}

	/** What the Beekeeper carries in his work bag. */
	private static final class ModAttachmentsView {
		static int bagDandelions(Villager v) {
			return io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.DANDELION);
		}
	}
}
