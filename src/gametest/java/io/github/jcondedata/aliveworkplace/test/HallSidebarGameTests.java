package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.PlayerBank;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * ROADMAP 40.1: the Village Hall's sidebar. Every section opens through the hall itself (a right-click on the block, then
 * the sidebar's click), holds the buttons {@code docs/design/M40.md} puts there and reaches every page and action of
 * today's hall from them; nothing on the main page is left out of every section; an empty section says so; the section
 * a player last opened is the one the hall opens on next.
 */
public class HallSidebarGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	/** Opens the hall as a player does: a right-click on the block, standing next to it. */
	private static ChoiceMenu openByHand(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/** A sidebar click, as the client's button click reaches the menu. */
	private static void sidebar(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player, Section section) {
		helper.assertTrue(menu.clickMenuButton(player, section.ordinal()), "the sidebar didn't take " + section);
		helper.assertTrue(HallSections.current(menu) == section, "clicked " + section + ", open: " + HallSections.current(menu));
		helper.assertTrue(!HallSections.slots(menu, Section.OVERVIEW).isEmpty(), section + ": not on the hall's main page");
	}

	/** What docs/design/M40.md puts in each section (the fixed buttons; the page tabs are checked by their page). */
	private static Map<Section, int[]> designed() {
		Map<Section, int[]> out = new EnumMap<>(Section.class);
		out.put(Section.OVERVIEW, new int[]{VillageHallScreen.NAME, 1, VillageHallScreen.BEDS, VillageHallScreen.FOOD, VillageHallScreen.GUARDS, 5, 6,
			VillageHallScreen.BUILDS, VillageHallScreen.QUESTS, VillageHallScreen.ADVICE});
		out.put(Section.PEOPLE, new int[]{1, 5, 6, VillageHallScreen.RECALL, VillageHallScreen.FIRST_PERSON});
		out.put(Section.HOMES, new int[]{VillageHallScreen.BEDS, VillageHallScreen.FOOD});
		out.put(Section.BUILDINGS, new int[]{VillageHallScreen.BUILDS, VillageHallScreen.ADVICE});
		out.put(Section.DEFENSE, new int[]{VillageHallScreen.GUARDS, VillageHallScreen.RECALL, VillageHallScreen.MERCENARIES});
		out.put(Section.QUESTS, new int[]{VillageHallScreen.QUESTS});
		out.put(Section.TRADE, new int[]{VillageHallScreen.ROUTES, PlayerBank.BUTTON});
		out.put(Section.CALENDAR, new int[]{VillageHallScreen.CHRONICLE, VillageHallScreen.FESTIVAL});
		out.put(Section.LAWS, new int[]{VillageHallScreen.NAME, VillageHallScreen.BOOK});
		out.put(Section.MAP, new int[]{VillageHallScreen.MAP});
		return out;
	}

	/** The buttons that open a page of their own (the rest act and stay, or are figures to read). */
	private static Set<Integer> pages() {
		Set<Integer> out = new HashSet<>(List.of(VillageHallScreen.BUILDS, VillageHallScreen.QUESTS, VillageHallScreen.BOOK, VillageHallScreen.CHRONICLE,
			VillageHallScreen.ROUTES, VillageHallScreen.ADVICE, PlayerBank.BUTTON));
		if (DefencePage.open()) {
			out.add(VillageHallScreen.GUARDS);
		}
		return out;
	}

	/**
	 * Every section through the hall's own entry point: its buttons are the design note's, each page opens from it (the
	 * main page gives way) and the sidebar brings the section back; the actions run and leave the section open; the
	 * villager's row makes them glow; every hall page's tab sits in its section; and every button on the main page sits
	 * in at least one section, so nothing of today's hall is lost.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallSidebarEverySection"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallSidebarEverySection")
	public void everySectionOpensAndReachesItsPages(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager villager = (Villager) helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(HallSections.current(menu) == Section.OVERVIEW, "a first visit opens on " + HallSections.current(menu));
			Seasons.Date today = Seasons.today(helper.getLevel());
			helper.assertTrue(menu.state(HallSections.SEASON) == today.season().ordinal() && menu.state(HallSections.DAY) == today.dayOfSeason()
				&& menu.state(HallSections.YEAR) == today.year(), "the sidebar's date isn't today's: " + menu.state(HallSections.SEASON) + " "
				+ menu.state(HallSections.DAY) + " " + menu.state(HallSections.YEAR));

			// Nothing dropped: every button of the main page is in a section.
			Set<Integer> placed = new HashSet<>();
			for (Section s : Section.values()) {
				placed.addAll(HallSections.slots(menu, s));
			}
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				ItemStack icon = menu.icon(slot);
				if (!icon.isEmpty() && !icon.has(DataComponents.HIDE_TOOLTIP)) {
					helper.assertTrue(placed.contains(slot), "button " + slot + " (" + icon.getHoverName().getString() + ") is in no section");
				}
			}

			Set<Integer> pages = pages();
			for (Map.Entry<Section, int[]> e : designed().entrySet()) {
				Section section = e.getKey();
				sidebar(helper, menu, player, section);
				List<Integer> in = HallSections.slots(menu, section);
				for (int slot : e.getValue()) {
					helper.assertTrue(in.contains(slot), section + " lacks button " + slot + " (" + menu.icon(slot).getHoverName().getString() + "): " + in);
					if (pages.contains(slot)) {
						menu.press(slot, player);
						helper.assertTrue(HallSections.slots(menu, Section.OVERVIEW).isEmpty() && !menu.icon(4).isEmpty(),
							section + ": button " + slot + " opened no page");
						sidebar(helper, menu, player, section); // back to the section's list from the page
					}
				}
			}

			// The people list: a jobless villager's row in People opens their page (the free jobs), whose Find makes them glow.
			sidebar(helper, menu, player, Section.PEOPLE);
			menu.press(VillageHallScreen.FIRST_PERSON, player);
			helper.assertTrue(HallSections.slots(menu, Section.OVERVIEW).isEmpty() && menu.icon(VillageHallScreen.FIND).is(net.minecraft.world.item.Items.SPYGLASS),
				"the villager's row in People didn't open their page: " + menu.icon(VillageHallScreen.FIND));
			String listed = menu.icon(4).getHoverName().getString();
			menu.press(VillageHallScreen.FIND, player);
			helper.assertTrue(villager.hasEffect(MobEffects.GLOWING), "Find on the villager's page didn't make them glow: listed " + listed
				+ ", spawned " + villager.getDisplayName().getString() + " alive " + villager.isAlive() + ", effects " + villager.getActiveEffects());
			menu.press(0, player);
			helper.assertTrue(HallSections.current(menu) == Section.PEOPLE && HallSections.slots(menu, Section.PEOPLE).contains(VillageHallScreen.FIRST_PERSON),
				"back from the villager's page didn't return to People");

			// The actions: each runs from its section and the section stays open.
			int[][] actions = {{Section.MAP.ordinal(), VillageHallScreen.MAP}, {Section.PEOPLE.ordinal(), VillageHallScreen.RECALL},
				{Section.DEFENSE.ordinal(), VillageHallScreen.MERCENARIES}, {Section.CALENDAR.ordinal(), VillageHallScreen.FESTIVAL},
				{Section.LAWS.ordinal(), VillageHallScreen.NAME}};
			for (int[] action : actions) {
				Section section = Section.of(action[0]);
				sidebar(helper, menu, player, section);
				menu.press(action[1], player);
				helper.assertTrue(HallSections.current(menu) == section && HallSections.slots(menu, section).contains(action[1]),
					section + ": button " + action[1] + " left the section");
			}
			for (Villager hired : io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(helper.getLevel(), helper.absolutePos(HALL))) {
				hired.discard(); // the band hired above doesn't wander into later tests
			}

			// Every hall page's tab sits in its section and opens from there.
			for (HallPages.Page page : HallPages.all()) {
				int slot = HallPages.slot(page.id());
				if (slot < 0) {
					continue; // a spare page with no tab (the row is full): as before 40.1
				}
				for (Section section : Section.values()) {
					if ((HallSections.sectionsOf(page.id()) & section.bit()) == 0) {
						continue;
					}
					sidebar(helper, menu, player, section);
					helper.assertTrue(HallSections.slots(menu, section).contains(slot), section + " lacks the " + page.id() + " page");
					menu.press(slot, player);
					helper.assertTrue(HallSections.slots(menu, Section.OVERVIEW).isEmpty() && !menu.icon(4).isEmpty(),
						"the " + page.id() + " page didn't open from " + section);
					menu.press(0, player); // its back arrow
					helper.assertTrue(HallSections.current(menu) == section && HallSections.slots(menu, section).contains(slot),
						"back from the " + page.id() + " page didn't return to " + section);
				}
			}
			helper.assertTrue((HallSections.sectionsOf("calendar") & Section.CALENDAR.bit()) != 0, "the calendar page isn't under Calendar");
			helper.assertTrue((HallSections.sectionsOf("legends") & Section.PEOPLE.bit()) != 0, "the Legends page isn't under People");
			helper.assertTrue((HallSections.sectionsOf("cup") & Section.CALENDAR.bit()) != 0, "the Festival Cup isn't under Calendar");
			helper.assertTrue((HallSections.sectionsOf("an_add_on_page") & Section.OVERVIEW.bit()) != 0, "a page placed nowhere was dropped");
			player.closeContainer();
			helper.succeed();
		});
	}

	/** The Overview: today's figures (the old "at a glance" row) and What next?, whose lines carry every tip and how. */
	//$ gametest_ticks_batch AREA '100' '"hallSidebarOverview"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallSidebarOverview")
	public void overviewShowsFiguresAndTips(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			List<Integer> overview = HallSections.slots(menu, Section.OVERVIEW);
			for (int slot = 0; slot < 9; slot++) {
				helper.assertTrue(overview.contains(slot), "figure " + slot + " isn't on the Overview: " + overview);
			}
			helper.assertTrue(overview.contains(VillageHallScreen.ADVICE), "What next? isn't on the Overview");
			List<VillageAdvice.Tip> tips = VillageAdvice.tips(helper.getLevel(), helper.absolutePos(HALL));
			helper.assertTrue(!tips.isEmpty(), "setup: an empty village should have advice");
			ItemLore lore = menu.icon(VillageHallScreen.ADVICE).get(DataComponents.LORE);
			List<String> lines = new ArrayList<>();
			for (Component line : lore == null ? List.<Component>of() : lore.lines()) {
				lines.add(line.getString());
			}
			for (VillageAdvice.Tip tip : tips) {
				helper.assertTrue(lines.contains(tip.title().getString()) && lines.contains(tip.how().getString()),
					"the tip " + tip.title().getString() + " isn't read out on the Overview: " + lines);
			}
			helper.assertTrue(!HallSections.isEmpty(menu, Section.OVERVIEW), "the Overview says it's empty");
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * A section with nothing in it says so: a hall with nobody, no guards and no laws has People,
	 * Defense and Laws & Realm empty, each with a sentence of its own; once a villager lives there, People
	 * isn't empty any more. The sidebar's words are all in en_us.json.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallSidebarEmpty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallSidebarEmpty")
	public void emptySectionSaysSo(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			for (Section s : List.of(Section.PEOPLE, Section.DEFENSE, Section.LAWS)) {
				sidebar(helper, menu, player, s);
				helper.assertTrue(HallSections.isEmpty(menu, s), s + " should say it's empty");
				helper.assertTrue(!HallSections.slots(menu, s).isEmpty(), s + ": an empty section still keeps its buttons");
			}
			for (Section s : List.of(Section.OVERVIEW, Section.CALENDAR, Section.MAP)) {
				helper.assertTrue(!HallSections.isEmpty(menu, s), s + " can't be empty");
			}
			Language lang = Language.getInstance();
			for (Section s : Section.values()) {
				for (String key : List.of("screen.aliveworkplace.hall.section." + s.id(), "screen.aliveworkplace.hall.section." + s.id() + ".about",
					"screen.aliveworkplace.hall.section." + s.id() + ".empty")) {
					helper.assertTrue(lang.has(key), "no text for " + key);
				}
			}
			helper.assertTrue(lang.has("screen.aliveworkplace.hall.date"), "no text for the date");
			helper.assertTrue(HallSections.date(0, 1, 1).getString().equals("Spring, day 1 · Year 1"),
				"the date reads " + HallSections.date(0, 1, 1).getString());
			helper.assertTrue(Section.HOMES.title().getString().equals("Homes & Food") && Section.LAWS.title().getString().equals("Laws & Realm"),
				"the sidebar's words: " + Section.HOMES.title().getString() + ", " + Section.LAWS.title().getString());
			helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
			helper.runAfterDelay(2, () -> {
				sidebar(helper, menu, player, Section.PEOPLE);
				helper.assertTrue(!HallSections.isEmpty(menu, Section.PEOPLE), "People still says it's empty with a villager there");
				player.closeContainer();
				helper.succeed();
			});
		});
	}

	/**
	 * The section last opened is remembered: closed and opened again (by hand or from a ledger), the hall opens on it;
	 * another player's hall opens on their own; a sidebar click from a page goes back to that section's list; a button
	 * number that's no section is refused and changes nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallSidebarRemembers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallSidebarRemembers")
	public void lastSectionIsRemembered(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			sidebar(helper, menu, player, Section.TRADE);
			player.closeContainer();
			ChoiceMenu again = openByHand(helper, player);
			helper.assertTrue(HallSections.current(again) == Section.TRADE, "reopened on " + HallSections.current(again));
			player.closeContainer();
			VillageHallScreen.openRemote(player, helper.absolutePos(HALL));
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu m && HallSections.current(m) == Section.TRADE, "the ledger's hall forgot");
			player.closeContainer();
			ChoiceMenu theirs = openByHand(helper, other);
			helper.assertTrue(HallSections.current(theirs) == Section.OVERVIEW, "another player's hall opened on " + HallSections.current(theirs));
			// From a page (the chronicle), the sidebar goes to a section's list.
			theirs.press(VillageHallScreen.CHRONICLE, other);
			helper.assertTrue(HallSections.slots(theirs, Section.OVERVIEW).isEmpty(), "setup: the chronicle didn't open");
			sidebar(helper, theirs, other, Section.CALENDAR);
			helper.assertTrue(!theirs.clickMenuButton(other, Section.values().length) && !theirs.clickMenuButton(other, -1),
				"a button that's no section was taken");
			helper.assertTrue(HallSections.current(theirs) == Section.CALENDAR, "a refused click changed the section");
			other.closeContainer();
			helper.succeed();
		});
	}
}
