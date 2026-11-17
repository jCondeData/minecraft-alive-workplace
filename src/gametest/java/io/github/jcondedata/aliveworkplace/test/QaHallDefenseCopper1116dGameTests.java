package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;

/**
 * QA, lane D 2026-11-16, ROADMAP 47.2 (the Village Hall in Copperline), the Defense section, from the spec (Milestone 47:
 * nothing a screen did before is lost, only how it looks): the hall opened by hand, Defense reached through the rail's
 * own click, and its buttons pressed through the menu's click path (what a real click sends). Hiring mercenaries
 * without the emeralds is refused and charges nothing; with them it takes the price and brings the band; a second hire
 * while the band is here is refused and charges nothing; the section stays open throughout. A village without guards
 * says Defense is empty and still lists Call everyone home and Hire mercenaries; one guard reads "Guards: 1" and clears
 * the empty flag. The price and counts read whole, in American English, without "(s)" or a cut.
 */
public class QaHallDefenseCopper1116dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	private static ChoiceMenu openByHand(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	private static ChoiceMenu railToDefense(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player) {
		helper.assertTrue(menu.clickMenuButton(player, Section.DEFENSE.ordinal()), "the rail's Defense click wasn't taken");
		helper.assertTrue(HallSections.current(menu) == Section.DEFENSE, "the rail opened " + HallSections.current(menu) + ", not Defense");
		return menu;
	}

	/** A real left click on a main-page button (the packet's path: {@link ChoiceMenu#clicked}). */
	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	private static List<String> lore(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static void words(List<String> problems, String where, String text) {
		String low = text.toLowerCase();
		for (String bad : List.of("…", "...", "(s)", "defence", "colour", "armour", "%s", "%d")) {
			if (low.contains(bad)) {
				problems.add(where + " has '" + bad + "': " + text);
			}
		}
	}

	private static void sendOff(ServerLevel level, BlockPos hall) {
		for (Villager v : Mercenaries.near(level, hall)) {
			v.discard();
		}
	}

	/**
	 * No guards, one villager: Defense opens from the rail, says it is empty ("No guards yet."), and still lists the
	 * guards' count, Call everyone home and Hire mercenaries; the mercenaries' line names the band and the whole price
	 * ("3 fighters in iron until dawn, for 12 emeralds"). A click one emerald short is refused (no band, nothing taken, the section stays);
	 * with the price plus two, the band of three comes and exactly the price is taken, the button then says they are here;
	 * a second click while they are is refused and takes nothing; reopened by hand, the hall is back on Defense.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaD1116DefenseHire"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaD1116DefenseHire")
	public void defenseHiresMercenariesThroughTheRailAndTheButton(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			sendOff(level, hall);
			level.getServer().getPlayerList().remove(player);
		});
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = railToDefense(helper, openByHand(helper, player), player);
			List<String> problems = new ArrayList<>();
			List<Integer> shown = HallSections.slots(menu, Section.DEFENSE);
			for (int slot : new int[]{VillageHallScreen.GUARDS, VillageHallScreen.RECALL, VillageHallScreen.MERCENARIES}) {
				if (!shown.contains(slot)) {
					problems.add("Defense lacks button " + slot + " (" + menu.icon(slot).getHoverName().getString() + ")");
				}
			}
			if (!HallSections.isEmpty(menu, Section.DEFENSE)) {
				problems.add("no guards, but Defense isn't flagged empty");
			}
			if (!Section.DEFENSE.empty().getString().equals("No guards yet.")) {
				problems.add("the empty sentence: " + Section.DEFENSE.empty().getString());
			}
			if (!menu.icon(VillageHallScreen.GUARDS).getHoverName().getString().equals("Guards: 0")) {
				problems.add("the guards' button: " + menu.icon(VillageHallScreen.GUARDS).getHoverName().getString());
			}
			int price = Mercenaries.price(level, hall);
			String priceWords = Money.describe((long) price * Money.DOLLARS_PER_EMERALD, price).getString();
			List<String> mercs = lore(menu.icon(VillageHallScreen.MERCENARIES));
			String expected = Mercenaries.BAND + " fighters in iron until dawn, for " + priceWords;
			if (mercs.isEmpty() || !mercs.get(0).equals(expected) || !priceWords.equals(price + " emeralds")) {
				problems.add("the mercenaries' line: " + mercs + ", expected '" + expected + "' with '" + price + " emeralds'");
			}
			if (!menu.icon(VillageHallScreen.MERCENARIES).getHoverName().getString().equals("Hire mercenaries")) {
				problems.add("the mercenaries' name: " + menu.icon(VillageHallScreen.MERCENARIES).getHoverName().getString());
			}
			for (int slot : shown) {
				words(problems, "button " + slot, menu.icon(slot).getHoverName().getString());
				for (String line : lore(menu.icon(slot))) {
					words(problems, "button " + slot, line);
				}
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));

			// One emerald short: refused, nothing charged, the section stays open.
			player.getInventory().add(new ItemStack(Items.EMERALD, price - 1));
			click(menu, VillageHallScreen.MERCENARIES, player);
			helper.assertTrue(Mercenaries.near(level, hall).isEmpty(), "a band came one emerald short: " + Mercenaries.near(level, hall).size());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == price - 1, "a refused hire took emeralds: " + player.getInventory().countItem(Items.EMERALD) + " of " + (price - 1));
			helper.assertTrue(HallSections.current(menu) == Section.DEFENSE, "the refused hire left Defense for " + HallSections.current(menu));

			// With the price and two over: the band comes, exactly the price is taken.
			player.getInventory().add(new ItemStack(Items.EMERALD, 3));
			click(menu, VillageHallScreen.MERCENARIES, player);
			int band = Mercenaries.near(level, hall).size();
			int left = player.getInventory().countItem(Items.EMERALD);
			helper.assertTrue(band == Mercenaries.BAND && left == 2, "hired: a band of " + band + ", " + left + " emeralds left of " + (price + 2));
			helper.assertTrue(HallSections.current(menu) == Section.DEFENSE && HallSections.slots(menu, Section.DEFENSE).contains(VillageHallScreen.MERCENARIES),
				"after the hire the hall shows " + HallSections.current(menu));
			List<String> here = lore(menu.icon(VillageHallScreen.MERCENARIES));
			helper.assertTrue(here.equals(List.of(Mercenaries.BAND + " mercenaries are here until dawn")), "the button after the hire: " + here);

			// Again while they are here: refused, nothing taken, no second band.
			player.getInventory().add(new ItemStack(Items.EMERALD, price));
			click(menu, VillageHallScreen.MERCENARIES, player);
			helper.assertTrue(Mercenaries.near(level, hall).size() == Mercenaries.BAND && player.getInventory().countItem(Items.EMERALD) == price + 2,
				"a second hire: band " + Mercenaries.near(level, hall).size() + ", emeralds " + player.getInventory().countItem(Items.EMERALD));
			player.closeContainer();

			// Opened by hand again: back on Defense, the band still listed as here.
			ChoiceMenu again = openByHand(helper, player);
			helper.assertTrue(HallSections.current(again) == Section.DEFENSE, "reopened on " + HallSections.current(again) + ", not Defense");
			helper.assertTrue(lore(again.icon(VillageHallScreen.MERCENARIES)).equals(here), "reopened, the button: " + lore(again.icon(VillageHallScreen.MERCENARIES)));
			player.closeContainer();
			sendOff(level, hall);
			HallSections.forget();
			helper.succeed();
		});
	}

	/**
	 * One guard at a guard post: "Guards: 1", the Overview card's number 1, Defense isn't empty; the guards' button keeps its lines
	 * (watched, the lair or camp line) and lists in Defense and on the Overview as before.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaD1116DefenseGuard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaD1116DefenseGuard")
	public void oneGuardFillsDefense(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos post = new BlockPos(1, 2, 6);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			ChoiceMenu menu = railToDefense(helper, openByHand(helper, player), player);
			List<String> problems = new ArrayList<>();
			if (HallSections.isEmpty(menu, Section.DEFENSE)) {
				problems.add("a guard, but Defense says it is empty");
			}
			if (menu.state(HallSections.GUARDS) != 1) {
				problems.add("the guards' number: " + menu.state(HallSections.GUARDS));
			}
			String name = menu.icon(VillageHallScreen.GUARDS).getHoverName().getString();
			if (!name.equals("Guards: 1")) {
				problems.add("the guards' button: " + name);
			}
			List<String> lines = lore(menu.icon(VillageHallScreen.GUARDS));
			if (lines.size() < 2) {
				problems.add("the guards' button lost its lines: " + lines);
			}
			for (String line : lines) {
				words(problems, "the guards' button", line);
			}
			List<Integer> defense = HallSections.slots(menu, Section.DEFENSE);
			List<Integer> overview = HallSections.slots(menu, Section.OVERVIEW);
			if (!defense.contains(VillageHallScreen.GUARDS) || !overview.contains(VillageHallScreen.GUARDS)) {
				problems.add("the guards' button: in Defense " + defense.contains(VillageHallScreen.GUARDS) + ", on the Overview " + overview.contains(VillageHallScreen.GUARDS));
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			player.closeContainer();
			HallSections.forget();
			helper.succeed();
		});
	}

	/**
	 * Call everyone home from Defense with everyone at home: nobody moves, the section stays open with its three
	 * buttons; the empty Overview card and "Needs you" send to Defense when there are villagers and no guards.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaD1116DefenseRecall"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaD1116DefenseRecall")
	public void recallFromDefenseKeepsTheSection(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = railToDefense(helper, openByHand(helper, player), player);
			BlockPos before = villager.blockPosition();
			click(menu, VillageHallScreen.RECALL, player);
			helper.assertTrue(villager.blockPosition().equals(before), "a villager at home was moved by the call: " + before + " -> " + villager.blockPosition());
			List<Integer> shown = HallSections.slots(menu, Section.DEFENSE);
			helper.assertTrue(HallSections.current(menu) == Section.DEFENSE && shown.contains(VillageHallScreen.RECALL)
				&& shown.contains(VillageHallScreen.MERCENARIES) && shown.contains(VillageHallScreen.GUARDS), "after the call: " + HallSections.current(menu) + " " + shown);
			helper.assertTrue(lore(menu.icon(VillageHallScreen.RECALL)).equals(List.of("Villagers who live or work here but wandered off come back to the hall")),
				"the call's line: " + lore(menu.icon(VillageHallScreen.RECALL)));
			List<io.github.jcondedata.aliveworkplace.hall.HallBoard.Need> needs = io.github.jcondedata.aliveworkplace.hall.HallBoard.needs(menu::icon, menu::state, 20);
			helper.assertTrue(needs.stream().anyMatch(n -> n.opens() == Section.DEFENSE), "no guards, but nothing in Needs you opens Defense: "
				+ needs.stream().map(n -> n.text().getString()).toList());
			player.closeContainer();
			HallSections.forget();
			helper.succeed();
		});
	}

	/**
	 * Call everyone home pressed from Defense brings a villager who lives here but wandered past the village's edge back
	 * beside the hall, and the section stays open (the hall's radius cut to 5 so the wanderer fits in the test area).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaD1116DefenseWanderer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaD1116DefenseWanderer")
	public void recallFromDefenseBringsAWandererHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		int was = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 5;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Villager wanderer = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ChoiceMenu menu = railToDefense(helper, openByHand(helper, player), player);
			wanderer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
				net.minecraft.core.GlobalPos.of(level.dimension(), hall.offset(1, 0, 1)));
			helper.assertTrue(wanderer.blockPosition().distSqr(hall) > 25, "setup: the wanderer stands within the village at " + wanderer.blockPosition());
			click(menu, VillageHallScreen.RECALL, player);
			helper.assertTrue(wanderer.blockPosition().distSqr(hall) <= 25, "Call everyone home from Defense left the wanderer at " + wanderer.blockPosition()
				+ " (the hall at " + hall + ")");
			helper.assertTrue(HallSections.current(menu) == Section.DEFENSE, "after the call the hall shows " + HallSections.current(menu));
			player.closeContainer();
			HallSections.forget();
			helper.succeed();
		});
	}

	/** Every sentence the Defense section reads is in the lang file, in American English, with no "(s)". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void defenseWordsAreWholeAndAmerican(GameTestHelper helper) {
		Language en = Language.getInstance();
		List<String> problems = new ArrayList<>();
		Map<String, String> expected = Map.of(
			"screen.aliveworkplace.hall.section.defense", "Defense",
			"screen.aliveworkplace.hall.section.defense.empty", "No guards yet.",
			"screen.aliveworkplace.hall.recall", "Call everyone home",
			"screen.aliveworkplace.hall.mercenaries", "Hire mercenaries",
			"message.aliveworkplace.hall.recall_none", "Everyone is home");
		expected.forEach((key, want) -> {
			if (!en.has(key) || !en.getOrDefault(key).equals(want)) {
				problems.add(key + ": '" + en.getOrDefault(key) + "', expected '" + want + "'");
			}
		});
		for (String key : List.of("screen.aliveworkplace.hall.section.defense.about", "screen.aliveworkplace.hall.guarded", "screen.aliveworkplace.hall.unguarded",
			"screen.aliveworkplace.hall.recall_hint", "message.aliveworkplace.hall.recalled", "message.aliveworkplace.hall.recalled.one",
			"screen.aliveworkplace.hall.mercenaries_hint", "screen.aliveworkplace.hall.mercenaries_here", "screen.aliveworkplace.hall.mercenaries_here.one",
			"message.aliveworkplace.mercenaries.already", "message.aliveworkplace.mercenaries.cant_afford", "message.aliveworkplace.mercenaries.hired")) {
			if (!en.has(key)) {
				problems.add("no words for " + key);
				continue;
			}
			String text = en.getOrDefault(key).replace("%s", "3");
			words(problems, key, text);
		}
		String refused = Component.translatable("message.aliveworkplace.mercenaries.cant_afford", Money.describe(12L * Money.DOLLARS_PER_EMERALD, 12)).getString();
		if (!refused.equals("Mercenaries cost 12 emeralds")) {
			problems.add("the refusal: " + refused);
		}
		String one = io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.hall.recalled", 1, 1).getString();
		if (!one.equals("1 villager came home")) {
			problems.add("one recalled: " + one);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
