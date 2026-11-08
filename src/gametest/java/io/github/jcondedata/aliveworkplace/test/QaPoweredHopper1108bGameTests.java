package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1108-0638) for the Builder and the Scan Tool (docs/wiki/builder.md: "A builder's steps need exactly the
 * build's material list, nothing more (B22)"; a step that can't be done is skipped, and a finished build is the drawing
 * standing). A scan whose box holds a hopper but not what powered it (a lever or redstone block just outside the box, as
 * an item sorter's torches often are) draws the hopper switched off ({@code enabled=false}). Built where nothing powers
 * it, the game switches it on the moment it is placed, as it does for a player's hopper. Expected: the Builder counts it
 * built, as he does a lamp that lights or a door that opens, takes exactly one hopper and finishes with nothing skipped.
 * Control: a hopper drawn switched on builds the same.
 */
public class QaPoweredHopper1108bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(1, 2, 1);
	private static final BlockPos CHEST = new BlockPos(1, 2, 3);
	private static final BlockPos SOURCE = new BlockPos(6, 2, 6);
	private static final BlockPos SITE = new BlockPos(4, 2, 2);

	//$ gametest_ticks_batch AREA '1400' '"qa1108bHopperOff"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1108bHopperOff")
	public void aHopperScannedSwitchedOffIsBuiltOnceAndCountedBuilt(GameTestHelper helper) {
		run(helper, true);
	}

	//$ gametest_ticks_batch AREA '1400' '"qa1108bHopperOn"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1108bHopperOn")
	public void controlAHopperScannedSwitchedOnIsBuiltOnce(GameTestHelper helper) {
		run(helper, false);
	}

	private static void run(GameTestHelper helper, boolean powered) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The "scan": a hopper, with (or without) a redstone block beside it outside the scanned box.
		helper.setBlock(SOURCE, Blocks.HOPPER);
		if (powered) {
			helper.setBlock(SOURCE.east(), Blocks.REDSTONE_BLOCK);
		}
		BlockState scanned = helper.getBlockState(SOURCE);
		helper.assertTrue(scanned.getValue(HopperBlock.ENABLED) == !powered, "the source hopper isn't " + (powered ? "switched off" : "on") + ": " + scanned);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1108b_hopper_" + (powered ? "off" : "on") + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SOURCE), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SOURCE.east(), Blocks.AIR);
		helper.setBlock(SOURCE, Blocks.AIR);

		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.HOPPER, 3));
		Villager builder = QaCarpenterStorehouse1101GameTests.builder(helper, BENCH);
		BuildSite site = QaCarpenterStorehouse1101GameTests.start(helper, builder, id, SITE);
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, site), "the one-hopper build isn't finished: stage " + site.stage()
				+ ", " + site.skipped() + " skipped, hopper there: " + helper.getBlockState(SITE) + ", status: "
				+ Builders.remainingNeed(level, site, site.plan(level)));
			BlockState built = helper.getBlockState(SITE);
			helper.assertTrue(built.is(Blocks.HOPPER), "no hopper standing at the site: " + built);
			// Nothing powers it where it was built, so it works, as a player's hopper put down there does.
			helper.assertTrue(built.getValue(HopperBlock.ENABLED), "the built hopper is switched off though nothing powers it (it moves no items): " + built);
			int left = chest.countItem(Items.HOPPER);
			int carried = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.HOPPER);
			helper.assertTrue(left + carried == 2, "the build took " + (3 - left - carried) + " hoppers for one (chest " + left + ", bag " + carried + ")");
			helper.assertTrue(site.skipped() == 0, "the hopper was counted skipped (" + site.skipped() + ") though it stands built");
		});
	}
}
