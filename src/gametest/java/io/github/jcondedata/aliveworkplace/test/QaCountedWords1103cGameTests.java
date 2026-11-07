package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.jeweller.JewellerWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Words;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-03; B367, B407, B413, B444): the counted sentences those bugs fixed, at 0, 1 and 2, read word
 * for word, through the player's own path where one can be reached (a luxury maker's line above the head, a taste of
 * home's request, a Netherworker's title, a worker's "starts later" line), else through the method that builds them.
 * No line may show a raw "aliveworkplace." key.
 */
public class QaCountedWords1103cGameTests implements FabricGameTest {
	private static final String LUX = QaLuxuryJobsGameTests.AREA;

	static void check(List<String> wrong, String got, String want) {
		if (!got.equals(want)) {
			wrong.add("\"" + got + "\" (want \"" + want + "\")");
		}
		if (got.contains("aliveworkplace.")) {
			wrong.add("raw key in \"" + got + "\"");
		}
	}

	/** A Jeweller's lines above the head for the rings they make, collected every tick. */
	static Set<String> jewellerLines(GameTestHelper helper, int rings, int makings) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.STONECUTTER);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(ModItems.AMETHYST_RING, rings));
		c.setItem(1, new ItemStack(Items.AMETHYST_SHARD, makings * 2));
		c.setItem(2, new ItemStack(Items.COPPER_INGOT, makings * 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.JEWELLER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new JewellerWork());
		Set<String> lines = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
			if (e != null && e.line() != null) {
				lines.add(e.line().getString());
			}
		});
		return lines;
	}

	/** B367: a Jeweller making one ring says "fetching what 1 Amethyst Ring takes", "making 1 Amethyst Ring", "putting 1 Amethyst Ring away". */
	//$ gametest_ticks_batch LUX '1600' '"qaCw1103cLuxOne"'
	@GameTest(template = LUX, timeoutTicks = 1600, batch = "qaCw1103cLuxOne")
	public void aJewellerMakingOneRingReadsInTheSingular(GameTestHelper helper) {
		Set<String> lines = jewellerLines(helper, 7, 1);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.AMETHYST_RING) == 8, c.countItem(ModItems.AMETHYST_RING) + " rings, expected 8");
			helper.assertTrue(lines.contains("fetching what 1 Amethyst Ring takes"), "no singular fetching line; lines seen: " + lines);
			helper.assertTrue(lines.contains("making 1 Amethyst Ring"), "no 'making 1 Amethyst Ring'; lines seen: " + lines);
			for (String l : lines) {
				helper.assertTrue(!l.contains("aliveworkplace."), "raw key: \"" + l + "\"");
				helper.assertTrue(!l.endsWith(" take") && !l.contains("Rings"), "one ring read as many: \"" + l + "\"");
				if (l.startsWith("putting") && !l.equals("putting things away")) {
					helper.assertTrue(l.equals("putting 1 Amethyst Ring away"), "the putting-away line: \"" + l + "\"");
				}
			}
		});
	}

	/** B367: a Jeweller making two rings at once says "take", never "takes" (and never the one form). */
	//$ gametest_ticks_batch LUX '2400' '"qaCw1103cLuxTwo"'
	@GameTest(template = LUX, timeoutTicks = 2400, batch = "qaCw1103cLuxTwo")
	public void aJewellerMakingTwoRingsReadsAsMany(GameTestHelper helper) {
		Set<String> lines = jewellerLines(helper, 6, 2);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ModItems.AMETHYST_RING) == 8, c.countItem(ModItems.AMETHYST_RING) + " rings, expected 8");
			helper.assertTrue(lines.stream().anyMatch(l -> l.matches("fetching what 2 Amethyst Rings? take")), "no 'fetching what 2 ... take'; lines seen: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.matches("making 2 Amethyst Rings?")), "no 'making 2 ...'; lines seen: " + lines);
			for (String l : lines) {
				helper.assertTrue(!l.contains("aliveworkplace."), "raw key: \"" + l + "\"");
				helper.assertTrue(!l.endsWith(" takes") && !l.contains(" 1 Amethyst"), "two rings read as one: \"" + l + "\"");
			}
		});
	}

	/** B367 by the method: every maker's fetching line (crafter, chef, luxury, toolsmith, fletcher, weaponsmith) at 0, 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyMakersFetchingLineCountsZeroOneAndTwo(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Component ring = ModItems.AMETHYST_RING.getDescription();
		for (String who : List.of("crafter", "chef", "luxury", "toolsmith", "fletcher", "weaponsmith_crafting")) {
			String key = "message.aliveworkplace." + who + ".state.fetching";
			check(wrong, Words.counted(key, 0, 0, ring).getString(), "fetching what 0 Amethyst Ring take");
			check(wrong, Words.counted(key, 1, 1, ring).getString(), "fetching what 1 Amethyst Ring takes");
			check(wrong, Words.counted(key, 2, 2, ring).getString(), "fetching what 2 Amethyst Ring take");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B407 through the request itself: a snowy villager's taste of home reads "4 Baked Potatoes" in what they want, the
	 * chat offer, the objective's line and its short form, and a jungle villager's "8 Cookies".
	 */
	//$ gametest_ticks_batch 'PersonalRequestGameTests.AREA' '80' '"qaCw1103cTaste"'
	@GameTest(template = PersonalRequestGameTests.AREA, timeoutTicks = 80, batch = "qaCw1103cTaste")
	public void aTasteOfHomeNamesManyInThePlural(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			Map<VillagerType, String> miss = new LinkedHashMap<>();
			miss.put(VillagerType.SNOW, "4 Baked Potatoes");
			miss.put(VillagerType.JUNGLE, "8 Cookies");
			miss.put(VillagerType.PLAINS, "1 Pumpkin Pie");
			Villager odo = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Odo");
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), odo, 300);
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<VillagerType, String> e : miss.entrySet()) {
				odo.setVillagerData(odo.getVillagerData().setType(e.getKey()));
				Quest quest = PersonalRequestGameTests.resolve(helper, "taste_of_home", odo, me);
				if (quest == null) {
					wrong.add(e.getKey() + ": nothing asked");
					continue;
				}
				String n = e.getValue();
				check(wrong, PersonalRequests.wants(helper.getLevel(), quest).getString(), "Odo wants a taste of home: " + n + " within 3 days");
				check(wrong, quest.objectives.get(0).line().getString(), "Bring " + n);
				check(wrong, quest.objectives.get(0).what().getString(), n);
				String offer = PersonalRequests.offerLine(helper.getLevel(), quest).getString();
				if (!offer.startsWith("Odo wants a taste of home: " + n + " within 3 days. For ")) {
					wrong.add("offer \"" + offer + "\" (want it to start \"Odo wants a taste of home: " + n + " within 3 days. For \")");
				}
				if (offer.contains("aliveworkplace.")) {
					wrong.add("raw key in offer \"" + offer + "\"");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "sentences that read wrong: " + wrong);
			helper.succeed();
		});
	}

	/** B407 by the objective: a bring of 0, 1 and 2 of a food, its line and its short form. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBringCountsZeroOneAndTwo(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String[][] foods = {
			{"minecraft:baked_potato", "Baked Potatoes", "Baked Potato"},
			{"minecraft:cookie", "Cookies", "Cookie"},
			{"minecraft:pumpkin_pie", "Pumpkin Pies", "Pumpkin Pie"},
			{"minecraft:sweet_berries", "Sweet Berries", "Sweet Berries"}};
		for (String[] f : foods) {
			for (int n = 0; n <= 2; n++) {
				String name = n == 1 ? f[2] : f[1];
				Objectives.Bring bring = new Objectives.Bring(f[0], n, "giver", Optional.empty());
				check(wrong, bring.line().getString(), "Bring " + n + " " + name);
				check(wrong, bring.what().getString(), n + " " + name);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B413: the sweep's countdowns and counters at 0 (and again at 1 and 2), each with its count as the only argument. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void countdownsAndCountersReadZeroOneAndTwo(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, String> lines = new LinkedHashMap<>(); // key -> the line with # for the count and {a|b} for one|many
		lines.put("quest.aliveworkplace.wait", "Wait # {day|days}");
		lines.put("legend.aliveworkplace.overhead.sulk_days", "Back to work in # {day|days}");
		lines.put("screen.aliveworkplace.colonies.wait", "The next colony can set out in # {day|days}");
		lines.put("screen.aliveworkplace.realm.vote_left", "About # {hour|hours} left");
		lines.put("screen.aliveworkplace.pact.ending", "Ends in # {hour|hours}");
		lines.put("screen.aliveworkplace.hall.elder_days_left", "Their time comes in # {day|days}");
		lines.put("screen.aliveworkplace.request.before_festival", "Before the next festival (# {day|days})");
		lines.put("message.aliveworkplace.trainer.state", "right-click to battle · # {battle|battles}");
		lines.put("message.aliveworkplace.pokemon_trader.state", "right-click to trade Pokémon · # {trade|trades}");
		lines.put("message.aliveworkplace.shop.log.header_items", "Your shop: # {sale|sales}. Latest:");
		lines.put("screen.aliveworkplace.life_story.next_hearts", "They'll have something to tell you at # {heart|hearts}");
		lines.put("screen.aliveworkplace.life_story.at_hearts", "A story for # {heart|hearts}");
		lines.forEach((key, want) -> {
			for (int n = 0; n <= 2; n++) {
				check(wrong, Words.counted(key, n, n).getString(), form(want, n));
			}
		});
		for (int n = 0; n <= 2; n++) {
			check(wrong, Words.counted("legend.aliveworkplace.overhead.mood_wants", n, "Gold", n).getString(), form("Wants Gold in the chest, # {day|days} left", n));
			check(wrong, Words.counted("message.aliveworkplace.colony.cooldown", n, "Oakvale", n).getString(), form("Oakvale can send its next colony in # {day|days}.", n));
			check(wrong, Words.counted("command.aliveworkplace.classes.settling", n, "Thornholm", n).getString(), form("Thornholm is settling in: # {day|days} left.", n));
			check(wrong, Words.counted("message.aliveworkplace.fossil.taken", n, "Ada", "Helix Fossil", n).getString(),
				form("Ada takes the Helix Fossil: it'll be revived in about # {minute|minutes} of their work.", n));
			check(wrong, Words.counted("message.aliveworkplace.shop.log.header", n, n, "3 CobbleDollars").getString(),
				form("Your shop: # {sale|sales}, 3 CobbleDollars taken in all. Latest:", n));
			check(wrong, Words.countedTwo("screen.aliveworkplace.feud.at_war", n, n, n, n).getString(),
				form("At war: # {day|days} and # {hour|hours} left", n));
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B413 through the player's path: a jobless-hours worker's "starts work" line under a minute, at one and at two. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaCw1103cStarts"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaCw1103cStarts")
	public void aWorkerWaitingForTheirWorkDayCountsMinutes(GameTestHelper helper) {
		long before = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(before));
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		v.setNoAi(true);
		v.setCustomName(Component.literal("Ada"));
		v.refreshBrain(helper.getLevel());
		List<String> wrong = new ArrayList<>();
		long day = before - Math.floorMod(before, 24000L);
		long[] at = {1995, 2000 - 1200, 2000 - 2400};
		String[] want = {"Ada starts work when their work day begins, in under a minute.",
			"Ada starts work when their work day begins, in about a minute.",
			"Ada starts work when their work day begins, in about 2 minutes."};
		for (int i = 0; i < at.length; i++) {
			helper.getLevel().setDayTime(day + at[i]);
			Component line = Jobs.startsLater(v);
			check(wrong, line == null ? "(none)" : line.getString(), want[i]);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B444 by the method: every counting title above a worker's head at 0, 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void countingTitlesReadZeroOneAndTwo(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, String> titles = new LinkedHashMap<>();
		titles.put("message.aliveworkplace.netherworker.title", "Netherworker · # {expedition|expeditions}");
		titles.put("message.aliveworkplace.explorer.title", "Explorer · # {expedition|expeditions}");
		titles.put("message.aliveworkplace.teacher.title", "Teacher · # {pupil|pupils} taught");
		titles.put("message.aliveworkplace.beekeeper.title", "Beekeeper · # {harvest|harvests}");
		titles.put("message.aliveworkplace.lumberjack.title", "Lumberjack · # {tree|trees}");
		titles.put("message.aliveworkplace.innkeeper.title", "Innkeeper · # {guest|guests} hosted");
		titles.put("message.aliveworkplace.daycare_keeper.title", "Daycare Keeper · # {pair|pairs}");
		titles.put("message.aliveworkplace.camp_cook.title", "Camp Cook · # {dish|dishes} cooked");
		titles.put("message.aliveworkplace.alchemist.title", "Cleric · # {potion|potions} brewed");
		titles.put("message.aliveworkplace.herder.title", "Butcher · # {bucket|buckets} of milk");
		titles.put("message.aliveworkplace.florist.title", "Florist · # {flower|flowers} grown");
		titles.put("message.aliveworkplace.berry_breeder.title", "Berry Breeder · # {berry|berries} picked");
		titles.put("message.aliveworkplace.gem_grower.title", "Gem Grower · # {cluster|clusters} picked");
		titles.put("message.aliveworkplace.smelter.title", "Armorer · # {ingot|ingots} smelted");
		titles.put("message.aliveworkplace.weaponsmith_crafting.title", "Weaponsmith · # {sword|swords} made");
		titles.put("message.aliveworkplace.innkeeper.state.hosting", "# {guest|guests} staying");
		titles.put("message.aliveworkplace.daycare_keeper.state.watching", "watching # {pair|pairs} in the pasture");
		titles.put("message.aliveworkplace.explorer.state.home", "Heading home with # {find|finds}");
		titles.forEach((key, want) -> {
			for (int n = 0; n <= 2; n++) {
				check(wrong, Words.countedFirst(key, n).getString(), form(want, n));
			}
		});
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B444 through the player's path: a Netherworker's title above the head with 0 trips, then 2. */
	//$ gametest_ticks_batch 'QaLuxuryJobsGameTests.AREA' '300' '"qaCw1103cNether"'
	@GameTest(template = QaLuxuryJobsGameTests.AREA, timeoutTicks = 300, batch = "qaCw1103cNether")
	public void aNetherworkersTitleCountsZeroAndTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos brazier = new BlockPos(2, 2, 2);
		helper.setBlock(brazier, ModBlocks.NETHER_BRAZIER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		((Container) helper.getBlockEntity(new BlockPos(2, 2, 4))).setItem(0, new ItemStack(Items.BREAD, 3));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(brazier), ModVillagers.NETHER_BRAZIER_POI, ModVillagers.NETHERWORKER);
		ModAttachments.NETHER_TRIPS.set(villager, 0);
		Set<String> titles = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry status = WorkerStatus.get(villager, helper.getLevel().getGameTime());
			if (status != null && status.title() != null) {
				titles.add(status.title().getString());
			}
		});
		helper.runAfterDelay(120, () -> ModAttachments.NETHER_TRIPS.set(villager, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(titles.contains("Netherworker · 0 expeditions") && titles.contains("Netherworker · 2 expeditions"), "titles seen: " + titles);
			for (String t : titles) {
				helper.assertTrue(!t.contains("aliveworkplace.") && !t.contains("expedition "), "title: \"" + t + "\"");
			}
		});
	}

	/** "# {one|many}" with n in place of # and the right word for n. */
	static String form(String pattern, int n) {
		String s = pattern.replace("#", String.valueOf(n));
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^|}]*)\\|([^}]*)\\}").matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(n == 1 ? m.group(1) : m.group(2)));
		}
		m.appendTail(out);
		return out.toString();
	}
}
