package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane tester, 2026-10-25) for scaffolding (ROADMAP 35.6, 35.6a), written from the spec and the wiki
 * (docs/wiki/scaffolding.md), not from the code: too little scaffolding, free materials, the drawing's blocks and the
 * end of each stage, the chest broken mid-build, a two-builder crew. Same 14-high test pillar as {@link ScaffoldingGameTests}.
 */
public class QaScaffoldingGameTests {
	static final String BIG = "aliveworkplace_test:big_area";
	static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	static final BlockPos BENCH = new BlockPos(3, 2, 3);
	static final BlockPos CHEST = new BlockPos(3, 2, 5);
	static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	static final BlockPos HELPER = new BlockPos(2, 2, 2);
	static final BlockPos BENCH2 = new BlockPos(1, 2, 1);
	static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	static final int SCAFFOLDING = 64;

	record Site(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan) {
	}

	/**
	 * Wiki: "With too little on hand it hops as it always did". Three scaffolds in the chest, far fewer than any column
	 * up the pillar needs: the build still ends without waiting, no scaffolding is left standing anywhere, and the chest
	 * ends with its three (none lost in a half-built column, none made).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldTooLittle"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldTooLittle")
	public void tooLittleScaffoldingIsNeitherLostNorLeftStanding(GameTestHelper helper) {
		Site s = pillar(helper, 3);
		boolean[] waited = {false};
		helper.onEachTick(() -> waited[0] |= s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
				+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds());
			helper.assertFalse(waited[0], "the build waited for materials with 3 scaffolding in the chest");
			assertNoScaffolding(helper);
			int total = chestCount(helper, Items.SCAFFOLDING) + loose(helper, Items.SCAFFOLDING);
			helper.assertTrue(total == 3, "3 scaffolding went in, " + total + " came out (chest " + chestCount(helper, Items.SCAFFOLDING) + ")");
		});
	}

	/**
	 * Wiki, Known limits: "With workplaceFreeMaterials on, scaffolding is put up and taken down for nothing (none taken
	 * from or added to the chests)". With free materials and no scaffolding at all, the pillar is scaffolded and finished
	 * with nothing skipped, and afterwards no scaffolding item exists anywhere: not in the chest, not on the ground.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaScaffoldFree"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaScaffoldFree")
	public void freeMaterialsScaffoldWithoutMakingAny(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(was, level.getServer()));
		Site s = start(helper, PILLAR, PILLAR_AT, null);
		helper.setBlock(CHEST, Blocks.CHEST);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], s.site().scaffolds().size()));
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped with free materials");
			helper.assertTrue(most[0] > 0, "no scaffolding went up with free materials");
			assertNoScaffolding(helper);
			int made = chestCount(helper, Items.SCAFFOLDING) + loose(helper, Items.SCAFFOLDING) + bagCount(s.builder());
			helper.assertTrue(made == 0, made + " scaffolding appeared out of nothing with free materials (chest "
				+ chestCount(helper, Items.SCAFFOLDING) + ", ground " + loose(helper, Items.SCAFFOLDING) + ")");
		});
	}

	/**
	 * Spec: scaffolding goes "only where the drawing leaves air or nothing" and is "taken down when the stage is done".
	 * Every tick: no scaffold stands where the drawing puts a block, and whenever the build moves to its next stage none
	 * of the previous stage's scaffolding is still recorded or standing.
	 */
	//$ gametest_ticks_batch BIG '8000' '"qaScaffoldDrawing"'
	@GameTest(template = BIG, timeoutTicks = 8000, batch = "qaScaffoldDrawing")
	public void scaffoldingNeverStandsOnTheDrawingNorOutlivesItsStage(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		BuildPlan.Stage[] last = {s.site().stage()};
		int[] most = {0};
		int[] stages = {0};
		helper.onEachTick(() -> {
			List<BlockPos> up = s.site().scaffolds();
			most[0] = Math.max(most[0], up.size());
			for (BlockPos p : up) {
				if (s.plan().wantsBlockAt(p)) {
					throw new GameTestAssertException("scaffolding recorded where the drawing puts a block: " + helper.relativePos(p));
				}
			}
			BuildPlan.Stage now = s.site().stage();
			if (now != last[0]) {
				stages[0]++;
				if (!up.isEmpty()) {
					throw new GameTestAssertException("the stage went from " + last[0] + " to " + now + " with " + up.size() + " scaffold(s) still up: "
						+ up.stream().map(helper::relativePos).toList());
				}
				last[0] = now;
			}
		});
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			helper.assertTrue(stages[0] >= 1, "the build never changed stage while watched");
			assertNoScaffolding(helper);
		});
	}

	/**
	 * The supply chest is broken (its contents spill) while scaffolding is up, then the build is cancelled. Wiki: "taken
	 * down at once, into the supply chests by its bench (else dropped there)". Every one of the 64 scaffolds ends on
	 * the ground near the bench (no chest left), none standing, none lost or duplicated.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaScaffoldChestGone"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaScaffoldChestGone")
	public void chestBrokenThenCancelLosesNoScaffolding(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		long[] brokenAt = {-1};
		boolean[] cancelled = {false};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && s.site().scaffolds().size() >= 3) {
				brokenAt[0] = helper.getTick();
				s.level().destroyBlock(helper.absolutePos(CHEST), true);
			} else if (brokenAt[0] >= 0 && !cancelled[0] && helper.getTick() >= brokenAt[0] + 20) {
				cancelled[0] = true;
				helper.assertTrue(!s.site().scaffolds().isEmpty(), "the scaffolding came down before the cancel");
				Builders.cancel(s.level(), s.site());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cancelled[0], "never cancelled: scaffolds " + s.site().scaffolds().size());
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "the build wasn't cancelled");
			assertNoScaffolding(helper);
			int total = loose(helper, Items.SCAFFOLDING) + bagCount(s.builder());
			helper.assertTrue(total == SCAFFOLDING, total + " scaffolding left after the chest broke and the cancel (ground "
				+ loose(helper, Items.SCAFFOLDING) + ", bag " + bagCount(s.builder()) + "), not " + SCAFFOLDING);
		});
	}

	/**
	 * Two builders on the pillar (the lead and a helper, workplaceBuildersHelp on). Wiki: "only the lead builder does it
	 * (helpers leave such blocks to the lead)". The pillar is finished with nothing skipped, nobody gets hurt (a helper
	 * on a column the lead takes down would fall), no scaffolding is left and the chest is as full as before.
	 */
	//$ gametest_ticks_batch BIG '10000' '"qaScaffoldCrew"'
	@GameTest(template = BIG, timeoutTicks = 10000, batch = "qaScaffoldCrew")
	public void twoBuildersFinishThePillarAndPutEveryScaffoldBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager second = null;
		Site s = start(helper, PILLAR, PILLAR_AT, null);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer()));
		fill(helper, SCAFFOLDING);
		helper.setBlock(BENCH2, ModBlocks.BUILDERS_BENCH);
		second = helper.spawn(EntityType.VILLAGER, HELPER);
		Builders.employ(level, second, helper.absolutePos(BENCH2));
		Villager mate = second;
		boolean[] helped = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			helped[0] |= Builders.isHelping(mate) || s.site().helpers(level.getGameTime()).contains(mate.getUUID());
			most[0] = Math.max(most[0], s.site().scaffolds().size());
		});
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(helped[0], "the second builder never helped (job " + io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.get(mate) + ", bench " + Builders.benchPos(mate) + ")");
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped with a crew");
			for (Villager v : List.of(s.builder(), mate)) {
				helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "a builder got hurt: " + v.getHealth());
			}
			assertNoScaffolding(helper);
			int total = chestCount(helper, Items.SCAFFOLDING) + loose(helper, Items.SCAFFOLDING);
			helper.assertTrue(total == SCAFFOLDING, "the chest and ground hold " + total + " scaffolding, not " + SCAFFOLDING
				+ " (chest " + chestCount(helper, Items.SCAFFOLDING) + ")");
		});
	}

	/**
	 * A player empties the scaffolding out of the chest while the builder's column is going up. Spec: a build never
	 * waits on scaffolding, and whatever it put up comes down at the end of the stage and goes back to the chests. The
	 * build ends, nothing is left standing, and what the player took plus what the chest holds afterwards is all 64.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScaffoldEmptied"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScaffoldEmptied")
	public void chestEmptiedMidColumnLosesNoScaffolding(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		int[] taken = {-1};
		helper.onEachTick(() -> {
			if (taken[0] < 0 && s.site().scaffolds().size() >= 2) {
				Container chest = helper.getBlockEntity(CHEST);
				taken[0] = 0;
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (chest.getItem(i).is(Items.SCAFFOLDING)) {
						taken[0] += chest.removeItemNoUpdate(i).getCount();
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(taken[0] >= 0, "the chest was never emptied");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
				+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds());
			assertNoScaffolding(helper);
			int total = taken[0] + chestCount(helper, Items.SCAFFOLDING) + loose(helper, Items.SCAFFOLDING) + bagCount(s.builder());
			helper.assertTrue(total == SCAFFOLDING, "the player took " + taken[0] + ", the chest holds " + chestCount(helper, Items.SCAFFOLDING)
				+ ", the ground " + loose(helper, Items.SCAFFOLDING) + ", the bag " + bagCount(s.builder()) + ": " + total + ", not " + SCAFFOLDING);
		});
	}

	// --- helpers -----------------------------------------------------------------------------

	static Site pillar(GameTestHelper helper, int scaffolding) {
		Site s = start(helper, PILLAR, PILLAR_AT, null);
		fill(helper, scaffolding);
		return s;
	}

	static void fill(GameTestHelper helper, int scaffolding) {
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
		chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
		if (scaffolding > 0) {
			chest.setItem(2, new ItemStack(Items.SCAFFOLDING, scaffolding));
		}
	}

	static Site start(GameTestHelper helper, ResourceLocation structure, BlockPos origin, Object unused) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the build of " + structure + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Site(level, villager, site, plan);
	}

	static void assertFinished(GameTestHelper helper, Site s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
			+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds().size()
			+ " builder at " + helper.relativePos(s.builder().blockPosition()));
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong, e.g. "
			+ unfinished.stream().limit(4).map(p -> helper.relativePos(p) + "=" + s.level().getBlockState(p)).toList());
	}

	/** Not a scaffold standing in the test's area, up to 29 high (the pillar and its cap fit inside 21 x 21). */
	static void assertNoScaffolding(GameTestHelper helper) {
		List<BlockPos> left = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(21, 29, 21)))) {
			if (helper.getLevel().getBlockState(p).is(Blocks.SCAFFOLDING)) {
				left.add(helper.relativePos(p));
			}
		}
		if (!left.isEmpty()) {
			throw new GameTestAssertException("scaffolding left standing at " + left);
		}
	}

	static int chestCount(GameTestHelper helper, Item item) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CHEST)) instanceof Container chest)) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			n += stack.is(item) ? stack.getCount() : 0;
		}
		return n;
	}

	/** Items of {@code item} lying anywhere in or just around the test area. */
	static int loose(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(new BlockPos(-4, -2, -4))).expandTowards(30, 34, 30);
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			n += e.getItem().is(item) ? e.getItem().getCount() : 0;
		}
		return n;
	}

	/** Scaffolding a builder still carries in its bag. */
	static int bagCount(Villager v) {
		io.github.jcondedata.aliveworkplace.build.BuilderBag bag = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.get(v);
		return bag == null ? 0 : bag.count(Items.SCAFFOLDING);
	}

	/** The requests the builder is showing right now, for the board. */
	static List<Requests.Request> board(Site s) {
		return Requests.of(s.level(), s.builder());
	}
}
