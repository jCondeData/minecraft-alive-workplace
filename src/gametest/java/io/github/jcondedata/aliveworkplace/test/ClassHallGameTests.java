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
}
