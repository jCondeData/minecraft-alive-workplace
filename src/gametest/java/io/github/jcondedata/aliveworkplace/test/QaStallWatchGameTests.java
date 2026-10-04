package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.StallWatch;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-1004-1033) for 23.1's stall line and B40's fix, from their specs: a builder making no progress for 30 seconds
 * is logged then, not later; and counting a finished supply run as progress (B40) must not hide a builder that empties
 * its bag and then has nothing to build with.
 */
public class QaStallWatchGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private static Villager builder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		return villager;
	}

	private static BuildSite start(GameTestHelper helper, Villager villager) {
		return Builders.start(helper.getLevel(), villager, null, TEST_HUT, new BlueprintData.Placement(
			helper.getLevel().dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE));
	}

	/**
	 * The boundary of "no progress for 30 seconds": the stall is logged when the 30 s are up (600 ticks after the
	 * watch last saw the site move on), not a check later. The watch looks every 20 game ticks, so it first sees a
	 * change at the next multiple of 20; the stall must show within a tick of 600 ticks after that.
	 */
	//$ gametest_ticks AREA '1800'
	@GameTest(template = AREA, timeoutTicks = 1800)
	public void qaAStallIsLoggedAtThirtySecondsNotLater(GameTestHelper helper) {
		Villager villager = builder(helper);
		BuildSite site = start(helper, villager);
		long[] s = {site.progressMark(), (helper.getLevel().getGameTime() + 19) / 20 * 20, -1}; // mark, since (watch's view), stalled at
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			boolean offShift = villager.isSleeping() || !villager.getBrain().isActive(Activity.WORK);
			if (site.progressMark() != s[0]) {
				s[0] = site.progressMark();
				s[1] = (now + 19) / 20 * 20; // the watch sees the new mark at its next look
			} else if (offShift && now % 20 == 0) {
				s[1] = now; // the watch restarts the count while the builder is off shift
			}
			if (s[2] < 0 && StallWatch.isStalled(site.id())) {
				s[2] = now;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(s[2] >= 0, "no stall logged yet: status " + site.status() + ", stage " + site.stage());
			long after = s[2] - s[1];
			helper.assertTrue(after >= StallWatch.STALL_TICKS, "the stall was logged after only " + after + " ticks without progress");
			helper.assertTrue(after <= StallWatch.STALL_TICKS + 2,
				"the stall was logged " + after + " ticks after the last progress, not at 30 s (" + StallWatch.STALL_TICKS + ")");
		});
	}

	/**
	 * B40 counts emptying the bag at the chests as progress. A builder whose bag is full of things the build doesn't
	 * use empties it into its chest (nothing lost), and with no materials anywhere it then waits: that wait is a stall
	 * and is still logged, 30 s after the bag was emptied.
	 */
	//$ gametest_ticks AREA '2000'
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void qaB40EmptyingAFullBagDoesntHideTheStallAfter(GameTestHelper helper) {
		Villager villager = builder(helper);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (int i = 0; i < BuilderBag.SLOTS; i++) {
			bag.add(new ItemStack(Items.CLAY_BALL, 64));
		}
		helper.assertTrue(bag.freeSlots() == 0, "the bag isn't full: " + bag.freeSlots() + " free slots");
		BuildSite site = start(helper, villager);
		long[] t = {-1, -1}; // bag emptied at, stalled at
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (t[0] < 0 && ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.CLAY_BALL) == 0) {
				t[0] = now;
			}
			if (t[1] < 0 && StallWatch.isStalled(site.id())) {
				t[1] = now;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(t[0] >= 0, "the builder never emptied its bag of clay ("
				+ ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.CLAY_BALL) + " left, status " + site.status() + ")");
			Container chest = helper.getBlockEntity(CHEST);
			int clay = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.CLAY_BALL)) {
					clay += chest.getItem(i).getCount();
				}
			}
			helper.assertTrue(clay == BuilderBag.SLOTS * 64, "the chest got " + clay + " of the bag's " + BuilderBag.SLOTS * 64 + " clay balls");
			helper.assertTrue(t[1] >= 0, "a builder with nothing to build with after emptying its bag was never logged as stalled (status "
				+ site.status() + ", stage " + site.stage() + ")");
			helper.assertTrue(t[1] - t[0] >= StallWatch.STALL_TICKS - 20,
				"the stall was logged " + (t[1] - t[0]) + " ticks after the bag was emptied, before 30 s without progress");
			String missing = StallWatch.missing(site);
			helper.assertTrue(missing.contains("minecraft:cobblestone"), "the stall line doesn't name what is missing: '" + missing + "'");
		});
	}
}
