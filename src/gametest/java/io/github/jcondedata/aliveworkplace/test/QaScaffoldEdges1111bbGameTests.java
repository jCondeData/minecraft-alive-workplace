package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1111-1837 tester) for scaffolding (ROADMAP 35.6), written from the spec: "the scaffolding going back into
 * the chests" when the chest a player keeps stuffing has no room left, a pillar placed turned and mirrored (the
 * scaffold columns must follow the turned drawing and stay off it), and the scaffolding split over two supply chests
 * ("from the supply chests beside it", plural). Same 14-high test pillar as {@link QaScaffoldingGameTests}, whose
 * helpers these reuse.
 */
public class QaScaffoldEdges1111bbGameTests {
	static final String BIG = QaScaffoldingGameTests.BIG;
	static final BlockPos CHEST = QaScaffoldingGameTests.CHEST;
	static final BlockPos CHEST2 = new BlockPos(5, 2, 3);
	static final int SCAFFOLDING = 64;

	/**
	 * A player keeps every free slot of the supply chest stuffed with cobblestone from the moment the first scaffolds
	 * stand (so when they come down there's no room for them in the chest). Spec: scaffolds are taken down at the end
	 * of the stage and go back to the chests; nothing may be lost. The build ends, nothing is left standing, and the
	 * chest, the ground and the builder's bag hold all 64 between them.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldFullChest1111bb"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldFullChest1111bb")
	public void aChestStuffedFullLosesNoScaffolding(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING);
		boolean[] stuffing = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			stuffing[0] |= s.site().scaffolds().size() >= 2;
			if (stuffing[0] && BuildSiteManager.get(s.level()).get(s.site().id()) != null
				&& helper.getLevel().getBlockEntity(helper.absolutePos(CHEST)) instanceof Container chest) {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (chest.getItem(i).isEmpty()) {
						chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stuffing[0], "no scaffolding ever went up, so the chest was never stuffed");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
				+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds().size());
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int chest = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
			int ground = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			int bag = QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(chest + ground + bag == SCAFFOLDING, "with the chest stuffed full, " + (chest + ground + bag)
				+ " scaffolding came back, not " + SCAFFOLDING + " (chest " + chest + ", ground " + ground + ", bag " + bag
				+ ", most standing at once " + most[0] + ")");
		});
	}

	/**
	 * The pillar placed turned 180 degrees and mirrored left-right. Spec: scaffolds go "only where the drawing leaves air
	 * or nothing", every one is taken down at the end of its stage and goes back to the chests. Every tick no scaffold
	 * stands where the turned drawing puts a block; the build ends whole with nothing skipped, nothing standing, and
	 * all 64 back.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldTurned1111bb"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldTurned1111bb")
	public void aTurnedAndMirroredPillarIsScaffoldedOffItsDrawing(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = start(helper, new BlockPos(11, 2, 9), Rotation.CLOCKWISE_180, Mirror.LEFT_RIGHT);
		QaScaffoldingGameTests.fill(helper, SCAFFOLDING);
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			for (BlockPos p : s.site().scaffolds()) {
				if (s.plan().wantsBlockAt(p)) {
					throw new GameTestAssertException("a scaffold is recorded at " + helper.relativePos(p) + " where the turned drawing puts "
						+ s.plan().targets().get(p));
				}
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up on the turned pillar");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped on the turned pillar");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int total = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			helper.assertTrue(total == SCAFFOLDING, total + " scaffolding back after the turned pillar, not " + SCAFFOLDING);
		});
	}

	/**
	 * The 64 scaffolding split over two supply chests beside the bench, 32 each (the main chest also holds the stone).
	 * The build ends whole, nothing stands, and the two chests, the ground and the bag hold all 64 between them.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldTwoChests1111bb"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldTwoChests1111bb")
	public void scaffoldingSplitOverTwoChestsAllComesBack(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING / 2);
		helper.setBlock(CHEST2, Blocks.CHEST);
		Container second = helper.getBlockEntity(CHEST2);
		second.setItem(0, new ItemStack(Items.SCAFFOLDING, SCAFFOLDING / 2));
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], s.site().scaffolds().size()));
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int one = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING);
			int two = second.countItem(Items.SCAFFOLDING);
			int ground = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			int bag = QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(one + two + ground + bag == SCAFFOLDING, (one + two + ground + bag) + " scaffolding back from two chests, not "
				+ SCAFFOLDING + " (chest " + one + ", second chest " + two + ", ground " + ground + ", bag " + bag + ", skipped "
				+ s.site().skipped() + ")");
		});
	}

	/**
	 * The only supply chest is broken (its contents spill) while scaffolds stand, and the build carries on. Spec: the
	 * scaffolds come down at the end of the stage and go back to the chests; with no chest left they must still not
	 * vanish. The build ends (it never waits on scaffolding), nothing is left standing, and the ground, the bag and any
	 * chest hold all 64.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldNoChest1111bb"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldNoChest1111bb")
	public void theChestBrokenMidColumnLosesNoScaffolding(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING);
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && s.site().scaffolds().size() >= 3) {
				broken[0] = true;
				s.level().destroyBlock(helper.absolutePos(CHEST), true);
			}
			if (broken[0]) {
				// The stone the pillar needs, handed over again as a new chest a little further off (no scaffolding in it).
				BlockPos other = new BlockPos(1, 2, 5);
				if (!(helper.getLevel().getBlockEntity(helper.absolutePos(other)) instanceof Container)) {
					helper.setBlock(other, Blocks.CHEST);
					Container chest = helper.getBlockEntity(other);
					chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
					chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "no scaffolding ever went up, so the chest was never broken");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
				+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds().size());
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int ground = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			int bag = QaScaffoldingGameTests.bagCount(s.builder());
			Container other = helper.getBlockEntity(new BlockPos(1, 2, 5));
			int chest = other.countItem(Items.SCAFFOLDING);
			helper.assertTrue(ground + bag + chest == SCAFFOLDING, "after the chest broke, " + (ground + bag + chest)
				+ " scaffolding is left, not " + SCAFFOLDING + " (ground " + ground + ", bag " + bag + ", new chest " + chest + ")");
		});
	}

	/**
	 * The builder's bag is full (every slot taken by other things, as after a long haul) while it takes its scaffolding
	 * down. Spec: each scaffold taken down goes back to the chests, nothing lost. From the first scaffold that comes
	 * down, every free bag slot is filled once with cobblestone (an empty second chest gives the builder room to unload
	 * it), and until the last comes down any scaffolding in the bag is lifted out and counted (so none can stack onto
	 * it). The build ends, nothing stands, and lifted + chests + ground + bag is all 64.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldFullBag1111bb"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldFullBag1111bb")
	public void aFullBagWhileTakingDownLosesNoScaffolding(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING);
		helper.setBlock(CHEST2, Blocks.CHEST); // room for the cobblestone when the builder empties its bag
		int[] last = {0};
		int[] lifted = {0};
		int[] stuffed = {0};
		boolean[] takingDown = {false};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			int now = s.site().scaffolds().size();
			takingDown[0] |= now < last[0];
			last[0] = now;
			var bag = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.get(s.builder());
			if (bag == null || !takingDown[0] || done[0]) {
				return;
			}
			if (now > 0) {
				lifted[0] += bag.remove(Items.SCAFFOLDING, bag.count(Items.SCAFFOLDING));
				if (stuffed[0] == 0) {
					for (int i = 0; i < 64 && bag.freeSlots() > 0; i++) {
						bag.add(new ItemStack(Items.COBBLESTONE, 64));
						stuffed[0] += 64;
					}
				}
			} else {
				done[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(takingDown[0] && stuffed[0] > 0, "the bag was never stuffed while scaffolding came down");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
				+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds().size());
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int chest = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING)
				+ ((Container) helper.getBlockEntity(CHEST2)).countItem(Items.SCAFFOLDING);
			int ground = QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
			int bag = QaScaffoldingGameTests.bagCount(s.builder());
			int total = lifted[0] + chest + ground + bag;
			helper.assertTrue(total == SCAFFOLDING, "with a full bag, " + total + " scaffolding is left, not " + SCAFFOLDING
				+ " (lifted " + lifted[0] + ", chest " + chest + ", ground " + ground + ", bag " + bag + ")");
		});
	}

	static QaScaffoldingGameTests.Site start(GameTestHelper helper, BlockPos origin, Rotation rotation, Mirror mirror) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(QaScaffoldingGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, QaScaffoldingGameTests.VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(QaScaffoldingGameTests.BENCH));
		BuildSite site = Builders.start(level, villager, null, QaScaffoldingGameTests.PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, mirror));
		if (site == null) {
			throw new GameTestAssertException("the turned pillar didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + QaScaffoldingGameTests.PILLAR);
		}
		return new QaScaffoldingGameTests.Site(level, villager, site, plan);
	}
}
