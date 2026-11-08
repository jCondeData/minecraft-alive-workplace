package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.block.state.properties.Tilt;

import java.util.function.Predicate;

/**
 * QA (lane-b-1108-0638) for the Builder and the Scan Tool (docs/wiki/builder.md, "Nothing a build leaves is broken or
 * changed by the game itself once placed", and the scan "keeps ... nothing else that moves"). A scan catches blocks in
 * the middle of a moment: a wooden pressure plate a villager was standing on, a button just pressed, a big dripleaf
 * tipped under someone's feet, a sculk sensor that just heard a step. The game springs each back by itself a moment
 * later. Built from such a scan, each must come out as a player's freshly placed one does and spring back the same:
 * the plate up (a door beside it shuts again), the button out, the leaf level (it holds a player up), the sensor
 * listening. A block left in that moment for good would hold a door open, keep a leaf a player falls through, or leave a
 * sensor deaf.
 */
public class QaStuckStates1108bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(1, 2, 1);
	private static final BlockPos CHEST = new BlockPos(1, 2, 3);
	private static final BlockPos SOURCE = new BlockPos(6, 2, 6);
	private static final BlockPos SITE = new BlockPos(4, 2, 2);

	//$ gametest_ticks_batch AREA '1600' '"qa1108bStuckPlate"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108bStuckPlate")
	public void aPressurePlateScannedPressedIsBuiltUp(GameTestHelper helper) {
		run(helper, "plate", Blocks.OAK_PLANKS, Items.OAK_PLANKS,
			Blocks.OAK_PRESSURE_PLATE.defaultBlockState().setValue(BlockStateProperties.POWERED, true), Items.OAK_PRESSURE_PLATE,
			s -> !s.getValue(BlockStateProperties.POWERED), "pressed (it powers what is beside it for good)");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108bStuckButton"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108bStuckButton")
	public void aButtonScannedPressedIsBuiltOut(GameTestHelper helper) {
		run(helper, "button", Blocks.OAK_PLANKS, Items.OAK_PLANKS,
			Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR).setValue(BlockStateProperties.POWERED, true),
			Items.STONE_BUTTON, s -> !s.getValue(BlockStateProperties.POWERED), "pressed in (it powers what is beside it for good)");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108bStuckLeaf"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108bStuckLeaf")
	public void aBigDripleafScannedTippedIsBuiltLevel(GameTestHelper helper) {
		run(helper, "leaf", Blocks.DIRT, Items.DIRT,
			Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(BlockStateProperties.TILT, Tilt.FULL), Items.BIG_DRIPLEAF,
			s -> s.getValue(BlockStateProperties.TILT) == Tilt.NONE, "tipped over (a player falls through it)");
	}

	//$ gametest_ticks_batch AREA '1600' '"qa1108bStuckSensor"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1108bStuckSensor")
	public void aSculkSensorScannedHearingIsBuiltListening(GameTestHelper helper) {
		run(helper, "sensor", Blocks.OAK_PLANKS, Items.OAK_PLANKS,
			Blocks.SCULK_SENSOR.defaultBlockState().setValue(BlockStateProperties.SCULK_SENSOR_PHASE, SculkSensorPhase.ACTIVE), Items.SCULK_SENSOR,
			s -> s.getValue(BlockStateProperties.SCULK_SENSOR_PHASE) == SculkSensorPhase.INACTIVE, "stuck hearing (it never hears another step)");
	}

	private static void run(GameTestHelper helper, String name, Block under, Item underItem, BlockState top, Item topItem,
							Predicate<BlockState> settled, String stuck) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The "scan": the block caught mid-moment (set without updates, so no tick is pending, as in a saved scan).
		helper.setBlock(SOURCE, under);
		level.setBlock(helper.absolutePos(SOURCE.above()), top, Block.UPDATE_CLIENTS);
		helper.assertTrue(helper.getBlockState(SOURCE.above()).equals(top), "the source isn't caught mid-moment: " + helper.getBlockState(SOURCE.above()));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1108b_stuck_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SOURCE), new Vec3i(1, 2, 1), false, Blocks.STRUCTURE_VOID);
		level.setBlock(helper.absolutePos(SOURCE.above()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.setBlock(SOURCE, Blocks.AIR);

		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(underItem, 4));
		chest.setItem(1, new ItemStack(topItem, 4));
		Villager builder = QaCarpenterStorehouse1101GameTests.builder(helper, BENCH);
		BuildSite site = QaCarpenterStorehouse1101GameTests.start(helper, builder, id, SITE);
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			if (doneAt[0] < 0 && QaCarpenterStorehouse1101GameTests.done(helper, site)) {
				doneAt[0] = level.getGameTime();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(doneAt[0] >= 0, "the " + name + " build isn't finished: stage " + site.stage() + ", " + site.skipped() + " skipped");
			BlockState built = helper.getBlockState(SITE.above());
			helper.assertTrue(built.is(top.getBlock()), "no " + name + " built on top: " + built);
			// A moment's state springs back within a second or two (a plate 20 ticks, a leaf 100, a sensor 40); give it 200.
			if (level.getGameTime() - doneAt[0] < 200) {
				helper.assertTrue(settled.test(built), "waiting for the " + name + " to settle");
			}
			helper.assertTrue(settled.test(built), "the built " + name + " is " + stuck + " 200 ticks after the build finished: " + built);
		});
	}
}
