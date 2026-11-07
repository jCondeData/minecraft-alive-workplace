package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-03): B333 (a Delivery Note opened a chest or barrel instead of marking it) and B334 (a full note answered
 * a 10th kind with "Stop carrying"), checked from the spec (docs/guide/delivery-note.md, CHANGELOG Courier routes):
 * "Right-click the container to take from, then the one to bring to"; "Hold that item in your other hand and
 * right-click the air ... A note carries up to 9 kinds; do the same with a kind it carries to take it off. Sneak and
 * right-click a block to clear the two containers." Every click goes through ServerPlayerGameMode, as a real click does;
 * messages are checked in the exact English of en_us.json. Uses the helpers of {@link QaStageThreeGameTests}.
 */
public class QaDeliveryNote1103cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static String text(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
	}

	private static RouteData route(ServerPlayer player, InteractionHand hand) {
		return DeliveryNoteItem.data(player.getItemInHand(hand));
	}

	private static String at(BlockPos abs) {
		return abs.getX() + " " + abs.getY() + " " + abs.getZ();
	}

	private static void assertShut(GameTestHelper helper, ServerPlayer player, String what) {
		boolean open = player.containerMenu != player.inventoryMenu;
		String menu = open ? player.containerMenu.getClass().getSimpleName() : "";
		if (open) {
			player.closeContainer();
		}
		helper.assertTrue(!open, what + " opened a menu (" + menu + ") instead of being marked");
	}

	/** Right-click the air the way the client does: the main hand first, then the off hand if the main hand passed. */
	private static void clickAirBothHands(GameTestHelper helper, ServerPlayer player) {
		var result = player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
		if (!result.consumesAction()) {
			player.gameMode.useItem(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND);
		}
	}

	/**
	 * A chest, a trapped chest, a barrel, a shulker box, a hopper and both halves of a double chest: each click marks the
	 * clicked container (take from, then bring to, then take from again on a finished note), says so in the exact
	 * English, opens nothing and leaves what's inside alone.
	 */
	//$ gametest_batch AREA '"qaDn1103cKinds"'
	@GameTest(template = AREA, batch = "qaDn1103cKinds")
	public void everyContainerKindIsMarkedAndStaysShut(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos chest = new BlockPos(3, 2, 1);
		BlockPos trapped = new BlockPos(5, 2, 1);
		BlockPos barrel = new BlockPos(3, 2, 3);
		BlockPos shulker = new BlockPos(5, 2, 3);
		BlockPos hopper = new BlockPos(3, 2, 5);
		BlockPos left = new BlockPos(5, 2, 5);
		BlockPos right = new BlockPos(6, 2, 5);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(trapped, Blocks.TRAPPED_CHEST);
		helper.setBlock(barrel, Blocks.BARREL);
		helper.setBlock(shulker, Blocks.SHULKER_BOX);
		helper.setBlock(hopper, Blocks.HOPPER);
		// facing north, a LEFT half's partner is to its east
		helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		Container inChest = helper.getBlockEntity(chest);
		inChest.setItem(4, new ItemStack(Items.DIAMOND, 7));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));

		BlockPos[] order = {chest, trapped, barrel, shulker, hopper, left, right};
		String[] names = {"a chest", "a trapped chest", "a barrel", "a shulker box", "a hopper", "a double chest's left half",
			"a double chest's right half"};
		for (int i = 0; i < order.length; i++) {
			BlockPos abs = helper.absolutePos(order[i]);
			QaStageThreeGameTests.click(helper, player, order[i]);
			assertShut(helper, player, names[i]);
			RouteData data = route(player, InteractionHand.MAIN_HAND);
			if (i % 2 == 0) {
				helper.assertTrue(data.from().equals(Optional.of(abs)) && data.to().isEmpty(),
					names[i] + " wasn't marked as where to take from: " + data + "; said \"" + text(seen) + "\"");
				helper.assertTrue(text(seen).equals("Take from " + at(abs) + ". Now right-click where it should go."),
					names[i] + " said \"" + text(seen) + "\"");
			} else {
				helper.assertTrue(data.to().equals(Optional.of(abs)) && data.from().equals(Optional.of(helper.absolutePos(order[i - 1]))),
					names[i] + " wasn't marked as where to bring to: " + data + "; said \"" + text(seen) + "\"");
				helper.assertTrue(text(seen).equals("Bring to " + at(abs) + ". Give the note to a Postman."),
					names[i] + " said \"" + text(seen) + "\"");
			}
		}
		helper.assertTrue(inChest.getItem(4).is(Items.DIAMOND) && inChest.getItem(4).getCount() == 7,
			"marking the chest changed what's in it: " + inChest.getItem(4));
		helper.assertTrue(helper.getBlockState(left).getValue(ChestBlock.TYPE) == ChestType.LEFT
			&& helper.getBlockState(right).getValue(ChestBlock.TYPE) == ChestType.RIGHT, "the double chest came apart");
		helper.succeed();
	}

	/**
	 * Sneak-right-click clears the two containers but keeps the kinds, on a barrel (which stays shut) and on any block
	 * (stone); the exact English is "Delivery Note cleared.".
	 */
	//$ gametest_batch AREA '"qaDn1103cSneak"'
	@GameTest(template = AREA, batch = "qaDn1103cSneak")
	public void sneakClickClearsTheContainersOnABarrelAndOnStone(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos barrel = new BlockPos(3, 2, 3);
		BlockPos chest = new BlockPos(5, 2, 3);
		BlockPos stone = new BlockPos(3, 2, 5);
		helper.setBlock(barrel, Blocks.BARREL);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(stone, Blocks.STONE);
		RouteData marked = new RouteData(Optional.of(helper.absolutePos(chest)), Optional.of(helper.absolutePos(barrel)), List.of(Items.WHEAT));
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, marked);
		player.setItemInHand(InteractionHand.MAIN_HAND, note);

		player.setShiftKeyDown(true);
		QaStageThreeGameTests.click(helper, player, barrel);
		player.setShiftKeyDown(false);
		assertShut(helper, player, "a sneak-right-click on a barrel");
		RouteData after = route(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(after.from().isEmpty() && after.to().isEmpty() && after.filter().equals(List.of(Items.WHEAT)),
			"a sneak-right-click on a barrel should clear the containers and keep wheat: " + after);
		helper.assertTrue(text(seen).equals("Delivery Note cleared."), "clearing said \"" + text(seen) + "\"");

		// a plain click marks again, then a sneak on stone clears it too
		QaStageThreeGameTests.click(helper, player, chest);
		assertShut(helper, player, "a chest");
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).from().equals(Optional.of(helper.absolutePos(chest))),
			"the chest wasn't marked after clearing: " + route(player, InteractionHand.MAIN_HAND));
		seen.clear();
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.click(helper, player, stone);
		player.setShiftKeyDown(false);
		after = route(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(after.from().isEmpty() && after.to().isEmpty() && after.filter().equals(List.of(Items.WHEAT)),
			"a sneak-right-click on stone should clear the containers: " + after);
		helper.assertTrue(text(seen).equals("Delivery Note cleared."), "clearing on stone said \"" + text(seen) + "\"");
		helper.succeed();
	}

	/**
	 * A stone block and a crafting table aren't containers: the note stays as it was (stone says so in the exact English);
	 * and the B333 hook only takes the note: a chest right-clicked with paper still opens.
	 */
	//$ gametest_batch AREA '"qaDn1103cNotContainer"'
	@GameTest(template = AREA, batch = "qaDn1103cNotContainer")
	public void nonContainersMarkNothingAndOtherItemsStillOpenAChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos stone = new BlockPos(3, 2, 3);
		BlockPos table = new BlockPos(5, 2, 3);
		BlockPos chest = new BlockPos(3, 2, 5);
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(table, Blocks.CRAFTING_TABLE);
		helper.setBlock(chest, Blocks.CHEST);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));

		var result = QaStageThreeGameTests.click(helper, player, stone);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).equals(RouteData.EMPTY), "stone changed the note: " + route(player, InteractionHand.MAIN_HAND));
		helper.assertTrue(!result.consumesAction(), "stone was accepted: " + result);
		helper.assertTrue(text(seen).equals("That's not a container or a Pasture Block a courier can use."), "stone said \"" + text(seen) + "\"");

		QaStageThreeGameTests.click(helper, player, table);
		player.closeContainer();
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).equals(RouteData.EMPTY),
			"a crafting table was marked as a container: " + route(player, InteractionHand.MAIN_HAND));

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
		QaStageThreeGameTests.click(helper, player, chest);
		boolean opened = player.containerMenu != player.inventoryMenu;
		player.closeContainer();
		helper.assertTrue(opened, "a chest right-clicked with paper didn't open (the note's hook took another item's click)");
		helper.succeed();
	}

	/**
	 * The note in the off hand: the off hand's click on a barrel marks it and opens nothing; with cobblestone in the main
	 * hand, right-clicking the air (main hand first, as the client does) adds cobblestone and uses none of it.
	 */
	//$ gametest_batch AREA '"qaDn1103cOffHand"'
	@GameTest(template = AREA, batch = "qaDn1103cOffHand")
	public void noteInTheOffHandMarksABarrelAndTakesTheMainHandsKind(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos barrel = new BlockPos(3, 2, 3);
		helper.setBlock(barrel, Blocks.BARREL);
		BlockPos abs = helper.absolutePos(barrel);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.DELIVERY_NOTE));

		player.gameMode.useItemOn(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND,
			new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
		assertShut(helper, player, "a barrel clicked with the off-hand note");
		helper.assertTrue(route(player, InteractionHand.OFF_HAND).from().equals(Optional.of(abs)),
			"the off-hand note didn't mark the barrel: " + route(player, InteractionHand.OFF_HAND) + "; said \"" + text(seen) + "\"");
		helper.assertTrue(text(seen).equals("Take from " + at(abs) + ". Now right-click where it should go."), "said \"" + text(seen) + "\"");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 64));
		clickAirBothHands(helper, player);
		helper.assertTrue(route(player, InteractionHand.OFF_HAND).filter().equals(List.of(Items.COBBLESTONE)),
			"the off-hand note didn't take the main hand's cobblestone: " + route(player, InteractionHand.OFF_HAND).filter());
		helper.assertTrue(text(seen).equals("Carry Cobblestone"), "adding said \"" + text(seen) + "\"");
		helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE) && player.getMainHandItem().getCount() == 64,
			"the main hand's cobblestone was used: " + player.getMainHandItem());
		helper.assertTrue(route(player, InteractionHand.OFF_HAND).from().equals(Optional.of(abs)), "adding a kind lost the barrel");
		helper.succeed();
	}

	/**
	 * 8 kinds, then a 9th is added ("Carry Emerald"); a 10th is refused with the full-note text and no "Stop carrying";
	 * a kind the full note carries is still taken off ("Stop carrying Dirt"), after which the 10th fits.
	 */
	//$ gametest_batch AREA '"qaDn1103cFull"'
	@GameTest(template = AREA, batch = "qaDn1103cFull")
	public void aFullNoteRefusesATenthKindButStillTakesOneOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
		Item[] kinds = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.WHEAT, Items.IRON_INGOT, Items.COAL, Items.SAND, Items.GRAVEL};
		for (Item kind : kinds) {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(kind, 3));
			QaStageThreeGameTests.clickAir(helper, player);
		}
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).filter().size() == 8, "8 kinds not kept: " + route(player, InteractionHand.MAIN_HAND).filter());

		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 3));
		QaStageThreeGameTests.clickAir(helper, player);
		List<Item> nine = route(player, InteractionHand.MAIN_HAND).filter();
		helper.assertTrue(nine.size() == 9 && nine.contains(Items.EMERALD), "the 9th kind wasn't added: " + nine);
		helper.assertTrue(text(seen).equals("Carry Emerald"), "the 9th kind said \"" + text(seen) + "\"");

		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CLAY_BALL, 3));
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).filter().equals(nine), "a 10th kind changed the full note: " + route(player, InteractionHand.MAIN_HAND).filter());
		helper.assertTrue(text(seen).equals("This Delivery Note already carries the most kinds (9). Hold one it carries to take it off."),
			"a 10th kind said \"" + text(seen) + "\"");
		helper.assertTrue(player.getOffhandItem().is(Items.CLAY_BALL) && player.getOffhandItem().getCount() == 3, "the 10th kind was used up");

		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
		QaStageThreeGameTests.clickAir(helper, player);
		List<Item> eight = route(player, InteractionHand.MAIN_HAND).filter();
		helper.assertTrue(eight.size() == 8 && !eight.contains(Items.DIRT), "a kind on the full note wasn't taken off: " + eight);
		helper.assertTrue(text(seen).equals("Stop carrying Dirt"), "taking dirt off the full note said \"" + text(seen) + "\"");

		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CLAY_BALL));
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).filter().size() == 9 && route(player, InteractionHand.MAIN_HAND).filter().contains(Items.CLAY_BALL),
			"the refused kind didn't fit after one was taken off: " + route(player, InteractionHand.MAIN_HAND).filter());
		helper.assertTrue(text(seen).equals("Carry Clay Ball"), "said \"" + text(seen) + "\"");
		helper.succeed();
	}

	/**
	 * The same kind twice adds then takes it off; an empty other hand, on a blank and on a full note, changes nothing and
	 * says nothing.
	 */
	//$ gametest_batch AREA '"qaDn1103cTwice"'
	@GameTest(template = AREA, batch = "qaDn1103cTwice")
	public void theSameKindTwiceTogglesAndAnEmptyOtherHandDoesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 10));
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).filter().equals(List.of(Items.IRON_INGOT)), "iron not added: " + route(player, InteractionHand.MAIN_HAND));
		helper.assertTrue(text(seen).equals("Carry Iron Ingot"), "said \"" + text(seen) + "\"");
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).filter().isEmpty(), "iron twice should take it off: " + route(player, InteractionHand.MAIN_HAND));
		helper.assertTrue(text(seen).equals("Stop carrying Iron Ingot"), "said \"" + text(seen) + "\"");
		helper.assertTrue(player.getOffhandItem().getCount() == 10, "the iron was used: " + player.getOffhandItem());

		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		int said = seen.size();
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).equals(RouteData.EMPTY) && seen.size() == said,
			"an empty other hand on a blank note changed it or spoke: " + route(player, InteractionHand.MAIN_HAND) + " \"" + text(seen) + "\"");

		List<Item> full = List.of(Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.WHEAT, Items.IRON_INGOT, Items.COAL, Items.SAND,
			Items.GRAVEL, Items.CLAY_BALL);
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, new RouteData(Optional.empty(), Optional.empty(), full));
		player.setItemInHand(InteractionHand.MAIN_HAND, note);
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(route(player, InteractionHand.MAIN_HAND).filter().equals(full) && seen.size() == said,
			"an empty other hand on a full note changed it or spoke: \"" + text(seen) + "\"");
		helper.succeed();
	}

	/**
	 * A note marked by real clicks (chest, barrel, two kinds) is the same after a save and after the trip to a client,
	 * and the reloaded note still works: a third click starts a new route from there.
	 */
	//$ gametest_batch AREA '"qaDn1103cSave"'
	@GameTest(template = AREA, batch = "qaDn1103cSave")
	public void aMarkedNoteSurvivesASaveAndKeepsWorking(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos chest = new BlockPos(3, 2, 3);
		BlockPos barrel = new BlockPos(5, 2, 3);
		BlockPos trapped = new BlockPos(3, 2, 5);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(barrel, Blocks.BARREL);
		helper.setBlock(trapped, Blocks.TRAPPED_CHEST);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
		QaStageThreeGameTests.click(helper, player, chest);
		QaStageThreeGameTests.click(helper, player, barrel);
		assertShut(helper, player, "the chest or barrel");
		for (Item kind : List.of(Items.WHEAT, Items.CARROT)) {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(kind));
			QaStageThreeGameTests.clickAir(helper, player);
		}
		RouteData marked = route(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(marked.isComplete() && marked.filter().equals(List.of(Items.WHEAT, Items.CARROT))
			&& marked.by().equals(Optional.of(player.getUUID())), "the clicks didn't make the route: " + marked);

		RouteData saved = QaStageThreeGameTests.saveAndLoad(helper, player.getMainHandItem(), ModComponents.ROUTE);
		helper.assertTrue(marked.equals(saved), "the route changed over a save: " + marked + " -> " + saved);
		ItemStack sent = QaStageThreeGameTests.overTheNetwork(helper, player.getMainHandItem());
		helper.assertTrue(marked.equals(sent.get(ModComponents.ROUTE)), "the route changed over the network: " + sent.get(ModComponents.ROUTE));

		ItemStack reloaded = new ItemStack(ModItems.DELIVERY_NOTE);
		reloaded.set(ModComponents.ROUTE, saved);
		player.setItemInHand(InteractionHand.MAIN_HAND, reloaded);
		QaStageThreeGameTests.click(helper, player, trapped);
		assertShut(helper, player, "the trapped chest");
		RouteData next = route(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(next.from().equals(Optional.of(helper.absolutePos(trapped))) && next.to().isEmpty()
			&& next.filter().equals(List.of(Items.WHEAT, Items.CARROT)), "the reloaded note didn't start a new route at the trapped chest: " + next);
		helper.succeed();
	}
}
