package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.OUT_DUST;
import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.OUT_DUST_LAMP;
import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.OUT_LADDER;
import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.OUT_LAMP;
import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.OUT_TORCH;
import static io.github.jcondedata.aliveworkplace.test.QaMoverRedstone1121eGameTests.SETTLE;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.test.MoverGameTests.Setup;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-21), failing on main: when the Mover takes a house down, the blocks round its old spot that
 * aren't part of it are never told (blocks come down as air with no neighbor or shape updates; B1216's note: "the old
 * spot's outside neighbors aren't told when blocks are taken down"). A player then finds, where the house stood, a
 * lamp still lit and dust still glowing with no power left, and a torch and a ladder hanging on a wall that's gone.
 * The house and wiring of {@link QaMoverRedstone1121eGameTests}.
 */
public class QaMoverRedstone1121eBugGameTests implements FabricGameTest {

	/** 2a. The wall's redstone blocks went with the house: the lamp and the dust it fed outside go dark. */
	//$ gametest_batch AREA '"qa_e1121_mover_redstone_old_power"'
	@GameTest(template = AREA, batch = "qa_e1121_mover_redstone_old_power")
	public void theLampOutsideGoesDarkWhenTheWallFeedingItMovesAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup[] s = new Setup[1];
		QaMoverRedstone1121eGameTests.build(helper);
		helper.runAfterDelay(5, () -> {
			s[0] = QaMoverRedstone1121eGameTests.capture(helper, "qa_redstone_old_power");
			MoveSite site = QaMoverRedstone1121eGameTests.run(level, s[0], 0, java.util.EnumSet.noneOf(MoveSite.Stage.class));
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(level.getBlockState(helper.absolutePos(new net.minecraft.core.BlockPos(2, 3, 5))).isAir(), "the old wall still stands");
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			List<String> wrong = new ArrayList<>();
			BlockState lamp = level.getBlockState(helper.absolutePos(OUT_LAMP));
			if (lamp.getValue(RedstoneLampBlock.LIT)) {
				wrong.add("the lamp at " + helper.absolutePos(OUT_LAMP).toShortString() + " is still lit");
			}
			BlockState dust = level.getBlockState(helper.absolutePos(OUT_DUST));
			if (dust.getValue(RedStoneWireBlock.POWER) != 0) {
				wrong.add("the dust at " + helper.absolutePos(OUT_DUST).toShortString() + " still has power " + dust.getValue(RedStoneWireBlock.POWER));
			}
			BlockState dustLamp = level.getBlockState(helper.absolutePos(OUT_DUST_LAMP));
			if (dustLamp.getValue(RedstoneLampBlock.LIT)) {
				wrong.add("the dust's lamp at " + helper.absolutePos(OUT_DUST_LAMP).toShortString() + " is still lit");
			}
			helper.assertTrue(wrong.isEmpty(), SETTLE + " ticks after the house moved away, with no power left at its old spot: " + String.join("; ", wrong));
			helper.succeed();
		});
	}

	/** 2b. A torch and a ladder a player hung on the house's outside walls don't stay hanging in the air once the walls are gone. */
	//$ gametest_batch AREA '"qa_e1121_mover_redstone_old_hanging"'
	@GameTest(template = AREA, batch = "qa_e1121_mover_redstone_old_hanging")
	public void aTorchAndLadderOnTheOutsideWallDontFloatAfterTheMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverRedstone1121eGameTests.build(helper);
		helper.runAfterDelay(5, () -> {
			Setup s = QaMoverRedstone1121eGameTests.capture(helper, "qa_redstone_old_hanging");
			helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_TORCH)).is(Blocks.WALL_TORCH), "setup: no torch on the wall");
			helper.assertTrue(level.getBlockState(helper.absolutePos(OUT_LADDER)).is(Blocks.LADDER), "setup: no ladder on the wall");
			MoveSite site = QaMoverRedstone1121eGameTests.run(level, s, 0, java.util.EnumSet.noneOf(MoveSite.Stage.class));
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
		});
		helper.runAfterDelay(5 + SETTLE, () -> {
			List<String> wrong = new ArrayList<>();
			BlockState torch = level.getBlockState(helper.absolutePos(OUT_TORCH));
			if (!torch.canSurvive(level, helper.absolutePos(OUT_TORCH))) {
				wrong.add("a " + torch.getBlock().getName().getString() + " floats at " + helper.absolutePos(OUT_TORCH).toShortString());
			}
			BlockState ladder = level.getBlockState(helper.absolutePos(OUT_LADDER));
			if (!ladder.canSurvive(level, helper.absolutePos(OUT_LADDER))) {
				wrong.add("a " + ladder.getBlock().getName().getString() + " floats at " + helper.absolutePos(OUT_LADDER).toShortString());
			}
			helper.assertTrue(wrong.isEmpty(), "where the house stood: " + String.join("; ", wrong));
			helper.succeed();
		});
	}
}
