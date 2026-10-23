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
 * QA stage 4 bug tests (they fail on the code of 2026-10-23; the fix commits them with itself). README, Scan Tool:
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

	/**
	 * B279 (CHANGELOG 0.146.x): "Right-click the Builder (or their Builder's Bench) with a Book and Quill to get the list
	 * for the rest of the build". A builder's workstation is the Blueprint Table now (README's job table; the Builder's
	 * Bench can't be crafted any more), so a Book and Quill on the table of a builder should answer as on the bench
	 * (here, no build under way: "no build at this bench"), not open the table's screen.
	 */
	//$ gametest_batch AREA '"qa4BookTable"'
	@GameTest(template = AREA, batch = "qa4BookTable")
	public void bookAndQuillOnABuildersBlueprintTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BLUEPRINT_TABLE);
		net.minecraft.world.entity.npc.Villager builder = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.build.Builders.employ(helper.getLevel(), builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK));
		QaStageFourGameTests.press(helper, player, new BlockPos(2, 2, 2), Direction.NORTH);
		helper.assertTrue(QaStageFourGameTests.saw(seen, "message.aliveworkplace.site_list.no_build_at_bench")
				|| seen.stream().anyMatch(c -> QaStageFourGameTests.key(c).startsWith("message.aliveworkplace.site_list")),
			"a Book and Quill on the builder's Blueprint Table: saw " + QaStageFourGameTests.keys(seen) + ", screen "
				+ player.containerMenu.getClass().getSimpleName());
		helper.succeed();
	}

	/**
	 * README, Shop Counter: "The owner can sneak-right-click the counter to see the latest sales." The owner holding
	 * something (an emerald, the goods he's about to stock) sneak-right-clicks it: the sales log should come as with an
	 * empty hand. (Vanilla skips a block's own use when a sneaking player holds anything.)
	 */
	//$ gametest_batch AREA '"qa4ShopLogHolding"'
	@GameTest(template = AREA, batch = "qa4ShopLogHolding")
	public void ownerHoldingSomethingSneakClicksTheCounterForTheSales(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.SHOP_COUNTER);
		List<String> wrong = new ArrayList<>();
		for (ItemStack held : List.of(new ItemStack(net.minecraft.world.item.Items.EMERALD, 5), new ItemStack(net.minecraft.world.item.Items.BREAD, 3))) {
			List<Component> seen = new ArrayList<>();
			ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
			((io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3)))
				.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			owner.setItemInHand(InteractionHand.MAIN_HAND, held);
			owner.setShiftKeyDown(true);
			QaStageFourGameTests.press(helper, owner, new BlockPos(3, 2, 3), Direction.NORTH);
			if (seen.stream().noneMatch(c -> QaStageFourGameTests.key(c).startsWith("message.aliveworkplace.shop.log"))) {
				wrong.add(held.getHoverName().getString() + ": saw " + QaStageFourGameTests.keys(seen));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the owner sneak-right-clicked the counter holding something and got no sales log: " + wrong);
		helper.succeed();
	}
}
