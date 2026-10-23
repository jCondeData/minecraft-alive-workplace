package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA stage 3 (2026-10-23): bug tests, failing on main (B-numbers in ROADMAP Bugs). They use the helpers of
 * {@link QaStageThreeGameTests}. Expectations from CHANGELOG.md, Courier routes: "Craft a Delivery Note ..., right-click
 * the container to take from and then the one to bring to ... hold an item in your other hand and right-click the air
 * with the note to carry only that kind (up to 9 kinds)".
 */
public class QaStageThreeBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * A survival player right-clicks a chest, then a barrel, with a fresh Delivery Note (through
	 * {@code ServerPlayerGameMode.useItemOn}, as the server handles a real click): the chest is where to take from, the
	 * barrel where to bring to, and neither opens.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3BugDeliveryMark")
	public void deliveryNoteMarksTheChestAndBarrelARealPlayerRightClicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos from = new BlockPos(3, 2, 3);
		BlockPos to = new BlockPos(5, 2, 3);
		helper.setBlock(from, Blocks.CHEST);
		helper.setBlock(to, Blocks.BARREL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));

		QaStageThreeGameTests.click(helper, player, from);
		RouteData data = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY);
		boolean opened = player.containerMenu != player.inventoryMenu;
		helper.assertTrue(data.from().equals(Optional.of(helper.absolutePos(from))) && data.to().isEmpty() && !opened,
			"right-clicking a chest with a Delivery Note " + (opened ? "opened the chest (" + player.containerMenu.getClass().getSimpleName() + ")" : "didn't open it")
				+ " and left the note " + data + "; last message: " + QaStageThreeGameTests.last(seen));
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.route.from"),
			"no 'Take from' message: " + QaStageThreeGameTests.last(seen));

		QaStageThreeGameTests.click(helper, player, to);
		data = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY);
		helper.assertTrue(data.isComplete() && data.to().equals(Optional.of(helper.absolutePos(to))),
			"right-clicking the barrel should mark where to bring to, got " + data);
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "the barrel opened instead of being marked");
		helper.succeed();
	}

	/**
	 * With nine kinds on the note, right-clicking the air with a tenth in the other hand leaves the note as it is (up to
	 * 9 kinds), so the player must not be told "Stop carrying" a kind the note never carried.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3BugDeliveryTenth")
	public void deliveryNoteDoesNotSayStopCarryingATenthKindItNeverCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
		Item[] kinds = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.WHEAT, Items.IRON_INGOT, Items.COAL, Items.SAND,
			Items.GRAVEL, Items.CLAY_BALL};
		for (Item kind : kinds) {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(kind));
			QaStageThreeGameTests.clickAir(helper, player);
		}
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD));
		QaStageThreeGameTests.clickAir(helper, player);
		helper.assertTrue(!player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY).filter().contains(Items.EMERALD),
			"a tenth kind was taken");
		helper.assertTrue(!QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.route.filter_remove"),
			"a tenth kind, never on the note, was answered with \"" + seen.get(seen.size() - 1).getString() + "\"");
		helper.succeed();
	}
}
