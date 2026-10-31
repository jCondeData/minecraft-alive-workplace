package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.CITY;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.FED9;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.PLAIN;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.PLAIN_MEALS;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.TOWN;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.VARIED;
import static io.github.jcondedata.aliveworkplace.test.QaClassHallGameTests.VARIED_MEALS;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-27, second pass), from the ROADMAP's words for 34.6 (Classes at the Village Hall), for what
 * {@code ClassHallGameTests}, {@code QaClassHallGameTests} and {@code CountedWords1027GameTests} leave: the page read
 * through the hall at one household and at none (the singular counted line, "0 of 0", the line colors); the "lacks"
 * list cut at three with "and 1 more" and the fewest-lacking first; a Burgher married to a Peasant as one Burgher
 * household, each spouse's own class line; "What next?" at three class tips on a tie, lower class first, each with its
 * how line; the rise's moods by name and their last day; the food icon's luxuries most first. Also a nightly mutation
 * survivor: Job ▾ on slot 0 of the hall's screen (HallJobs.click's slot bound). VillageWaystones.network's viewer
 * fallback needs the Waystones mod (the extension is empty in this suite), so it belongs in src/compattest.
 */
public class QaHallClasses1027GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static List<String> lore(ItemStack stack) {
		return QaClassHallGameTests.lore(stack);
	}

	/** The lore line of {@code stack} whose text is {@code text}'s color, or null when no line reads so. */
	private static ChatFormatting colorOf(ItemStack stack, String text) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		for (Component line : lore.lines()) {
			if (line.getString().equals(text)) {
				var color = line.getStyle().getColor();
				if (color == null && !line.getSiblings().isEmpty()) {
					color = line.getSiblings().get(0).getStyle().getColor();
				}
				return color == null ? null : ChatFormatting.getByName(color.serialize());
			}
		}
		return null;
	}

	/** The Classes page as a player reaches it: right-click on the hall, People, the Classes tab. */
	private static ChoiceMenu classesPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = QaClassHallGameTests.open(helper, player);
		int tab = QaClassHallGameTests.classesTab(helper, menu, player);
		QaClassHallGameTests.click(menu, player, tab);
		return menu;
	}

	/**
	 * One Peasant household (Odo, a plain eater) with a Peasant need of a plain diet, Artisans needing a varied diet and
	 * Burghers a City. The tab reads "Peasant households: 1"; the page's Peasant button "Households: 1", "Counted over its 1
	 * household", "  A plain diet: 1 of 1" in green; the Artisan button "Counted over the 1 household who is Artisan or
	 * Peasant", "  A varied diet: 0 of 1" in gray and "  Odo: lacks A varied diet"; the Burgher button, nobody to count,
	 * "Counted over the 0 households who are Burgher or Artisan", "  The village a City or bigger: 0 of 0" and "  No Artisan
	 * households yet".
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027ClassOneHousehold"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027ClassOneHousehold")
	public void oneHouseholdReadsInTheSingularAndNoneAsZeroOfZero(GameTestHelper helper) {
		QaClassHallGameTests.classVillage(helper);
		java.util.Map<net.minecraft.resources.ResourceLocation, com.google.gson.JsonElement> files = new java.util.HashMap<>();
		files.put(AliveWorkplace.id("peasant"), com.google.gson.JsonParser.parseString("{\"tier\": 0, \"tax\": 1, \"needs\": [" + PLAIN + "]}"));
		files.put(AliveWorkplace.id("artisan"), com.google.gson.JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + VARIED + "]}"));
		files.put(AliveWorkplace.id("burgher"), com.google.gson.JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + CITY + "]}"));
		SocialClasses.load(files);
		Villager odo = QaClassHallGameTests.person(helper, 4, 4, "Odo", "peasant");
		QaClassHallGameTests.eats(odo, PLAIN_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = QaClassHallGameTests.open(helper, player);
			int tab = QaClassHallGameTests.classesTab(helper, menu, player);
			List<String> tip = lore(menu.icon(tab));
			helper.assertTrue(tip.contains("Peasant households: 1") && tip.contains("Artisan households: 0") && tip.contains("Burgher households: 0"), "the tab: " + tip);
			QaClassHallGameTests.click(menu, player, tab);

			ItemStack peasantButton = QaClassHallGameTests.button(menu, "Peasant");
			List<String> peasant = lore(peasantButton);
			helper.assertTrue(peasant.contains("Households: 1") && peasant.contains("Counted over its 1 household") && peasant.contains("Needs:")
				&& peasant.contains("  A plain diet: 1 of 1"), "the Peasant button, one household: " + peasant);
			helper.assertTrue(peasant.stream().noneMatch(l -> l.startsWith("Closest to becoming")), "nobody rises into the floor class: " + peasant);
			helper.assertTrue(colorOf(peasantButton, "  A plain diet: 1 of 1") == ChatFormatting.GREEN, "1 of 1 isn't green: " + colorOf(peasantButton, "  A plain diet: 1 of 1"));

			ItemStack artisanButton = QaClassHallGameTests.button(menu, "Artisan");
			List<String> artisan = lore(artisanButton);
			helper.assertTrue(artisan.contains("Households: 0") && artisan.contains("Counted over the 1 household who is Artisan or Peasant")
				&& artisan.contains("  A varied diet: 0 of 1"), "the Artisan button over Odo: " + artisan);
			helper.assertTrue(QaClassHallGameTests.listAfter(artisan, "Closest to becoming Artisan:").equals(List.of("  Odo: lacks A varied diet")),
				"Odo, closest, with what he lacks: " + artisan);
			helper.assertTrue(colorOf(artisanButton, "  A varied diet: 0 of 1") == ChatFormatting.GRAY, "0 of 1 isn't gray: " + colorOf(artisanButton, "  A varied diet: 0 of 1"));

			ItemStack burgherButton = QaClassHallGameTests.button(menu, "Burgher");
			List<String> burgher = lore(burgherButton);
			helper.assertTrue(burgher.contains("Households: 0") && burgher.contains("Counted over the 0 households who are Burgher or Artisan")
				&& burgher.contains("  The village a City or bigger: 0 of 0") && burgher.contains("  No Artisan households yet"), "the Burgher button, nobody: " + burgher);
			helper.assertTrue(colorOf(burgherButton, "  The village a City or bigger: 0 of 0") == ChatFormatting.GRAY, "0 of 0 must not read as everyone has it: "
				+ colorOf(burgherButton, "  The village a City or bigger: 0 of 0"));
			player.closeContainer();
			odo.discard();
			helper.succeed();
		});
	}

	/**
	 * Artisans need a varied diet, a City, a Town and to have been fed 9 days running. Bo (bread only) lacks all four, Cy
	 * (a varied eater) three. Under "Closest to becoming Artisan:" Cy comes first, though Bo came first in the village,
	 * with "  Cy: lacks The village a City or bigger, The village a Town or bigger, Fed from the store 9 days running" (all
	 * three, no "more"), then "  Bo: lacks A varied diet, The village a City or bigger, The village a Town or bigger and 1
	 * more".
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027ClassLacksList"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027ClassLacksList")
	public void theLacksListNamesThreeAndCountsTheRestFewestFirst(GameTestHelper helper) {
		QaClassHallGameTests.classVillage(helper);
		QaClassHallGameTests.ladder(String.join(", ", VARIED, CITY, TOWN, FED9), PLAIN);
		Villager bo = QaClassHallGameTests.person(helper, 3, 3, "Bo", "peasant");
		Villager cy = QaClassHallGameTests.person(helper, 7, 3, "Cy", "peasant");
		QaClassHallGameTests.eats(bo, "bread", "bread", "bread");
		QaClassHallGameTests.eats(cy, VARIED_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = classesPage(helper, player);
			List<String> artisan = lore(QaClassHallGameTests.button(menu, "Artisan"));
			List<String> closest = QaClassHallGameTests.listAfter(artisan, "Closest to becoming Artisan:");
			helper.assertTrue(closest.equals(List.of(
				"  Cy: lacks The village a City or bigger, The village a Town or bigger, Fed from the store 9 days running",
				"  Bo: lacks A varied diet, The village a City or bigger, The village a Town or bigger and 1 more")),
				"the closest, fewest lacking first, three named and the rest counted: " + closest);
			helper.assertTrue(artisan.contains("  A varied diet: 1 of 2") && artisan.contains("  Fed from the store 9 days running: 0 of 2"), "the counts: " + artisan);
			player.closeContainer();
			List.of(bo, cy).forEach(Villager::discard);
			helper.succeed();
		});
	}

	/**
	 * Tomas, a Burgher, married to Pia, a Peasant: one household of the higher class. The tab reads "Burgher households:
	 * 1" and "Peasant households: 0"; the Burgher button "Households: 1" and "Counted over the 1 household who is Burgher or
	 * Artisan"; the Artisan button has no Peasant household to name. The People table: "Burgher · married to Pia" on his row,
	 * "Peasant · married to Tomas" on hers.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027ClassMixedCouple"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027ClassMixedCouple")
	public void aBurgherMarriedToAPeasantIsOneBurgherHousehold(GameTestHelper helper) {
		QaClassHallGameTests.classVillage(helper);
		QaClassHallGameTests.ladder(VARIED, PLAIN);
		Villager tomas = QaClassHallGameTests.person(helper, 3, 3, "Tomas", "burgher");
		Villager pia = QaClassHallGameTests.person(helper, 5, 3, "Pia", "peasant");
		QaClassHallGameTests.marry(tomas, pia, true);
		QaClassHallGameTests.eats(tomas, PLAIN_MEALS);
		QaClassHallGameTests.eats(pia, PLAIN_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = QaClassHallGameTests.open(helper, player);
			int tab = QaClassHallGameTests.classesTab(helper, menu, player);
			List<String> tip = lore(menu.icon(tab));
			helper.assertTrue(tip.contains("Burgher households: 1") && tip.contains("Peasant households: 0") && tip.contains("Artisan households: 0"),
				"the couple is one Burgher household: " + tip);
			List<String> t = QaClassHallGameTests.row(helper, menu, tomas);
			List<String> p = QaClassHallGameTests.row(helper, menu, pia);
			helper.assertTrue(t.contains("Burgher · married to Pia"), "Tomas's row: " + t);
			helper.assertTrue(p.contains("Peasant · married to Tomas"), "Pia's row, her own class: " + p);
			QaClassHallGameTests.click(menu, player, tab);
			List<String> burgher = lore(QaClassHallGameTests.button(menu, "Burgher"));
			helper.assertTrue(burgher.contains("Households: 1") && burgher.contains("Counted over the 1 household who is Burgher or Artisan")
				&& burgher.contains("  A plain diet: 1 of 1"), "the Burgher button: " + burgher);
			List<String> artisan = lore(QaClassHallGameTests.button(menu, "Artisan"));
			helper.assertTrue(artisan.contains("Households: 0") && artisan.contains("  No Peasant households yet"), "Pia isn't a Peasant household of her own: " + artisan);
			player.closeContainer();
			List.of(tomas, pia).forEach(Villager::discard);
			helper.succeed();
		});
	}

	/**
	 * Two Peasant households (bread only) short of the Artisans' varied diet, two Artisan households short of each of the
	 * Burghers' City, Town and 9 fed days: four tips of two households each. "What next?" on the Overview reads three: the
	 * Peasants' first (the lower class on a tie), then the City and the Town in the file's order, each followed by its how
	 * ("Keep 3 kinds of meal in the store", "Grow the village into a City", "... a Town"); the fed days aren't there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027ClassTipsTie"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027ClassTipsTie")
	public void threeTipsOnATieLowerClassFirstEachWithItsHow(GameTestHelper helper) {
		QaClassHallGameTests.classVillage(helper);
		QaClassHallGameTests.ladder(VARIED, String.join(", ", CITY, TOWN, FED9));
		Villager ann = QaClassHallGameTests.person(helper, 3, 3, "Ann", "peasant");
		Villager bo = QaClassHallGameTests.person(helper, 5, 3, "Bo", "peasant");
		Villager cy = QaClassHallGameTests.person(helper, 3, 6, "Cy", "artisan");
		Villager di = QaClassHallGameTests.person(helper, 5, 6, "Di", "artisan");
		QaClassHallGameTests.eats(ann, "bread", "bread", "bread");
		QaClassHallGameTests.eats(bo, "bread", "bread", "bread");
		QaClassHallGameTests.eats(cy, VARIED_MEALS);
		QaClassHallGameTests.eats(di, VARIED_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = QaClassHallGameTests.open(helper, player);
			List<String> whatNext = lore(menu.icon(VillageHallScreen.ADVICE));
			List<String> classTips = whatNext.stream().filter(l -> l.contains(" households want ") || l.contains(" household wants ")).toList();
			helper.assertTrue(classTips.equals(List.of(
				"2 Peasant households want A varied diet to rise to Artisan",
				"2 Artisan households want The village a City or bigger to rise to Burgher",
				"2 Artisan households want The village a Town or bigger to rise to Burgher")),
				"three class tips, lower class first on a tie, then file order: " + classTips + " in " + whatNext);
			String[][] how = {
				{"2 Peasant households want A varied diet to rise to Artisan", "Keep 3 kinds of meal in the store"},
				{"2 Artisan households want The village a City or bigger to rise to Burgher", "Grow the village into a City"},
				{"2 Artisan households want The village a Town or bigger to rise to Burgher", "Grow the village into a Town"}};
			for (String[] h : how) {
				int at = whatNext.indexOf(h[0]);
				helper.assertTrue(at >= 0 && at + 1 < whatNext.size() && whatNext.get(at + 1).equals(h[1]), "the how under '" + h[0] + "': " + whatNext);
			}
			player.closeContainer();
			List.of(ann, bo, cy, di).forEach(Villager::discard);
			helper.succeed();
		});
	}

	private static int points(List<LegendPowers.MoodReason> moods, String text) {
		return moods.stream().filter(m -> m.reason().getString().equals(text)).mapToInt(LegendPowers.MoodReason::points).sum();
	}

	/**
	 * Odo, a Peasant eating a varied diet, rises to Artisan at today's dawn after yesterday's. Today and tomorrow his mood
	 * reads "rose in the world" +10 and "has what their class needs" +5, in those words, and the mood the player sees has
	 * them; the day after tomorrow neither (the rise is old news, the dawn count two days stale). Una, an Artisan eating
	 * bread, reads "lacks 1 of what their class needs" -5.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1027ClassRiseMoods"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1027ClassRiseMoods")
	public void theRisesMoodsReadByNameAndEndOnTheThirdDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaClassHallGameTests.classVillage(helper);
		Moods.ENABLED = true;
		Moods.forget();
		QaClassHallGameTests.ladder(VARIED, TOWN);
		Villager odo = QaClassHallGameTests.person(helper, 4, 4, "Odo", "peasant");
		Villager una = QaClassHallGameTests.person(helper, 6, 4, "Una", "artisan");
		QaClassHallGameTests.eats(odo, VARIED_MEALS);
		QaClassHallGameTests.eats(una, "bread", "bread", "bread");
		long time = level.getDayTime();
		level.setDayTime(time + 10 * VillageNeeds.DAY);
		Leftovers.after(helper, () -> level.setDayTime(time));
		long today = Chronicle.day(level);
		BlockPos at = helper.absolutePos(HALL);
		SocialClasses.check(level, at, List.of(odo, una), today - 1, 8);
		SocialClasses.check(level, at, List.of(odo, una), today, 8);
		helper.assertTrue("artisan".equals(QaClassMigrationGameTests.cls(odo)), "setup: Odo didn't rise: " + QaClassMigrationGameTests.cls(odo));
		for (long d = today; d <= today + 1; d++) {
			List<LegendPowers.MoodReason> m = SocialClasses.moods(odo, d);
			helper.assertTrue(points(m, "rose in the world") == 10 && points(m, "has what their class needs") == 5 && m.size() == 2,
				"day " + (d - today) + " after the rise: " + m.stream().map(r -> r.reason().getString() + " " + r.points()).toList());
		}
		helper.assertTrue(SocialClasses.moods(odo, today + 2).isEmpty(), "the day after tomorrow: "
			+ SocialClasses.moods(odo, today + 2).stream().map(r -> r.reason().getString() + " " + r.points()).toList());
		Moods.forget(odo);
		Moods.Mood mood = Moods.of(odo);
		List<String> good = mood.good().stream().map(Component::getString).toList();
		helper.assertTrue(good.contains("rose in the world") && good.contains("has what their class needs"), "the mood the player sees: " + good);
		List<LegendPowers.MoodReason> u = SocialClasses.moods(una, today);
		helper.assertTrue(points(u, "lacks 1 of what their class needs") == -5 && u.size() == 1, "Una: "
			+ u.stream().map(r -> r.reason().getString() + " " + r.points()).toList());
		Moods.forget(odo);
		Moods.forget(una);
		List.of(odo, una).forEach(Villager::discard);
		helper.succeed();
	}

	/**
	 * The food icon's tooltip, classes on: a storehouse's chest with 2 disc fragments (the Test Trinket) and 5 phantom
	 * membranes (the Test Fine Trinket) reads "Luxuries in store: Test Fine Trinket ×5, Test Trinket ×2", most first; with
	 * the chest emptied, "No luxuries in store".
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1027ClassLuxuries"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1027ClassLuxuries")
	public void theFoodIconListsTheLuxuriesMostFirst(GameTestHelper helper) {
		QaClassHallGameTests.classVillage(helper);
		helper.setBlock(new BlockPos(15, 2, 15), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(15, 2, 13), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(15, 2, 13));
		chest.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 2));
		chest.setItem(1, new ItemStack(Items.PHANTOM_MEMBRANE, 5));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = QaClassHallGameTests.open(helper, player);
			List<String> food = lore(menu.icon(3));
			helper.assertTrue(food.contains("Luxuries in store: Test Fine Trinket ×5, Test Trinket ×2"), "most first: " + food);
			player.closeContainer();
			chest.clearContent();
			menu = QaClassHallGameTests.open(helper, player);
			food = lore(menu.icon(3));
			helper.assertTrue(food.contains("No luxuries in store") && food.stream().noneMatch(l -> l.startsWith("Luxuries in store")), "an empty store: " + food);
			player.closeContainer();
			helper.succeed();
		});
	}

	// --- HallJobs.click's slot bound (nightly survivor: slot >= 0 made slot > 0) ----------------------------------------

	private static final BlockPos JOB_HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);

	/**
	 * Job ▾ sent for slot 0 when slot 0 holds a person's row opens that person's list: the bound on the slot takes 0, the
	 * first slot of the screen. The hall's own layout keeps the village's name in slot 0, so the row is moved there by
	 * hand (the same icon, with its person mark), as a screen laid out from slot 0 would have it; Job ▾ on slot 0 with the
	 * name there still opens nothing (QaHallScreenGameTests).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1027HallJobSlotZero"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1027HallJobSlotZero")
	public void jobOnSlotZeroOpensThePersonThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = was;
			HallJobs.forget();
		});
		helper.setBlock(JOB_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager dara = helper.spawn(EntityType.VILLAGER, LECTERN.offset(1, 0, 1));
		dara.setNoAi(true);
		Jobs.employ(helper.getLevel(), dara, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		dara.setVillagerData(dara.getVillagerData().setLevel(2));
		dara.setVillagerXp(140);
		dara.setCustomName(Component.literal("Dara"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			BlockPos hall = helper.absolutePos(JOB_HALL);
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			helper.useBlock(JOB_HALL, player);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
			int slot = -1;
			for (int s = 0; s < ChoiceMenu.SIZE; s++) {
				CompoundTag row = HallPeople.row(menu.icon(s));
				if (row != null && row.getString("uuid").equals(dara.getStringUUID())) {
					slot = s;
				}
			}
			helper.assertTrue(slot > 0, "setup: no row for Dara");
			helper.assertTrue(HallJobs.canChange(helper.getLevel(), dara), "setup: Dara's job can't be changed from the table");
			menu.button(0, menu.icon(slot).copy(), null); // her row in slot 0
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN), "Job ▾ on slot 0 wasn't taken");
			helper.assertTrue(HallJobs.isOpen(menu, dara), "Job ▾ on slot 0, her row there, didn't open her list");
			player.closeContainer();
			dara.discard();
			helper.succeed();
		});
	}
}
