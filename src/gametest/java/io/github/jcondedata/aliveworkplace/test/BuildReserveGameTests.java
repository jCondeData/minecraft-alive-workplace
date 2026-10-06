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
