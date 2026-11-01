package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildReserve;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Village;
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
 * QA (lane-b-1101-0038), from B84's spec ("a site that is finished, cancelled or taken down frees its share at once";
 * the sites "within the village's reach" of a store keep back in it): a canceled build and a take-down keep nothing
 * back, and the village's reach (48 blocks from the store's chest to the build's bench) holds on both sides of its edge.
 * BuildReserveGameTests covers a finished site (removed from the list), carried goods, seeds and a reload.
 */
public class QaBuildReserve1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
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
	 * The player cancels the Farmstead (Builders.cancel, as the cancel button does): what it kept back is free at once,
	 * and the village eats one of the carrots it had been leaving.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveCancel1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveCancel1101")
	public void aCanceledBuildFreesItsShareAtOnce(GameTestHelper helper) {
		Setup s = setup(helper);
		int need = BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT);
		helper.assertTrue(need > 0, "set-up: the Farmstead keeps back no carrots: " + BuildReserve.of(s.level(), s.store()).reserved());
		s.chest().setItem(0, new ItemStack(Items.CARROT, need));
		helper.assertFalse(VillageNeeds.eat(s.level(), s.eater(), s.store()), "ate a carrot the build still needs");
		Builders.cancel(s.level(), s.site());
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "set-up: the canceled site is still listed");
		helper.assertTrue(BuildReserve.of(s.level(), s.store()).reserved().isEmpty(),
			"a canceled build still keeps back " + BuildReserve.of(s.level(), s.store()).reserved());
		helper.assertTrue(VillageNeeds.eat(s.level(), s.eater(), s.store()), "the carrots stayed locked after the build was canceled");
		helper.assertTrue(s.chest().countItem(Items.CARROT) == need - 1, "carrots left: " + s.chest().countItem(Items.CARROT));
		helper.succeed();
	}

	/** The same Farmstead handed over as a take-down: taking a building down needs nothing, so nothing is kept back. */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveTakeDown1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveTakeDown1101")
	public void aTakeDownKeepsNothingBack(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.assertTrue(BuildReserve.of(s.level(), s.store()).reserved(Items.CARROT) > 0, "set-up: the Farmstead keeps back no carrots");
		s.site().setDeconstruction();
		helper.assertTrue(BuildReserve.of(s.level(), s.store()).reserved().isEmpty(),
			"a take-down keeps back " + BuildReserve.of(s.level(), s.store()).reserved());
		s.chest().setItem(0, new ItemStack(Items.CARROT, 2));
		helper.assertTrue(VillageNeeds.eat(s.level(), s.eater(), s.store()), "the village can't eat a carrot while a take-down goes on");
		helper.succeed();
	}

	/**
	 * A store chest exactly {@code Village.RADIUS} (48) blocks from the build's bench keeps the Farmstead's carrots back;
	 * one a block further keeps nothing for it (its builder doesn't take from there, and it isn't the bench's own chest).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveReach1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveReach1101")
	public void theVillagesReachHoldsOnBothSidesOfItsEdge(GameTestHelper helper) {
		Setup s = setup(helper);
		ServerLevel level = s.level();
		BlockPos bench = helper.absolutePos(BENCH);
		BlockPos edge = bench.east(Village.RADIUS);
		BlockPos past = bench.east(Village.RADIUS + 1);
		for (BlockPos at : List.of(edge, past)) {
			level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
			Leftovers.after(helper, () -> level.removeBlock(at, false));
		}
		int atEdge = BuildReserve.of(level, List.of(edge)).reserved(Items.CARROT);
		helper.assertTrue(atEdge > 0, "a store chest " + Village.RADIUS + " blocks from the bench keeps back no carrots: "
			+ BuildReserve.of(level, List.of(edge)).reserved());
		helper.assertTrue(atEdge == BuildReserve.of(level, s.store()).reserved(Items.CARROT), "the edge keeps back " + atEdge
			+ ", the store nearby " + BuildReserve.of(level, s.store()).reserved(Items.CARROT));
		helper.assertTrue(BuildReserve.of(level, List.of(past)).reserved().isEmpty(), "a store chest " + (Village.RADIUS + 1)
			+ " blocks from the bench keeps back " + BuildReserve.of(level, List.of(past)).reserved());
		helper.succeed();
	}

	/**
	 * A site at its last stage (DONE: finished, still listed for the tick it takes to wrap up) keeps nothing back, even
	 * though the world doesn't hold its blocks (it frees its share "at once"); the same site one stage earlier does.
	 * (The mutant {@code bench == null && site.isDone()} in BuildReserve.of survives this: a DONE site's remaining need is
	 * empty anyway, so that skip only saves the count. Checked by hand, lane-b-1101-0038.)
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveDone1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveDone1101")
	public void aSiteAtItsDoneStageKeepsNothingBack(GameTestHelper helper) {
		Setup s = setup(helper);
		net.minecraft.nbt.CompoundTag tag = s.site().save();
		helper.assertTrue(!BuildReserve.of(s.level(), s.store(), List.of(BuildSite.load(tag.copy()))).reserved().isEmpty(),
			"set-up: the Farmstead, saved and loaded, keeps back nothing");
		net.minecraft.nbt.CompoundTag done = tag.copy();
		done.putString("stage", "DONE");
		BuildSite finished = BuildSite.load(done);
		helper.assertTrue(finished.isDone(), "set-up: the site isn't at its DONE stage: " + finished.stage());
		helper.assertTrue(BuildReserve.of(s.level(), s.store(), List.of(finished)).reserved().isEmpty(),
			"a site at its DONE stage keeps back " + BuildReserve.of(s.level(), s.store(), List.of(finished)).reserved());
		helper.succeed();
	}

	/**
	 * BuildReserve.cached (25.3: "worked out at most once a second for the same chests"): asked again in the same tick
	 * after the build is canceled, it answers what it worked out (no second count); 20 ticks on it counts again and the
	 * canceled build keeps nothing.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaReserveCached1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaReserveCached1101")
	public void theCachedReserveIsReusedForASecondAndThenCountedAgain(GameTestHelper helper) {
		Setup s = setup(helper);
		ServerLevel level = s.level();
		long t0 = level.getGameTime();
		java.util.Map<net.minecraft.world.item.Item, Integer> first = BuildReserve.cached(level, s.store()).reserved();
		helper.assertTrue(first.getOrDefault(Items.CARROT, 0) > 0, "set-up: the cached reserve keeps back no carrots: " + first);
		Builders.cancel(level, s.site());
		helper.assertTrue(BuildReserve.of(level, s.store()).reserved().isEmpty(), "set-up: the canceled build still keeps back something");
		java.util.Map<net.minecraft.world.item.Item, Integer> again = BuildReserve.cached(level, s.store()).reserved();
		helper.assertTrue(again.equals(first), "asked again in the same tick, it counted afresh: " + again + " (first " + first + ")");
		java.util.concurrent.atomic.AtomicBoolean checked = new java.util.concurrent.atomic.AtomicBoolean();
		helper.onEachTick(() -> {
			if (!checked.get() && level.getGameTime() - t0 == 20) {
				checked.set(true);
				java.util.Map<net.minecraft.world.item.Item, Integer> later = BuildReserve.cached(level, s.store()).reserved();
				helper.assertTrue(later.isEmpty(), "20 ticks on, it still answers the old count: " + later);
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(checked.get(), "not 20 ticks yet"));
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
}
