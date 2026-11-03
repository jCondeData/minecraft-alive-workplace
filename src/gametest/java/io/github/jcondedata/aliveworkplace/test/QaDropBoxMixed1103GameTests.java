package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1103-0353), B847 and B848 from the spec: a Drop Box whose goods fit the Storehouse is emptied past a nearer
 * one whose goods don't. The corners the fix's own tests leave: a nearer box that holds both kinds (the goods that fit
 * come in, the rest stay in the box, none dropped or kept in hand), for the golem and the porter; and a porter whose
 * Storehouse gets room later goes back for the nearer box it passed over.
 */
public class QaDropBoxMixed1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return QaStackRoom1102cGameTests.count(helper, pos, item);
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	/**
	 * The golem's Storehouse has room for logs only; the nearer Drop Box holds 32 cobblestone and 16 logs, the farther 32
	 * logs. All 48 logs come in, the 32 cobblestone stay in the nearer box, the golem ends empty-handed, nothing dropped.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaDropBoxMixed1103Golem"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaDropBoxMixed1103Golem")
	public void qaAGolemTakesTheLogsFromAMixedDropBoxAndLeavesTheCobblestone(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		BlockPos near = QaStackRoom1102cGameTests.HAUL_BOX;
		BlockPos far = QaLaneC1102eGameTests.FAR_BOX;
		helper.setBlock(near, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(near)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		((Container) helper.getBlockEntity(near)).setItem(1, new ItemStack(Items.OAK_LOG, 16));
		helper.setBlock(far, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(far)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 0);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		BlockPos chest = QaStackRoom1102cGameTests.STORE_CHEST;
		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, chest, Items.OAK_LOG) == 49 && count(helper, near, Items.OAK_LOG) == 0
				&& count(helper, far, Items.OAK_LOG) == 0 && HaulerGolems.load(golem).isEmpty(),
				"the Storehouse has " + count(helper, chest, Items.OAK_LOG) + " of 49 logs; boxes keep " + count(helper, near, Items.OAK_LOG)
					+ " and " + count(helper, far, Items.OAK_LOG) + "; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(count(helper, near, Items.COBBLESTONE) == 32 && count(helper, chest, Items.COBBLESTONE) == 26 * 64
				&& onGround(helper, Items.COBBLESTONE) == 0,
				"cobblestone moved: the box keeps " + count(helper, near, Items.COBBLESTONE) + " of 32, the chest has "
					+ count(helper, chest, Items.COBBLESTONE) + ", " + onGround(helper, Items.COBBLESTONE) + " on the ground");
			LOG.info("[QA 1103] B847 golem: a mixed Drop Box gave up its 16 logs and kept its 32 cobblestone; the farther box's 32 logs came in");
		});
	}

	/** The porter's kin of the golem's mixed box: 16 logs in the nearer box with its 32 cobblestone, 32 in the farther. */
	//$ gametest_ticks_batch AREA '3600' '"qaDropBoxMixed1103Porter"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaDropBoxMixed1103Porter")
	public void qaAPorterTakesTheLogsFromAMixedDropBoxAndLeavesTheCobblestone(GameTestHelper helper) {
		Villager porter = QaLaneC1102eGameTests.porterTwoBoxes(helper, true);
		BlockPos near = QaLaneC1102eGameTests.P_NEAR_BOX;
		BlockPos far = QaLaneC1102eGameTests.P_FAR_BOX;
		BlockPos chest = QaLaneC1102eGameTests.P_STORE_CHEST;
		((Container) helper.getBlockEntity(near)).setItem(1, new ItemStack(Items.OAK_LOG, 16));
		helper.succeedWhen(() -> {
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(porter);
			helper.assertTrue(count(helper, chest, Items.OAK_LOG) == 49 && count(helper, near, Items.OAK_LOG) == 0
				&& count(helper, far, Items.OAK_LOG) == 0 && bag.count(Items.OAK_LOG) == 0,
				"the Storehouse has " + count(helper, chest, Items.OAK_LOG) + " of 49 logs; boxes keep " + count(helper, near, Items.OAK_LOG)
					+ " and " + count(helper, far, Items.OAK_LOG) + "; the porter's bag " + bag.count(Items.OAK_LOG));
			helper.assertTrue(count(helper, near, Items.COBBLESTONE) == 32 && bag.count(Items.COBBLESTONE) == 0
				&& count(helper, chest, Items.COBBLESTONE) == 26 * 64 && onGround(helper, Items.COBBLESTONE) == 0,
				"cobblestone moved: the box keeps " + count(helper, near, Items.COBBLESTONE) + " of 32, the bag " + bag.count(Items.COBBLESTONE)
					+ ", the chest " + count(helper, chest, Items.COBBLESTONE) + ", " + onGround(helper, Items.COBBLESTONE) + " on the ground");
			LOG.info("[QA 1103] B848 porter: a mixed Drop Box gave up its 16 logs and kept its 32 cobblestone; the farther box's 32 logs came in");
		});
	}

	/**
	 * B848's shape, then room: once the farther box's logs are in, a stack of cobblestone leaves the Storehouse chest, and
	 * the porter goes back for the nearer box's 32 cobblestone it had passed over.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaDropBoxMixed1103PorterRoomLater"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaDropBoxMixed1103PorterRoomLater")
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
	//$ gametest_ticks_batch AREA '600' '"qaDropBoxMixed1103PorterEmptyBox"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaDropBoxMixed1103PorterEmptyBox")
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
