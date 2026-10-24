package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
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
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-24) candidate bug tests for the builder's clicks: not registered for main, they fail on the code they
 * were written against. Each names the spec it holds the mod to.
 */
public class QaBuilderClicksBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * B335 ("the click marks it and the block stays shut", docs/wiki/builder.md) with the Scan Tool in the off hand and
	 * the main hand empty, the way markers already work from the off hand (QaStageFourGameTests.markersWorkFromTheOffHand)
	 * and BuilderEvents' items do since B342: a right-click on a chest should mark it as the first corner, not open it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksScanOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksScanOff")
	public void scanToolFromTheOffHandMarksAChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos chestPos = new BlockPos(2, 2, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacSO"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SCAN_TOOL));
		QaStageFourGameTests.press(helper, p, chestPos, Direction.NORTH);
		boolean opened = p.containerMenu != p.inventoryMenu;
		Optional<BlockPos> first = ScanToolItem.data(p.getOffhandItem()).first();
		helper.assertTrue(!opened && first.equals(Optional.of(helper.absolutePos(chestPos))),
			"off-hand Scan Tool on a chest: opened " + (opened ? p.containerMenu.getClass().getSimpleName() : "no") + ", first corner " + first
				+ ", saw " + c.seen().stream().map(Component::getString).toList());
		p.closeContainer();
		helper.succeed();
	}
}
