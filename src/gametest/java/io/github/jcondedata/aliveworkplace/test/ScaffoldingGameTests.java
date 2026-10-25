package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Scaffolding (ROADMAP 35.6): a builder puts up scaffolding where a step has nowhere to stand within reach, works from
 * its top, and takes it down when the stage is done, the scaffolding going back to the chests. The test pillar is 14
 * blocks high with a 5 x 5 cap overhanging it by two: nothing near its top or under its cap can be reached from the
 * ground, and nothing there can be stood on.
 */
public class ScaffoldingGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder_drawn/test_folly");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	private static final int SCAFFOLDING = 64;

	private record Site(ServerLevel level, Villager[] builder, BuildSite site, BuildPlan plan) {
	}

	/** The pillar is built whole from scaffolding, which is all down again afterwards and back in the chest. */
	//$ gametest_ticks_batch BIG '8000' '"scaffoldPillar"'
	@GameTest(template = BIG, timeoutTicks = 8000, batch = "scaffoldPillar")
	public void builderScaffoldsUpAPillarAndItsOverhangingCap(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], s.site().scaffolds().size()));
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding was put up");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped");
			assertNoScaffolding(helper, s);
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING,
				"the chest holds " + chestCount(helper, Items.SCAFFOLDING) + " scaffolding, not the " + SCAFFOLDING + " it had");
			helper.assertTrue(s.builder()[0].isAlive() && s.builder()[0].getHealth() >= s.builder()[0].getMaxHealth(),
				"the builder got hurt: " + s.builder()[0].getHealth());
		});
	}

	/** Cancelled with scaffolds up: they come straight down, into the chest, and the builder isn't left up there. */
	//$ gametest_ticks_batch BIG '6000' '"scaffoldCancel"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "scaffoldCancel")
	public void cancellingTakesTheScaffoldingDown(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		int[] cancelledWith = {0};
		helper.onEachTick(() -> {
			if (cancelledWith[0] == 0 && s.site().scaffolds().size() >= 2) {
				cancelledWith[0] = s.site().scaffolds().size();
				Builders.cancel(s.level(), s.site());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cancelledWith[0] > 0, "no scaffolding up yet: " + s.site().scaffolds().size());
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "the build wasn't cancelled");
			assertNoScaffolding(helper, s);
			helper.assertTrue(s.site().scaffolds().isEmpty(), "the site still lists scaffolds: " + s.site().scaffolds());
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING,
				"the chest holds " + chestCount(helper, Items.SCAFFOLDING) + " scaffolding after the cancel, not " + SCAFFOLDING);
			Villager v = s.builder()[0];
			helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "the builder got hurt: " + v.getHealth());
		});
	}

	/**
	 * QA (lane-b-1025-0937), the wiki (scaffolding.md): "A site removed with scaffolds still recorded (canceled, its
	 * builder died, ...) has them all taken down at once, into the supply chests by its bench (else dropped there)". The
	 * builder killed with scaffolds up: none is left standing, the site is gone, and every one of the 64 scaffolds is in
	 * the chest or lying on the ground (what was in his bag drops where he fell), none lost.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaScaffoldBuilderDies"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaScaffoldBuilderDies")
	public void theBuilderDyingWithScaffoldsUpTakesThemAllDown(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		int[] killedWith = {0};
		helper.onEachTick(() -> {
			if (killedWith[0] == 0 && s.site().scaffolds().size() >= 2) {
				killedWith[0] = s.site().scaffolds().size();
				s.builder()[0].kill();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killedWith[0] > 0, "no scaffolding up yet: " + s.site().scaffolds().size());
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "the dead builder's site is still there");
			assertNoScaffolding(helper, s);
			int loose = 0;
			for (net.minecraft.world.entity.item.ItemEntity item : s.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(32, 32, 32))) {
				loose += item.getItem().is(Items.SCAFFOLDING) ? item.getItem().getCount() : 0;
			}
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) + loose == SCAFFOLDING, "scaffolding after the builder died: "
				+ chestCount(helper, Items.SCAFFOLDING) + " in the chest and " + loose + " on the ground, not " + SCAFFOLDING + " (killed with "
				+ killedWith[0] + " up)");
		});
	}

	/**
	 * QA (lane-b-1025-0937), the wiki: "Scaffolding someone else broke is simply struck off the list." A player breaks the
	 * foot of the builder's column while it's up (the column above falls, as vanilla scaffolding does): the build still
	 * finishes whole and no scaffolding is left standing.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaScaffoldBroken"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaScaffoldBroken")
	public void scaffoldingAPlayerBreaksIsStruckOffAndTheBuildFinishes(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && s.site().scaffolds().size() >= 3) {
				BlockPos lowest = s.site().scaffolds().stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
				broken[0] = s.level().destroyBlock(lowest, true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "no column was broken");
			assertFinished(helper, s);
			assertNoScaffolding(helper, s);
			helper.assertTrue(s.site().scaffolds().isEmpty(), "the site still lists scaffolds: " + s.site().scaffolds());
		});
	}

	/** Saved and loaded with scaffolds up (the site's list and the builder both): the build still finishes, all down. */
	//$ gametest_ticks_batch BIG '9000' '"scaffoldReload"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "scaffoldReload")
	public void reloadWithScaffoldsUpFinishesTheBuild(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && s.site().scaffolds().size() >= 2) {
				reloaded[0] = true;
				CompoundTag saved = s.site().save();
				BuildSite back = BuildSite.load(saved);
				helper.assertTrue(back != null && back.scaffolds().equals(s.site().scaffolds()),
					"the scaffolds didn't survive a save: " + (back == null ? null : back.scaffolds()) + " vs " + s.site().scaffolds());
				// The builder comes back from its save as a new entity, with nothing of the column it was putting up.
				Villager old = s.builder()[0];
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				s.level().addFreshEntity(loaded);
				s.builder()[0] = (Villager) loaded;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "no scaffolding up to reload with");
			assertFinished(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped after the reload");
			assertNoScaffolding(helper, s);
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING,
				"the chest holds " + chestCount(helper, Items.SCAFFOLDING) + " scaffolding, not " + SCAFFOLDING);
		});
	}

	/**
	 * 35.6a (the owner: in the shots no scaffolding showed, the builder stood on the cap and the pillar had a gap): while
	 * the builder works high up, a continuous column of vanilla scaffolding stands beside the pillar from the ground up,
	 * the builder is up it at the working height, never on the pillar or its cap, and the pillar always rises from the
	 * ground without a gap. Afterwards it's all down.
	 */
	//$ gametest_ticks_batch BIG '8000' '"scaffoldColumn"'
	@GameTest(template = BIG, timeoutTicks = 8000, batch = "scaffoldColumn")
	public void builderClimbsAContinuousColumnBesideThePillar(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		int ground = helper.absolutePos(PILLAR_AT).getY();
		BlockPos pillarFoot = helper.absolutePos(PILLAR_AT.offset(2, 0, 2));
		int[] most = {0};
		double[] highestOnScaffold = {Double.NEGATIVE_INFINITY};
		helper.onEachTick(() -> {
			List<BlockPos> up = s.site().scaffolds();
			most[0] = Math.max(most[0], up.size());
			for (BlockPos p : up) {
				BlockPos below = p.below();
				boolean based = s.level().getBlockState(below).is(Blocks.SCAFFOLDING) || below.getY() < ground;
				if (s.level().getBlockState(p).is(Blocks.SCAFFOLDING) && !based) {
					throw new GameTestAssertException("a gap in the scaffolding under " + helper.relativePos(p) + ": " + up.stream().map(helper::relativePos).toList());
				}
			}
			Villager v = s.builder()[0];
			BlockPos feet = v.blockPosition();
			if (v.getY() > ground + 1.5) {
				net.minecraft.world.level.block.state.BlockState under = s.level().getBlockState(feet.below());
				if (under.is(Blocks.STONE_BRICKS) || under.is(Blocks.POLISHED_ANDESITE)) {
					throw new GameTestAssertException("the builder stood on the build at " + helper.relativePos(feet) + " (on " + under + ")");
				}
				if (s.level().getBlockState(feet).is(Blocks.SCAFFOLDING) || under.is(Blocks.SCAFFOLDING)) {
					highestOnScaffold[0] = Math.max(highestOnScaffold[0], v.getY() - ground);
				}
			}
			boolean gap = false;
			for (int y = 0; y < 14; y++) {
				boolean placed = s.level().getBlockState(pillarFoot.above(y)).is(Blocks.STONE_BRICKS);
				if (placed && gap) {
					throw new GameTestAssertException("the pillar has a gap below " + helper.relativePos(pillarFoot.above(y)));
				}
				gap |= !placed;
			}
		});
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped");
			helper.assertTrue(most[0] >= 6, "the scaffolding never stood more than " + most[0] + " high");
			helper.assertTrue(highestOnScaffold[0] >= 7, "the builder was never up its scaffolding at the working height: highest " + highestOnScaffold[0]);
			assertNoScaffolding(helper, s);
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING,
				"the chest holds " + chestCount(helper, Items.SCAFFOLDING) + " scaffolding, not the " + SCAFFOLDING + " it had");
		});
	}

	/** 35.6a: saved and loaded with the builder high up its scaffolding: it carries on, and the scaffolding all comes down. */
	//$ gametest_ticks_batch BIG '9000' '"scaffoldReloadHigh"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "scaffoldReloadHigh")
	public void reloadWithTheBuilderUpTheScaffolding(GameTestHelper helper) {
		Site s = pillar(helper, SCAFFOLDING);
		int ground = helper.absolutePos(PILLAR_AT).getY();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			Villager old = s.builder()[0];
			if (!reloaded[0] && old.getY() >= ground + 5 && s.site().scaffolds().size() >= 4
				&& s.level().getBlockState(old.blockPosition().below()).is(Blocks.SCAFFOLDING)) {
				reloaded[0] = true;
				BuildSite back = BuildSite.load(s.site().save());
				helper.assertTrue(back != null && back.scaffolds().equals(s.site().scaffolds()), "the scaffolds didn't survive a save");
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				s.level().addFreshEntity(loaded);
				s.builder()[0] = (Villager) loaded;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the builder was never up the scaffolding to reload with");
			assertFinished(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped after the reload");
			assertNoScaffolding(helper, s);
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING,
				"the chest holds " + chestCount(helper, Items.SCAFFOLDING) + " scaffolding, not " + SCAFFOLDING);
			Villager v = s.builder()[0];
			helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "the builder got hurt: " + v.getHealth());
		});
	}

	/** A save from before 35.6 has no scaffolds: it loads with none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oldSitesLoadWithNoScaffolds(GameTestHelper helper) {
		BuildSite site = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Jesse", PILLAR,
			new BlueprintData.Placement(helper.getLevel().dimension().location(), BlockPos.ZERO, Rotation.NONE, Mirror.NONE));
		CompoundTag tag = site.save();
		helper.assertFalse(tag.contains("scaffolds"), "a site with no scaffolds saves a scaffolds list");
		BuildSite back = BuildSite.load(tag);
		helper.assertTrue(back != null && back.scaffolds().isEmpty(), "an old site loaded with scaffolds: " + (back == null ? null : back.scaffolds()));
		site.addScaffold(new BlockPos(1, 2, 3));
		site.addScaffold(new BlockPos(1, 3, 3));
		BuildSite two = BuildSite.load(site.save());
		helper.assertTrue(two != null && two.scaffolds().equals(List.of(new BlockPos(1, 2, 3), new BlockPos(1, 3, 3))),
			"scaffolds after a save: " + (two == null ? null : two.scaffolds()));
		helper.succeed();
	}

	/**
	 * With no scaffolding in the chests, it's asked for (the requests board shows a site's missing list), but the build
	 * never waits on it: the step goes as it always did (skipped and counted) and the build finishes.
	 */
	//$ gametest_ticks_batch BIG '12000' '"scaffoldMissing"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "scaffoldMissing")
	public void noScaffoldingInTheChestsIsAskedFor(GameTestHelper helper) {
		Site s = pillar(helper, 0);
		boolean[] asked = {false};
		boolean[] waited = {false};
		helper.onEachTick(() -> {
			asked[0] |= s.site().missing().getOrDefault(Items.SCAFFOLDING, 0) > 0;
			waited[0] |= s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
				+ " status " + s.site().status() + " missing " + s.site().missing());
			helper.assertTrue(asked[0], "scaffolding wasn't asked for: missing " + s.site().missing());
			helper.assertFalse(waited[0], "the build waited for materials (for scaffolding)");
			helper.assertTrue(s.site().skipped() > 0, "nothing was skipped without scaffolding");
			assertNoScaffolding(helper, s);
		});
	}

	/** A carpenter can make what's asked for: scaffolding from bamboo and string, by the vanilla recipe. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void carpentersMakeScaffoldingFromBambooAndString(GameTestHelper helper) {
		Crafting.Plan made = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SCAFFOLDING, 6, Map.of(Items.BAMBOO, 6L, Items.STRING, 1L));
		helper.assertTrue(made != null, "no plan for scaffolding from 6 bamboo and a string");
		helper.assertTrue(Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SCAFFOLDING, 6, Map.of(Items.BAMBOO, 6L)) == null,
			"scaffolding planned without string");
		helper.succeed();
	}

	/** Switched off: the old way, the pillar's top and cap are skipped and no scaffolding goes up. */
	//$ gametest_ticks_batch BIG '12000' '"scaffoldOff"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "scaffoldOff")
	public void switchedOffNothingIsPutUp(GameTestHelper helper) {
		boolean was = Scaffolds.ENABLED;
		Scaffolds.ENABLED = false;
		Leftovers.after(helper, () -> Scaffolds.ENABLED = was);
		Site s = pillar(helper, SCAFFOLDING);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], s.site().scaffolds().size()));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage());
			helper.assertFalse(s.plan().unfinished(s.level()).isEmpty(), "the pillar was finished without scaffolding");
			helper.assertTrue(most[0] == 0, "scaffolding went up while switched off");
			helper.assertTrue(s.site().skipped() > 0, "nothing was skipped: the pillar's top was reached without scaffolding");
			assertNoScaffolding(helper, s);
			helper.assertTrue(chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING, "the chest holds " + chestCount(helper, Items.SCAFFOLDING));
		});
	}

	/** The test folly (35.3) as an ordinary build: its dome and finial are finished, nothing skipped, the scaffolding down. */
	//$ gametest_ticks_batch TALL '40000' '"scaffoldFolly"'
	@GameTest(template = TALL, timeoutTicks = 40000, batch = "scaffoldFolly")
	public void follysDomeIsFinishedWithNoSkippedSteps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, FOLLY).orElseThrow(() -> new GameTestAssertException("no folly blueprint"));
		BlockPos origin = new BlockPos(12, 2, 12);
		BuildPlan whole = BuildPlan.create(blueprint, placement(helper, origin));
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : whole.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		stock.add(new ItemStack(Items.SCAFFOLDING, SCAFFOLDING));
		BlockPos[] barrels = {new BlockPos(1, 2, 5), new BlockPos(2, 2, 5), new BlockPos(4, 2, 5), new BlockPos(5, 2, 5)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "the folly needs more barrels");
		Site s = start(helper, FOLLY, origin);
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			Container barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], s.site().scaffolds().size()));
		helper.succeedWhen(() -> {
			assertFinished(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped");
			int domeY = helper.absolutePos(origin).getY() + 14;
			int dome = 0;
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : s.plan().steps(stage)) {
					if (step.pos().getY() >= domeY) {
						dome++;
						helper.assertTrue(MaterialRules.matches(level.getBlockState(step.pos()), step.state()),
							"the dome isn't finished at " + helper.relativePos(step.pos()) + ": " + level.getBlockState(step.pos()) + " for " + step.state());
					}
				}
			}
			helper.assertTrue(dome >= 40, "only " + dome + " dome blocks in the plan");
			helper.assertTrue(most[0] > 0, "the dome was built without scaffolding");
			assertNoScaffolding(helper, s);
		});
	}

	// --- helpers -----------------------------------------------------------------------------

	/** A builder on the pillar, its chest holding exactly the pillar's blocks and {@code scaffolding}. */
	private static Site pillar(GameTestHelper helper, int scaffolding) {
		Site s = start(helper, PILLAR, PILLAR_AT);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
		chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
		if (scaffolding > 0) {
			chest.setItem(2, new ItemStack(Items.SCAFFOLDING, scaffolding));
		}
		return s;
	}

	private static Site start(GameTestHelper helper, ResourceLocation structure, BlockPos origin) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure, placement(helper, origin));
		if (site == null) {
			throw new GameTestAssertException("the build of " + structure + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Site(level, new Villager[]{villager}, site, plan);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void assertFinished(GameTestHelper helper, Site s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage()
			+ " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds().size()
			+ " builder at " + helper.relativePos(s.builder()[0].blockPosition()));
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong, e.g. "
			+ unfinished.stream().limit(4).map(p -> helper.relativePos(p) + "=" + s.level().getBlockState(p)).toList());
	}

	/** Not a scaffold standing in the test's area, nor above the build. */
	private static void assertNoScaffolding(GameTestHelper helper, Site s) {
		BlockPos low = helper.absolutePos(BlockPos.ZERO);
		BlockPos high = helper.absolutePos(new BlockPos(s.plan().bounds().maxX() > helper.absolutePos(new BlockPos(21, 0, 0)).getX() ? 29 : 21, 29,
			s.plan().bounds().maxZ() > helper.absolutePos(new BlockPos(0, 0, 21)).getZ() ? 29 : 21));
		for (BlockPos p : BlockPos.betweenClosed(low, high)) {
			if (s.level().getBlockState(p).is(Blocks.SCAFFOLDING)) {
				throw new GameTestAssertException("scaffolding left standing at " + helper.relativePos(p));
			}
		}
	}

	private static int chestCount(GameTestHelper helper, Item item) {
		Container chest = helper.getBlockEntity(CHEST);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			n += stack.is(item) ? stack.getCount() : 0;
		}
		return n;
	}
}
