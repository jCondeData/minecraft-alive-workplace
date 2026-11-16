package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallBoard;
import io.github.jcondedata.aliveworkplace.hall.HallMap;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * ROADMAP 47.2: the Village Hall in Copperline. The Overview's four stat cards, "Needs you" and "At work now" are read
 * from the hall's own main page (opened by hand), each goes where it says when clicked (a section through the rail's
 * click, or the "What next?" page through its button), an empty village reads sensibly, the Map section carries the
 * village map only while it is open (and Draw a map still works), and the Overview's and the Map's parts fit inside the
 * window at GUI scale 2 and 3 (320x180), with "At work now" giving way on the small screen.
 */
public class HallBoardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);
	/** GUI-pixel screens: GUI scale 2 on 854x480 and on 960x540, GUI scale 3 on 960x540. */
	private static final int[][] SCREENS = {{427, 240}, {480, 270}, {320, 180}};

	private static ChoiceMenu openByHand(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/** A click on the rail (or on a card or a line that opens a section): the menu button with the section's number. */
	private static void open(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player, Section section) {
		helper.assertTrue(menu.clickMenuButton(player, section.ordinal()), "the hall didn't take the click for " + section);
		helper.assertTrue(HallSections.current(menu) == section, "clicked " + section + ", open: " + HallSections.current(menu));
	}

	/**
	 * Two villagers without a bed, a job or a guard: the People card counts both "of" the village's cap and says "2 no
	 * bed"; Mood shows the hall's wellbeing; Food and Defense say the store and the watch are empty. "Needs you" starts
	 * with the beds, then the jobs, the guards and the food, each opening its section, then the hall's advice, which opens
	 * the "What next?" page; every card's click opens its section.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallBoardOverview"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBoardOverview")
	public void overviewCardsAndNeedsComeFromTheHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 2, "people: " + menu.state(HallSections.PEOPLE_TOTAL));
			helper.assertTrue(menu.state(HallSections.NO_BED) == 2, "without a bed: " + menu.state(HallSections.NO_BED));
			helper.assertTrue(menu.state(HallSections.CAP) > 0, "the village's cap wasn't sent: " + menu.state(HallSections.CAP));
			List<HallBoard.Card> cards = HallBoard.cards(menu::state);
			helper.assertTrue(cards.size() == 4, "cards: " + cards.size());
			Section[] opens = {Section.PEOPLE, Section.PEOPLE, Section.HOMES, Section.DEFENSE};
			String[] ids = {"people", "mood", "food", "defense"};
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(cards.get(i).id().equals(ids[i]) && cards.get(i).opens() == opens[i], "card " + i + ": " + cards.get(i));
				helper.assertTrue(!menu.icon(cards.get(i).slot()).isEmpty(), "card " + ids[i] + " stands for an empty button " + cards.get(i).slot());
				helper.assertTrue(!cards.get(i).title().getString().startsWith("screen."), "card " + ids[i] + " has no title");
			}
			HallBoard.Card people = cards.get(0);
			helper.assertTrue(people.big().equals("2") && people.unit().getString().equals("of " + menu.state(HallSections.CAP))
				&& people.line().getString().equals("2 no bed") && people.tone() == HallBoard.Tone.WARNING,
				"the People card: " + people.big() + " " + people.unit().getString() + " / " + people.line().getString());
			helper.assertTrue(cards.get(1).big().equals(menu.state(HallSections.WELLBEING) + "%"), "the Mood card: " + cards.get(1).big());
			helper.assertTrue(cards.get(2).big().equals("0") && cards.get(2).line().getString().equals("No food in store") && cards.get(2).tone() == HallBoard.Tone.BAD,
				"the Food card: " + cards.get(2).big() + " / " + cards.get(2).line().getString());
			helper.assertTrue(cards.get(3).big().equals("0") && cards.get(3).unit().getString().equals("guards")
				&& cards.get(3).line().getString().equals("Nobody on watch"), "the Defense card: " + cards.get(3).line().getString());

			List<HallBoard.Need> needs = HallBoard.needs(menu::icon, menu::state, 20);
			helper.assertTrue(needs.size() >= 4, "needs: " + needs.size());
			String[] first = {"2 villagers need beds", "2 villagers have no job", "No guards on watch", "No food in store"};
			Section[] goes = {Section.PEOPLE, Section.PEOPLE, Section.DEFENSE, Section.HOMES};
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(needs.get(i).text().getString().equals(first[i]) && needs.get(i).opens() == goes[i],
					"need " + i + ": " + needs.get(i).text().getString() + " -> " + needs.get(i).opens());
			}
			helper.assertTrue(HallBoard.needs(menu::icon, menu::state, 3).size() == 3, "\"Needs you\" took more lines than fit");
			HallBoard.Need advice = needs.stream().filter(n -> n.opens() == null).findFirst().orElse(null);
			helper.assertTrue(advice != null && advice.slot() == VillageHallScreen.ADVICE && advice.tone() == HallBoard.Tone.INFO,
				"no advice line under \"Needs you\": " + needs.stream().map(n -> n.text().getString()).toList());

			// every card and line that opens a section opens it, through the hall's own click
			for (HallBoard.Card card : cards) {
				open(helper, menu, player, card.opens());
				open(helper, menu, player, Section.OVERVIEW);
			}
			for (HallBoard.Need need : needs) {
				if (need.opens() != null) {
					open(helper, menu, player, need.opens());
					open(helper, menu, player, Section.OVERVIEW);
				}
			}
			// the advice line presses the "What next?" button: its page opens and the Overview gives way
			menu.press(advice.slot(), player);
			helper.assertTrue(HallSections.slots(menu, Section.OVERVIEW).isEmpty(), "the advice line opened no page");
			open(helper, menu, player, Section.OVERVIEW);
			helper.assertTrue(!HallSections.slots(menu, Section.OVERVIEW).isEmpty(), "the rail didn't bring the Overview back");
			player.closeContainer();
			helper.succeed();
		});
	}

	/** A hall with nobody: the cards say so plainly, nothing about beds, jobs, guards or food needs the player, nobody is at work. */
	//$ gametest_ticks_batch AREA '100' '"hallBoardEmpty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallBoardEmpty")
	public void emptyVillageReadsPlainly(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			List<HallBoard.Card> cards = HallBoard.cards(menu::state);
			helper.assertTrue(cards.get(0).big().equals("0") && cards.get(0).line().getString().equals("Nobody lives here yet")
				&& cards.get(0).tone() == HallBoard.Tone.PLAIN, "the People card in an empty village: " + cards.get(0).line().getString());
			for (HallBoard.Need need : HallBoard.needs(menu::icon, menu::state, 20)) {
				helper.assertTrue(need.opens() == null, "an empty village needs \"" + need.text().getString() + "\"");
			}
			helper.assertTrue(HallBoard.atWork(menu::icon, 10).isEmpty(), "someone is at work in an empty village");
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * The Map section: the village map (the land 128 pixels a side, the Hall where it stands) is on the Map button only
	 * while the section is open; another section drops it. Draw a map (the button's own click) still runs there.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallBoardMap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallBoardMap")
	public void mapSectionCarriesTheVillageMap(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallMap.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(HallMap.view(menu.icon(VillageHallScreen.MAP)) == null, "the Overview carries the village map");
			open(helper, menu, player, Section.MAP);
			CompoundTag view = HallMap.view(menu.icon(VillageHallScreen.MAP));
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(view != null && view.getByteArray("colors").length == MovePage.MAP * MovePage.MAP
				&& view.getInt("hallX") == hall.getX() && view.getInt("hallZ") == hall.getZ(), "the Map section has no village map: " + view);
			helper.assertTrue(HallMap.buildings(view) == 0, "buildings in an empty village: " + HallMap.buildings(view));
			player.getInventory().add(new ItemStack(Items.MAP));
			menu.press(VillageHallScreen.MAP, player); // Draw a map: the button's own click
			helper.assertTrue(HallSections.current(menu) == Section.MAP, "Draw a map left the Map section");
			open(helper, menu, player, Section.PEOPLE);
			helper.assertTrue(HallMap.view(menu.icon(VillageHallScreen.MAP)) == null, "People still carries the village map");
			player.closeContainer();
			helper.succeed();
		});
	}

	/** A click on the map selects the building under it, the smallest where two overlap, none on open ground. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void mapClickFindsTheBuilding(GameTestHelper helper) {
		CompoundTag view = new CompoundTag();
		view.putIntArray("others", new int[]{0, 0, 20, 20, 5, 5, 9, 9});
		helper.assertTrue(HallMap.buildings(view) == 2, "buildings: " + HallMap.buildings(view));
		helper.assertTrue(HallMap.buildingAt(view, 6, 6) == 1, "the small building inside the big one wasn't picked");
		helper.assertTrue(HallMap.buildingAt(view, 15, 2) == 0, "the big building wasn't picked");
		helper.assertTrue(HallMap.buildingAt(view, 30, 30) == -1, "open ground picked a building");
		helper.assertTrue(HallMap.nameJson(view, 0).isEmpty(), "a building without a name has one");
		helper.succeed();
	}

	/** "At work now" lists the people whose button says what they're doing, with that line; nobody else. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void atWorkReadsWhatTheyAreDoing(GameTestHelper helper) {
		ItemStack[] slots = new ItemStack[ChoiceMenu.SIZE];
		java.util.Arrays.fill(slots, ItemStack.EMPTY);
		ItemStack mira = person("Mira", "Building the Bakery");
		ItemStack idle = person("Tomas", null);
		slots[VillageHallScreen.FIRST_PERSON] = idle;
		slots[VillageHallScreen.FIRST_PERSON + 1] = mira;
		List<HallBoard.Worker> workers = HallBoard.atWork(i -> slots[i], 5);
		helper.assertTrue(workers.size() == 1 && workers.get(0).slot() == VillageHallScreen.FIRST_PERSON + 1
			&& workers.get(0).name().getString().equals("Mira") && workers.get(0).doing().getString().equals("Building the Bakery"),
			"at work: " + workers.stream().map(w -> w.name().getString() + ": " + w.doing().getString()).toList());
		helper.assertTrue(HallBoard.atWork(i -> slots[i], 0).isEmpty(), "\"At work now\" took more lines than fit");
		helper.succeed();
	}

	private static ItemStack person(String name, String doing) {
		ItemStack icon = new ItemStack(Items.BRICKS);
		icon.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		List<Component> lore = new ArrayList<>(List.of(Component.literal("Journeyman")));
		CompoundTag tag = new CompoundTag();
		CompoundTag row = new CompoundTag();
		row.putString("uuid", java.util.UUID.randomUUID().toString());
		row.putString("kind", "builder");
		tag.put(HallPeople.MARK, row);
		if (doing != null) {
			tag.putInt(HallBoard.DOING, lore.size());
			lore.add(Component.literal(doing));
		}
		icon.set(DataComponents.LORE, new ItemLore(lore));
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return icon;
	}

	/**
	 * The Overview and the Map at GUI scale 2 and 3: every part inside the body and apart; four cards wide enough for a
	 * big number; "Needs you" shows at least three lines; "At work now" shows at least two at GUI scale 2 and gives way at
	 * 320x180; the map is square with the legend and Draw a map beside it.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void hallBoardsFitAtGuiScaleTwoAndThree(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int labelW = 0;
		for (Section s : Section.values()) {
			labelW = Math.max(labelW, s.title().getString().length() * 6);
		}
		for (int[] screen : SCREENS) {
			String at = screen[0] + "x" + screen[1] + ": ";
			boolean small = CopperLayout.compact(screen[0], screen[1]);
			int[] win = CopperLayout.window(screen[0], screen[1]);
			int[] body = CopperLayout.body(win, CopperLayout.railWidth(win, labelW, small));
			int[] whisper = CopperLayout.whisper(win);
			if (body[1] + body[3] > whisper[1]) {
				problems.add(at + "the body runs into the whisper line");
			}
			int[][] o = HallBoard.overview(body, small);
			for (int i = 0; i < o.length; i++) {
				if (o[i][2] > 0 && !within(o[i], body)) {
					problems.add(at + "overview part " + i + " " + box(o[i]) + " leaves the body " + box(body));
				}
				for (int j = i + 1; j < o.length; j++) {
					if (o[i][2] > 0 && o[j][2] > 0 && CopperLayout.overlap(o[i], o[j])) {
						problems.add(at + "overview parts " + i + " and " + j + " overlap");
					}
				}
			}
			for (int i = 1; i <= 4; i++) {
				if (o[i][2] < 50) {
					problems.add(at + "card " + i + " is " + o[i][2] + " wide");
				}
			}
			if (HallBoard.fits(o[5][3], HallBoard.LINE_H) < 3) {
				problems.add(at + "\"Needs you\" shows " + HallBoard.fits(o[5][3], HallBoard.LINE_H) + " lines");
			}
			if (small ? o[6][2] != 0 : o[6][2] < 100 || HallBoard.fits(o[6][3], HallBoard.WORK_H) < 2) {
				problems.add(at + "\"At work now\" is " + box(o[6]));
			}
			int[][] m = HallMap.layout(body, small);
			if (m[0][2] != m[0][3] || m[0][2] < 96) {
				problems.add(at + "the map is " + box(m[0]));
			}
			for (int i = 0; i < m.length; i++) {
				if (!within(m[i], body)) {
					problems.add(at + "map part " + i + " " + box(m[i]) + " leaves the body " + box(body));
				}
			}
			if (CopperLayout.overlap(m[0], m[1]) || CopperLayout.overlap(m[1], m[2]) || CopperLayout.overlap(m[0], m[2])) {
				problems.add(at + "the map's parts overlap");
			}
			int legend = 13 + HallMap.LAYERS.size() * (small ? HallMap.LAYER_H_SMALL : HallMap.LAYER_H) + 4 + 13 + 14;
			if (m[1][3] < legend || m[1][2] < 80) {
				problems.add(at + "the legend and the selected building don't fit in " + box(m[1]));
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}
}
