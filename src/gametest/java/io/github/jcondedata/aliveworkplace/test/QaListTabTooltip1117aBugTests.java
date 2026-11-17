package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

/**
 * B1355 (lane A, 2026-11-17): since 48.4 a list menu's rail tab drew no tooltip, and the whisper line holds only the tab's
 * name and its first line, so every later line was lost: the Move Tutor's "N more from a more experienced tutor" and
 * Research's "Click: show this research". The tooltip a rail tab offers ({@link ChoiceList#tabTooltip}, what the screen
 * draws under the mouse) must keep every line, and the whisper line ({@link ChoiceList#tabWhisper}) its name and first line.
 */
public class QaListTabTooltip1117aBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static List<String> tooltip(GameTestHelper helper, ItemStack tab) {
		return ChoiceList.tabTooltip(tab, Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.Default.NORMAL)
			.stream().map(Component::getString).toList();
	}

	/** The capital's Realm tab, as a real Research screen sends it: its about line and its click line both in the tooltip. */
	//$ gametest_ticks_batch HUGE '100' '"qaListTabTooltip1117a"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaListTabTooltip1117a")
	public void aResearchTabsTooltipKeepsItsClickLine(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0));
		ItemStack tab = menu.icon(ResearchScreen.REALM_TAB);
		ChoiceList.Mark mark = ChoiceList.mark(tab);
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.TAB, "setup: the Realm tab isn't a rail tab: " + tab);
		List<String> lines = tooltip(helper, tab);
		String about = Component.translatable("screen.aliveworkplace.research.tab.realm.about").getString();
		String click = Component.translatable("screen.aliveworkplace.research.tab.click").getString();
		helper.assertTrue(lines.size() >= 3 && lines.get(0).equals("The realm's research") && lines.contains(about) && lines.contains(click),
			"the Realm tab's tooltip lost a line (expected its name, '" + about + "' and '" + click + "'): " + lines);
		String whisper = ChoiceList.tabWhisper(tab).getString();
		helper.assertTrue(whisper.equals("The realm's research: " + about),
			"the whisper line should name the tab and its first line: '" + whisper + "'");
		// the open Scholars tab has no click line; its tooltip is its name and about line
		List<String> scholars = tooltip(helper, menu.icon(ResearchScreen.TAB_SLOTS[0]));
		helper.assertTrue(scholars.contains(Component.translatable("screen.aliveworkplace.research.tab.scholars.about").getString()) && !scholars.contains(click),
			"the open Scholars tab's tooltip: " + scholars);
		helper.succeed();
	}

	/**
	 * A tab built as the Move Tutor builds a Pokémon's (a name, "N moves to learn" and "N more from a more experienced
	 * tutor"): the second line is in the tooltip, and the whisper line gives the name and the first; a tab with no lines
	 * whispers just its name and its tooltip is that name.
	 */
	//$ gametest_ticks_batch HUGE '20' '"qaListTabTooltip1117a"'
	@GameTest(template = HUGE, timeoutTicks = 20, batch = "qaListTabTooltip1117a")
	public void aTutorTabsTooltipKeepsItsMoreLaterLine(GameTestHelper helper) {
		ItemStack icon = new ItemStack(Items.PAPER);
		icon.set(DataComponents.CUSTOM_NAME, Component.literal("Ivysaur").withStyle(s -> s.withItalic(false)));
		icon.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("message.aliveworkplace.tutor.can_learn", 3).withStyle(ChatFormatting.GREEN),
			Component.translatable("message.aliveworkplace.tutor.more_later", 2).withStyle(ChatFormatting.DARK_GRAY))));
		ItemStack tab = ChoiceList.tab(icon, false);
		String first = Component.translatable("message.aliveworkplace.tutor.can_learn", 3).getString();
		String later = Component.translatable("message.aliveworkplace.tutor.more_later", 2).getString();
		List<String> lines = tooltip(helper, tab);
		helper.assertTrue(lines.size() >= 3 && lines.get(0).equals("Ivysaur") && lines.get(1).equals(first) && lines.get(2).equals(later),
			"the tutor tab's tooltip lost its lines (expected Ivysaur, '" + first + "', '" + later + "'): " + lines);
		String whisper = ChoiceList.tabWhisper(tab).getString();
		helper.assertTrue(whisper.equals("Ivysaur: " + first), "the tutor tab's whisper line: '" + whisper + "'");
		ItemStack bare = ChoiceList.tab(new ItemStack(Items.BOOK), true);
		helper.assertTrue(ChoiceList.tabWhisper(bare).getString().equals(bare.getHoverName().getString())
			&& tooltip(helper, bare).equals(List.of(bare.getHoverName().getString())),
			"a tab with no lines: whisper '" + ChoiceList.tabWhisper(bare).getString() + "', tooltip " + tooltip(helper, bare));
		helper.succeed();
	}
}
