package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.ui.ListMenuHover;
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
 * B1355: since Copperline 48.4 a list menu's rail tab drew no tooltip and the whisper line read only its name and first
 * line, so a tab's later lines were lost (the Move Tutor's "N more from a more experienced tutor", Research's click
 * line). {@link ListMenuHover} decides what {@code ChoiceTableScreen} shows for the entry under the mouse; every line
 * of a tab's tooltip must be readable there: in the floating tooltip it draws, and the whisper line names the tab.
 */
public class ListMenuHoverBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The lines the entry's floating tooltip shows (the item's own tooltip, as {@code renderTooltip} draws it). */
	private static List<String> tooltip(ItemStack stack) {
		return stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	/** Every lore line of {@code tab} is readable when it's under the mouse, and the whisper line names it. */
	private static void everyLineReadable(GameTestHelper helper, ItemStack tab, String what) {
		helper.assertTrue(ChoiceList.mark(tab) != null && ChoiceList.mark(tab).kind() == ChoiceList.Kind.TAB, what + " isn't marked a tab");
		helper.assertTrue(ListMenuHover.tooltip(tab), what + ": a rail tab under the mouse draws no tooltip");
		List<String> shown = tooltip(tab);
		helper.assertTrue(!shown.isEmpty() && shown.get(0).equals(tab.getHoverName().getString()), what + "'s tooltip doesn't start with its name: " + shown);
		for (Component line : ListMenuHover.lore(tab)) {
			helper.assertTrue(shown.contains(line.getString()), what + "'s line '" + line.getString() + "' is in no tooltip: " + shown);
		}
		Component fallback = Component.literal("the menu's hint");
		String whisper = ListMenuHover.whisper(tab, true, fallback).getString();
		String first = ListMenuHover.lore(tab).get(0).getString();
		helper.assertTrue(whisper.equals(Component.translatable("screen.aliveworkplace.list.tab_whisper", tab.getHoverName(), first).getString()),
			what + "'s whisper line doesn't name it with its first line: " + whisper);
	}

	/**
	 * A Move Tutor Pokemon tab, made as {@code CobblemonTutors} makes it (Cobblemon isn't loaded here): its second line,
	 * "N more from a more experienced tutor", is in the tooltip drawn on hover. A row reads its first line in the
	 * whisper line, nothing under the mouse the menu's hint, and a tab with no lore its name.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tutorTabShowsMoreLater(GameTestHelper helper) {
		ItemStack tab = new ItemStack(Items.PAPER);
		tab.set(DataComponents.CUSTOM_NAME, Component.translatable("message.aliveworkplace.tutor.pokemon", "Pikachu", 30)
			.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE)));
		tab.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("message.aliveworkplace.tutor.can_learn", 3L).withStyle(ChatFormatting.GREEN),
			Component.translatable("message.aliveworkplace.tutor.more_later", 2L).withStyle(ChatFormatting.DARK_GRAY))));
		ChoiceList.tab(tab, false);
		everyLineReadable(helper, tab, "the Move Tutor's Pokemon tab");
		String later = Component.translatable("message.aliveworkplace.tutor.more_later", 2L).getString();
		helper.assertTrue(tooltip(tab).contains(later), "'more from a more experienced tutor' is lost: " + tooltip(tab));

		Component hint = Component.literal("the menu's hint");
		helper.assertTrue(ListMenuHover.whisper(null, false, hint) == hint && ListMenuHover.whisper(ItemStack.EMPTY, true, hint) == hint,
			"nothing under the mouse doesn't read the menu's hint");
		ItemStack row = new ItemStack(Items.PAPER);
		row.set(DataComponents.CUSTOM_NAME, Component.literal("Thunderbolt"));
		row.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Power 90"), Component.literal("Accuracy 100"))));
		helper.assertTrue(ListMenuHover.whisper(row, false, hint).getString().equals("Power 90") && ListMenuHover.tooltip(row),
			"a row's whisper line or tooltip: " + ListMenuHover.whisper(row, false, hint).getString());
		ItemStack bare = new ItemStack(Items.PAPER);
		bare.set(DataComponents.CUSTOM_NAME, Component.literal("Party"));
		helper.assertTrue(ListMenuHover.whisper(bare, true, hint).getString().equals("Party") && ListMenuHover.whisper(bare, false, hint) == hint,
			"a tab without lore names itself, a row without lore reads the hint");
		helper.assertTrue(!ListMenuHover.tooltip(ItemStack.EMPTY), "an empty slot draws a tooltip");
		helper.succeed();
	}

	/**
	 * The real Research screen in a capital: the Realm tab (not the open one) has its about line and then its click line
	 * ("Click: show this research"), and both are in the tooltip drawn on hover.
	 */
	//$ gametest_ticks_batch AREA '100' '"listMenuHoverResearch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "listMenuHoverResearch")
	public void researchTabKeepsItsClickLine(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0));
		ItemStack realm = menu.icon(ResearchScreen.REALM_TAB);
		helper.assertTrue(!realm.isEmpty(), "the capital has no Realm tab");
		List<Component> lore = ListMenuHover.lore(realm);
		String click = Component.translatable("screen.aliveworkplace.research.tab.click").getString();
		helper.assertTrue(lore.size() >= 2 && lore.get(lore.size() - 1).getString().equals(click),
			"the Realm tab's click line isn't after its first line: " + lore.stream().map(Component::getString).toList());
		everyLineReadable(helper, realm, "Research's Realm tab");
		helper.assertTrue(tooltip(realm).contains(click), "Research's click line is lost: " + tooltip(realm));
		helper.succeed();
	}
}
