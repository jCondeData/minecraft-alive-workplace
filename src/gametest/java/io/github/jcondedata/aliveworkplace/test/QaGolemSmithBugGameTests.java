package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 29.15 (qa-lane-d-1027-1612): tests the mod fails today, each a spec breach. NOT registered in fabric.mod.json
 * until the mod is fixed (then register it, or fold the tests into QaGolemSmithEdgesGameTests).
 */
public class QaGolemSmithBugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos TABLE = new BlockPos(10, 2, 10);

	/**
	 * A smith working under a low roof (2 blocks of room, too low for a golem, roofed 6 blocks all round): the spec says a
	 * new golem stands on solid ground beside the Smith, never on a roof. Today it is put on top of the roof.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSmithBugRoof"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithBugRoof")
	public void aGolemIsNeverPutOnTheRoof(GameTestHelper helper) {
		QaGolemSmithEdgesGameTests.setUp(helper);
		Legend legend = QaGolemSmithEdgesGameTests.staged(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 4; x <= 16; x++) {
			for (int z = 2; z <= 14; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
			}
		}
		Villager smith = QaGolemSmithEdgesGameTests.smith(helper, legend);
		QaGolemSmithEdgesGameTests.villagers(helper, 4, 18);
		Container chest = helper.getBlockEntity(TABLE.east());
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		chest.setItem(2, new ItemStack(Items.CHEST, 1));
		IronGolem golem = GolemSmith.forgeOne(level, helper.absolutePos(HALL), smith);
		if (golem != null) {
			BlockPos at = golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
			helper.assertTrue(at.getY() < 4, "the golem was put on the roof (roof at y 4, golem at " + at + ", the smith at " + smith.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ")");
		} else {
			helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 4, "no golem, but the iron was taken");
		}
		helper.succeed();
	}

	/** 7 forged golems: the hall shows 6 lines and "...and 1 more golem" (not "1 more golems"). */
	//$ gametest_ticks_batch AREA '100' '"qaSmithBugMore"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithBugMore")
	public void theHallsOverflowLineCountsOneGolem(GameTestHelper helper) {
		QaGolemSmithEdgesGameTests.setUp(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 7; i++) {
			IronGolem g = GolemSmith.build(level, helper.absolutePos(new BlockPos(5 + 3 * i, 1, 20)), GolemSmith.Role.HAULER);
			helper.assertTrue(g != null, "no golem " + i);
			g.setNoAi(true);
		}
		List<Component> lines = GolemSmith.hallLines(level, helper.absolutePos(HALL));
		helper.assertTrue(lines.size() == 7, "the hall's lines: " + lines.stream().map(Component::getString).toList());
		String last = lines.get(6).getString();
		helper.assertTrue(last.equals("...and 1 more golem"), "the last line: " + last);
		helper.succeed();
	}
}
