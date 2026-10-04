package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B37, from the bug's text: the Village Hall name tag's shift-click hint must match the village's state. The
 * reported case was a hall that was already protected when the screen opened (not toggled in the same screen), so
 * these open a fresh screen on a protected hall that went through a save and reload, check that a stranger's
 * shift-click changes neither the protection nor the hint, and read the English a player sees.
 */
public class QaB37HallHintGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final String PROTECT = "screen.aliveworkplace.hall.protect_click";
	private static final String UNPROTECT = "screen.aliveworkplace.hall.unprotect_click";
	private static final String PROTECTED = "screen.aliveworkplace.hall.protected";

	/** A hall protected earlier, saved and loaded again, opens with "open the village again", never "protect". */
	//$ gametest_ticks_batch AREA '40' '"qaB37AReloadedProtectedHallOpensWithTheOpenAgainHint"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB37AReloadedProtectedHallOpensWithTheOpenAgainHint")
	public void qaB37AReloadedProtectedHallOpensWithTheOpenAgainHint(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(owner));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos at = helper.absolutePos(HALL);
		unprotectAfter(helper, at);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at);
		hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		hall.setProtected(true);

		CompoundTag saved = hall.saveWithFullMetadata(level.registryAccess());
		helper.setBlock(HALL, Blocks.AIR);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity reloaded = (VillageHallBlockEntity) level.getBlockEntity(at);
		reloaded.loadWithComponents(saved, level.registryAccess());
		Leftovers.after(helper, () -> reloaded.setProtected(false)); // a protected hall left behind locks later tests' halls nearby
		helper.assertTrue(reloaded.isProtected(), "the hall forgot it was protected after a save and reload");

		ChoiceMenu menu = VillageHallScreen.forTest(owner, at);
		List<String> keys = keys(level, owner, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(keys.contains(PROTECTED), "a protected hall's name tag doesn't say it's protected: " + keys);
		helper.assertTrue(keys.contains(UNPROTECT) && !keys.contains(PROTECT),
			"a protected hall opened fresh still hints 'Shift-click: protect the village': " + keys);
		helper.succeed();
	}

	/** Someone who isn't the owner shift-clicks a protected hall's name tag: still protected, the hint unchanged. */
	//$ gametest_ticks_batch AREA '40' '"qaB37AStrangersShiftClickLeavesTheHintAlone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB37AStrangersShiftClickLeavesTheHintAlone")
	public void qaB37AStrangersShiftClickLeavesTheHintAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().remove(owner);
			level.getServer().getPlayerList().remove(stranger);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos at = helper.absolutePos(HALL);
		unprotectAfter(helper, at);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at);
		hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		hall.setProtected(true);
		Leftovers.after(helper, () -> hall.setProtected(false)); // a protected hall left behind locks later tests' halls nearby
		helper.assertFalse(stranger.hasPermissions(2), "the test's stranger is an operator, so it can't play a stranger");

		ChoiceMenu menu = VillageHallScreen.forTest(stranger, at);
		menu.clicked(VillageHallScreen.NAME, 0, ClickType.QUICK_MOVE, stranger);
		helper.assertTrue(hall.isProtected(), "a stranger's shift-click opened the owner's protected village");
		List<String> keys = keys(level, stranger, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(keys.contains(UNPROTECT) && !keys.contains(PROTECT),
			"after a stranger's refused shift-click the hint no longer matches the protected village: " + keys);
		helper.succeed();
	}

	/** A hall nobody owns yet is open, so its hint offers protection (the first shift-click claims and protects it). */
	//$ gametest_ticks_batch AREA '40' '"qaB37AnUnownedHallHintsProtect"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB37AnUnownedHallHintsProtect")
	public void qaB37AnUnownedHallHintsProtect(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos at = helper.absolutePos(HALL);
		unprotectAfter(helper, at);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at);
		Leftovers.after(helper, () -> hall.setProtected(false)); // a protected hall left behind locks later tests' halls nearby
		ChoiceMenu menu = VillageHallScreen.forTest(player, at);
		List<String> before = keys(level, player, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(before.contains(PROTECT) && !before.contains(UNPROTECT), "an unowned, open hall doesn't hint 'protect': " + before);

		menu.clicked(VillageHallScreen.NAME, 0, ClickType.QUICK_MOVE, player);
		helper.assertTrue(hall.isProtected() && player.getUUID().equals(hall.owner()), "the first shift-click didn't claim and protect the hall");
		List<String> after = keys(level, player, menu.icon(VillageHallScreen.NAME));
		helper.assertTrue(after.contains(UNPROTECT) && !after.contains(PROTECT), "after claiming, the hint still says 'protect': " + after);
		helper.succeed();
	}

	/** The two hints, as a player reads them: one protects, the other opens the village again, and they differ. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB37TheTwoHintsReadRight(GameTestHelper helper) {
		Language lang = Language.getInstance();
		helper.assertTrue(lang.has(UNPROTECT), "the open-again hint has no English text");
		String protect = lang.getOrDefault(PROTECT);
		String open = lang.getOrDefault(UNPROTECT);
		helper.assertTrue(protect.startsWith("Shift-click") && protect.contains("protect"), "the protect hint reads '" + protect + "'");
		helper.assertTrue(open.startsWith("Shift-click") && open.contains("open the village") && !open.contains("protect the village"),
			"the open-again hint reads '" + open + "'");
		helper.assertFalse(open.contains("%"), "the open-again hint has a placeholder nobody fills: '" + open + "'");
		helper.succeed();
	}

	/** Leaves nothing protected behind: a protected hall keeps the next batches' mock players out for 64 blocks. */
	private static void unprotectAfter(GameTestHelper helper, BlockPos at) {
		Leftovers.after(helper, () -> {
			if (helper.getLevel().getBlockEntity(at) instanceof VillageHallBlockEntity hall) {
				hall.setProtected(false);
			}
		});
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
