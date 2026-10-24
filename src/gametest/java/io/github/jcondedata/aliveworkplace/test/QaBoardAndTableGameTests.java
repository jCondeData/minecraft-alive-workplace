package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (2026-10-24, lane-b-1024-1837), from the specs of B337 and B344 (ROADMAP, the comments on the Blueprint Table's
 * click), for the two survivors of the nightly mutation run 40076984883 in builder code:
 * <ul>
 *   <li>{@code SiteList.isBuildersBench} ({@code atBench != null} turned to {@code == null}): B337 says "A Table no
 *   builder works at opens as usual", so a Book and Quill on a lone Blueprint Table must open the Table, not answer
 *   "No build is under way from this bench";</li>
 *   <li>{@code Requests.lower} ({@code count <= 0} turned to {@code count <= 1}): B344 says "the board's request drops
 *   by what was handed over at once", which must hold for a hand-over of a single item too.</li>
 * </ul>
 */
public class QaBoardAndTableGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String NO_BUILD = "No build is under way from this bench. Hand its builder a placed blueprint.";

	private static void noVillageProtection(GameTestHelper helper) {
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
	}

	/**
	 * B337: a Blueprint Table nobody builds from, clicked with a Book and Quill from either hand, opens the Table (its
	 * screen's packet goes to the player, for this Table) as it does with any other item; no "no build at this bench"
	 * line, and the book stays a Book and Quill.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaBoardTableLone"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaBoardTableLone")
	public void aBookAndQuillOnALoneBlueprintTableOpensTheTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		BlockPos table = new BlockPos(3, 2, 3);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		BlockPos abs = helper.absolutePos(table);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabtL"));
		ServerPlayer p = c.player();
		for (InteractionHand hand : InteractionHand.values()) {
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setItemInHand(hand, new ItemStack(Items.WRITABLE_BOOK));
			c.sent();
			c.seen().clear();
			QaStageFourGameTests.press(helper, p, table, Direction.NORTH);
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			List<String> seen = c.seen().stream().map(Component::getString).toList();
			helper.assertTrue(opened.size() == 1 && opened.get(0).table().equals(abs),
				hand + ": the lone Table didn't open with a Book and Quill: sent " + opened + ", saw " + seen);
			helper.assertFalse(seen.contains(NO_BUILD), hand + ": the lone Table answered as a builder's bench: " + seen);
			helper.assertTrue(p.getItemInHand(hand).is(Items.WRITABLE_BOOK) && p.getItemInHand(hand).getCount() == 1,
				hand + ": the book went: " + p.getItemInHand(hand));
			helper.assertTrue(p.getInventory().countItem(Items.WRITTEN_BOOK) == 0, hand + ": a list was written at a lone Table");
		}
		LOGGER.info("qaBoardTableLone: a Book and Quill from either hand opened the lone Blueprint Table, no bench line, book kept");
		helper.succeed();
	}

	/**
	 * B344 with the smallest hand-over: a player with a single cobblestone clicks the builder's cobblestone on the
	 * Storehouse board. The one block goes into his chest, and at once (the same tick, before the builder looks at his
	 * chests again) his missing list and the board, opened again, ask for 24, not 25.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaBoardOne"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBoardOne")
	public void handingOverASingleItemLowersTheRequestByOneAtOnce(GameTestHelper helper) {
		QaStageSixGameTests.Waiting w = QaStageSixGameTests.waitingBuilder(helper);
		noVillageProtection(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			Integer before = w.site().missing().get(Items.COBBLESTONE);
			helper.assertTrue(before != null && before == 25, "the builder should miss 25 cobblestone, misses " + before);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabtO"));
			ServerPlayer p = c.player();
			p.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 1));
			Container chest = helper.getBlockEntity(w.chest());
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
			int slot = QaStageSixGameTests.slotOf((ChoiceMenu) p.containerMenu, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0, "the board doesn't ask for cobblestone");
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 1 && p.getInventory().countItem(Items.COBBLESTONE) == 0,
				"the hand-over of one: chest " + chest.countItem(Items.COBBLESTONE) + ", kept " + p.getInventory().countItem(Items.COBBLESTONE));
			Integer after = w.site().missing().get(Items.COBBLESTONE);
			helper.assertTrue(after != null && after == 24, "right after handing over one, the builder still misses " + after + " (want 24)");
			p.closeContainer();
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open again: " + p.containerMenu);
			ChoiceMenu board = (ChoiceMenu) p.containerMenu;
			int again = QaStageSixGameTests.slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(again >= 0 && board.icon(again).getCount() == 24,
				"opened again the same tick, the board asks for " + (again < 0 ? "no" : String.valueOf(board.icon(again).getCount())) + " cobblestone (want 24)");
			p.closeContainer();
			LOGGER.info("qaBoardOne: one cobblestone handed over; missing 25 -> {}, the board asks for 24", after);
			helper.succeed();
		});
	}
}
