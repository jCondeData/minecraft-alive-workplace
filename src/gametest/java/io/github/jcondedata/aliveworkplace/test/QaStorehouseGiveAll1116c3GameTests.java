package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * QA (lane-c-1116-2153) of B1338, the Storehouse board's Give all in Copperline (ROADMAP 47.4, docs/wiki/villager-jobs.md:
 * "its number is what a press gives, so two rows asking for the same item share what you carry ... it's gray when you
 * carry nothing asked for"), the edges QaStorehouseCopper1116c* leave: three rows of one item, mixed items with one
 * shared, carrying more than every row needs, a second press after the first (the count re-read from what is left),
 * over 99 (the label keeps the whole count), and the press's line in the singular and the plural. Every case checks the
 * button's number against what the press then hands over.
 */
public class QaStorehouseGiveAll1116c3GameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(24, 2, 24);
	private static final BlockPos[] BENCHES = {new BlockPos(3, 2, 3), new BlockPos(3, 2, 10), new BlockPos(3, 2, 17), new BlockPos(10, 2, 3)};

	static String label(ChoiceMenu board) {
		return board.icon(StorehouseBoard.GIVE_ALL_SLOT).getHoverName().getString();
	}

	static int dataCount(ChoiceMenu board) {
		CustomData data = board.icon(StorehouseBoard.GIVE_ALL_SLOT).get(DataComponents.CUSTOM_DATA);
		return data == null ? -1 : data.copyTag().getInt(StorehouseBoard.GIVE_ALL_COUNT);
	}

	static int inChests(GameTestHelper helper, int benches, Item item) {
		int n = 0;
		for (int i = 0; i < benches; i++) {
			n += ((Container) helper.getBlockEntity(BENCHES[i].north())).countItem(item);
		}
		return n;
	}

	static ServerPlayer carrying(GameTestHelper helper, List<Component> seen, String name, ItemStack... stacks) {
		ServerPlayer player = QaFarVillageName1114eGameTests.player(helper, seen, name);
		for (int i = 0; i < stacks.length; i++) {
			player.getInventory().setItem(i, stacks[i]);
		}
		return player;
	}

	static long said(List<Component> seen, String line) {
		return seen.stream().filter(c -> c.getString().equals(line)).count();
	}

	/**
	 * Three builders each short of 16 oak logs, 40 carried: Give all 40, the press puts 40 in their chests (16, 16, 8)
	 * and says "Gave 40 items to the workers who asked"; with nothing left that's asked for, no Give all.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1116c3GiveAllThreeRows"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1116c3GiveAllThreeRows")
	public void threeRowsOfOneItemShareWhatIsCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] v = new Villager[3];
		for (int i = 0; i < 3; i++) {
			v[i] = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[i]);
		}
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> QaStorehouseCopper1116cGameTests.withReach(helper, () -> {
			for (Villager b : v) {
				QaStorehouseCopper1116cGameTests.ask(b, Items.OAK_LOG, 16);
			}
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = carrying(helper, seen, "qa1116c3Three", new ItemStack(Items.OAK_LOG, 40));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 40", "Give all with 40 logs for three rows of 16");
			helper.assertValueEqual(dataCount(board), 40, "the button's count for the screen");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			helper.assertValueEqual(inChests(helper, 3, Items.OAK_LOG), 40, "logs in the builders' chests");
			helper.assertValueEqual(player.getInventory().countItem(Items.OAK_LOG), 0, "logs left in the pack");
			helper.assertValueEqual(said(seen, "Gave 40 items to the workers who asked"), 1L, "the press's line " + seen.stream().map(Component::getString).toList());
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all after everything carried was given: '" + label(board) + "'");
		}));
	}

	/**
	 * Two builders short of 16 oak logs and a third of 8 glass, 10 logs and 5 glass carried: Give all 15 (the logs shared
	 * once, the glass its own), and a press gives exactly that, 10 logs and 5 glass.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1116c3GiveAllMixed"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1116c3GiveAllMixed")
	public void mixedItemsWithOneSharedCountEachOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] v = new Villager[3];
		for (int i = 0; i < 3; i++) {
			v[i] = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[i]);
		}
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> QaStorehouseCopper1116cGameTests.withReach(helper, () -> {
			QaStorehouseCopper1116cGameTests.ask(v[0], Items.OAK_LOG, 16);
			QaStorehouseCopper1116cGameTests.ask(v[1], Items.GLASS, 8);
			QaStorehouseCopper1116cGameTests.ask(v[2], Items.OAK_LOG, 16);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = carrying(helper, seen, "qa1116c3Mixed", new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.GLASS, 5));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 15", "Give all with 10 logs for two rows and 5 glass for one");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			helper.assertValueEqual(inChests(helper, 3, Items.OAK_LOG), 10, "logs given");
			helper.assertValueEqual(inChests(helper, 3, Items.GLASS), 5, "glass given");
			helper.assertValueEqual(said(seen, "Gave 15 items to the workers who asked"), 1L, "the press's line " + seen.stream().map(Component::getString).toList());
		}));
	}

	/**
	 * Two builders short of 16 oak logs, 50 carried (more than both need): Give all 32, the press gives 32 and 18 stay in
	 * the pack; the rows are then met, so there's no Give all though logs are still carried.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1116c3GiveAllMore"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1116c3GiveAllMore")
	public void carryingMoreThanEveryRowNeedsGivesOnlyWhatIsAsked(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager a = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[0]);
		Villager b = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[1]);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> QaStorehouseCopper1116cGameTests.withReach(helper, () -> {
			QaStorehouseCopper1116cGameTests.ask(a, Items.OAK_LOG, 16);
			QaStorehouseCopper1116cGameTests.ask(b, Items.OAK_LOG, 16);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = carrying(helper, seen, "qa1116c3More", new ItemStack(Items.OAK_LOG, 50));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 32", "Give all with 50 logs for two rows of 16");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			helper.assertValueEqual(inChests(helper, 2, Items.OAK_LOG), 32, "logs given");
			helper.assertValueEqual(player.getInventory().countItem(Items.OAK_LOG), 18, "logs left in the pack");
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all once every row is met: '" + label(board) + "'");
		}));
	}

	/**
	 * A press, then the count afterwards: two rows of 16 oak logs, 10 carried, a press gives 10 ("Gave 10 items..."); with
	 * 64 more logs the board reads Give all 22 (the 32 asked less the 10 given), and a second press gives exactly 22.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1116c3GiveAllAgain"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1116c3GiveAllAgain")
	public void theCountAfterAPressIsWhatIsStillAsked(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager a = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[0]);
		Villager b = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[1]);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> QaStorehouseCopper1116cGameTests.withReach(helper, () -> {
			QaStorehouseCopper1116cGameTests.ask(a, Items.OAK_LOG, 16);
			QaStorehouseCopper1116cGameTests.ask(b, Items.OAK_LOG, 16);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = carrying(helper, seen, "qa1116c3Again", new ItemStack(Items.OAK_LOG, 10));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 10", "the first count");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			helper.assertValueEqual(inChests(helper, 2, Items.OAK_LOG), 10, "the first press");
			helper.assertValueEqual(said(seen, "Gave 10 items to the workers who asked"), 1L, "the first press's line " + seen.stream().map(Component::getString).toList());
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all with nothing left carried: '" + label(board) + "'");
			player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 64));
			board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 22", "the count after 10 of the 32 were given");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			helper.assertValueEqual(inChests(helper, 2, Items.OAK_LOG), 32, "after the second press");
			helper.assertValueEqual(player.getInventory().countItem(Items.OAK_LOG), 42, "left in the pack after the second press");
		}));
	}

	/**
	 * One log carried for a row of 16: "Give all 1" and "Gave 1 item to the worker who asked" (the singular); four rows of
	 * 40 logs with 192 carried: the label keeps the whole count, Give all 160, past the button's 99 stack, and a press
	 * gives the 160.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1116c3GiveAllOneAndMany"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1116c3GiveAllOneAndMany")
	public void oneItemInTheSingularAndOverNinetyNine(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager[] v = new Villager[4];
		for (int i = 0; i < 4; i++) {
			v[i] = QaStorehouseCopper1116cGameTests.builder(helper, BENCHES[i]);
		}
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> QaStorehouseCopper1116cGameTests.withReach(helper, () -> {
			QaStorehouseCopper1116cGameTests.ask(v[0], Items.GLASS, 16);
			List<Component> seen = new ArrayList<>();
			ServerPlayer one = carrying(helper, seen, "qa1116c3One", new ItemStack(Items.GLASS, 1));
			ChoiceMenu board = StorehouseBoard.boardForTest(one, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 1", "Give all with one glass");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, one);
			helper.assertValueEqual(said(seen, "Gave 1 item to the worker who asked"), 1L, "the singular line " + seen.stream().map(Component::getString).toList());
			for (Villager b : v) {
				QaStorehouseCopper1116cGameTests.ask(b, Items.OAK_LOG, 40);
			}
			seen.clear();
			ServerPlayer many = carrying(helper, seen, "qa1116c3Many", new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64));
			board = StorehouseBoard.boardForTest(many, helper.absolutePos(STOREHOUSE));
			helper.assertValueEqual(label(board), "Give all 160", "Give all with 192 logs for four rows of 40");
			helper.assertValueEqual(dataCount(board), 160, "the button's count for the screen past 99");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, many);
			helper.assertValueEqual(inChests(helper, 4, Items.OAK_LOG), 160, "logs given");
			helper.assertValueEqual(said(seen, "Gave 160 items to the workers who asked"), 1L, "the plural line " + seen.stream().map(Component::getString).toList());
		}));
	}
}
