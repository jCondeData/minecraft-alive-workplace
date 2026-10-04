package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.StallWatch;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
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

/** The "builder stalled" log line (23.1): written after 30 s without progress, never for a builder at work or a queued site. */
public class StallWatchGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private static Villager builder(GameTestHelper helper, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chestItems.length; i++) {
			chest.setItem(i, chestItems[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		return villager;
	}

	private static BlueprintData.Placement at(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** No materials anywhere: the builder waits, and after 30 s of that the stall is logged once. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void aBuilderWaitingForMaterialsIsLoggedAsStalled(GameTestHelper helper) {
		Villager villager = builder(helper);
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		long started = helper.getTick();
		helper.succeedWhen(() -> {
			helper.assertTrue(StallWatch.isStalled(site.id()), "no stall logged yet: status " + site.status() + ", stage " + site.stage());
			helper.assertTrue(helper.getTick() - started >= StallWatch.STALL_TICKS,
				"stall logged after only " + (helper.getTick() - started) + " ticks");
			// The line names what the builder waits for, so a soak stall can be triaged from the log alone.
			String missing = StallWatch.missing(site);
			helper.assertTrue(missing.contains("minecraft:cobblestone") && missing.contains("minecraft:oak_planks"),
				"the stall line doesn't name the missing materials: '" + missing + "'");
		});
	}

	/** With everything in the chest, the hut goes up and the site is never once logged as stalled. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void aBuilderAtWorkIsNeverLoggedAsStalled(GameTestHelper helper) {
		Villager villager = builder(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		AtomicBoolean stalled = new AtomicBoolean();
		helper.onEachTick(() -> stalled.compareAndSet(false, StallWatch.isStalled(site.id())));
		helper.succeedWhen(() -> {
			helper.assertFalse(stalled.get(), "a builder at work was logged as stalled");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null,
				"still building: stage " + site.stage() + ", status " + site.status());
		});
	}

	/**
	 * B40: a builder whose chests are far from the site walks 30 s for its materials without placing a block (each leg
	 * of the trip ends at most 15 s in, with a hop). That supply run is work, not a stall: taking the materials counts
	 * as progress, so the longest stretch without progress is one leg, not the round trip. Here the builder starts at
	 * the site, slowed so both legs run the full 15 s.
	 */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"stall_supply_run"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "stall_supply_run")
	public void aLongSupplyRunIsNeverLoggedAsStalled(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = builder(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		BlockPos far = helper.absolutePos(new BlockPos(23, 2, 23));
		villager.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, new BlockPos(22, 2, 22)));
		long started = helper.getTick();
		AtomicLong firstPlaced = new AtomicLong(-1);
		long[] mark = {site.progressMark(), started, 0}; // the mark, since when, the longest stretch without progress
		helper.onEachTick(() -> {
			helper.assertFalse(StallWatch.isStalled(site.id()), "a builder on a supply run was logged as stalled ("
				+ site.status() + ", stage " + site.stage() + ", " + site.placed() + " placed)");
			if (site.progressMark() != mark[0]) {
				mark[0] = site.progressMark();
				mark[1] = helper.getTick();
			}
			if (firstPlaced.get() < 0) {
				mark[2] = Math.max(mark[2], helper.getTick() - mark[1]);
				if (site.placed() > 0) {
					firstPlaced.set(helper.getTick());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(firstPlaced.get() >= 0 && site.placed() >= 5, "only " + site.placed() + " placed (status " + site.status() + ")");
			// The case itself: the first block came only after a long supply run ...
			helper.assertTrue(firstPlaced.get() - started > 450,
				"the supply run took only " + (firstPlaced.get() - started) + " ticks: the test no longer covers B40");
			// ... and taking the materials halfway through it was progress.
			helper.assertTrue(mark[2] < 400, "the supply run went " + mark[2] + " ticks without progress");
		});
	}

	/** After the work shift (evening, night) a builder with nothing to build with is resting, not stalled. */
	//$ gametest_ticks_batch AREA '1200' '"stall_off_shift"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stall_off_shift")
	public void aBuilderOffShiftIsNeverLoggedAsStalled(GameTestHelper helper) {
		// Its own batch: the time of day is the whole level's, and the other tests here need daytime.
		Leftovers.clear(helper);
		Villager villager = builder(helper);
		helper.setDayTime(13000);
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		helper.onEachTick(() -> helper.assertFalse(StallWatch.isStalled(site.id()), "a builder off shift was logged as stalled"));
		helper.runAtTickTime(StallWatch.STALL_TICKS + 300, helper::succeed);
	}

	/** A site waiting in a busy builder's queue is not a stall, even while the builder's own site is stuck. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void aQueuedSiteIsNeverLoggedAsStalled(GameTestHelper helper) {
		Villager villager = builder(helper);
		BuildSite first = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		BuildSite queued = Builders.enqueue(helper.getLevel(), villager, null, TEST_HUT, at(helper, new BlockPos(6, 2, 1)));
		AtomicLong firstStalledAt = new AtomicLong(-1);
		helper.onEachTick(() -> {
			helper.assertFalse(StallWatch.isStalled(queued.id()), "the queued site was logged as stalled");
			if (firstStalledAt.get() < 0 && StallWatch.isStalled(first.id())) {
				firstStalledAt.set(helper.getTick());
			}
		});
		// Keep watching for another 100 ticks after the builder's own site stalls.
		helper.succeedWhen(() -> helper.assertTrue(firstStalledAt.get() >= 0 && helper.getTick() - firstStalledAt.get() >= 100,
			"the builder's own site hasn't stalled yet (status " + first.status() + ")"));
	}

	/**
	 * The soak's item check (23.1): with the ledger on, a build from a chest adds up exactly (stocked + gained − built
	 * in − dropped = left in the chest and the bag), and the ledger saw every block built in.
	 */
	//$ gametest_ticks_batch AREA '2400' '"material_ledger"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "material_ledger")
	public void theMaterialLedgerBalancesABuildFromAChest(GameTestHelper helper) {
		// Its own batch: the ledger counts every builder on the server, so no other build may run beside this one.
		Leftovers.clear(helper);
		java.util.Map<net.minecraft.world.item.Item, Integer> stocked = java.util.Map.of(Items.COBBLESTONE, 25,
			Items.OAK_PLANKS, 55, Items.OAK_DOOR, 1, Items.TORCH, 1);
		Villager villager = builder(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		io.github.jcondedata.aliveworkplace.build.MaterialLedger.start();
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null,
				"still building: stage " + site.stage() + ", status " + site.status());
			io.github.jcondedata.aliveworkplace.build.MaterialLedger.stop();
			java.util.Map<net.minecraft.world.item.Item, Integer> left = new java.util.HashMap<>();
			Container chest = helper.getBlockEntity(CHEST);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				if (!stack.isEmpty()) {
					left.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
			for (ItemStack stack : io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
				if (!stack.isEmpty()) {
					left.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
			var used = io.github.jcondedata.aliveworkplace.build.MaterialLedger.used();
			helper.assertTrue(used.getOrDefault(Items.COBBLESTONE, 0) > 0 && used.getOrDefault(Items.OAK_DOOR, 0) == 1,
				"the ledger missed blocks built in: " + used);
			String off = io.github.jcondedata.aliveworkplace.command.Soak.itemsOff(stocked,
				io.github.jcondedata.aliveworkplace.build.MaterialLedger.gained(), used,
				io.github.jcondedata.aliveworkplace.build.MaterialLedger.dropped(), left);
			helper.assertTrue(off.equals("none"), "items off after a clean build: " + off);
		});
	}

	/** The item check itself: an extra item is "+", a missing one "−", gains and drops are accounted for. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSoakItemCheckNamesDuplicatedAndLostItems(GameTestHelper helper) {
		var stocked = java.util.Map.of(Items.COBBLESTONE, 10, Items.GLASS, 4);
		var gained = java.util.Map.of(Items.DIRT, 6);
		var used = java.util.Map.of(Items.COBBLESTONE, 7, Items.GLASS, 4);
		var dropped = java.util.Map.of(Items.DIRT, 2);
		String fine = io.github.jcondedata.aliveworkplace.command.Soak.itemsOff(stocked, gained, used, dropped,
			java.util.Map.of(Items.COBBLESTONE, 3, Items.DIRT, 4));
		helper.assertTrue(fine.equals("none"), "a balanced ledger reported: " + fine);
		String bad = io.github.jcondedata.aliveworkplace.command.Soak.itemsOff(stocked, gained, used, dropped,
			java.util.Map.of(Items.COBBLESTONE, 5, Items.DIRT, 3, Items.GLASS, 0, Items.STICK, 1));
		helper.assertTrue(bad.equals("cobblestone +2, dirt -1, stick +1"), "wrong report: " + bad);
		helper.succeed();
	}
}
