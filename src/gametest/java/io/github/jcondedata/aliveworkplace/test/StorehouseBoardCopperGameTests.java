package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.BoardRows;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 47.4, the Storehouse board in Copperline: each request's button carries the table's columns (how many are
 * asked for, held in the store and carried, who asked and who brings it) beside its unchanged lore; the rail's filters,
 * the Give / Stocked / Have 0 button and the count's color follow those; Give all (the board's one copper button, also
 * Shift-click) hands over everything the player carries that a row asks for; and every new sentence has its words.
 */
public class StorehouseBoardCopperGameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The storehouse and its chest, more than 8 blocks from the benches so the store and the builders' chests are apart. */
	private static final BlockPos STOREHOUSE = new BlockPos(24, 2, 24);
	private static final BlockPos STORE_CHEST = new BlockPos(25, 2, 24);

	private static Villager builder(GameTestHelper helper, BlockPos bench, String name) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(bench.north(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER)); // at once, not on the brain's next pass
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
	}

	private static void ask(Villager v, Item item, int count) {
		Requests.post(v, new ItemStack(item), count, item.getDescription(), new Requests.ExactItem(item));
	}

	private static int rowOf(ChoiceMenu board, Item item) {
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	private static String lore(ItemStack icon) {
		ItemLore lines = icon.get(DataComponents.LORE);
		return lines == null ? "" : lines.lines().stream().map(Component::getString).collect(Collectors.joining(" | "));
	}

	/**
	 * Through the player's own entry point (the board a click on the Storehouse opens): a builder named Mira short of 16
	 * logs, 5 in the store, 4 in the player's pack and Bo the Lumberjack nearby: her row says 16 asked, 5 held, 4 carried,
	 * Builders, "Mira" / the Builder, "Bo" / the Lumberjack, Give 4; the lore reads as it always did; Give all is there.
	 */
	//$ gametest_ticks_batch HUGE '200' '"storehouseCopperRow"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "storehouseCopperRow")
	public void aRequestsRowCarriesTheTablesColumns(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager mira = builder(helper, new BlockPos(3, 2, 3), "Mira");
		Villager bo = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
		bo.setNoAi(true);
		bo.setVillagerData(bo.getVillagerData().setProfession(ModVillagers.LUMBERJACK));
		bo.setCustomName(Component.literal("Bo"));
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(STORE_CHEST)).setItem(0, new ItemStack(Items.OAK_LOG, 5));
		helper.runAfterDelay(20, () -> {
			// the village's reach: GameTests run with none (WorkplaceConfig), so the board would list nobody
			Village.RADIUS = 48;
			try {
				aRowsColumns(helper, mira);
			} finally {
				Village.RADIUS = 0;
			}
			helper.succeed();
		});
	}

	private static void aRowsColumns(GameTestHelper helper, Villager mira) {
		{
			ask(mira, Items.OAK_LOG, 16);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			int slot = rowOf(board, Items.OAK_LOG);
			helper.assertTrue(slot >= 0, "the board doesn't list the logs");
			ItemStack icon = board.icon(slot);
			CompoundTag row = BoardRows.row(icon);
			helper.assertTrue(row != null, "the logs' row has no columns");
			helper.assertTrue(row.getInt("need") == 16 && row.getInt("store") == 5 && row.getInt("have") == 4,
				"asked, held, carried: " + row);
			helper.assertTrue(BoardRows.Group.of(row.getString("group")) == BoardRows.Group.BUILDERS, "group: " + row.getString("group"));
			helper.assertTrue(BoardRows.state(row) == BoardRows.State.GIVE && BoardRows.give(row) == 4, "the button: " + BoardRows.state(row));
			helper.assertTrue(BoardRows.tone(5, 16) == BoardRows.Tone.PART, "5 of 16: " + BoardRows.tone(5, 16));
			List<Component> lines = BoardRows.lore(icon);
			Component[] needed = BoardRows.neededBy(row, lines);
			helper.assertTrue(needed[0].getString().equals("Mira") && needed[1].getString().equals("Builder"),
				"Needed by: " + needed[0].getString() + " / " + needed[1].getString());
			BoardRows.BroughtBy by = BoardRows.broughtBy(row, lines);
			helper.assertTrue(!by.nobody() && by.who().getString().equals("Bo") && by.how().getString().equals("Lumberjack"),
				"Brought by: " + by.who().getString() + " / " + by.how().getString());
			helper.assertTrue(BoardRows.what(icon.getHoverName()).getString().equals("Oak Log"), "what: " + BoardRows.what(icon.getHoverName()).getString());
			String text = lore(icon);
			helper.assertTrue(text.contains("In the storehouse: 5") && text.contains("Click to give 4 of yours")
				&& text.contains("Gathered by: the Lumberjack (Bo)"), "the lore as before: " + text);
			ItemStack all = board.icon(StorehouseBoard.GIVE_ALL_SLOT);
			helper.assertTrue(all.getHoverName().getString().equals("Give all 4"), "Give all: '" + all.getHoverName().getString() + "'");
		}
	}

	/**
	 * Give all, as a click on the board's copper button: two builders short of logs and glass get what the player carries
	 * (10 logs, 8 glass) in their own chests, the dirt nobody asked for stays, and with nothing left to give the button
	 * is gone. A player carrying nothing asked for never sees it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"storehouseCopperGiveAll"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "storehouseCopperGiveAll")
	public void giveAllHandsOverEverythingAsked(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos benchA = new BlockPos(3, 2, 3);
		BlockPos benchB = new BlockPos(3, 2, 12);
		Villager a = builder(helper, benchA, null);
		Villager b = builder(helper, benchB, "Pell");
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(20, () -> {
			Village.RADIUS = 48; // GameTests run with no village reach (WorkplaceConfig)
			try {
				giveAll(helper, a, b, benchA, benchB);
			} finally {
				Village.RADIUS = 0;
			}
			helper.succeed();
		});
	}

	private static void giveAll(GameTestHelper helper, Villager a, Villager b, BlockPos benchA, BlockPos benchB) {
		{
			ask(a, Items.OAK_LOG, 16);
			ask(b, Items.GLASS, 12);
			ServerPlayer empty = helper.makeMockServerPlayerInLevel();
			ChoiceMenu none = StorehouseBoard.boardForTest(empty, helper.absolutePos(STOREHOUSE));
			helper.assertTrue(rowOf(none, Items.OAK_LOG) >= 0 && rowOf(none, Items.GLASS) >= 0, "the board doesn't list both");
			helper.assertTrue(none.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all with nothing to give");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 10));
			player.getInventory().setItem(1, new ItemStack(Items.GLASS, 8));
			player.getInventory().setItem(2, new ItemStack(Items.DIRT, 5));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).getHoverName().getString().equals("Give all 18"),
				"Give all: '" + board.icon(StorehouseBoard.GIVE_ALL_SLOT).getHoverName().getString() + "'");
			board.press(StorehouseBoard.GIVE_ALL_SLOT, player);
			Container chestA = (Container) helper.getBlockEntity(benchA.north());
			Container chestB = (Container) helper.getBlockEntity(benchB.north());
			helper.assertTrue(chestA.countItem(Items.OAK_LOG) == 10, "the first builder's chest: " + chestA.countItem(Items.OAK_LOG) + " logs");
			helper.assertTrue(chestB.countItem(Items.GLASS) == 8, "the second builder's chest: " + chestB.countItem(Items.GLASS) + " glass");
			helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 0 && player.getInventory().countItem(Items.GLASS) == 0,
				"left in the pack: " + player.getInventory().countItem(Items.OAK_LOG) + " logs, " + player.getInventory().countItem(Items.GLASS) + " glass");
			helper.assertTrue(player.getInventory().countItem(Items.DIRT) == 5, "the dirt nobody asked for was taken");
			helper.assertTrue(board.icon(StorehouseBoard.GIVE_ALL_SLOT).isEmpty(), "Give all still shown with nothing left to give");
		}
	}

	/**
	 * The rail's filters and the row's cells, as plain logic on marked rows: a group shows only its own (Everyone all),
	 * Missing hides what the store already holds, I carry hides what the player can't give; the button and the count's
	 * color; each kind of "who brings it" line read into its two lines (a named worker, an unnamed one, the missing job, no job at all, a tool of any kind); and every new sentence has its words.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFiltersButtonsAndCellsFollowTheRow(GameTestHelper helper) {
		ItemStack logs = new ItemStack(Items.OAK_LOG);
		BoardRows.tag(logs, 48, 32, 16, -1, BoardRows.Group.BUILDERS, 12, "");
		ItemStack stone = new ItemStack(Items.COBBLESTONE);
		BoardRows.tag(stone, 64, 64, 0, -1, BoardRows.Group.BUILDERS, 12, "");
		ItemStack wheat = new ItemStack(Items.WHEAT);
		BoardRows.tag(wheat, 30, 0, 0, -1, BoardRows.Group.KITCHEN, 3, "Ilse");
		CompoundTag l = BoardRows.row(logs);
		CompoundTag s = BoardRows.row(stone);
		CompoundTag w = BoardRows.row(wheat);
		helper.assertTrue(BoardRows.row(new ItemStack(Items.WRITABLE_BOOK)) == null, "an unmarked button has a row");
		// the filters
		for (CompoundTag row : List.of(l, s, w)) {
			helper.assertTrue(BoardRows.shown(row, BoardRows.Group.EVERYONE, false, false), "Everyone hides a row");
		}
		helper.assertTrue(BoardRows.shown(l, BoardRows.Group.BUILDERS, false, false) && !BoardRows.shown(w, BoardRows.Group.BUILDERS, false, false),
			"Builders shows the kitchen's wheat or hides the builder's logs");
		helper.assertTrue(BoardRows.shown(w, BoardRows.Group.KITCHEN, false, false) && !BoardRows.shown(l, BoardRows.Group.KITCHEN, false, false),
			"Kitchen");
		helper.assertTrue(!BoardRows.shown(s, BoardRows.Group.EVERYONE, true, false) && BoardRows.shown(l, BoardRows.Group.EVERYONE, true, false),
			"Missing should hide the stocked stone and keep the short logs");
		helper.assertTrue(BoardRows.shown(l, BoardRows.Group.EVERYONE, false, true) && !BoardRows.shown(w, BoardRows.Group.EVERYONE, false, true),
			"I carry should keep the logs (16 carried) and hide the wheat (none)");
		// the button and the count's color
		helper.assertTrue(BoardRows.state(l) == BoardRows.State.GIVE && BoardRows.give(l) == 16, "logs: " + BoardRows.state(l));
		helper.assertTrue(BoardRows.state(s) == BoardRows.State.STOCKED, "stone: " + BoardRows.state(s));
		helper.assertTrue(BoardRows.state(w) == BoardRows.State.NONE, "wheat: " + BoardRows.state(w));
		helper.assertTrue(BoardRows.tone(0, 12) == BoardRows.Tone.EMPTY && BoardRows.tone(6, 30) == BoardRows.Tone.LOW
			&& BoardRows.tone(32, 48) == BoardRows.Tone.PART && BoardRows.tone(64, 64) == BoardRows.Tone.FULL
			&& BoardRows.tone(-1, 1) == BoardRows.Tone.UNKNOWN, "the tones");
		// Needed by
		Component forLine = Component.translatable("screen.aliveworkplace.storehouse.for", Component.translatable("entity.minecraft.villager.builder"), 12);
		Component[] unnamed = BoardRows.neededBy(l, List.of(forLine));
		helper.assertTrue(unnamed[0].getString().equals("Builder") && unnamed[1].getString().equals("12 blocks off"),
			"unnamed: " + unnamed[0].getString() + " / " + unnamed[1].getString());
		Component[] named = BoardRows.neededBy(w, List.of(forLine));
		helper.assertTrue(named[0].getString().equals("Ilse"), "named: " + named[0].getString());
		helper.assertTrue(BoardRows.neededBy(stoneOneAway(), List.of(forLine))[1].getString().equals("1 block off"), "one block");
		// Brought by, each kind of line
		Component lumberjack = Component.literal("Lumberjack");
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.gathered_by_unnamed", lumberjack), "Lumberjack", "gathers it", false);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.made_by_worker", Component.literal("Carpenter"), Component.literal("Ada")),
			"Ada", "Carpenter", false);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.made_by_worker_unnamed", Component.literal("Carpenter")), "Carpenter", "makes it", false);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.hire_maker", Component.literal("Cleric")), "Nobody", "no Cleric", true);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.hire_maker", Component.literal("Armorer")), "Nobody", "no Armorer", true);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.hire_gatherer.miner"), "Nobody", "no Miner", true);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.no_job"), "Nobody", "no job makes it", true);
		assertBy(helper, Component.translatable("screen.aliveworkplace.storehouse.made_by", Component.literal("Mason")), "Mason", "makes it", false);
		BoardRows.BroughtBy tool = BoardRows.broughtBy(l, List.of(forLine));
		helper.assertTrue(tool.who().getString().equals("You") && tool.how().getString().equals("bring one"), "no line: " + tool.who().getString());
		helper.assertTrue(BoardRows.what(Component.translatable("screen.aliveworkplace.storehouse.wants", 16, Items.OAK_LOG.getDescription())).getString().equals("Oak Log"),
			"what of '16 × Oak Log'");
		// every new sentence has its words
		Language en = Language.getInstance();
		for (String key : List.of("board", "needs", "needs.one", "group.everyone", "group.builders", "group.workers", "group.kitchen", "group.projects",
				"rail.needed_by", "rail.show", "rail.store", "rail.stock", "rail.shopping", "rail.orders", "rail.filter", "rail.filter_all",
				"show.missing", "show.missing_hint", "show.carry", "show.carry_hint", "col.item", "col.needed_by", "col.brought_by",
				"away", "away.one", "by.you", "by.bring", "by.gathers", "by.makes", "by.nobody", "by.none_here", "by.no_job",
				"by.none_here.lumberjack", "by.none_here.miner", "by.none_here.shepherd", "by.none_here.farmer",
				"by.none_here.fisherman", "by.none_here.beekeeper", "by.none_here.florist", "needs_short", "needs_short.one",
				"btn.give", "btn.stocked", "btn.none", "pack", "give_all", "give_all_hint", "whisper", "stocked_whisper", "empty", "filtered", "filtered_hint")) {
			helper.assertTrue(en.has("screen.aliveworkplace.storehouse." + key), "no words for screen.aliveworkplace.storehouse." + key);
		}
		for (String key : List.of("gave_all", "gave_all.one", "gave_none")) {
			helper.assertTrue(en.has("message.aliveworkplace.storehouse." + key), "no words for message.aliveworkplace.storehouse." + key);
		}
		helper.assertTrue(Component.translatable("message.aliveworkplace.storehouse.gave_all", 18).getString().equals("Gave 18 items to the workers who asked"),
			"the Give all message");
		helper.succeed();
	}

	private static CompoundTag stoneOneAway() {
		ItemStack stack = new ItemStack(Items.COBBLESTONE);
		BoardRows.tag(stack, 1, 0, 0, -1, BoardRows.Group.WORKERS, 1, "");
		return BoardRows.row(stack);
	}

	private static void assertBy(GameTestHelper helper, Component line, String who, String how, boolean nobody) {
		ItemStack stack = new ItemStack(Items.STONE);
		BoardRows.tag(stack, 1, 0, 0, 1, BoardRows.Group.WORKERS, 5, "");
		BoardRows.BroughtBy by = BoardRows.broughtBy(BoardRows.row(stack), List.of(Component.empty(), line));
		helper.assertTrue(by.who().getString().equals(who) && by.how().getString().equals(how) && by.nobody() == nobody,
			"for '" + line.getString() + "': " + by.who().getString() + " / " + by.how().getString() + " / " + by.nobody());
	}
}
