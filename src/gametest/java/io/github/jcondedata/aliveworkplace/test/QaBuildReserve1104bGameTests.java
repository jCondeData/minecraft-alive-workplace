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
 * QA (2026-11-04), from B84's spec (BuildReserve's header): a build site keeps back in a shared store, by material
 * family, exactly what the rest of its build takes beyond its bench's own chests and its builder's bag. These tests hold
 * the edges the earlier classes (BuildReserveGameTests, QaBuildReserve1101GameTests) leave open: the boundary on both
 * sides of the shortfall, bench chests lowering it item for item, the store never counting as a bench's own chest, two
 * benches adding up, one builder's bag counted once, nobody else's bag counting, and one family never holding another.
 */
public class QaBuildReserve1104bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FARMSTEAD = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "farmstead");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_CHEST = new BlockPos(1, 2, 2);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	private static final BlockPos SITE = new BlockPos(6, 2, 6);
	private static final BlockPos STORE = new BlockPos(26, 2, 26);
	private static final BlockPos EATER = new BlockPos(25, 2, 24);

	private record Setup(ServerLevel level, Villager builder, BuildSite site, List<BlockPos> store, Container chest, Villager eater) {
	}

	/**
	 * The store holds one fewer, exactly, and one more carrot than the Farmstead still needs: only the one beyond the
	 * shortfall is ever free (extract, takeOne, spare and a meal all agree), and nothing below it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveEdge1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveEdge1104b")
	public void onlyWhatIsBeyondTheShortfallIsFree(GameTestHelper helper) {
		Setup s = setup(helper, STORE);
		BuildReserve reserve = BuildReserve.of(s.level(), s.store());
		int need = reserve.reserved(Items.CARROT);
		helper.assertTrue(need > 1, "the Farmstead keeps back " + need + " carrots: " + reserve.reserved());
		// One short of the shortfall: nothing is free.
		s.chest().setItem(0, new ItemStack(Items.CARROT, need - 1));
		helper.assertTrue(reserve.spare(s.level(), Items.CARROT) == 0, "spare with " + (need - 1) + ": " + reserve.spare(s.level(), Items.CARROT));
		helper.assertTrue(reserve.extract(s.level(), Items.CARROT, 64) == 0, "took a carrot from a store short of the build's need");
		helper.assertTrue(reserve.takeOne(s.level(), st -> st.is(Items.CARROT)).isEmpty(), "takeOne took a carrot below the shortfall");
		helper.assertFalse(VillageNeeds.eat(s.level(), s.eater(), s.store()), "a villager ate a carrot below the shortfall");
		// Exactly the shortfall: still nothing.
		s.chest().setItem(0, new ItemStack(Items.CARROT, need));
		helper.assertTrue(reserve.spare(s.level(), Items.CARROT) == 0, "spare at the shortfall: " + reserve.spare(s.level(), Items.CARROT));
		helper.assertTrue(reserve.extract(s.level(), Items.CARROT, 64) == 0, "took a carrot at exactly the shortfall");
		// One beyond: exactly one, once.
		s.chest().setItem(0, new ItemStack(Items.CARROT, need + 1));
		helper.assertTrue(reserve.spare(s.level(), Items.CARROT) == 1, "spare one beyond: " + reserve.spare(s.level(), Items.CARROT));
		helper.assertFalse(reserve.takeOne(s.level(), st -> st.is(Items.CARROT)).isEmpty(), "the one spare carrot wasn't free");
		helper.assertTrue(reserve.takeOne(s.level(), st -> st.is(Items.CARROT)).isEmpty(), "a second carrot came out");
		helper.assertTrue(s.chest().countItem(Items.CARROT) == need, "carrots left " + s.chest().countItem(Items.CARROT) + ", need " + need);
		done(s);
		helper.succeed();
	}

	/**
	 * Carrots in the bench's own chest go into the build: the store keeps back that many fewer, item for item, none once
	 * the bench chest holds them all, and never a negative amount when it holds more (other materials keep theirs).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveBenchChest1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveBenchChest1104b")
	public void benchChestsLowerTheShareItemForItem(GameTestHelper helper) {
		Setup s = setup(helper, STORE);
		BuildReserve before = BuildReserve.of(s.level(), s.store());
		int need = before.reserved(Items.CARROT);
		int seeds = before.reserved(Items.WHEAT_SEEDS);
		helper.assertTrue(need > 2 && seeds > 0, "the Farmstead keeps back too little: " + before.reserved());
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Container bench = (Container) helper.getBlockEntity(BENCH_CHEST);
		bench.setItem(0, new ItemStack(Items.CARROT, 2));
		int partly = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		helper.assertTrue(partly == need - 2, "with 2 carrots at the bench it keeps back " + partly + ", not " + (need - 2));
		bench.setItem(0, new ItemStack(Items.CARROT, need));
		helper.assertTrue(BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT) == 0, "the bench holds them all, still kept back");
		bench.setItem(0, new ItemStack(Items.CARROT, need + 10));
		BuildReserve over = BuildReserve.of(s.level(), s.store());
		helper.assertTrue(over.reserved(Items.CARROT) == 0, "kept back " + over.reserved(Items.CARROT) + " with a full bench chest");
		helper.assertTrue(over.reserved(Items.WHEAT_SEEDS) == seeds, "extra carrots changed the seeds kept back: " + over.reserved(Items.WHEAT_SEEDS));
		s.chest().setItem(0, new ItemStack(Items.CARROT, 5));
		helper.assertTrue(over.extract(s.level(), Items.CARROT, 64) == 5, "the store's carrots stayed locked though the bench holds all");
		done(s);
		helper.succeed();
	}

	/**
	 * The store is the bench's own chest (a builder's chest a village-mate shares): what it holds isn't counted as
	 * already going into the build, so the whole shortfall stays kept back in it and a meal leaves the carrots.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveOwnStore1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveOwnStore1104b")
	public void theStoreNeverCountsAsTheBenchsOwnChest(GameTestHelper helper) {
		Setup far = setup(helper, STORE);
		int need = BuildReserve.of(far.level(), far.store()).reserved(Items.CARROT);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(BENCH_CHEST);
		chest.setItem(0, new ItemStack(Items.CARROT, need));
		List<BlockPos> store = List.of(helper.absolutePos(BENCH_CHEST));
		BuildReserve reserve = BuildReserve.of(far.level(), store);
		helper.assertTrue(reserve.reserved(Items.CARROT) == need, "the bench's chest as the store keeps back " + reserve.reserved(Items.CARROT) + ", not " + need);
		helper.assertTrue(reserve.extract(far.level(), Items.CARROT, 64) == 0, "took a carrot the build needs from its own bench chest");
		helper.assertTrue(chest.countItem(Items.CARROT) == need, "carrots left " + chest.countItem(Items.CARROT));
		done(far);
		helper.succeed();
	}

	/** Two builders at two benches build Farmsteads near one store: the store keeps back the sum of their shortfalls. */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveTwoBenches1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveTwoBenches1104b")
	public void twoBenchesSharingOneStoreKeepBackTheSum(GameTestHelper helper) {
		Setup s = setup(helper, STORE);
		ServerLevel level = s.level();
		java.util.Map<net.minecraft.world.item.Item, Integer> one = BuildReserve.of(level, s.store()).reserved();
		BlockPos bench2 = new BlockPos(2, 2, 27);
		helper.setBlock(bench2, ModBlocks.BUILDERS_BENCH);
		Villager builder2 = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 26));
		builder2.setNoAi(true);
		Builders.employ(level, builder2, helper.absolutePos(bench2));
		BuildSite site2 = Builders.start(level, builder2, null, FARMSTEAD, s.site().placement());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site2.id()));
		helper.assertTrue(helper.absolutePos(bench2).equals(site2.bench()), "the second site's bench is " + site2.bench());
		java.util.Map<net.minecraft.world.item.Item, Integer> two = BuildReserve.of(level, s.store()).reserved();
		helper.assertTrue(!one.isEmpty() && one.keySet().equals(two.keySet()), "kept-back items changed: " + one + " -> " + two);
		one.forEach((item, n) -> helper.assertTrue(two.get(item) == 2 * n, item + ": two benches keep back " + two.get(item) + ", not " + (2 * n)));
		// One finishes: the other's share alone remains.
		BuildSiteManager.get(level).remove(site2.id());
		helper.assertTrue(BuildReserve.of(level, s.store()).reserved().equals(one), "after one ended: " + BuildReserve.of(level, s.store()).reserved());
		done(s);
		helper.succeed();
	}

	/**
	 * One builder has a second Farmstead queued at the same bench and carries some carrots: his bag goes into his
	 * builds once, so the store keeps back both builds' need less the carrots once (not twice).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveQueuedBag1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveQueuedBag1104b")
	public void oneBuildersBagCountsOnceForHisQueuedBuilds(GameTestHelper helper) {
		Setup s = setup(helper, STORE);
		ServerLevel level = s.level();
		int need = BuildReserve.of(level, s.store()).reserved(Items.CARROT);
		BuildSite queued = Builders.enqueue(level, s.builder(), null, FARMSTEAD, s.site().placement());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(queued.id()));
		int both = BuildReserve.of(level, s.store()).reserved(Items.CARROT);
		helper.assertTrue(both == 2 * need, "a queued Farmstead: keeps back " + both + ", not " + (2 * need));
		int carried = Math.min(need, 3);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(s.builder());
		bag.add(new ItemStack(Items.CARROT, carried));
		int after = BuildReserve.of(level, s.store()).reserved(Items.CARROT);
		helper.assertTrue(after == 2 * need - carried, "carrying " + carried + ": keeps back " + after + ", not " + (2 * need - carried));
		BuildSiteManager.get(level).remove(queued.id());
		done(s);
		helper.succeed();
	}

	/** Only the site's own builder's bag goes into it: another villager's builder bag full of carrots changes nothing. */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveOtherBag1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveOtherBag1104b")
	public void anotherVillagersBagDoesntCount(GameTestHelper helper) {
		Setup s = setup(helper, STORE);
		int need = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		ModAttachments.BUILDER_BAG.getOrCreate(s.eater()).add(new ItemStack(Items.CARROT, need));
		int after = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		helper.assertTrue(after == need, "a stranger's bag lowered the reserve from " + need + " to " + after);
		done(s);
		helper.succeed();
	}

	/**
	 * Carrots kept back don't hold back any other item: a worker taking "any food" from the store gets every loaf and
	 * every potato beyond the build's potatoes, then nothing, and the carrots stay whole.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveFamilies1104b"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveFamilies1104b")
	public void oneKeptBackItemNeverHoldsBackAnother(GameTestHelper helper) {
		Setup s = setup(helper, STORE);
		BuildReserve reserve = BuildReserve.of(s.level(), s.store());
		int carrots = reserve.reserved(Items.CARROT);
		int potatoes = reserve.reserved(Items.POTATO);
		helper.assertTrue(reserve.reserved(Items.BREAD) == 0, "bread kept back: " + reserve.reserved());
		s.chest().setItem(0, new ItemStack(Items.CARROT, carrots));
		s.chest().setItem(1, new ItemStack(Items.BREAD, 4));
		s.chest().setItem(2, new ItemStack(Items.POTATO, potatoes + 3));
		java.util.function.Predicate<ItemStack> food = st -> st.is(Items.CARROT) || st.is(Items.BREAD) || st.is(Items.POTATO);
		int taken = 0;
		while (taken < 40 && !reserve.takeOne(s.level(), food).isEmpty()) {
			taken++;
		}
		helper.assertTrue(taken == 7, "took " + taken + " foods, not the 4 loaves and 3 spare potatoes");
		helper.assertTrue(s.chest().countItem(Items.BREAD) == 0, "bread left: " + s.chest().countItem(Items.BREAD));
		helper.assertTrue(s.chest().countItem(Items.POTATO) == potatoes, "potatoes left " + s.chest().countItem(Items.POTATO) + ", need " + potatoes);
		helper.assertTrue(s.chest().countItem(Items.CARROT) == carrots, "carrots left " + s.chest().countItem(Items.CARROT) + ", need " + carrots);
		done(s);
		helper.succeed();
	}

	private static Setup setup(GameTestHelper helper, BlockPos storeAt) {
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
		helper.setBlock(storeAt, Blocks.CHEST);
		Villager eater = helper.spawn(EntityType.VILLAGER, EATER);
		eater.setNoAi(true);
		return new Setup(level, builder, site, List.of(helper.absolutePos(storeAt)), (Container) helper.getBlockEntity(storeAt), eater);
	}

	private static void done(Setup s) {
		BuildSiteManager.get(s.level()).remove(s.site().id());
	}
}
