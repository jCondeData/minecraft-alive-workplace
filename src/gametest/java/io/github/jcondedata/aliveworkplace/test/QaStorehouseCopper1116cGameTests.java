package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.BoardRows;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA, lane C 2026-11-16, ROADMAP 47.4 (the Storehouse board in Copperline), from the spec and the 47.4 notes: Give all
 * never hands over more than a row asks (and leaves the rest in the pack); a row the store already covers says Stocked,
 * reads full and the Missing filter hides it; a full chest takes nothing and nothing is lost; the stock orders page still
 * opens from the board and Back brings the board (and Give all) back.
 */
public class QaStorehouseCopper1116cGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(24, 2, 24);
	private static final BlockPos STORE_CHEST = new BlockPos(25, 2, 24);

	static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(bench.north(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER));
		return v;
	}

	static void ask(Villager v, Item item, int count) {
		Requests.post(v, new ItemStack(item), count, item.getDescription(), new Requests.ExactItem(item));
	}

	static int rowOf(ChoiceMenu board, Item item) {
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/** Runs {@code body} with the village's reach on (GameTests run with none), then succeeds. */
	static void withReach(GameTestHelper helper, Runnable body) {
		Village.RADIUS = 48;
		try {
			body.run();
		} finally {
			Village.RADIUS = 0;
		}
		helper.succeed();
	}

	/**
	 * Carrying more than asked: 30 logs for a request of 16. The row and Give all both say 16; Give all puts 16 in the
	 * builder's chest and leaves 14 in the pack; then the button is gone. The orders page opens from the board, shows no
	 * Give all, and Back brings the board back.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaC1116StoreGiveAllCap"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaC1116StoreGiveAllCap")
	public void giveAllNeverGivesMoreThanAsked(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos bench = new BlockPos(3, 2, 3);
		Villager a = builder(helper, bench);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> withReach(helper, () -> {
			ask(a, Items.OAK_LOG, 16);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 30));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			int slot = rowOf(board, Items.OAK_LOG);
			helper.assertTrue(slot >= 0, "the board doesn't list the logs");
			CompoundTag row = BoardRows.row(board.icon(slot));
			helper.assertTrue(BoardRows.give(row) == 16 && BoardRows.state(row) == BoardRows.State.GIVE, "the row's button: Give " + BoardRows.give(row));
			String label = board.icon(StorehouseBoard.GIVE_ALL_SLOT).getHoverName().getString();
			helper.assertTrue(label.equals("Give all 16"), "Give all: '" + label + "'");
			// the stock orders page and back
			board.press(StorehouseBoard.ORDERS_SLOT, player);
			helper.assertTrue(!board.icon(StorehouseBoard.BACK_SLOT).isEmpty(), "the orders page didn't open");
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all shows on the orders page");
			board.press(StorehouseBoard.BACK_SLOT, player);
			helper.assertTrue(rowOf(board, Items.OAK_LOG) >= 0 && !board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Back didn't bring the board back");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			Container chest = (Container) helper.getBlockEntity(bench.north());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 16, "the builder's chest: " + chest.countItem(Items.OAK_LOG));
			helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 14, "left in the pack: " + player.getInventory().countItem(Items.OAK_LOG));
		}));
	}

	/**
	 * A row's button and count color at their edges: carrying exactly one gives one (Give 1, kept by I carry); none in the
	 * store reads empty (red), a quarter reads part, under a quarter low; a tool of any kind (-1) is unknown, never stocked.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRowsButtonAndColorAtTheirEdges(GameTestHelper helper) {
		ItemStack one = new ItemStack(Items.OAK_LOG);
		BoardRows.tag(one, 5, 0, 1, -1, BoardRows.Group.BUILDERS, 3, "");
		CompoundTag row = BoardRows.row(one);
		helper.assertTrue(BoardRows.state(row) == BoardRows.State.GIVE && BoardRows.give(row) == 1, "carrying one: " + BoardRows.state(row) + " " + BoardRows.give(row));
		helper.assertTrue(BoardRows.shown(row, BoardRows.Group.EVERYONE, false, true), "I carry hides a row the player can give one of");
		helper.assertTrue(BoardRows.tone(0, 5) == BoardRows.Tone.EMPTY, "0 of 5: " + BoardRows.tone(0, 5));
		helper.assertTrue(BoardRows.tone(1, 4) == BoardRows.Tone.PART && BoardRows.tone(1, 5) == BoardRows.Tone.LOW, "1 of 4 / 1 of 5: "
			+ BoardRows.tone(1, 4) + " / " + BoardRows.tone(1, 5));
		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		BoardRows.tag(tool, 1, -1, 0, -1, BoardRows.Group.WORKERS, 3, "");
		helper.assertTrue(BoardRows.state(BoardRows.row(tool)) == BoardRows.State.NONE && BoardRows.tone(-1, 1) == BoardRows.Tone.UNKNOWN,
			"a tool of any kind: " + BoardRows.state(BoardRows.row(tool)));
		helper.succeed();
	}

	/**
	 * A row the store covers (16 of 16 logs in the storehouse's chest) and the player carrying none: Stocked, full, hidden
	 * by Missing, kept by Everyone; no Give all. A builder whose chest is full: Give all hands nothing over and the player
	 * keeps every block.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaC1116StoreStockedFull"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaC1116StoreStockedFull")
	public void aStockedRowAndAFullChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos benchA = new BlockPos(3, 2, 3);
		BlockPos benchB = new BlockPos(3, 2, 12);
		Villager a = builder(helper, benchA);
		Villager b = builder(helper, benchB);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(STORE_CHEST)).setItem(0, new ItemStack(Items.OAK_LOG, 16));
		helper.runAfterDelay(20, () -> withReach(helper, () -> {
			ask(a, Items.OAK_LOG, 16);
			ServerPlayer empty = helper.makeMockServerPlayerInLevel();
			ChoiceMenu board = StorehouseBoard.boardForTest(empty, helper.absolutePos(STOREHOUSE));
			int slot = rowOf(board, Items.OAK_LOG);
			helper.assertTrue(slot >= 0, "the board doesn't list the logs");
			CompoundTag row = BoardRows.row(board.icon(slot));
			helper.assertTrue(row.getInt("store") == 16 && BoardRows.state(row) == BoardRows.State.STOCKED
				&& BoardRows.tone(row.getInt("store"), row.getInt("need")) == BoardRows.Tone.FULL, "a stocked row: " + row);
			helper.assertTrue(!BoardRows.shown(row, BoardRows.Group.EVERYONE, true, false) && BoardRows.shown(row, BoardRows.Group.EVERYONE, false, false),
				"Missing should hide a stocked row and Everyone keep it");
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all with nothing carried");

			// a full chest
			Container chestB = (Container) helper.getBlockEntity(benchB.north());
			for (int i = 0; i < chestB.getContainerSize(); i++) {
				chestB.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			ask(b, Items.GLASS, 12);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().setItem(0, new ItemStack(Items.GLASS, 8));
			board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertTrue(!board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "no Give all while carrying glass that is asked for");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			helper.assertTrue(player.getInventory().countItem(Items.GLASS) == 8, "glass lost into a full chest: " + player.getInventory().countItem(Items.GLASS) + " left");
			helper.assertTrue(chestB.countItem(Items.GLASS) == 0, "glass in a full chest");
		}));
	}
}
