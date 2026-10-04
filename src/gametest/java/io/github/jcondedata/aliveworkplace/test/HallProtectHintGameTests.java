package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * B37: the hall's name tag tells the owner what a shift-click will do now: protect an open village, or open a protected
 * one again. Shift-clicking it (the real click, not a shortcut) flips the hint with the state.
 */
public class HallProtectHintGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final String PROTECT = "screen.aliveworkplace.hall.protect_click";
	private static final String UNPROTECT = "screen.aliveworkplace.hall.unprotect_click";

	//$ gametest_ticks_batch AREA '40' '"theHallsShiftClickHintFollowsProtection"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "theHallsShiftClickHintFollowsProtection")
	public void theHallsShiftClickHintFollowsProtection(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(owner));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos at = helper.absolutePos(HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at);
		hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		ChoiceMenu menu = VillageHallScreen.forTest(owner, at);

		List<String> open = keys(level, owner, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(open.contains(PROTECT) && !open.contains(UNPROTECT), "an open village's hint isn't 'protect': " + open);

		menu.clicked(VillageHallScreen.NAME, 0, ClickType.QUICK_MOVE, owner);
		helper.assertTrue(hall.isProtected(), "shift-clicking the name tag didn't protect the village");
		List<String> shut = keys(level, owner, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(shut.contains(UNPROTECT) && !shut.contains(PROTECT), "a protected village's hint still says 'protect': " + shut);

		menu.clicked(VillageHallScreen.NAME, 0, ClickType.QUICK_MOVE, owner);
		helper.assertFalse(hall.isProtected(), "shift-clicking again didn't open the village");
		List<String> again = keys(level, owner, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(again.contains(PROTECT) && !again.contains(UNPROTECT), "the hint didn't go back to 'protect': " + again);
		helper.succeed();
	}

	private static List<String> keys(ServerLevel level, ServerPlayer player, ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component line : stack.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL)) {
			walk(line, out);
		}
		return out;
	}

	private static void walk(Component component, List<String> out) {
		if (component.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c) {
					walk(c, out);
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			walk(sibling, out);
		}
	}
}
