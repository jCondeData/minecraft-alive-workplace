package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-27), written from the ROADMAP's words for 34.6 (Classes at the Village Hall), filling the gaps the builder's
 * tests ({@code ClassHallGameTests}) and the earlier QA pass ({@code QaClassHallRetirementGameTests}) leave. Every page
 * check goes through the hall as a player opens it: a right-click on the block, the sidebar's People, the Classes tab's
 * click.
 * <ul>
 *   <li>the page's counts at the boundaries: a need no household has (0 of 5), one every household has (5 of 5), a married
 *   couple counted as one household, the households one need short listed with it, at most three closest; then the
 *   village made a City and those households "has everything";</li>
 *   <li>an empty village: every class 0 households, nobody closest, no class tips;</li>
 *   <li>the "What next?" class tips on the Overview, most households first over file order, "1 ... household wants" and
 *   "3 ... households want", gone once nothing is lacking;</li>
 *   <li>the missing-needs cap in the villager's mood score itself: 3 and 4 missing cost the same;</li>
 *   <li>a couple's fall: one chronicle entry naming both, no chat line, -10 on the day and the next, gone the day after;
 *   kept over a save and reload, and the hall's CLASS entry kept over the hall's;</li>
 *   <li>a rise through the hall's own dawn rounds, its +10 kept over a reload;</li>
 *   <li>the People table's "Burgher · married to Tomas" and "Peasant · a household of one" lines, none for a villager with
 *   no class;</li>
 *   <li>villageClasses off: the tab and page say so, no class lines, no class tips, no luxuries line, nobody rises, and
 *   the classes are kept for when it's back on.</li>
 * </ul>
 */
public class QaClassHallGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(11, 2, 11);
	static final String VARIED = "{\"type\": \"diet\", \"kind\": \"varied\"}";
	static final String PLAIN = "{\"type\": \"diet\", \"kind\": \"plain\"}";
	static final String CITY = "{\"type\": \"village_rank\", \"rank\": \"city\"}";
	static final String TOWN = "{\"type\": \"village_rank\", \"rank\": \"town\"}";
	static final String FED9 = "{\"type\": \"fed_days\", \"days\": 9}";
	static final String[] VARIED_MEALS = {"bread", "baked_potato", "cooked_cod"};
	static final String[] PLAIN_MEALS = {"bread", "baked_potato", "bread"};

	/** A hall at {@link #HALL}, classes on, a 16-block village; everything shared put back afterwards. */
	static VillageHallBlockEntity classVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		HallSections.forget();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		boolean moods = Moods.ENABLED;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Moods.ENABLED = moods;
			new WorkplaceConfig().apply();
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	/** Peasant; Artisan with {@code artisan} needs; Burgher with {@code burgher} needs. */
	static void ladder(String artisan, String burgher) {
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(AliveWorkplace.id("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(AliveWorkplace.id("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + artisan + "]}"));
		files.put(AliveWorkplace.id("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + burgher + "]}"));
		SocialClasses.load(files);
	}

	static Villager person(GameTestHelper helper, int x, int z, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (cls != null) {
			SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		}
		return v;
	}

	static void eats(Villager villager, String... meals) {
		ModAttachments.RECENT_MEALS.set(villager, Arrays.stream(meals).map(ResourceLocation::withDefaultNamespace).toList());
	}

	static void marry(Villager a, Villager b, boolean married) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, married));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, married));
	}

	static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	static int points(List<LegendPowers.MoodReason> moods, String reason) {
		return moods.stream().filter(m -> key(m.reason()).equals("mood.aliveworkplace.reason." + reason)).mapToInt(LegendPowers.MoodReason::points).sum();
	}

	static boolean has(List<LegendPowers.MoodReason> moods, String reason) {
		return moods.stream().anyMatch(m -> key(m.reason()).equals("mood.aliveworkplace.reason." + reason));
	}

	static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** The button on the open page named {@code name}, or empty. */
	static ItemStack button(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (!icon.isEmpty() && icon.getHoverName().getString().equals(name)) {
				return icon;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Opens the hall as a player does: a right-click on the block. */
	static ChoiceMenu open(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/** The sidebar's People, and the Classes tab there (its slot). */
	static int classesTab(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player) {
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		int tab = HallPages.slot(ClassesPage.PAGE);
		helper.assertTrue(tab >= 0 && HallSections.slots(menu, Section.PEOPLE).contains(tab), "no Classes tab under People: " + tab);
		helper.assertTrue(menu.icon(tab).getHoverName().getString().equals("Classes"), "the tab is named " + menu.icon(tab).getHoverName().getString());
		return tab;
	}

	/** Clicks the tab the way the client sends a click. */
	static void click(ChoiceMenu menu, ServerPlayer player, int slot) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	/** The lines of {@code lines} after {@code header} that start with two spaces (that header's list). */
	static List<String> listAfter(List<String> lines, String header) {
		int at = lines.indexOf(header);
		List<String> out = new ArrayList<>();
		if (at < 0) {
			return out;
		}
		for (int i = at + 1; i < lines.size() && lines.get(i).startsWith("  "); i++) {
			out.add(lines.get(i));
		}
		return out;
	}

	/**
	 * Five Peasant households (Ann, Bo, Cy, Fay and the married Di and Ed, one household) and Artisans needing a plain diet
	 * (all five have it), a varied one (Cy lacks it) and a City (nobody has it). Through the hall: the tab says 5, 0 and 0;
	 * the Artisan button reads "A plain diet: 5 of 5", "A varied diet: 4 of 5", "The village a City or bigger: 0 of 5"; the
	 * closest are three of the four households one need short, each "lacks The village a City or bigger" (the couple named
	 * together), and Cy (two short) isn't among them; the Burgher button has no Artisan households to list. Made a City, the
	 * page reads 5 of 5 and those three "have everything".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaClassPageBounds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaClassPageBounds")
	public void thePageCountsNoneAllAndOneShortThroughTheHall(GameTestHelper helper) {
		VillageHallBlockEntity hall = classVillage(helper);
		ladder(String.join(", ", PLAIN, VARIED, CITY), TOWN);
		Villager ann = person(helper, 3, 3, "Ann", "peasant");
		Villager bo = person(helper, 5, 3, "Bo", "peasant");
		Villager cy = person(helper, 7, 3, "Cy", "peasant");
		Villager di = person(helper, 3, 6, "Di", "peasant");
		Villager ed = person(helper, 5, 6, "Ed", "peasant");
		Villager fay = person(helper, 7, 6, "Fay", "peasant");
		marry(di, ed, true);
		for (Villager v : List.of(ann, bo, di, ed, fay)) {
			eats(v, VARIED_MEALS);
		}
		eats(cy, PLAIN_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = open(helper, player);
			int tab = classesTab(helper, menu, player);
			List<String> tip = lore(menu.icon(tab));
			helper.assertTrue(tip.contains("Peasant households: 5") && tip.contains("Artisan households: 0") && tip.contains("Burgher households: 0"),
				"the tab counts households (the couple once): " + tip + "; village " + VillageHalls.villagers(helper.getLevel(), helper.absolutePos(HALL)).stream()
					.map(v -> v.getName().getString() + "=" + QaClassMigrationGameTests.cls(v) + (v.isBaby() ? " (baby)" : "")).toList());
			click(menu, player, tab);
			List<String> artisan = lore(button(menu, "Artisan"));
			helper.assertTrue(artisan.contains("Households: 0"), "no Artisans yet: " + artisan);
			helper.assertTrue(artisan.contains("  A plain diet: 5 of 5"), "every household has a plain diet: " + artisan);
			helper.assertTrue(artisan.contains("  A varied diet: 4 of 5"), "all but Cy have a varied diet: " + artisan);
			helper.assertTrue(artisan.contains("  The village a City or bigger: 0 of 5"), "no household has a City: " + artisan);
			List<String> closest = listAfter(artisan, "Closest to becoming Artisan:");
			helper.assertTrue(closest.size() == 3, "three closest at most: " + closest);
			helper.assertTrue(closest.stream().allMatch(l -> l.endsWith(": lacks The village a City or bigger")), "each one need short, with what they lack: " + closest);
			helper.assertTrue(closest.stream().filter(l -> l.contains("Di") || l.contains("Ed")).allMatch(l -> l.contains("Di") && l.contains("Ed") && l.contains(" and ")),
				"the couple, if listed, as one household: " + closest);
			helper.assertTrue(closest.stream().noneMatch(l -> l.contains("Cy")), "Cy, two short, is further off: " + closest);
			List<String> burgher = lore(button(menu, "Burgher"));
			helper.assertTrue(burgher.contains("Households: 0") && burgher.contains("  No Artisan households yet"), "nobody to rise to Burgher: " + burgher);

			// A City: the need every household has, and the households one short now have everything.
			hall.setRank(VillageRanks.Rank.CITY);
			menu.press(0, player); // the page's back arrow
			click(menu, player, tab);
			artisan = lore(button(menu, "Artisan"));
			helper.assertTrue(artisan.contains("  The village a City or bigger: 5 of 5"), "a City: 5 of 5: " + artisan);
			closest = listAfter(artisan, "Closest to becoming Artisan:");
			helper.assertTrue(closest.size() == 3 && closest.stream().allMatch(l -> l.endsWith(": has everything, rising at a dawn soon")),
				"the three have everything: " + closest);
			player.closeContainer();
			List.of(ann, bo, cy, di, ed, fay).forEach(Villager::discard);
			helper.succeed();
		});
	}

	/** A hall with nobody: every class has 0 households, nobody is closest to rising, and "What next?" has no class tip. */
	//$ gametest_ticks_batch AREA '100' '"qaClassPageEmpty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaClassPageEmpty")
	public void anEmptyVillageCountsNothingAndTipsNothing(GameTestHelper helper) {
		classVillage(helper);
		ladder(VARIED, CITY);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = open(helper, player);
			int tab = classesTab(helper, menu, player);
			List<String> tip = lore(menu.icon(tab));
			helper.assertTrue(tip.contains("Peasant households: 0") && tip.contains("Artisan households: 0") && tip.contains("Burgher households: 0"), "nobody: " + tip);
			click(menu, player, tab);
			for (String[] c : new String[][] {{"Artisan", "Peasant"}, {"Burgher", "Artisan"}}) {
				List<String> lines = lore(button(menu, c[0]));
				helper.assertTrue(lines.contains("Households: 0") && lines.contains("  No " + c[1] + " households yet"), c[0] + " in an empty village: " + lines);
			}
			helper.assertTrue(lore(button(menu, "Peasant")).contains("Households: 0"), "no Peasants: " + lore(button(menu, "Peasant")));
			BlockPos at = helper.absolutePos(HALL);
			helper.assertTrue(ClassesPage.classTips(helper.getLevel(), at).isEmpty(), "no class tips with nobody");
			helper.assertTrue(VillageAdvice.tips(helper.getLevel(), at).stream().noneMatch(t -> t.key().startsWith("class")), "What next? has no class tip with nobody");
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * Three Peasants (Ann eats a varied diet, Bo and Cy a plain one) and Artisans needing a varied diet, then a City (file
	 * order). "What next?" on the Overview reads the City first (3 households) and the diet second (2), in those words; once
	 * the village is a City and only Cy eats plainly, one tip: "1 Peasant household wants A varied diet to rise to Artisan";
	 * once Cy eats well too, none.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaClassTipsOverview"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaClassTipsOverview")
	public void tipsGoMostFirstReadOneAndSeveralAndVanishWhenMet(GameTestHelper helper) {
		VillageHallBlockEntity hall = classVillage(helper);
		ladder(String.join(", ", VARIED, CITY), TOWN);
		Villager ann = person(helper, 3, 3, "Ann", "peasant");
		Villager bo = person(helper, 5, 3, "Bo", "peasant");
		Villager cy = person(helper, 7, 3, "Cy", "peasant");
		eats(ann, VARIED_MEALS);
		eats(bo, PLAIN_MEALS);
		eats(cy, PLAIN_MEALS);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = open(helper, player);
			List<String> whatNext = lore(menu.icon(VillageHallScreen.ADVICE));
			int city = whatNext.indexOf("3 Peasant households want The village a City or bigger to rise to Artisan");
			int diet = whatNext.indexOf("2 Peasant households want A varied diet to rise to Artisan");
			helper.assertTrue(city >= 0 && diet > city, "What next? on the Overview, most households first: " + whatNext);
			player.closeContainer();

			hall.setRank(VillageRanks.Rank.CITY);
			eats(bo, VARIED_MEALS);
			List<String> one = VillageAdvice.tips(helper.getLevel(), at).stream().filter(t -> t.key().startsWith("class")).map(t -> t.title().getString()).toList();
			helper.assertTrue(one.equals(List.of("1 Peasant household wants A varied diet to rise to Artisan")), "one household, one tip, in the singular: " + one);
			eats(cy, VARIED_MEALS);
			helper.assertTrue(ClassesPage.classTips(helper.getLevel(), at).isEmpty(), "nothing lacking, no class tip: " + ClassesPage.classTips(helper.getLevel(), at));
			List.of(ann, bo, cy).forEach(Villager::discard);
			helper.succeed();
		});
	}

	/** The villager's mood score with {@code needs} the Artisans' needs, counted afresh at today's dawn. */
	private static int scoreWith(GameTestHelper helper, Villager v, String needs) {
		ServerLevel level = helper.getLevel();
		ladder(needs, TOWN);
		ModAttachments.CLASS_PROGRESS.set(v, SocialClasses.Progress.NONE); // counted afresh today, never 3 dawns short
		SocialClasses.check(level, helper.absolutePos(HALL), List.of(v), Chronicle.day(level), 8);
		helper.assertTrue("artisan".equals(ModAttachments.SOCIAL_CLASS.get(v).getPath()), "setup: Una stays an Artisan");
		Moods.forget(v);
		Moods.Mood mood = Moods.of(v);
		helper.assertTrue(mood != null, "no mood");
		return mood.score();
	}

	/**
	 * The cap in the mood the player sees, not only in the reasons: an Artisan eating only bread scores 5 less with 2 needs
	 * missing than with 1, 5 less again with 3, and the same with 4 (at most -15); with everything she scores 10 more than
	 * with 1 missing (+5 against -5). Her bad moods read "lacks 4 of what their class needs".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassMoodCap"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassMoodCap")
	public void threeAndFourMissingNeedsCostTheSameMood(GameTestHelper helper) {
		classVillage(helper);
		Moods.ENABLED = true;
		Moods.forget();
		ladder(VARIED, TOWN);
		Villager una = person(helper, 4, 4, "Una", "artisan");
		eats(una, "bread", "bread", "bread");
		helper.runAfterDelay(2, () -> {
			int met = scoreWith(helper, una, CITY.replace("city", "hamlet"));
			int one = scoreWith(helper, una, VARIED);
			int two = scoreWith(helper, una, String.join(", ", VARIED, CITY));
			int three = scoreWith(helper, una, String.join(", ", VARIED, CITY, TOWN));
			int four = scoreWith(helper, una, String.join(", ", VARIED, CITY, TOWN, FED9));
			String all = "met " + met + ", 1 " + one + ", 2 " + two + ", 3 " + three + ", 4 " + four;
			helper.assertTrue(met - one == 10, "everything (+5) against one missing (-5): " + all);
			helper.assertTrue(one - two == 5 && two - three == 5, "each missing need -5: " + all);
			helper.assertTrue(three == four, "at most -15: a fourth missing need costs nothing more: " + all);
			Moods.Mood mood = Moods.of(una);
			helper.assertTrue(mood.bad().stream().anyMatch(c -> c.getString().equals("lacks 4 of what their class needs")), "the bad moods: "
				+ mood.bad().stream().map(Component::getString).toList());
			Moods.forget(una);
			una.discard();
			helper.succeed();
		});
	}

	/**
	 * An Artisan couple, Dee and Eli, short of a varied diet three dawns running fall together: one CLASS entry "Dee and Eli
	 * came down in the world, to the Peasant class", no chat line for a player beside them, "came down in the world" -10 on
	 * the day and the next and gone the day after. Dee saved and loaded the day after still feels it; the hall saved and
	 * loaded keeps the entry.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassFallCouple"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassFallCouple")
	public void aCoupleFallsInOneEntryWithoutChatAndTheMoodLastsTwoDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = classVillage(helper);
		ladder(VARIED, TOWN);
		Villager dee = person(helper, 4, 4, "Dee", "artisan");
		Villager eli = person(helper, 4, 5, "Eli", "artisan");
		marry(dee, eli, true);
		eats(dee, "bread", "bread", "bread");
		eats(eli, "bread", "bread", "bread");
		List<Component> heard = new ArrayList<>();
		BlockPos door = dee.blockPosition();
		QaClassMigrationGameTests.listener(helper, heard, new Vec3(door.getX() + 3.5, door.getY() + 0.5, door.getZ() + 0.5), "Beside");
		BlockPos at = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		int before = (int) hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).count();
		for (int d = 0; d < 3; d++) {
			SocialClasses.check(level, at, List.of(dee, eli), today + d, 8);
		}
		long fell = today + 2;
		helper.assertTrue(QaClassMigrationGameTests.cls(dee).equals("peasant") && QaClassMigrationGameTests.cls(eli).equals("peasant"),
			"both fell: " + QaClassMigrationGameTests.cls(dee) + " / " + QaClassMigrationGameTests.cls(eli));
		List<Chronicle.Entry> entries = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).toList();
		helper.assertTrue(entries.size() == before + 1, "one CLASS entry for the household: " + entries.stream().map(e -> e.text().getString()).toList());
		String text = entries.get(entries.size() - 1).text().getString();
		helper.assertTrue(text.equals("Dee and Eli came down in the world, to the Peasant class") || text.equals("Eli and Dee came down in the world, to the Peasant class"),
			"the entry reads: " + text);
		List<String> chat = heard.stream().map(Component::getString).filter(s -> s.contains("Dee") || s.contains("Eli")).toList();
		helper.assertTrue(chat.isEmpty(), "a fall isn't told in chat: " + chat);
		for (Villager v : List.of(dee, eli)) {
			helper.assertTrue(points(SocialClasses.moods(v, fell), "class_fell") == -10 && points(SocialClasses.moods(v, fell + 1), "class_fell") == -10,
				v.getName().getString() + ": -10 on the day and the next: " + SocialClasses.moods(v, fell) + " / " + SocialClasses.moods(v, fell + 1));
			helper.assertTrue(!has(SocialClasses.moods(v, fell + 2), "class_fell"), "gone after 2 days: " + SocialClasses.moods(v, fell + 2));
			helper.assertTrue(!has(SocialClasses.moods(v, fell), "class_rose"), "a fall isn't a rise");
		}
		CompoundTag tag = dee.saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		helper.assertTrue(points(SocialClasses.moods(copy, fell + 1), "class_fell") == -10 && QaClassMigrationGameTests.cls(copy).equals("peasant"),
			"reloaded the next day, still came down: " + SocialClasses.moods(copy, fell + 1));
		BlockEntity back = BlockEntity.loadStatic(hall.getBlockPos(), hall.getBlockState(), hall.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
		helper.assertTrue(back instanceof VillageHallBlockEntity, "the hall didn't load: " + back);
		List<Chronicle.Entry> loaded = ((VillageHallBlockEntity) back).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).toList();
		helper.assertTrue(loaded.size() == entries.size() && loaded.get(loaded.size() - 1).text().getString().equals(text),
			"the hall reloaded keeps the CLASS entry: " + loaded.stream().map(e -> e.text().getString()).toList());
		List.of(dee, eli).forEach(Villager::discard);
		helper.succeed();
	}

	/** Runs the hall's dawn rounds for {@code day} until it has counted everyone. */
	private static void dawn(GameTestHelper helper, VillageHallBlockEntity hall, long day) {
		for (int i = 0; i < 10; i++) {
			SocialClasses.Result r = SocialClasses.round(helper.getLevel(), helper.absolutePos(HALL), hall, day);
			if (r == null || r.finished()) {
				return;
			}
		}
	}

	/**
	 * Odo, a Peasant with a varied diet, rises to Artisan through the hall's own dawn rounds after two dawns: the chronicle
	 * reads "Odo rose in the world and joined the Artisan class", he feels +10 on the day and the next, and reloaded the
	 * next day he still does.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClassRiseRounds"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClassRiseRounds")
	public void aRiseThroughTheHallsDawnRoundsIsKeptOverAReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = classVillage(helper);
		ladder(VARIED, TOWN);
		Villager odo = person(helper, 4, 4, "Odo", "peasant");
		eats(odo, VARIED_MEALS);
		long time = level.getDayTime();
		level.setDayTime(time + 10 * VillageNeeds.DAY);
		Leftovers.after(helper, () -> level.setDayTime(time));
		long today = Chronicle.day(level);
		dawn(helper, hall, today - 1);
		helper.assertTrue(QaClassMigrationGameTests.cls(odo).equals("peasant"), "one dawn isn't enough: " + QaClassMigrationGameTests.cls(odo));
		dawn(helper, hall, today);
		helper.assertTrue(QaClassMigrationGameTests.cls(odo).equals("artisan"), "two dawns: Artisan, is " + QaClassMigrationGameTests.cls(odo));
		List<String> entries = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).map(e -> e.text().getString()).toList();
		helper.assertTrue(entries.contains("Odo rose in the world and joined the Artisan class"), "the chronicle: " + entries);
		helper.assertTrue(points(SocialClasses.moods(odo, today), "class_rose") == 10 && points(SocialClasses.moods(odo, today + 1), "class_rose") == 10,
			"+10 on the day and the next: " + SocialClasses.moods(odo, today));
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(odo.saveWithoutId(new CompoundTag()));
		helper.assertTrue(points(SocialClasses.moods(copy, today + 1), "class_rose") == 10, "reloaded, still proud: " + SocialClasses.moods(copy, today + 1));
		helper.assertTrue(!has(SocialClasses.moods(copy, today + 2), "class_rose"), "and after 2 days, old news");
		odo.discard();
		helper.succeed();
	}

	/** The People table row's details for {@code v}, as the hall shows them. */
	static List<String> row(GameTestHelper helper, ChoiceMenu menu, Villager v) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag r = HallPeople.row(menu.icon(slot));
			if (r != null && r.getString("uuid").equals(v.getStringUUID())) {
				return lore(menu.icon(slot));
			}
		}
		helper.fail("no People row for " + v.getName().getString());
		return List.of();
	}

	/**
	 * The People table, opened by hand: Tomas, a Burgher married to Ida, reads "Burgher · married to Ida" (once: no separate
	 * "Married to" line) and his class's needs; Rolf reads "Peasant · a household of one" and what he needs to become an
	 * Artisan; Kit, only courting Lu, is a household of one too; Vic, with no class yet, has no class line.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaClassPeopleRows"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaClassPeopleRows")
	public void thePeopleTableSaysClassAndHousehold(GameTestHelper helper) {
		classVillage(helper);
		ladder(VARIED, PLAIN);
		Villager tomas = person(helper, 3, 3, "Tomas", "burgher");
		Villager ida = person(helper, 5, 3, "Ida", "burgher");
		marry(tomas, ida, true);
		Villager rolf = person(helper, 7, 3, "Rolf", "peasant");
		Villager kit = person(helper, 3, 6, "Kit", "peasant");
		Villager lu = person(helper, 5, 6, "Lu", "peasant");
		marry(kit, lu, false);
		Villager vic = person(helper, 7, 6, "Vic", null);
		for (Villager v : List.of(tomas, ida, rolf, kit, lu, vic)) {
			eats(v, PLAIN_MEALS);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = open(helper, player);
			helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
			List<String> t = row(helper, menu, tomas);
			helper.assertTrue(t.contains("Burgher · married to Ida"), "Tomas's class and household: " + t);
			helper.assertTrue(t.stream().filter(l -> l.toLowerCase().contains("married to ida")).count() == 1, "married told once: " + t);
			helper.assertTrue(t.contains("Burgher needs:") && t.contains("  ✔ A plain diet"), "Tomas's needs, ticked: " + t);
			List<String> r = row(helper, menu, rolf);
			helper.assertTrue(r.contains("Peasant · a household of one") && r.contains("To become Artisan:") && r.contains("  ✘ A varied diet"), "Rolf: " + r);
			List<String> k = row(helper, menu, kit);
			helper.assertTrue(k.contains("Peasant · a household of one") && k.stream().noneMatch(l -> l.contains("married")), "Kit, only courting: " + k);
			List<String> v = row(helper, menu, vic);
			helper.assertTrue(v.stream().noneMatch(l -> l.contains(" · ") || l.endsWith("needs:") || l.startsWith("To become")), "Vic has no class: " + v);
			player.closeContainer();
			List.of(tomas, ida, rolf, kit, lu, vic).forEach(Villager::discard);
			helper.succeed();
		});
	}

	/**
	 * villageClasses off (the config's switch): the Classes tab and page say "Village classes are switched off", Tomas's row
	 * has no class line but still "Married to Ida", What next? has no class tip, the food icon no luxuries line, a Peasant
	 * meeting the Artisans' needs three dawns running stays a Peasant with no class moods; switched back on, everyone's
	 * class is as it was.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaClassSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaClassSwitchOff")
	public void switchedOffTheHallShowsNoClassesAndKeepsThem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = classVillage(helper);
		ladder(VARIED, PLAIN);
		Villager tomas = person(helper, 3, 3, "Tomas", "burgher");
		Villager ida = person(helper, 5, 3, "Ida", "burgher");
		marry(tomas, ida, true);
		Villager odo = person(helper, 7, 3, "Odo", "peasant");
		Villager bo = person(helper, 9, 3, "Bo", "peasant");
		for (Villager v : List.of(tomas, ida, odo)) {
			eats(v, VARIED_MEALS);
		}
		eats(bo, PLAIN_MEALS);
		WorkplaceConfig off = new WorkplaceConfig();
		off.villageClasses = false;
		off.apply();
		helper.assertTrue(!SocialClasses.ENABLED, "villageClasses false leaves classes on");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = open(helper, player);
			helper.assertTrue(lore(menu.icon(3)).stream().noneMatch(l -> l.startsWith("Luxuries") || l.startsWith("No luxuries")), "the food icon: " + lore(menu.icon(3)));
			helper.assertTrue(lore(menu.icon(VillageHallScreen.ADVICE)).stream().noneMatch(l -> l.contains("households want") || l.contains("household wants")),
				"What next? with classes off: " + lore(menu.icon(VillageHallScreen.ADVICE)));
			int tab = classesTab(helper, menu, player);
			helper.assertTrue(lore(menu.icon(tab)).equals(List.of("Village classes are switched off")), "the tab: " + lore(menu.icon(tab)));
			List<String> t = row(helper, menu, tomas);
			helper.assertTrue(t.stream().noneMatch(l -> l.contains("Burgher")) && t.contains("Married to Ida"), "Tomas with classes off: " + t);
			click(menu, player, tab);
			helper.assertTrue(!button(menu, "Village classes are switched off").isEmpty() && button(menu, "Artisan").isEmpty(), "the page with classes off");
			player.closeContainer();
			long today = Chronicle.day(level);
			for (int d = 0; d < 3; d++) {
				dawn(helper, hall, today + d);
			}
			helper.assertTrue(QaClassMigrationGameTests.cls(odo).equals("peasant") && SocialClasses.moods(odo, today + 2).isEmpty(), "nobody rises with classes off: "
				+ QaClassMigrationGameTests.cls(odo));
			helper.assertTrue(hall.chronicle().stream().noneMatch(e -> e.kind() == Chronicle.Kind.CLASS), "no CLASS entries with classes off");
			SocialClasses.ENABLED = true;
			helper.assertTrue(SocialClasses.of(tomas) == SocialClasses.get(AliveWorkplace.id("burgher")) && SocialClasses.of(bo) == SocialClasses.get(AliveWorkplace.id("peasant")),
				"back on, the classes kept: " + QaClassMigrationGameTests.cls(tomas) + " / " + QaClassMigrationGameTests.cls(bo));
			List.of(tomas, ida, odo, bo).forEach(Villager::discard);
			helper.succeed();
		});
	}
}
