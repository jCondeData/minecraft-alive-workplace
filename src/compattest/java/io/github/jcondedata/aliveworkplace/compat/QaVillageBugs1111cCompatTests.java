package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-11) verifying B1147 (CobbleDollars shop, 42.4) and B996 (Waystones, 41.2/41.3) from their bugs'
 * "Expected", for the corners their own tests leave out: the goods and the price changed together, and a bigger lot at
 * the same price (B1147); the other village's hall broken, the network file saved and loaded after the break, the hall
 * put back on its spot and its round run (B996). The remaining village's round with a hall-less plaza nearby is
 * QaWaystoneOrphanPlaza1111cBugCompatTests (a bug, on tests/waystone-orphan-plaza-1111c).
 */
public class QaVillageBugs1111cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final int SLOT = Shops.FIRST_GOODS_SLOT;

	private static String goodsChanged(int count, String goods, String price) {
		return "This counter now sells " + count + " " + goods + " for " + price + ". Click Confirm again to buy that.";
	}

	// ---- B1147 ----------------------------------------------------------------------------------------------------

	/**
	 * The owner swaps the goods and raises the price at once (16 Cobblestone for 200 becomes 1 Diamond for 900) between
	 * Buy and Confirm: nothing is taken and the buyer reads "This counter now sells 1 Diamond for 900 CobbleDollars."
	 * (what changed, never "The price changed"); the next Confirm charges exactly 900 for the one diamond.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopSwapAndPrice1111c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopSwapAndPrice1111c")
	public void goodsAndPriceChangedTogetherSaysWhatTheCounterNowSells(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(1, new ItemStack(Items.DIAMOND, 2));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 2000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.EMERALD, 9));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before && buyer.getInventory().countItem(Items.DIAMOND) == 0
			&& buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "Confirm on a lot swapped and repriced bought something");
		helper.assertTrue(seen.contains(goodsChanged(1, "Diamond", "900 CobbleDollars")), "the buyer read: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("The price changed")), "the goods changed too, but the buyer read: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 900 && buyer.getInventory().countItem(Items.DIAMOND) == 1
			&& buyer.getInventory().countItem(Items.COBBLESTONE) == 0, "second Confirm: paid " + (before - CobbleDollarsBank.balance(buyer))
			+ ", diamonds " + buyer.getInventory().countItem(Items.DIAMOND));
		helper.succeed();
	}

	/**
	 * The other side of B1107's smaller lot: a bigger lot at the same price (16 to 32 Cobblestone for 200). Confirm on the
	 * 16-row takes nothing (the buyer never saw 32) and says "This counter now sells 32 Cobblestone for 200 CobbleDollars.";
	 * the next Confirm hands over 32 for 200.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopLotGrown1111c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopLotGrown1111c")
	public void aBiggerLotAtTheSamePriceSaysWhatTheCounterNowSells(GameTestHelper helper) {
		ServerPlayer owner = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		Villager keeper = QaJobShopLists1109cCompatTests.shop(helper, owner);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		List<String> seen = new ArrayList<>();
		ServerPlayer buyer = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(buyer, 1000);
		long before = CobbleDollarsBank.balance(buyer);
		ChoiceMenu menu = QaJobShopLists1109cCompatTests.open(helper, buyer, keeper, false);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		QaJobShopLists1109cCompatTests.lot(helper, 0, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.EMERALD, 2));
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(CobbleDollarsBank.balance(buyer) == before && buyer.getInventory().countItem(Items.COBBLESTONE) == 0,
			"Confirm on a grown lot bought something: paid " + (before - CobbleDollarsBank.balance(buyer)));
		helper.assertTrue(seen.contains(goodsChanged(32, "Cobblestone", "200 CobbleDollars")), "the buyer read: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("The price changed")), "the price didn't change, but the buyer read: " + seen);
		QaJobShopLists1109cCompatTests.click(buyer, menu.containerId, SLOT);
		helper.assertTrue(before - CobbleDollarsBank.balance(buyer) == 200 && buyer.getInventory().countItem(Items.COBBLESTONE) == 32,
			"second Confirm: paid " + (before - CobbleDollarsBank.balance(buyer)) + ", got " + buyer.getInventory().countItem(Items.COBBLESTONE));
		helper.succeed();
	}

	// ---- B996 (two villages with plazas, as QaWaystones1107dBugCompatTests sets them up) ------------------------------

	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(helper, origin), UUID.randomUUID());
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static void hall(GameTestHelper helper, BlockPos at, String name, UUID owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner, "Jesse");
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
		}
	}

	/** Ashford (A) and Brookmere (B), each with a hall and a plaza whose stone is raised, both on the network. */
	private static UUID start(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, a(helper), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(a(helper), 160)) {
				manager.remove(site.id());
			}
		}
		cleanUp(helper);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		hall(helper, HALL_A, "Ashford", owner);
		hall(helper, HALL_B, "Brookmere", owner);
		plaza(helper, PLAZA_A);
		plaza(helper, PLAZA_B);
		VillageWaystones.round(level, a(helper));
		VillageWaystones.round(level, b(helper));
		helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the two stones don't stand");
		return owner;
	}

	/**
	 * The other side of B996's test: Ashford's hall (the settlers' own side) broken, Brookmere's standing. Brookmere isn't
	 * linked to Ashford either way, Ashford is off the network (villages), off Brookmere's Map, and stays off after the
	 * network file is saved and loaded again.
	 */
	//$ gametest_batch HUGE '"qaWaystonesHallAGone1111c"'
	@GameTest(template = HUGE, batch = "qaWaystonesHallAGone1111c")
	public void theOtherVillagesHallBrokenTakesItOffTheNetworkForGood(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		try {
			helper.setBlock(HALL_A, Blocks.AIR);
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			helper.assertTrue(VillageWaystones.isStone(level.getBlockState(stone(helper, PLAZA_A))), "setup: Ashford's stone went with the hall");
			helper.assertFalse(VillageWaystones.linked(level, b(helper), a(helper)), "Brookmere is linked to Ashford, whose hall is gone");
			helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "Ashford, whose hall is gone, is linked to Brookmere");
			helper.assertFalse(VillageWaystones.villages(level).contains(a(helper)), "Ashford, whose hall is gone, is still a village on the network");
			helper.assertTrue(VillageWaystones.villages(level).contains(b(helper)), "Brookmere left the network when Ashford's hall was broken");
			List<VillageWaystones.Village> map = VillageWaystones.network(level, b(helper), null);
			helper.assertTrue(map.stream().noneMatch(v -> v.hall().equals(a(helper))), "Brookmere's Map still lists Ashford: " + map.size() + " villages");
			CompoundTag saved = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
			VillageWaystones.Data loaded = VillageWaystones.Data.load(saved, level.registryAccess());
			helper.assertFalse(loaded.halls().contains(a(helper)), "after a save and load Ashford, whose hall is gone, is back in the network file");
			helper.assertTrue(loaded.halls().contains(b(helper)), "after a save and load Brookmere is missing from the network file");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * The hall put back on its spot (its plaza and stone untouched): after its next round the village is on the network
	 * again and linked, as any village with a hall and a standing stone.
	 */
	//$ gametest_batch HUGE '"qaWaystonesHallBack1111c"'
	@GameTest(template = HUGE, batch = "qaWaystonesHallBack1111c")
	public void aHallPutBackOnItsSpotRejoinsTheNetworkAtItsNextRound(GameTestHelper helper) {
		UUID owner = start(helper);
		ServerLevel level = helper.getLevel();
		try {
			helper.setBlock(HALL_B, Blocks.AIR);
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "setup: Brookmere still linked with its hall broken");
			hall(helper, HALL_B, "Brookmere", owner);
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			VillageWaystones.round(level, b(helper));
			helper.assertTrue(VillageWaystones.villages(level).contains(b(helper)), "Brookmere's hall is back and has had its round, yet it's off the network");
			helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "Brookmere's hall is back and has had its round, yet Ashford isn't linked to it");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
