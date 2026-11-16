package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallBoard;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * QA, lane C 2026-11-16, ROADMAP 47.2 (the Village Hall in Copperline), from the spec: every one of the ten sections
 * opens through the rail's own click and shows its buttons or says it is empty (Overview, Calendar and Map never are);
 * a click on no section changes nothing; the hall reopens on the section last opened; one villager reads in the
 * singular; the four cards at their boundaries; and the rail's entries (the Hall's ten, the Storehouse's, Move's three
 * steps) each sit inside the rail, apart and clickable at GUI scale 2 and on the 320x180 icon grid.
 */
public class QaHallCopper1116cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {320, 180}};

	private static ChoiceMenu openByHand(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/**
	 * One villager: every section opens through the rail's click and either lists buttons or is flagged empty (never
	 * Overview, Calendar or Map); clicks past the ten sections are refused and leave the section as it was; closed on
	 * the Map and opened by hand again, the hall opens on the Map. Every section has its title, its line and (where it
	 * can be empty) its empty sentence.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1116HallEverySection"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1116HallEverySection")
	public void everySectionOpensThroughTheRailAndShowsSomething(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(HallSections.current(menu) == Section.OVERVIEW, "a first visit opens on " + HallSections.current(menu));
			List<String> problems = new ArrayList<>();
			Language en = Language.getInstance();
			for (Section s : Section.values()) {
				if (!menu.clickMenuButton(player, s.ordinal())) {
					problems.add(s + ": the rail's click wasn't taken");
					continue;
				}
				if (HallSections.current(menu) != s) {
					problems.add(s + ": clicked, open " + HallSections.current(menu));
				}
				boolean empty = HallSections.isEmpty(menu, s);
				if (!s.canBeEmpty() && empty) {
					problems.add(s + " says it is empty");
				}
				if (!empty && HallSections.slots(menu, s).isEmpty() && s != Section.OVERVIEW) {
					problems.add(s + " shows nothing and doesn't say it is empty");
				}
				for (String key : List.of("screen.aliveworkplace.hall.section." + s.id(), "screen.aliveworkplace.hall.section." + s.id() + ".about")) {
					if (!en.has(key)) {
						problems.add("no words for " + key);
					}
				}
				if (s.canBeEmpty() && !en.has("screen.aliveworkplace.hall.section." + s.id() + ".empty")) {
					problems.add("no words for " + s + "'s empty sentence");
				}
			}
			Section before = HallSections.current(menu);
			for (int id : new int[]{Section.values().length, -1}) {
				boolean taken;
				try {
					taken = menu.clickMenuButton(player, id);
				} catch (RuntimeException e) {
					taken = true;
					problems.add("a click on " + id + " threw " + e);
				}
				if (taken || HallSections.current(menu) != before) {
					problems.add("a click on " + id + " was taken (open: " + HallSections.current(menu) + ")");
				}
			}
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			menu.clickMenuButton(player, Section.MAP.ordinal());
			player.closeContainer();
			ChoiceMenu again = openByHand(helper, player);
			helper.assertTrue(HallSections.current(again) == Section.MAP, "reopened on " + HallSections.current(again) + ", not the Map last opened");
			again.clickMenuButton(player, Section.OVERVIEW.ordinal());
			player.closeContainer();
			HallSections.forget();
			helper.succeed();
		});
	}

	/** One villager without a bed or a job: "1 villager needs a bed", "1 villager has no job", the People card "1 no bed". */
	//$ gametest_ticks_batch AREA '200' '"qaC1116HallOne"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1116HallOne")
	public void oneVillagerReadsInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 1, "people: " + menu.state(HallSections.PEOPLE_TOTAL));
			HallBoard.Card people = HallBoard.cards(menu::state).get(0);
			helper.assertTrue(people.big().equals("1") && people.line().getString().equals("1 no bed"), "the People card: " + people.big() + " / " + people.line().getString());
			List<String> needs = HallBoard.needs(menu::icon, menu::state, 20).stream().map(n -> n.text().getString()).toList();
			helper.assertTrue(needs.size() >= 2 && needs.get(0).equals("1 villager needs a bed") && needs.get(1).equals("1 villager has no job"),
				"Needs you: " + needs);
			player.closeContainer();
			helper.succeed();
		});
	}

	private static List<HallBoard.Card> cards(int people, int cap, int noBed, int jobless, int wellbeing, int meals, int kinds, int guards) {
		Map<Integer, Integer> s = new HashMap<>();
		s.put(HallSections.PEOPLE_TOTAL, people);
		s.put(HallSections.CAP, cap);
		s.put(HallSections.NO_BED, noBed);
		s.put(HallSections.PEOPLE_JOBLESS, jobless);
		s.put(HallSections.WELLBEING, wellbeing);
		s.put(HallSections.MEALS, meals);
		s.put(HallSections.MEAL_KINDS, kinds);
		s.put(HallSections.GUARDS, guards);
		return HallBoard.cards(i -> s.getOrDefault(i, 0));
	}

	/**
	 * The cards at their edges: Mood 70 Happy (good), 69 and 40 Getting by, 39 Unhappy (bad); one meal of one kind reads
	 * "1 meal" / "1 kind of meal"; one guard "1 guard" / "On watch"; everyone housed and employed "Everyone has a bed"
	 * (good); jobless but housed "3 no job"; no cap sent, no "of 0".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCardsAtTheirEdges(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[][] moods = {{70, 0}, {69, 1}, {40, 1}, {39, 2}};
		String[] words = {"Happy", "Getting by", "Unhappy"};
		HallBoard.Tone[] tones = {HallBoard.Tone.GOOD, HallBoard.Tone.WARNING, HallBoard.Tone.BAD};
		for (int[] m : moods) {
			HallBoard.Card mood = cards(5, 10, 0, 0, m[0], 4, 4, 1).get(1);
			if (!mood.big().equals(m[0] + "%") || !mood.line().getString().equals(words[m[1]]) || mood.tone() != tones[m[1]]) {
				problems.add("Mood " + m[0] + ": " + mood.big() + " " + mood.line().getString() + " " + mood.tone());
			}
		}
		List<HallBoard.Card> one = cards(5, 10, 0, 0, 80, 1, 1, 1);
		if (!one.get(2).unit().getString().equals("meal") || !one.get(2).line().getString().equals("1 kind of meal")) {
			problems.add("one meal: " + one.get(2).unit().getString() + " / " + one.get(2).line().getString());
		}
		if (!one.get(3).unit().getString().equals("guard") || !one.get(3).line().getString().equals("On watch") || one.get(3).tone() != HallBoard.Tone.INFO) {
			problems.add("one guard: " + one.get(3).unit().getString() + " / " + one.get(3).line().getString());
		}
		if (!one.get(0).line().getString().equals("Everyone has a bed") || one.get(0).tone() != HallBoard.Tone.GOOD) {
			problems.add("all housed: " + one.get(0).line().getString() + " " + one.get(0).tone());
		}
		HallBoard.Card jobless = cards(5, 10, 0, 3, 80, 1, 1, 1).get(0);
		if (!jobless.line().getString().equals("3 no job") || jobless.tone() != HallBoard.Tone.WARNING) {
			problems.add("jobless: " + jobless.line().getString());
		}
		HallBoard.Card noCap = cards(2, 0, 0, 0, 80, 1, 1, 1).get(0);
		if (!noCap.unit().getString().isEmpty()) {
			problems.add("no cap: '" + noCap.unit().getString() + "'");
		}
		// an empty village needs nothing: no "No guards" or "No food" line with nobody to guard or feed
		Map<Integer, Integer> none = new HashMap<>();
		if (HallBoard.needs(i -> net.minecraft.world.item.ItemStack.EMPTY, i -> none.getOrDefault(i, 0), 20).stream().anyMatch(n -> n.opens() != null)) {
			problems.add("an empty village needs something");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * "Needs you" never says a thing twice: with villagers short of beds, jobs, guards and food, the hall's advice lines
	 * for those four are left out (the lines above say them) while another tip stays; with everyone housed and employed,
	 * guarded and fed, the advice's own beds and jobless tips are listed (nothing above says them) and nothing opens a
	 * section.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void needsYouNeverSaysAThingTwice(GameTestHelper helper) {
		net.minecraft.world.item.ItemStack advice = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK);
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		lines.add(net.minecraft.network.chat.Component.literal("5 tips"));
		for (String tip : List.of("beds", "jobless", "guards", "food")) {
			lines.add(net.minecraft.network.chat.Component.translatable("advice.aliveworkplace." + tip, 2, 3));
			lines.add(net.minecraft.network.chat.Component.translatable("advice.aliveworkplace." + tip + ".how"));
		}
		lines.add(net.minecraft.network.chat.Component.literal("Build a Storehouse"));
		lines.add(net.minecraft.network.chat.Component.literal("how"));
		advice.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
		java.util.function.IntFunction<net.minecraft.world.item.ItemStack> icons = slot ->
			slot == io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ADVICE ? advice : net.minecraft.world.item.ItemStack.EMPTY;
		Map<Integer, Integer> short_ = new HashMap<>(Map.of(HallSections.PEOPLE_TOTAL, 4, HallSections.NO_BED, 2, HallSections.PEOPLE_JOBLESS, 3,
			HallSections.GUARDS, 0, HallSections.MEALS, 0));
		List<String> said = HallBoard.needs(icons, i -> short_.getOrDefault(i, 0), 20).stream().map(n -> n.text().getString()).toList();
		List<String> want = List.of("2 villagers need beds", "3 villagers have no job", "No guards on watch", "No food in store", "Build a Storehouse");
		helper.assertTrue(said.equals(want), "Needs you, short of everything: " + said + ", not " + want);
		Map<Integer, Integer> fine = new HashMap<>(Map.of(HallSections.PEOPLE_TOTAL, 4, HallSections.GUARDS, 1, HallSections.MEALS, 9));
		List<HallBoard.Need> needs = HallBoard.needs(icons, i -> fine.getOrDefault(i, 0), 20);
		helper.assertTrue(needs.size() == 5 && needs.stream().allMatch(n -> n.opens() == null),
			"Needs you, fine: " + needs.stream().map(n -> n.text().getString() + " -> " + n.opens()).toList());
		helper.succeed();
	}

	/**
	 * The rail at GUI scale 2 and on the 320x180 icon grid: the Hall's ten sections, the Storehouse's eight entries and
	 * Move's three steps each sit inside the rail, apart from each other, at least 16 pixels tall (an icon), and a click
	 * in an entry's middle finds that entry; on the small screen the rail is the two-column grid.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRailFitsAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int labelW = 0;
		for (Section s : Section.values()) {
			labelW = Math.max(labelW, s.title().getString().length() * 6);
		}
		for (int[] screen : SCREENS) {
			boolean small = CopperLayout.compact(screen[0], screen[1]);
			if (small != (screen[0] == 320)) {
				problems.add(screen[0] + "x" + screen[1] + " compact=" + small);
			}
			int[] win = CopperLayout.window(screen[0], screen[1]);
			int railW = CopperLayout.railWidth(win, labelW, small);
			int[] rail = CopperLayout.rail(win, railW);
			for (int count : new int[]{Section.values().length, 8, 3}) {
				String at = screen[0] + "x" + screen[1] + " with " + count + ": ";
				int[][] boxes = CopperLayout.railEntries(rail, count, small);
				for (int i = 0; i < count; i++) {
					int[] b = boxes[i];
					if (b[0] < rail[0] || b[1] < rail[1] || b[0] + b[2] > rail[0] + rail[2] || b[1] + b[3] > rail[1] + rail[3]) {
						problems.add(at + "entry " + i + " [" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "] leaves the rail ["
							+ rail[0] + "," + rail[1] + " " + rail[2] + "x" + rail[3] + "]");
					}
					if (b[3] < 16 || b[2] < 16) {
						problems.add(at + "entry " + i + " is " + b[2] + "x" + b[3] + ", smaller than its icon");
					}
					for (int j = i + 1; j < count; j++) {
						if (CopperLayout.overlap(b, boxes[j])) {
							problems.add(at + "entries " + i + " and " + j + " overlap");
						}
					}
					if (CopperLayout.at(boxes, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0) != i) {
						problems.add(at + "a click in entry " + i + "'s middle finds " + CopperLayout.at(boxes, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0));
					}
				}
				if (small && count > 1 && boxes[0][1] != boxes[1][1]) {
					problems.add(at + "the small screen's rail isn't a grid");
				}
			}
			int[] whisper = CopperLayout.whisper(win);
			if (rail[1] + rail[3] > whisper[1]) {
				problems.add(screen[0] + "x" + screen[1] + ": the rail runs into the whisper line");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
