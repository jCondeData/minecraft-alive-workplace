package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * B19: menu buttons that use a weapon as their picture (the hall's Guards and Mercenaries, Research's Drill) show only
 * their own lines, never the sword's "When in Main Hand: 6 Attack Damage, 1.6 Attack Speed".
 */
public class MenuIconTooltipGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** Every button on the hall's main page, Guards and Mercenaries among them: name and lore, no attribute lines. */
	//$ gametest_ticks_batch AREA '40' '"hallButtonsHideAttributeLines"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "hallButtonsHideAttributeLines")
	public void hallButtonsHideAttributeLines(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		helper.assertTrue(menu.icon(VillageHallScreen.MERCENARIES).is(Items.IRON_SWORD), "no Mercenaries button: " + menu.icon(VillageHallScreen.MERCENARIES));
		ItemStack guards = menu.icon(VillageHallScreen.GUARDS);
		helper.assertTrue(guards.is(Items.IRON_SWORD), "no Guards button: " + guards);
		List<String> guardLines = lines(level, player, guards);
		helper.assertTrue(guardLines.contains("screen.aliveworkplace.hall.guards"), "the Guards button lost its own name: " + guardLines);
		checkAll(helper, level, player, menu, "hall");
		helper.succeed();
	}

	/** Every topic on the research screen, Drill (an iron sword) among them: no attribute lines. */
	//$ gametest_ticks_batch AREA '40' '"researchButtonsHideAttributeLines"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "researchButtonsHideAttributeLines")
	public void researchButtonsHideAttributeLines(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().remove(player);
			Research.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ChoiceMenu menu = ResearchScreen.forTest(player, helper.absolutePos(HALL));
		ItemStack drill = menu.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]);
		helper.assertTrue(drill.is(Items.IRON_SWORD), "Drill isn't drawn as a sword: " + drill);
		checkAll(helper, level, player, menu, "research");
		helper.succeed();
	}

	private static void checkAll(GameTestHelper helper, ServerLevel level, ServerPlayer player, ChoiceMenu menu, String screen) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty()) {
				continue;
			}
			for (String key : lines(level, player, icon)) {
				helper.assertTrue(!key.startsWith("attribute.") && !key.startsWith("item.modifiers."),
					screen + " slot " + slot + " (" + icon.getItem() + ") shows an attribute line: " + key);
			}
		}
	}

	/** Every translation key and literal in the tooltip a player sees, arguments and siblings included. */
	private static List<String> lines(ServerLevel level, ServerPlayer player, ItemStack stack) {
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
