package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1103-0353), B847 and B848 from the spec: a Drop Box whose goods fit the Storehouse is emptied past a nearer
 * one whose goods don't. The corners the fix's own tests and lane B's mixed boxes (QaDropBoxMixed1103GameTests) leave: a
 * porter whose Storehouse gets room later goes back for the nearer box he passed over, and an empty Drop Box is never
 * goods without room. And B842's tribute at the edges of "1 emerald".
 */
public class QaDropBoxRoom1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return QaStackRoom1102cGameTests.count(helper, pos, item);
	}

	/**
	 * B848's shape, then room: once the farther box's logs are in, a stack of cobblestone leaves the Storehouse chest, and
	 * the porter goes back for the nearer box's 32 cobblestone it had passed over.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaDropBoxRoom1103cPorterRoomLater"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaDropBoxRoom1103cPorterRoomLater")
	public void qaAPorterGoesBackForTheNearerBoxOnceTheStorehouseHasRoom(GameTestHelper helper) {
		Villager porter = QaLaneC1102eGameTests.porterTwoBoxes(helper, true);
		BlockPos near = QaLaneC1102eGameTests.P_NEAR_BOX;
		BlockPos far = QaLaneC1102eGameTests.P_FAR_BOX;
		BlockPos chest = QaLaneC1102eGameTests.P_STORE_CHEST;
		boolean[] roomMade = {false};
		helper.onEachTick(() -> {
			if (!roomMade[0] && count(helper, chest, Items.OAK_LOG) == 33) {
				helper.assertTrue(count(helper, near, Items.COBBLESTONE) == 32, "the cobblestone left the nearer box before there was room: "
					+ count(helper, near, Items.COBBLESTONE) + " left");
				((Container) helper.getBlockEntity(chest)).setItem(0, ItemStack.EMPTY);
				roomMade[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(porter);
			helper.assertTrue(roomMade[0], "the farther box's logs never came in (" + count(helper, far, Items.OAK_LOG) + " left)");
			helper.assertTrue(count(helper, near, Items.COBBLESTONE) == 0 && count(helper, chest, Items.COBBLESTONE) == 25 * 64 + 32
				&& bag.count(Items.COBBLESTONE) == 0, "after room opened the Storehouse has " + count(helper, chest, Items.COBBLESTONE)
				+ " of " + (25 * 64 + 32) + " cobblestone; the box keeps " + count(helper, near, Items.COBBLESTONE) + ", the bag " + bag.count(Items.COBBLESTONE));
			LOG.info("[QA 1103] B848 porter: room opened later and the passed-over Drop Box's 32 cobblestone came in");
		});
	}

	/**
	 * An empty Drop Box by a porter's empty Storehouse, and nothing anywhere to carry: the porter says he has "nothing to
	 * carry right now", never that "the storehouse chests are full" (an empty box is no box of goods without room).
	 */
	//$ gametest_ticks_batch AREA '600' '"qaDropBoxRoom1103cPorterEmptyBox"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaDropBoxRoom1103cPorterEmptyBox")
	public void qaAnEmptyDropBoxNeverMakesThePorterSayTheStorehouseIsFull(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Leftovers.village(helper, 48);
		QaStackRoom1102cGameTests.strayDropBoxes(helper);
		BlockPos storehouse = QaLaneC1102eGameTests.P_STOREHOUSE;
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(QaLaneC1102eGameTests.P_STORE_CHEST, net.minecraft.world.level.block.Blocks.CHEST);
		helper.setBlock(QaLaneC1102eGameTests.P_NEAR_BOX, ModBlocks.DROP_BOX);
		Villager porter = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(18, 2, 18));
		io.github.jcondedata.aliveworkplace.store.Porters.employ(helper.getLevel(), porter, helper.absolutePos(storehouse));
		boolean[] saidFull = {false};
		helper.onEachTick(() -> {
			var entry = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(porter, helper.getLevel().getGameTime());
			if (entry != null && entry.line().getString().equals("the storehouse chests are full")) {
				saidFull[0] = true;
			}
		});
		helper.runAtTickTime(500, () -> {
			var entry = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(porter, helper.getLevel().getGameTime());
			String line = entry == null ? "(none)" : entry.line().getString();
			helper.assertFalse(saidFull[0], "the porter said the storehouse chests are full, by an empty Drop Box and an empty Storehouse");
			helper.assertTrue(line.equals("nothing to carry right now"), "the porter's line: " + line);
			LOG.info("[QA 1103] B848 porter: an empty Drop Box left him '{}'", line);
			helper.succeed();
		});
	}

	// ---- B842: the Bandit King's tribute at the edges of "1 emerald" ----

	/** The top of the singular: a treasury of 5.99 emeralds, a third is 1.99, and the line reads "1 emerald". */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTribute1103Top"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTribute1103Top")
	public void qaATributeJustUnderTwoEmeraldsReadsInTheSingular(GameTestHelper helper) {
		String line = QaKingTribute1102dBugTests.tribute(helper, 599);
		helper.assertTrue(line.equals("The Bandit King took 1 emerald in tribute and left his fort a ruin."), "the tribute line at 5.99: " + line);
		LOG.info("[QA 1103] B842: 5.99 emeralds in the treasury reads '{}'", line);
		helper.succeed();
	}

	/** Past the singular: a treasury of 9 emeralds, 3 taken, reads "3 emeralds" (the .one form isn't used past one). */
	//$ gametest_ticks_batch HUGE '60' '"qaKingTribute1103Three"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingTribute1103Three")
	public void qaATributeOfThreeEmeraldsReadsInThePlural(GameTestHelper helper) {
		String line = QaKingTribute1102dBugTests.tribute(helper, 900);
		helper.assertTrue(line.equals("The Bandit King took 3 emeralds in tribute and left his fort a ruin."), "the tribute line at 9: " + line);
		LOG.info("[QA 1103] B842: 9 emeralds in the treasury reads '{}'", line);
		helper.succeed();
	}
}
