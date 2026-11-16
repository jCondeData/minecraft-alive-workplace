package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * ROADMAP 47.2: the Village Hall in Copperline. The rail's counts (People's waiting, Defense's "!", the open quests) and
 * the Overview's four stat cards come from what the hall already sends, through the player's own entry points (a
 * right-click on the block, the rail's click); the cards' places at GUI scale 2 and 3 never overlap or leave the stage.
 */
public class HallCopperlineGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String EMPTY_STRUCTURE = FabricGameTest.EMPTY_STRUCTURE;
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/**
	 * Two villagers without a job and no guards: the rail says "2" on People and nothing on Defense (no attack coming);
	 * Quests shows the open quests' count; the counts stay when the player clicks People on the rail. The Overview's cards
	 * read 2 people, a mood in percent, the food and a Defense line in the warning tone ("Nobody guards the village").
	 */
	//$ gametest_ticks_batch AREA '200' '"hallCopperline"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallCopperline")
	public void railCountsAndStatCards(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x : new int[]{10, 14}) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 20));
			v.setNoAi(true);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = open(helper, player);
			int waiting = menu.state(HallSections.RAIL_WAITING);
			helper.assertTrue(waiting == 2, "the rail's waiting count is " + waiting + ", expected 2");
			String people = HallSections.railBadge(Section.PEOPLE, waiting, menu.state(HallSections.RAIL_DANGER), menu.state(HallSections.RAIL_QUESTS), false);
			helper.assertTrue(people.equals("2"), "People's badge reads '" + people + "'");
			String defense = HallSections.railBadge(Section.DEFENSE, waiting, menu.state(HallSections.RAIL_DANGER), menu.state(HallSections.RAIL_QUESTS), false);
			helper.assertTrue(defense.isEmpty(), "Defense shows '" + defense + "' with no attack coming");
			int quests = menu.state(HallSections.RAIL_QUESTS);
			String q = HallSections.railBadge(Section.QUESTS, 0, 0, quests, false);
			helper.assertTrue(quests == 0 ? q.isEmpty() : q.equals(String.valueOf(quests)), "Quests' badge '" + q + "' for " + quests);
			// The cards, from the Overview's buttons.
			HallSections.Card card = HallSections.card(0, menu.icon(VillageHallScreen.PEOPLE));
			helper.assertTrue(card.value().equals("2"), "the People card reads " + card.value());
			HallSections.Card mood = HallSections.card(1, menu.icon(VillageHallScreen.WELLBEING));
			helper.assertTrue(mood.value().endsWith("%"), "the Mood card reads " + mood.value());
			HallSections.Card guard = HallSections.card(3, menu.icon(VillageHallScreen.GUARDS));
			helper.assertTrue(guard.value().equals("0") && guard.tone() == 2, "the Defense card reads " + guard.value() + " tone " + guard.tone()
				+ " '" + guard.line().getString() + "'");
			helper.assertTrue(guard.line().getString().equals(Language.getInstance().getOrDefault("screen.aliveworkplace.hall.unguarded")),
				"the Defense card's line: " + guard.line().getString());
			HallSections.Card food = HallSections.card(2, menu.icon(VillageHallScreen.FOOD));
			helper.assertFalse(food.value().isEmpty(), "the Food card has no number");
			for (String key : new String[]{"needs_you", "card.people", "card.mood", "card.food", "card.defense"}) {
				helper.assertTrue(Language.getInstance().has("screen.aliveworkplace.hall." + key), "no text for " + key);
			}
			// A rail click keeps the counts (they're set with the main page).
			helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the rail didn't take People");
			helper.assertTrue(menu.state(HallSections.RAIL_WAITING) == 2, "after the click the waiting count is " + menu.state(HallSections.RAIL_WAITING));
			player.closeContainer();
			helper.succeed();
		});
	}

	/** The badges' words: a count, "9+" past nine on the compact rail, "!" for an attack, nothing on other sections. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void railBadgeWords(GameTestHelper helper) {
		helper.assertTrue(HallSections.railBadge(Section.QUESTS, 0, 0, 12, false).equals("12"), "12 quests");
		helper.assertTrue(HallSections.railBadge(Section.QUESTS, 0, 0, 12, true).equals("9+"), "12 quests, compact");
		helper.assertTrue(HallSections.railBadge(Section.PEOPLE, 3, 1, 5, true).equals("3"), "3 waiting");
		helper.assertTrue(HallSections.railBadge(Section.DEFENSE, 3, 1, 5, false).equals("!"), "an attack");
		helper.assertTrue(HallSections.railBadge(Section.DEFENSE, 3, 0, 5, false).isEmpty(), "no attack");
		for (Section s : new Section[]{Section.OVERVIEW, Section.HOMES, Section.BUILDINGS, Section.TRADE, Section.CALENDAR, Section.LAWS, Section.MAP}) {
			helper.assertTrue(HallSections.railBadge(s, 3, 1, 5, false).isEmpty(), s + " has a badge");
		}
		helper.succeed();
	}

	/**
	 * The stat cards at GUI scale 2 (427x240) and 3 (320x180): inside the stage, never overlapping, four across when
	 * there's room and two by two when not.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void statCardsFitBothScales(GameTestHelper helper) {
		int[][] sizes = {{427, 240}, {320, 180}};
		for (int[] size : sizes) {
			boolean compact = CopperLayout.compact(size[0], size[1]);
			// The stage column right of the rail: the window less the rail and padding (as the screen gives it).
			int win = CopperLayout.windowWidth(size[0]);
			int w = win - (compact ? 2 * CopperLayout.RAIL_CELL + 8 : 90) - 16;
			int[][] cards = CopperLayout.statCards(10, 30, w);
			for (int i = 0; i < 4; i++) {
				int[] a = cards[i];
				helper.assertTrue(a[0] >= 10 && a[0] + a[2] <= 10 + w && a[2] > 30, size[0] + "x" + size[1] + ": card " + i + " outside " + w);
				for (int j = i + 1; j < 4; j++) {
					int[] b = cards[j];
					boolean overlap = a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
					helper.assertFalse(overlap, size[0] + "x" + size[1] + ": cards " + i + " and " + j + " overlap");
				}
			}
		}
		helper.assertTrue(CopperLayout.statCards(0, 0, 260)[3][1] == 0, "260 wide: four across");
		helper.assertTrue(CopperLayout.statCards(0, 0, 150)[3][1] > 0, "150 wide: two by two");
		helper.succeed();
	}
}
