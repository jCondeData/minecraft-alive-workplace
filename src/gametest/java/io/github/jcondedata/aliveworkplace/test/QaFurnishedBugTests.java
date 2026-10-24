package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA bug tests (lane-c-1024-0353) for the furnished material list; each fails on the mod as it is. Not registered:
 * they travel on a tests/ branch with their fix.
 */
public class QaFurnishedBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** A placed nook by a Blueprint Table whose chest holds the floor; the player holds the blueprint in {@code blueprintHand}. */
	private static ServerPlayer placedNook(GameTestHelper helper, InteractionHand blueprintHand) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(QaFurnishedGameTests.BENCH, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(QaFurnishedGameTests.CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 9));
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		player.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)), 0, 0);
		player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(QaFurnishedGameTests.NOOK, QaFurnishedGameTests.NOOK_SIZE));
		QaFurnishedGameTests.rightClickGround(player, helper.absolutePos(new BlockPos(9, 1, 9)));
		Optional<BlueprintData> placed = BlueprintItem.data(player.getMainHandItem());
		helper.assertTrue(placed.isPresent() && placed.get().placement().isPresent(), "the blueprint wasn't placed by the click");
		if (blueprintHand == InteractionHand.OFF_HAND) {
			player.setItemInHand(InteractionHand.OFF_HAND, player.getMainHandItem());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		} else {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK));
		}
		return player;
	}

	/**
	 * The blueprint held in the OFF hand and the Book and Quill in the main hand: a right-click writes the material list
	 * (BlueprintItem.use handles the book in either hand), but the main hand's book is tried first and opens the book's
	 * editor, so the list is never written.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFurnishedBugHands"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFurnishedBugHands")
	public void theListIsWrittenWithTheBlueprintInTheOffHand(GameTestHelper helper) {
		QaFurnishedGameTests.resetAfter(helper);
		ServerPlayer player = placedNook(helper, InteractionHand.OFF_HAND);
		helper.runAfterDelay(2, () -> {
			QaFurnishedGameTests.rightClickAir(player);
			helper.assertTrue(player.getMainHandItem().is(Items.WRITTEN_BOOK),
				"blueprint in the off hand, Book and Quill in the main hand: no list written, the main hand holds " + player.getMainHandItem());
			helper.succeed();
		});
	}

	/** One thing still to bring (the table's chair): the list's first page reads "1 item of 1 kind", not "1 items of 1 kinds". */
	//$ gametest_ticks_batch AREA '60' '"qaFurnishedBugOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFurnishedBugOne")
	public void oneThingToBringReadsInTheSingular(GameTestHelper helper) {
		QaFurnishedGameTests.resetAfter(helper);
		ServerPlayer player = placedNook(helper, InteractionHand.MAIN_HAND);
		helper.runAfterDelay(2, () -> {
			QaFurnishedGameTests.rightClickAir(player);
			String first = QaFurnishedGameTests.page(player.getOffhandItem(), 0);
			helper.assertTrue(first != null && first.contains("Still to bring"), "no list: " + first);
			helper.assertTrue(!first.contains("1 items") && !first.contains("1 kinds"), "the list's first page reads: " + first.replace("\n", " "));
			helper.succeed();
		});
	}
}
