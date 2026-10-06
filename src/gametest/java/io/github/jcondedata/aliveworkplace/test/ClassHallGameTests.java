package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * Classes at the Village Hall (ROADMAP 34.6), the part a household's rise and fall shows: the chronicle's new CLASS entry,
 * the chat line to players within 32 blocks, "rose in the world" (+10) and "came down in the world" (-10) for 2 days, the
 * class needs' moods (+5 met, -5 a missing need, at most -15), the chatter they open, and an old chronicle still loading.
 */
public class ClassHallGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final String VARIED = "{\"type\": \"diet\", \"kind\": \"varied\"}";
	private static final String PLAIN = "{\"type\": \"diet\", \"kind\": \"plain\"}";
	private static final String CITY = "{\"type\": \"village_rank\", \"rank\": \"city\"}";

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	/** A Village Hall at {@link #HALL}, classes on; everything shared put back when the test ends. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
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

	private static void ladder(String artisan, String... burgher) {
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + artisan + "]}"));
		files.put(ours("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + String.join(", ", burgher) + "]}"));
		SocialClasses.load(files);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void eats(Villager villager, String... meals) {
		ModAttachments.RECENT_MEALS.set(villager, Arrays.stream(meals).map(ResourceLocation::withDefaultNamespace).toList());
	}

	private static String id(Villager v) {
		ResourceLocation c = ModAttachments.SOCIAL_CLASS.get(v);
		return c == null ? "none" : c.getPath();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static int points(List<LegendPowers.MoodReason> moods, String reason) {
		return moods.stream().filter(m -> key(m.reason()).equals("mood.aliveworkplace.reason." + reason)).mapToInt(LegendPowers.MoodReason::points).sum();
	}

	private static boolean has(List<LegendPowers.MoodReason> moods, String reason) {
		return moods.stream().anyMatch(m -> key(m.reason()).equals("mood.aliveworkplace.reason." + reason));
	}

	private static List<Chronicle.Entry> classEntries(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).toList();
	}

	/**
	 * A married couple who meet the Artisans' needs two dawns running rise together: one CLASS chronicle line naming both,
	 * a chat line for the player 10 blocks off but not the one 60 off, and "rose in the world" (+10) for 2 days, then gone.
	 */
	//$ gametest_batch AREA '"classHallRise"'
	@GameTest(template = AREA, batch = "classHallRise")
	public void aRiseIsChronicledToldAndFelt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = village(helper);
		ladder(VARIED, CITY);
		Villager a = villager(helper, new BlockPos(4, 2, 4), "Odo");
		Villager b = villager(helper, new BlockPos(6, 2, 4), "Pia");
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
		for (Villager v : List.of(a, b)) {
			SocialClasses.seed(v, SocialClasses.get(ours("peasant")));
			eats(v, "bread", "baked_potato", "cooked_cod");
		}
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		near.teleportTo(a.getX() + 10, a.getY(), a.getZ());
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		far.teleportTo(a.getX() + 60, a.getY(), a.getZ());
		BlockPos at = helper.absolutePos(HALL);
		long time = level.getDayTime();
		level.setDayTime(time + 10 * VillageNeeds.DAY); // a day well after the first, so the dawn before it is counted
		Leftovers.after(helper, () -> level.setDayTime(time));
		long today = Chronicle.day(level);
		int before = classEntries(hall).size();
		SocialClasses.check(level, at, List.of(a, b), today - 1, 8);
		helper.assertTrue(classEntries(hall).size() == before && !has(SocialClasses.moods(a, today - 1), "class_rose"), "nothing to tell after one dawn");
		SocialClasses.Result rise = SocialClasses.check(level, at, List.of(a, b), today, 8);
		helper.assertTrue(id(a).equals("artisan") && rise.changes().size() == 1 && rise.changes().get(0).rose(), "the couple rose: " + id(a));
		List<Chronicle.Entry> entries = classEntries(hall);
		helper.assertTrue(entries.size() == before + 1, "one CLASS entry for the household: " + entries);
		Chronicle.Entry entry = entries.get(entries.size() - 1);
		String text = entry.text().getString();
		helper.assertTrue(key(entry.text()).equals("chronicle.aliveworkplace.class.rose") && text.contains("Odo") && text.contains("Pia") && text.contains("Artisan"),
			"the entry names both and their class: " + text);
		List<ServerPlayer> told = SocialClasses.listeners(level, a.blockPosition());
		helper.assertTrue(told.contains(near) && !told.contains(far), "players within 32 blocks hear of it, not further: " + told.size());
		for (Villager v : List.of(a, b)) {
			List<LegendPowers.MoodReason> moods = SocialClasses.moods(v, today);
			helper.assertTrue(points(moods, "class_rose") == 10, "rose in the world, +10: " + moods);
		}
		helper.assertTrue(points(SocialClasses.moods(a, today + 1), "class_rose") == 10, "still proud the next day");
		helper.assertTrue(!has(SocialClasses.moods(a, today + 2), "class_rose"), "after 2 days the rise is old news");
		// Moods list it among the good, and the chatter's topics take it from there.
		boolean moodsOn = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = moodsOn);
		Moods.forget(a);
		Moods.Mood mood = Moods.of(a);
		helper.assertTrue(mood != null && mood.good().stream().anyMatch(c -> key(c).equals("mood.aliveworkplace.reason.class_rose")), "the mood lists the rise: " + (mood == null ? null : mood.good()));
		List<String> topics = io.github.jcondedata.aliveworkplace.people.Chatter.topics(level, a, at);
		helper.assertTrue(topics.contains("class_rose"), "the rise is something to talk about: " + topics);
		Moods.forget(a);
		List.of(a, b).forEach(Villager::discard);
		near.discard();
		far.discard();
		helper.succeed();
	}

	/** Three dawns short of a need of their own class: a CLASS entry, "came down in the world" (-10), and no chat line. */
	//$ gametest_batch AREA '"classHallFall"'
	@GameTest(template = AREA, batch = "classHallFall")
	public void aFallIsChronicledAndFelt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = village(helper);
		ladder(VARIED, CITY);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Tam");
		SocialClasses.seed(v, SocialClasses.get(ours("artisan")));
		eats(v, "bread", "bread", "bread");
		BlockPos at = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		int before = classEntries(hall).size();
		SocialClasses.check(level, at, List.of(v), today, 8);
		List<LegendPowers.MoodReason> lacking = SocialClasses.moods(v, today);
		helper.assertTrue(points(lacking, "class_lacking") == -5 && !has(lacking, "class_needs_met"), "one need missing, -5: " + lacking);
		SocialClasses.check(level, at, List.of(v), today + 1, 8);
		SocialClasses.check(level, at, List.of(v), today + 2, 8);
		helper.assertTrue(id(v).equals("peasant"), "fell after 3 dawns: " + id(v));
		List<Chronicle.Entry> entries = classEntries(hall);
		helper.assertTrue(entries.size() == before + 1 && key(entries.get(entries.size() - 1).text()).equals("chronicle.aliveworkplace.class.fell")
			&& entries.get(entries.size() - 1).text().getString().contains("Tam"), "one fall entry naming Tam: " + entries);
		List<LegendPowers.MoodReason> moods = SocialClasses.moods(v, today + 2);
		helper.assertTrue(points(moods, "class_fell") == -10, "came down in the world, -10: " + moods);
		helper.assertTrue(points(moods, "class_needs_met") == 5, "a Peasant has what their class needs, +5: " + moods);
		helper.assertTrue(!has(SocialClasses.moods(v, today + 4), "class_fell"), "after 2 days the fall is forgotten");
		v.discard();
		helper.succeed();
	}

	/** Each missing need is -5, at most -15; all met is +5; classes off or not yet seeded, no class moods at all. */
	//$ gametest_batch AREA '"classHallNeeds"'
	@GameTest(template = AREA, batch = "classHallNeeds")
	public void missingNeedsCostMoodUpToFifteen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		ladder(String.join(", ", VARIED, CITY, "{\"type\": \"village_rank\", \"rank\": \"town\"}", "{\"type\": \"fed_days\", \"days\": 9}"), PLAIN);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Una");
		Villager unseeded = villager(helper, new BlockPos(8, 2, 4), "Vic");
		SocialClasses.seed(v, SocialClasses.get(ours("artisan")));
		eats(v, "bread", "bread", "bread");
		BlockPos at = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		SocialClasses.check(level, at, List.of(v, unseeded), today, 8);
		List<LegendPowers.MoodReason> moods = SocialClasses.moods(v, today);
		helper.assertTrue(points(moods, "class_lacking") == -15, "4 needs missing is -15, no more: " + moods);
		helper.assertTrue(SocialClasses.moods(unseeded, today).isEmpty(), "no class, no class moods");
		ladder(PLAIN, CITY);
		eats(v, "bread", "carrot", "apple");
		SocialClasses.check(level, at, List.of(v), today + 1, 8);
		helper.assertTrue(points(SocialClasses.moods(v, today + 1), "class_needs_met") == 5 && !has(SocialClasses.moods(v, today + 1), "class_lacking"),
			"all met, +5: " + SocialClasses.moods(v, today + 1));
		helper.assertTrue(SocialClasses.moods(v, today + 5).isEmpty(), "a standing days old (the hall gone) says nothing");
		SocialClasses.ENABLED = false;
		helper.assertTrue(SocialClasses.moods(v, today + 1).isEmpty(), "villageClasses off: no class moods");
		List.of(v, unseeded).forEach(Villager::discard);
		helper.succeed();
	}

	/** The new chronicle kind saves and loads as "class"; an unknown kind in an old save still loads (as Founded). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"classHallKind"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "classHallKind")
	public void theClassKindLoads(GameTestHelper helper) {
		helper.assertTrue(Chronicle.Kind.valueOf("CLASS").name().equals("CLASS"), "CLASS is a kind");
		for (int i = 0; i < 3; i++) {
			for (String topic : List.of("class_rose", "class_fell")) {
				String line = Component.translatable("chatter.aliveworkplace." + topic + "." + i).getString();
				helper.assertTrue(!line.startsWith("chatter."), "chatter line " + topic + "." + i + " has text");
			}
		}
		helper.succeed();
	}

	private static List<String> lore(net.minecraft.world.item.ItemStack stack) {
		net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** The Classes button on the class page that names {@code cls}, or empty. */
	private static net.minecraft.world.item.ItemStack classButton(io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu, String cls) {
		for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < io.github.jcondedata.aliveworkplace.work.ChoiceMenu.SIZE; slot++) {
			net.minecraft.world.item.ItemStack icon = menu.icon(slot);
			if (!icon.isEmpty() && icon.getHoverName().getString().equals(cls)) {
				return icon;
			}
		}
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	/**
	 * A staged village (34.6): three Peasant households (Ann eats a varied diet, Bo and Cy only bread) and an Artisan couple
	 * (Dee and Eli). The Classes tab says how many of each; the page counts each need over the class and the one below
	 * ("A varied diet: 3 of 4"), says what each class gives and who is closest to rising with what they lack; "What next?"
	 * gets the class tips, most households first; the people list says "Artisan · married to Eli" and ticks the needs; the
	 * food icon lists the luxuries in store.
	 */
	//$ gametest_batch AREA '"classHallPage"'
	@GameTest(template = AREA, batch = "classHallPage")
	public void theClassesPageCountsAStagedVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		helper.setBlock(new BlockPos(19, 2, 19), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(19, 2, 17), net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(new BlockPos(19, 2, 17));
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DISC_FRAGMENT_5, 3));
		ladder(VARIED, CITY, PLAIN);
		Villager ann = villager(helper, new BlockPos(3, 2, 3), "Ann");
		Villager bo = villager(helper, new BlockPos(5, 2, 3), "Bo");
		Villager cy = villager(helper, new BlockPos(7, 2, 3), "Cy");
		Villager dee = villager(helper, new BlockPos(3, 2, 7), "Dee");
		Villager eli = villager(helper, new BlockPos(5, 2, 7), "Eli");
		ModAttachments.PARTNER.set(dee, new Couples.Partner(eli.getUUID(), eli.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(eli, new Couples.Partner(dee.getUUID(), dee.getDisplayName(), 1, true));
		for (Villager v : List.of(ann, bo, cy)) {
			SocialClasses.seed(v, SocialClasses.get(ours("peasant")));
		}
		for (Villager v : List.of(dee, eli)) {
			SocialClasses.seed(v, SocialClasses.get(ours("artisan")));
			eats(v, "bread", "baked_potato", "cooked_cod");
		}
		eats(ann, "bread", "baked_potato", "cooked_cod");
		eats(bo, "bread", "bread", "bread");
		eats(cy, "bread", "bread", "bread");
		BlockPos at = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		// The survey behind the page.
		List<io.github.jcondedata.aliveworkplace.hall.ClassesPage.Row> rows = io.github.jcondedata.aliveworkplace.hall.ClassesPage.survey(level, at);
		helper.assertTrue(rows.size() == 3 && rows.get(0).households() == 3 && rows.get(1).households() == 1 && rows.get(2).households() == 0,
			"3 Peasant households, 1 Artisan (the couple), 0 Burgher: " + rows.stream().map(r -> r.households()).toList());
		io.github.jcondedata.aliveworkplace.hall.ClassesPage.NeedCount varied = rows.get(1).counts().get(0);
		helper.assertTrue(varied.have() == 2 && varied.of() == 4, "a varied diet: Ann and the couple of 4 households: " + varied);
		List<io.github.jcondedata.aliveworkplace.hall.ClassesPage.Close> closest = rows.get(1).closest();
		helper.assertTrue(closest.size() == 3 && closest.get(0).household().lead() == ann && closest.get(0).lacking().isEmpty()
			&& closest.get(1).lacking().size() == 1, "Ann is closest to Artisan (lacks nothing), then Bo and Cy lacking one: " + closest);

		// The tab's tooltip on the hall's screen.
		io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, at);
		int tab = io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.ClassesPage.PAGE);
		helper.assertTrue(tab >= 0, "the Classes tab is in the page row");
		List<String> tip = lore(menu.icon(tab));
		helper.assertTrue(menu.icon(tab).getHoverName().getString().equals("Classes") && tip.contains("Peasant households: 3")
			&& tip.contains("Artisan households: 1") && tip.contains("Burgher households: 0"), "the tab counts each class: " + tip);
		// The food icon: the test luxury (a disc fragment) in the store.
		List<String> food = lore(menu.icon(3));
		helper.assertTrue(food.contains("Luxuries in store: Test Trinket ×3"), "the food icon lists the luxuries in store: " + food);

		// The page.
		menu.press(tab, player);
		List<String> artisan = lore(classButton(menu, "Artisan"));
		helper.assertTrue(artisan.contains("Households: 1") && artisan.contains("  A varied diet: 2 of 4")
			&& artisan.contains("Counted over the 4 households who are Artisan or Peasant"), "the Artisan button counts its need: " + artisan);
		helper.assertTrue(artisan.contains("Pays 1.5× the tax") && artisan.contains("Closest to becoming Artisan:")
			&& artisan.contains("  Ann: has everything, rising at a dawn soon") && artisan.contains("  Bo: lacks A varied diet"),
			"what the Artisans give, and who's closest: " + artisan);
		List<String> burgher = lore(classButton(menu, "Burgher"));
		helper.assertTrue(burgher.contains("  The village a City or bigger: 0 of 1") && burgher.contains("  A plain diet: 1 of 1")
			&& burgher.stream().anyMatch(l -> l.contains("Dee") && l.contains("Eli") && l.endsWith(": lacks The village a City or bigger")), "the Burgher button: " + burgher);
		List<String> peasant = lore(classButton(menu, "Peasant"));
		helper.assertTrue(peasant.contains("Households: 3") && peasant.contains("Needs nothing: everyone starts here"), "the Peasant button: " + peasant);

		// "What next?": most households first.
		List<io.github.jcondedata.aliveworkplace.hall.ClassesPage.ClassTip> tips = io.github.jcondedata.aliveworkplace.hall.ClassesPage.classTips(level, at);
		helper.assertTrue(tips.size() == 2 && tips.get(0).households() == 2 && tips.get(1).households() == 1, "two tips, 2 households first: " + tips);
		List<String> advice = io.github.jcondedata.aliveworkplace.hall.VillageAdvice.tips(level, at).stream()
			.filter(t -> t.key().startsWith("class")).map(t -> t.title().getString() + " | " + t.how().getString()).toList();
		helper.assertTrue(advice.size() == 2 && advice.get(0).equals("2 Peasant households want A varied diet to rise to Artisan | Keep 3 kinds of meal in the store")
			&& advice.get(1).startsWith("1 Artisan household wants The village a City or bigger to rise to Burgher | Grow the village into a City"),
			"the class tips read: " + advice);

		// The people list: class and household, and the needs ticked.
		List<String> dees = lore(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.person(level, at, dee));
		helper.assertTrue(dees.contains("Artisan · married to Eli") && dees.stream().filter(l -> l.toLowerCase().contains("married to eli")).count() == 1, "Dee's class line: " + dees);
		helper.assertTrue(dees.contains("Artisan needs:") && dees.contains("  ✔ A varied diet") && dees.contains("To become Burgher:")
			&& dees.contains("  ✘ The village a City or bigger") && dees.contains("  ✔ A plain diet"), "Dee's needs, ticked: " + dees);
		List<String> bos = lore(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.person(level, at, bo));
		helper.assertTrue(bos.contains("Peasant · a household of one") && bos.contains("To become Artisan:") && bos.contains("  ✘ A varied diet"), "Bo's lines: " + bos);

		// Classes off: the tab says so, and nobody's class shows.
		SocialClasses.ENABLED = false;
		helper.assertTrue(lore(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, at).icon(tab)).contains("Village classes are switched off")
			&& io.github.jcondedata.aliveworkplace.hall.ClassesPage.classTips(level, at).isEmpty()
			&& io.github.jcondedata.aliveworkplace.hall.ClassesPage.classLine(dee) == null
			&& lore(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, at).icon(3)).stream().noneMatch(l -> l.startsWith("Luxuries")), "villageClasses off");
		List.of(ann, bo, cy, dee, eli).forEach(Villager::discard);
		player.discard();
		helper.succeed();
	}

	/** Every sentence the page and tips can show has text, and every class need and effect of ours has a name. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"classHallWords"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "classHallWords")
	public void everyClassLineHasWords(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		helper.assertTrue(SocialClasses.ladder().size() == 4, "our four classes load");
		for (SocialClasses.SocialClass c : SocialClasses.ladder()) {
			for (ClassNeeds.Need need : c.needs()) {
				for (Component line : List.of(io.github.jcondedata.aliveworkplace.hall.ClassesPage.needName(need), io.github.jcondedata.aliveworkplace.hall.ClassesPage.how(need))) {
					String text = line.getString();
					helper.assertTrue(!text.contains("aliveworkplace.") && !text.isBlank(), c.id() + " need " + need.type() + " reads: " + text);
				}
			}
			for (ClassNeeds.Need need : c.wants()) {
				String text = io.github.jcondedata.aliveworkplace.hall.ClassesPage.needName(need).getString();
				helper.assertTrue(!text.contains("aliveworkplace."), c.id() + " want reads: " + text);
			}
			for (Component g : io.github.jcondedata.aliveworkplace.hall.ClassesPage.gives(c)) {
				helper.assertTrue(!g.getString().contains("aliveworkplace.") && !g.getString().contains("data pack"), c.id() + " gives: " + g.getString());
			}
		}
		helper.succeed();
	}
}
