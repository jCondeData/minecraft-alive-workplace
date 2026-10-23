package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA stage 4 bug tests (they fail on the code of 2026-10-23; each fix brings its own test here from tests/qa-stage4-clicks). README, Scan Tool:
 * "Right-click one corner block of something you've built, then the opposite corner". The corner block of a build is
 * often a chest, a barrel, a crafting table, a furnace, a trapdoor or a job block; the click must mark it, not open or
 * flip it (the B333 pattern: the block's own use takes the click before the item's).
 */
public class QaStageFourBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_batch AREA '"qa4ScanCorner"'
	@GameTest(template = AREA, batch = "qa4ScanCorner")
	public void scanToolMarksACornerOnAChestOrJobBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Block> corners = List.of(Blocks.CHEST, Blocks.BARREL, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.OAK_TRAPDOOR,
			ModBlocks.BUILDERS_BENCH);
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < corners.size(); i++) {
			BlockPos rel = new BlockPos(1 + i, 2, 3);
			helper.setBlock(rel, corners.get(i));
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
			QaStageFourGameTests.press(helper, player, rel, Direction.NORTH);
			Optional<BlockPos> first = ScanToolItem.data(player.getMainHandItem()).first();
			boolean marked = first.equals(Optional.of(helper.absolutePos(rel))) && QaStageFourGameTests.saw(seen, "message.aliveworkplace.scan.first");
			boolean opened = player.containerMenu != player.inventoryMenu;
			boolean flipped = corners.get(i) == Blocks.OAK_TRAPDOOR && helper.getBlockState(rel).getValue(BlockStateProperties.OPEN);
			if (!marked || opened || flipped) {
				wrong.add(corners.get(i).getName().getString() + " (marked " + marked + ", opened " + (opened ? player.containerMenu.getClass().getSimpleName() : "no")
					+ (flipped ? ", trapdoor flipped" : "") + ", saw " + QaStageFourGameTests.keys(seen) + ")");
			}
			player.closeContainer();
		}
		helper.assertTrue(wrong.isEmpty(), "a Scan Tool right-click on a corner didn't mark it: " + wrong);
		helper.succeed();
	}
}
