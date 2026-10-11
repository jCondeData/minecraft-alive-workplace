package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildReserve;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
 * B84: what a build site still needs stays in the village's store. A Steward's Farmstead never finished because the
 * village ate its carrots and potatoes from the storehouse and farmers took its wheat seeds to sow.
 */
public class BuildReserveGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FARMSTEAD = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "farmstead");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	private static final BlockPos SITE = new BlockPos(6, 2, 6);
	private static final BlockPos STORE = new BlockPos(26, 2, 26);
	private static final BlockPos EATER = new BlockPos(25, 2, 24);

	private record Setup(ServerLevel level, Villager builder, BuildSite site, List<BlockPos> store, Container chest, Villager eater) {
	}

	/**
	 * The store holds the Farmstead's carrots (all it still needs and two more) and three loaves: the village eats the
	 * bread and the two spare carrots, then nothing; the carrots the build needs stay, and its builder can take them.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b84Meals"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b84Meals")
	public void mealsLeaveWhatABuildStillNeeds(GameTestHelper helper) {
		Setup s = setup(helper);
		int need = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		helper.assertTrue(need > 0, "the Farmstead keeps back no carrots: " + BuildReserve.of(s.level(), s.store()).reserved());
		s.chest().setItem(0, new ItemStack(Items.CARROT, need + 2));
		s.chest().setItem(1, new ItemStack(Items.BREAD, 3));
		int meals = 0;
		while (meals < 20 && VillageNeeds.eat(s.level(), s.eater(), s.store())) {
			meals++;
		}
		helper.assertTrue(meals == 5, "ate " + meals + " meals, not the 3 loaves and 2 spare carrots");
		helper.assertTrue(s.chest().countItem(Items.BREAD) == 0, "bread left uneaten: " + s.chest().countItem(Items.BREAD));
		int left = s.chest().countItem(Items.CARROT);
		helper.assertTrue(left == need, "carrots left " + left + ", the build needs " + need);
		// Its builder still gets them: the store keeps them for the build, not from it.
		helper.assertTrue(Builders.spare(s.level(), s.store(), Items.CARROT, java.util.Map.of()) == need, "the builder can't see the carrots");
		done(s);
		helper.succeed();
	}

	/** A finished (or cancelled) site frees what it kept back: the village eats the carrots again. */
	//$ gametest_ticks_batch HUGE '100' '"b84Released"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b84Released")
	public void aFinishedSiteFreesItsShare(GameTestHelper helper) {
		Setup s = setup(helper);
		int need = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		s.chest().setItem(0, new ItemStack(Items.CARROT, need));
		helper.assertFalse(VillageNeeds.eat(s.level(), s.eater(), s.store()), "ate a carrot the build needs");
		done(s);
		helper.assertTrue(BuildReserve.of(s.level(), s.store()).reserved().isEmpty(), "still kept back after the site ended");
		helper.assertTrue(VillageNeeds.eat(s.level(), s.eater(), s.store()), "the carrots stayed locked after the site ended");
		helper.assertTrue(s.chest().countItem(Items.CARROT) == need - 1, "carrots: " + s.chest().countItem(Items.CARROT));
		helper.succeed();
	}

	/** What the builder already carries goes into the build, so only the rest is kept back. */
	//$ gametest_ticks_batch HUGE '100' '"b84Carried"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b84Carried")
	public void whatTheBuilderCarriesIsntKeptBackTwice(GameTestHelper helper) {
		Setup s = setup(helper);
		int need = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(s.builder());
		bag.add(new ItemStack(Items.CARROT, need));
		int after = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		helper.assertTrue(after == 0, "still keeps back " + after + " carrots though the builder carries all " + need);
		s.chest().setItem(0, new ItemStack(Items.CARROT, 3));
		helper.assertTrue(VillageNeeds.eat(s.level(), s.eater(), s.store()), "no carrot eaten though none is needed");
		done(s);
		helper.succeed();
	}

	/**
	 * Seeds: a farmer taking from a village-mate's store gets only what the builds don't need, and a worker taking
	 * from the store (extract) never digs into the build's share.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b84Seeds"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b84Seeds")
	public void seedsTheBuildNeedsStay(GameTestHelper helper) {
		Setup s = setup(helper);
		BuildReserve reserve = BuildReserve.of(s.level(), s.store());
		int need = reserve.reserved(Items.WHEAT_SEEDS);
		helper.assertTrue(need > 0, "the Farmstead keeps back no wheat seeds: " + reserve.reserved());
		s.chest().setItem(0, new ItemStack(Items.WHEAT_SEEDS, need + 4));
		int taken = reserve.extract(s.level(), Items.WHEAT_SEEDS, 64);
		helper.assertTrue(taken == 4, "took " + taken + " seeds, not the 4 spare");
		helper.assertTrue(reserve.takeOne(s.level(), st -> st.is(Items.WHEAT_SEEDS)).isEmpty(), "took a seed the build needs");
		helper.assertTrue(s.chest().countItem(Items.WHEAT_SEEDS) == need, "seeds left " + s.chest().countItem(Items.WHEAT_SEEDS));
		done(s);
		helper.succeed();
	}

	/** Nothing new is saved: a site saved and loaded keeps back exactly what it did before. */
	//$ gametest_ticks_batch HUGE '100' '"b84Reload"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b84Reload")
	public void aReloadedSiteKeepsBackTheSame(GameTestHelper helper) {
		Setup s = setup(helper);
		java.util.Map<net.minecraft.world.item.Item, Integer> before = BuildReserve.of(s.level(), s.store(), List.of(s.site())).reserved();
		BuildSite loaded = BuildSite.load(s.site().save());
		java.util.Map<net.minecraft.world.item.Item, Integer> after = BuildReserve.of(s.level(), s.store(), List.of(loaded)).reserved();
		helper.assertTrue(!before.isEmpty() && before.equals(after), "before " + before + ", after reload " + after);
		done(s);
		helper.succeed();
	}

	/**
	 * 25.3: a site's remaining need is counted once and reused while the site hasn't moved on (a big village's waiting
	 * builders recounted every other build block by block); once its builder places a block or makes a supply run, it is
	 * counted again from the world.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b84NeedCache"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b84NeedCache")
	public void aSitesNeedIsCountedAgainOnceItMovesOn(GameTestHelper helper) {
		Setup s = setup(helper);
		java.util.Map<net.minecraft.world.item.Item, Integer> before = BuildReserve.of(s.level(), s.store()).reserved();
		io.github.jcondedata.aliveworkplace.build.BuildPlan plan = s.site().plan(s.level());
		io.github.jcondedata.aliveworkplace.build.BuildPlan.Step step = s.site().upcoming(plan, Integer.MAX_VALUE).stream()
			.filter(st -> !st.requirements().isEmpty() && before.containsKey(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(st.requirements().get(0).item()))
				&& !io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(s.level().getBlockState(st.pos()), st.state()))
			.findFirst().orElseThrow(() -> new GameTestAssertException("no step of the Farmstead needs a kept-back item: " + before));
		net.minecraft.world.item.Item item = io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(step.requirements().get(0).item());
		// The block appears without the site moving on: the count is reused.
		s.level().setBlock(step.pos(), step.state(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		helper.assertTrue(BuildReserve.of(s.level(), s.store()).reserved().equals(before), "counted again though the site didn't move on");
		// Its builder makes a supply run: the site has moved on, and the block is counted as done.
		s.site().supplied();
		int after = BuildReserve.of(s.level(), s.store()).reserved(item);
		helper.assertTrue(after < before.get(item), "still keeps back " + after + " " + item + " after the block went in (was " + before.get(item) + ")");
		done(s);
		helper.succeed();
	}

	/**
	 * 25.4: recounting what the other builds at a bench still need reads at most the tick's budget of blocks (plus the
	 * one site that crosses it) in one call, however many builds are queued there; a build not counted yet keeps its
	 * whole remaining plan back (never less than it needs), and within a few ticks every count is exact.
	 */
	//$ gametest_ticks_batch HUGE '200' '"b84NeedBudget"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "b84NeedBudget")
	public void otherBuildsNeedsAreRecountedAFewAtATime(GameTestHelper helper) {
		Setup s = setup(helper);
		ServerLevel level = s.level();
		int budget = io.github.jcondedata.aliveworkplace.build.BuildBudget.RECOUNT_READS_PER_TICK;
		List<BuildSite> sites = new java.util.ArrayList<>(List.of(s.site()));
		int total = s.site().upcoming(s.site().plan(level), Integer.MAX_VALUE).size();
		int biggest = total;
		while (total <= 3 * budget && sites.size() < 40) {
			BuildSite queued = Builders.enqueue(level, s.builder(), null, FARMSTEAD, s.site().placement());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(queued.id()));
			int size = queued.upcoming(queued.plan(level), Integer.MAX_VALUE).size(); // planned ahead, as its builder would
			total += size;
			biggest = Math.max(biggest, size);
			sites.add(queued);
		}
		helper.assertTrue(total > 2 * budget, sites.size() + " Farmsteads need only " + total + " blocks: too few to pass the budget");
		java.util.Map<net.minecraft.world.item.Item, Integer> exact = new java.util.HashMap<>();
		for (BuildSite site : sites) {
			Builders.remainingNeed(level, site, site.plan(level)).forEach((item, n) -> exact.merge(item, n, Integer::sum));
		}
		BlockPos bench = helper.absolutePos(BENCH);
		long now = level.getGameTime();
		java.util.Map<net.minecraft.world.item.Item, Integer> first = Builders.reservedAt(level, bench, null);
		long reads = io.github.jcondedata.aliveworkplace.build.BuildBudget.readsUsed(level, now);
		helper.assertTrue(reads > 0 && reads <= budget + biggest,
			"one call read " + reads + " blocks for " + sites.size() + " builds (" + total + " in all; budget " + budget + " + " + biggest + ")");
		exact.forEach((item, n) -> helper.assertTrue(first.getOrDefault(item, 0) >= n,
			"kept back " + first.getOrDefault(item, 0) + " " + item + " while the builds still need " + n));
		final int cap = budget + biggest;
		helper.succeedWhen(() -> {
			long t = level.getGameTime();
			java.util.Map<net.minecraft.world.item.Item, Integer> counted = Builders.reservedAt(level, bench, null);
			long r = io.github.jcondedata.aliveworkplace.build.BuildBudget.readsUsed(level, t);
			if (r > cap) {
				throw new GameTestAssertException("a later tick read " + r + " blocks (cap " + cap + ")");
			}
			if (!counted.equals(exact)) {
				throw new GameTestAssertException("not counted exactly yet: " + counted + " against " + exact);
			}
		});
	}

	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, FARMSTEAD,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SITE), Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("blueprint not found: " + FARMSTEAD);
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.setBlock(STORE, Blocks.CHEST);
		Villager eater = helper.spawn(EntityType.VILLAGER, EATER);
		eater.setNoAi(true);
		return new Setup(level, builder, site, List.of(helper.absolutePos(STORE)), (Container) helper.getBlockEntity(STORE), eater);
	}

	/** The site ends (a finished build leaves the site list the same way). */
	private static void done(Setup s) {
		BuildSiteManager.get(s.level()).remove(s.site().id());
	}
}
