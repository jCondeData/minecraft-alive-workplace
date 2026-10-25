package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;

/**
 * QA (lane-b-1024-1537) bug tests found while verifying B366: B393, the builder's status list in the singular (fixed by B402),
 * and B394, the off-hand Book and Quill on the builder's Blueprint Table.
 */
public class QaBuilderBugFixesBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * B366's "a .one form, as the mod's other counted lines have", for the builder's status: his chest holds everything
	 * for the test hut but the torch. A sneak-right-click on him with an empty hand heads the list "Still to bring for
	 * the Test Hut: 1 item of 1 kind", not "1 items of 1 kinds" (message.aliveworkplace.site_list.chat_heading has no
	 * .one form).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixBugStatusOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixBugStatusOne")
	public void theBuildersStatusListWithOneThingToBringReadsInTheSingular(GameTestHelper helper) {
		QaBuilderBugFixesGameTests.OneShort s = QaBuilderBugFixesGameTests.oneThingShort(helper, "qafxH");
		ServerPlayer p = s.client().player();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setShiftKeyDown(true);
		QaStageSixGameTests.clickVillager(p, s.builder(), s.client().seen());
		p.setShiftKeyDown(false);
		String hut = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut")).getString();
		String heading = "Still to bring for the " + hut + ": 1 item of 1 kind";
		List<String> seen = QaBuilderBugFixesGameTests.texts(s.client().seen());
		helper.assertTrue(seen.stream().anyMatch(t -> t.startsWith("Still to bring for the ")), "no list in the builder's status: " + seen);
		helper.assertTrue(seen.stream().anyMatch(t -> t.startsWith(heading + "\n") || t.equals(heading)),
			"the builder's status heads the list: " + seen.stream().filter(t -> t.startsWith("Still to bring")).map(t -> t.split("\n")[0]).toList()
				+ ", expected \"" + heading + "\"");
		helper.succeed();
	}

	/**
	 * B337 ("right-clicking a builder's Blueprint Table with a Book and Quill" gives the list) with the Book and Quill in
	 * the OFF hand and the main hand empty, as the other held-item clicks work since B341/B342 and the Scan Tool since
	 * B374: the list should be written into the off hand. The main hand's empty turn opens the Table first, so no list.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixBugTableOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixBugTableOff")
	public void aBookAndQuillInTheOffHandOnTheBuildersTableWritesTheList(GameTestHelper helper) {
		QaBuilderBugFixesGameTests.assertTableListSingular(helper, QaBuilderBugFixesGameTests.oneThingShort(helper, "qafxF"), InteractionHand.OFF_HAND);
		helper.succeed();
	}

	/**
	 * B394's edges at the builder's Table: with both hands empty it still opens as usual, and with something else in the
	 * main hand (a stick) the main hand wins, as for the Scan Tool (B374): the Table opens and the off-hand book is kept.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixBugTableOpen"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixBugTableOpen")
	public void theBuildersTableStillOpensEmptyHandedOrWithSomethingElseInTheMainHand(GameTestHelper helper) {
		QaBuilderBugFixesGameTests.OneShort s = QaBuilderBugFixesGameTests.oneThingShort(helper, "qafxE");
		ServerPlayer p = s.client().player();
		net.minecraft.core.BlockPos table = new net.minecraft.core.BlockPos(2, 2, 2);
		for (ItemStack main : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK))) {
			p.setItemInHand(InteractionHand.MAIN_HAND, main.copy());
			p.setItemInHand(InteractionHand.OFF_HAND, main.isEmpty() ? ItemStack.EMPTY : new ItemStack(Items.WRITABLE_BOOK));
			s.client().sent();
			QaStageFourGameTests.press(helper, p, table, Direction.NORTH);
			List<TablePayloads.Open> open = s.client().sent(TablePayloads.Open.class);
			helper.assertTrue(open.size() == 1 && open.get(0).table().equals(helper.absolutePos(table)),
				"main hand " + main + ": the builder's Table didn't open: sent " + open);
			helper.assertTrue(p.getInventory().countItem(Items.WRITTEN_BOOK) == 0
				&& (main.isEmpty() || p.getOffhandItem().is(Items.WRITABLE_BOOK)), "main hand " + main + ": a list was written: " + p.getOffhandItem());
		}
		helper.succeed();
	}
}
