package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * QA (lane E, 2026-11-16): hand-edited config files at the extremes, for the settings added after the first config QA
 * classes (QaConfigFile/OddFiles/HandEdits, 2026-10-24/25): the realms, colonies, classes, ages, story arcs, Wonders,
 * furniture, Cups, seasons and money settings. From the spec (WorkplaceConfig's class comment and config-switches.md):
 * an out-of-range value is clamped to the documented range, a missing or unreadable one takes its default, every other
 * value in the file is kept, and the file is never lost. Then the features whose arithmetic hangs on a clamped extreme
 * (the Cup calendar, the seasons, money) are run at it. Pure logic: every test applies a config and puts the default
 * one back within its one tick, so no other test sees it.
 */
public class QaConfigNewFields1116eGameTests implements FabricGameTest {
	/** The newer whole-number settings, and where the game reads each. */
	static Map<String, IntSupplier> wired() {
		Map<String, IntSupplier> m = new LinkedHashMap<>();
		m.put("denHelperPrice", () -> RaidHelpers.PRICE);
		m.put("arcCooldownDays", () -> Arcs.COOLDOWN_DAYS);
		m.put("arcsAtOnce", () -> Arcs.AT_ONCE);
		m.put("lostCaravanChance", () -> LostCaravanStory.CHANCE);
		m.put("maxWorkPace", () -> Pace.MAX_PERCENT);
		m.put("guildsPerRank", () -> Guilds.PER_RANK);
		m.put("edictMinDays", () -> Edicts.MIN_DAYS);
		m.put("colonyCooldownDays", () -> Colonies.COOLDOWN_DAYS);
		m.put("coloniesPerVillage", () -> Colonies.PER_VILLAGE);
		m.put("realmMaxMembers", () -> Realms.MAX_MEMBERS);
		m.put("leaderboardHour", () -> Leaderboard.HOUR);
		m.put("classRiseDays", () -> SocialClasses.RISE_DAYS);
		m.put("classFallDays", () -> SocialClasses.FALL_DAYS);
		m.put("classGraceDays", () -> ClassMigration.GRACE_DAYS);
		m.put("villagerElderDays", () -> LifeStages.ELDER_DAYS);
		m.put("stewardMaxOpenBuilds", () -> Stewards.MAX_OPEN_BUILDS);
		m.put("caravanRoadReach", () -> CaravanRoads.REACH);
		m.put("wonderServerLimit", () -> Wonders.SERVER_LIMIT);
		m.put("wonderCrew", () -> WonderCrews.LIMIT);
		m.put("wonderStanding", () -> WonderUpkeep.STANDING);
		m.put("cupEveryFestivals", () -> Cups.EVERY);
		m.put("seasonDays", () -> Seasons.DAYS);
		m.put("treasuryPerWorker", () -> io.github.jcondedata.aliveworkplace.hall.Treasury.CENTS_PER_WORKER);
		m.put("dollarsPerEmerald", () -> Money.DOLLARS_PER_EMERALD);
		return m;
	}

	/** The documented ranges (config-switches.md and the feature pages), written out here rather than read from the code. */
	static final Map<String, int[]> DOCUMENTED = documented();

	private static Map<String, int[]> documented() {
		Map<String, int[]> m = new LinkedHashMap<>();
		m.put("denHelperPrice", new int[] {5, 1, 64});
		m.put("arcCooldownDays", new int[] {8, 0, 60});
		m.put("arcsAtOnce", new int[] {3, 0, 20});
		m.put("lostCaravanChance", new int[] {20, 0, 1000});
		m.put("maxWorkPace", new int[] {200, 100, 400});
		m.put("guildsPerRank", new int[] {1, 1, 4});
		m.put("edictMinDays", new int[] {3, 0, 30});
		m.put("colonyCooldownDays", new int[] {7, 0, 60});
		m.put("coloniesPerVillage", new int[] {3, 0, 16});
		m.put("realmMaxMembers", new int[] {12, 2, 32});
		m.put("leaderboardHour", new int[] {19, 0, 23});
		m.put("classRiseDays", new int[] {2, 1, 30});
		m.put("classFallDays", new int[] {3, 1, 30});
		m.put("classGraceDays", new int[] {7, 0, 30});
		m.put("villagerElderDays", new int[] {1200, 20, 10_000});
		m.put("stewardMaxOpenBuilds", new int[] {4, 1, 8});
		m.put("caravanRoadReach", new int[] {256, 32, 512});
		m.put("wonderServerLimit", new int[] {0, 0, 64});
		m.put("wonderCrew", new int[] {12, 1, 32});
		m.put("wonderStanding", new int[] {90, 50, 100});
		m.put("cupEveryFestivals", new int[] {1, 1, 8});
		m.put("seasonDays", new int[] {16, 1, 120});
		m.put("treasuryPerWorker", new int[] {20, 0, 500});
		m.put("dollarsPerEmerald", new int[] {100, 1, 10_000});
		return m;
	}

	/** A file holding {@code body} as a format-5 file (so no old-format migration moves a value). */
	private static String file(String body) {
		return "{\"configVersion\": 5" + (body.isEmpty() ? "" : ", " + body) + "}";
	}

	/** Applies {@code json}, runs {@code check} while it's in force, then puts the default config back. */
	private static void with(String json, Runnable check) {
		try {
			WorkplaceConfig.parse(json).apply();
			check.run();
		} finally {
			new WorkplaceConfig().apply();
		}
	}

	private static Map<String, Integer> read() {
		Map<String, Integer> out = new LinkedHashMap<>();
		wired().forEach((name, read) -> out.put(name, read.getAsInt()));
		return out;
	}

	/** The defaults in the docs are the code's defaults (a fresh file), so "takes its default" below means the documented one. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theNewerSettingsStartAtTheirDocumentedDefaults(GameTestHelper helper) {
		WorkplaceConfig fresh = new WorkplaceConfig();
		List<String> problems = new ArrayList<>();
		DOCUMENTED.forEach((name, d) -> {
			if (fresh.getInt(name) != d[0]) {
				problems.add(name + " starts at " + fresh.getInt(name) + ", the docs say " + d[0]);
			}
			WorkplaceConfig.Range r = WorkplaceConfig.RANGES.get(name);
			if (r == null || r.min() != d[1] || r.max() != d[2]) {
				problems.add(name + "'s range (the file's clamp and the settings slider) is " + r + ", the docs say " + d[1] + " to " + d[2]);
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** A value of its own inside each range (no two alike, none an end) reaches the code that uses it. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void eachNewerSettingReachesTheGame(GameTestHelper helper) {
		Map<String, Integer> want = new LinkedHashMap<>();
		DOCUMENTED.forEach((name, d) -> {
			int v = d[1] + 1;
			if (v == d[0]) {
				v++;
			}
			want.put(name, Math.min(v, d[2] - 1));
		});
		StringBuilder body = new StringBuilder();
		want.forEach((k, v) -> body.append(body.length() == 0 ? "" : ", ").append('"').append(k).append("\": ").append(v));
		Map<String, Integer> got = new LinkedHashMap<>();
		with(file(body.toString()), () -> got.putAll(read()));
		List<String> problems = new ArrayList<>();
		want.forEach((k, v) -> {
			if (!v.equals(got.get(k))) {
				problems.add(k + ": file " + v + ", game " + got.get(k));
			}
		});
		helper.assertTrue(problems.isEmpty(), "settings that didn't reach the game: " + problems);
		// and the default file puts each back
		Map<String, Integer> back = read();
		DOCUMENTED.forEach((k, d) -> {
			if (back.get(k) != d[0]) {
				problems.add(k + " stays " + back.get(k));
			}
		});
		helper.assertTrue(problems.isEmpty(), "not back to the default: " + problems);
		helper.succeed();
	}

	/** 0, a negative, a big number and one past what an int holds: the game gets the documented end, never the file's number. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void eachNewerSettingIsClampedAtBothEnds(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String raw : List.of("0", "-1", "-2147483648", "-99999999999", "1000000", "2147483647", "99999999999", "\"-5\"", "\"123456789012\"")) {
			StringBuilder body = new StringBuilder();
			DOCUMENTED.keySet().forEach(k -> body.append(body.length() == 0 ? "" : ", ").append('"').append(k).append("\": ").append(raw));
			Map<String, Integer> got = new LinkedHashMap<>();
			with(file(body.toString()), () -> got.putAll(read()));
			boolean low = raw.contains("-") || raw.equals("0");
			DOCUMENTED.forEach((k, d) -> {
				int expect = raw.equals("0") ? Math.max(0, d[1]) : low ? d[1] : d[2];
				if (got.get(k) != expect) {
					problems.add(k + "=" + raw + " -> " + got.get(k) + " (want " + expect + ")");
				}
			});
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A value of the wrong kind (a word, an object, a list, a switch, an empty string) or null: that one setting takes its
	 * default (a fraction, 12.5, its default or 12 clamped); another setting in the same file (supplyRadius 12, wonderCrew
	 * 5) is kept.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aNewerSettingOfTheWrongKindTakesItsDefaultAlone(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String bad : List.of("\"lots\"", "{}", "{\"value\": 3}", "[]", "[4]", "true", "12.5", "\"\"", "null")) {
			for (String name : DOCUMENTED.keySet()) {
				String keep = name.equals("wonderCrew") ? "\"supplyRadius\": 12" : "\"supplyRadius\": 12, \"wonderCrew\": 5";
				WorkplaceConfig c;
				try {
					c = WorkplaceConfig.parse(file(keep + ", \"" + name + "\": " + bad));
				} catch (RuntimeException e) {
					problems.add(name + "=" + bad + " threw " + e);
					continue;
				}
				int[] d = DOCUMENTED.get(name);
				// 12.5 may also be read as 12, then clamped (the earlier config QA's accepted reading, QaConfigFileBugTests)
				boolean cut = bad.equals("12.5") && c.getInt(name) == Math.max(d[1], Math.min(d[2], 12));
				if (c.getInt(name) != d[0] && !cut) {
					problems.add(name + "=" + bad + " read as " + c.getInt(name));
				}
				if (c.supplyRadius != 12 || !name.equals("wonderCrew") && c.wonderCrew != 5) {
					problems.add(name + "=" + bad + " cost the other settings (supplyRadius " + c.supplyRadius + ", wonderCrew " + c.wonderCrew + ")");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}

	/**
	 * The file-only lists given something that isn't a list (a word, an object, a number, a switch): each takes its
	 * default, the others and every number are kept, and applying the result throws nothing.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aFileOnlyListOfTheWrongKindTakesItsDefault(GameTestHelper helper) {
		Map<String, Object> defaults = new LinkedHashMap<>();
		defaults.put("furnitureMods", List.of("handcrafted", "cozyhome", "cobblefurnies", "beautify"));
		defaults.put("disabledWonders", List.of());
		defaults.put("disabledArcs", List.of());
		defaults.put("retrainPrices", List.of(5, 10, 20, 35, 50));
		defaults.put("wonderCaps", List.of(0, 0, 1, 2));
		List<String> problems = new ArrayList<>();
		for (String bad : List.of("\"handcrafted\"", "{}", "{\"0\": 3}", "7", "true")) {
			for (String name : defaults.keySet()) {
				try {
					WorkplaceConfig c = WorkplaceConfig.parse(file("\"seasonDays\": 9, \"realmMaxMembers\": 4, \"" + name + "\": " + bad));
					Object got = switch (name) {
						case "furnitureMods" -> c.furnitureMods;
						case "disabledWonders" -> c.disabledWonders;
						case "disabledArcs" -> c.disabledArcs;
						case "retrainPrices" -> c.retrainPrices;
						default -> c.wonderCaps;
					};
					if (!defaults.get(name).equals(got)) {
						problems.add(name + "=" + bad + " read as " + got);
					}
					if (c.seasonDays != 9 || c.realmMaxMembers != 4) {
						problems.add(name + "=" + bad + " cost the other settings");
					}
					with(file("\"" + name + "\": " + bad), () -> {
					});
				} catch (RuntimeException e) {
					problems.add(name + "=" + bad + " threw " + e);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * colonyRank and leaderboardDay are words: any case and spaces are read as meant ("TOWN ", " Friday"); a number, a
	 * list, an object, null or a misspelling reads as the documented fallback (City, Sunday), and the game gets it.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theWordSettingsReadAnyCaseAndFallBackOnJunk(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Map<String, VillageRanks.Rank> ranks = new LinkedHashMap<>();
		ranks.put("\"TOWN \"", VillageRanks.Rank.TOWN);
		ranks.put("\" hamlet\"", VillageRanks.Rank.HAMLET);
		ranks.put("\"Village\"", VillageRanks.Rank.VILLAGE);
		ranks.put("\"metropolis\"", VillageRanks.Rank.CITY);
		ranks.put("\"towns\"", VillageRanks.Rank.CITY);
		ranks.put("2", VillageRanks.Rank.CITY);
		ranks.put("[\"town\"]", VillageRanks.Rank.CITY);
		ranks.put("{}", VillageRanks.Rank.CITY);
		ranks.put("null", VillageRanks.Rank.CITY);
		ranks.put("\"\"", VillageRanks.Rank.CITY);
		ranks.forEach((raw, want) -> with(file("\"colonyRank\": " + raw), () -> {
			if (Colonies.RANK != want) {
				problems.add("colonyRank " + raw + " -> " + Colonies.RANK);
			}
		}));
		Map<String, DayOfWeek> days = new LinkedHashMap<>();
		days.put("\" Friday\"", DayOfWeek.FRIDAY);
		days.put("\"MONDAY\"", DayOfWeek.MONDAY);
		days.put("\"saturday \"", DayOfWeek.SATURDAY);
		days.put("\"fri\"", DayOfWeek.SUNDAY);
		days.put("\"wendsday\"", DayOfWeek.SUNDAY);
		days.put("5", DayOfWeek.SUNDAY);
		days.put("[\"friday\"]", DayOfWeek.SUNDAY);
		days.put("{}", DayOfWeek.SUNDAY);
		days.put("null", DayOfWeek.SUNDAY);
		days.forEach((raw, want) -> with(file("\"leaderboardDay\": " + raw), () -> {
			if (Leaderboard.DAY != want) {
				problems.add("leaderboardDay " + raw + " -> " + Leaderboard.DAY);
			}
		}));
		// and the file is written back with the word it was read as, so the owner sees what the game uses
		WorkplaceConfig c = WorkplaceConfig.parse(file("\"colonyRank\": \"TOWN \", \"leaderboardDay\": \"wendsday\""));
		if (!c.colonyRank.equals("town") || !c.leaderboardDay.equals("sunday")) {
			problems.add("written back as " + c.colonyRank + " / " + c.leaderboardDay);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * disabledWonders with empty entries, blanks, repeats, spaces round a name, another pack's id and text that is no id:
	 * applying it throws nothing, and exactly the real ids are off.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void junkInDisabledWondersIsSkippedAndTheRealIdsAreOff(GameTestHelper helper) {
		Set<ResourceLocation>[] got = new Set[1];
		with(file("\"disabledWonders\": [null, \"\", \"   \", \"great_cathedral\", \"great_cathedral\", \" lighthouse \", \"pack:my_wonder\", \"not an id!\", \"a:b:c\"]"),
			() -> got[0] = Set.copyOf(Wonders.DISABLED));
		Set<ResourceLocation> want = Set.of(AliveWorkplace.id("great_cathedral"), AliveWorkplace.id("lighthouse"),
			ResourceLocation.fromNamespaceAndPath("pack", "my_wonder"));
		helper.assertTrue(got[0].equals(want), "disabled Wonders: " + got[0] + ", want " + want);
		helper.assertTrue(Wonders.DISABLED.isEmpty(), "the default file left Wonders disabled: " + Wonders.DISABLED);
		helper.succeed();
	}

	/**
	 * disabledArcs with an empty entry, repeats, a blank and another pack's id: applying throws nothing, the listed arcs
	 * are off by bare name or full id, the rest stay on; the list survives a save and a second start.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void junkInDisabledArcsIsSkippedAndTheRealIdsAreOff(GameTestHelper helper) throws Exception {
		String json = file("\"disabledArcs\": [null, \"bandit_king\", \"bandit_king\", \"\", \"pack:tale\", \"aliveworkplace:the_sickness\"]");
		List<String> problems = new ArrayList<>();
		with(json, () -> {
			if (!Arcs.disabled(AliveWorkplace.id("bandit_king"))) {
				problems.add("bandit_king is on");
			}
			if (!Arcs.disabled(ResourceLocation.fromNamespaceAndPath("pack", "tale"))) {
				problems.add("pack:tale is on");
			}
			if (!Arcs.disabled(AliveWorkplace.id("the_sickness"))) {
				problems.add("the_sickness (full id) is on");
			}
			if (Arcs.disabled(AliveWorkplace.id("lost_caravan")) || Arcs.disabled(ResourceLocation.fromNamespaceAndPath("other", "bandit_king"))) {
				problems.add("an arc not listed is off");
			}
		});
		Path dir = Files.createTempDirectory("aw-qa-arcs");
		try {
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), json);
			WorkplaceConfig.load(dir).save(dir);
			WorkplaceConfig again = WorkplaceConfig.load(dir);
			if (!again.disabledArcs.contains("bandit_king") || !again.disabledArcs.contains("pack:tale")) {
				problems.add("after a save and a start: " + again.disabledArcs);
			}
			if (Files.exists(dir.resolve(WorkplaceConfig.BAD_FILE))) {
				problems.add("the file was set aside as unreadable");
			}
		} finally {
			deleteAll(dir);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The number lists: too long is cut to its length (5 prices, 4 caps), an empty entry takes its default, a short one
	 * is filled from the defaults, and every entry is clamped (prices 0 to 1000, caps 0 to 8); the game gets that.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void numberListsAreCutFilledAndClamped(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[][] got = new int[2][];
		with(file("\"retrainPrices\": [1, null, -3, 99999, 7, 8, 9, 10], \"wonderCaps\": [9, null, -1, 3, 5, 6]"), () -> {
			got[0] = Retraining.PRICES.clone();
			got[1] = Wonders.CAPS.clone();
		});
		if (!Arrays.equals(got[0], new int[] {1, 10, 0, 1000, 7})) {
			problems.add("retraining prices in the game " + Arrays.toString(got[0]));
		}
		if (!Arrays.equals(got[1], new int[] {8, 0, 0, 3})) {
			problems.add("Wonder caps in the game " + Arrays.toString(got[1]));
		}
		WorkplaceConfig shortOnes = WorkplaceConfig.parse(file("\"retrainPrices\": [2], \"wonderCaps\": [1]"));
		if (!shortOnes.retrainPrices.equals(List.of(2, 10, 20, 35, 50)) || !shortOnes.wonderCaps.equals(List.of(1, 0, 1, 2))) {
			problems.add("short lists read as " + shortOnes.retrainPrices + " / " + shortOnes.wonderCaps);
		}
		if (!Arrays.equals(Retraining.PRICES, new int[] {5, 10, 20, 35, 50}) || !Arrays.equals(Wonders.CAPS, new int[] {0, 0, 1, 2})) {
			problems.add("the default file didn't put the lists back");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * furnitureMods: a long list of the same mod in every case and with spaces, blanks and an empty entry comes to one
	 * entry each, in the order first written; a list of only blanks is the documented "empty list means vanilla".
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void furnitureModsOfRepeatsAndBlanksAreCleanedInOrder(GameTestHelper helper) {
		StringBuilder list = new StringBuilder("[\" Beautify\"");
		for (int i = 0; i < 300; i++) {
			list.append(i % 3 == 0 ? ", \"HANDCRAFTED\"" : i % 3 == 1 ? ", \"handcrafted \"" : ", null");
		}
		list.append(", \"\", \"cozyhome\", \"BEAUTIFY\"]");
		List<String>[] got = new List[1];
		with(file("\"furnitureMods\": " + list), () -> got[0] = List.copyOf(Furnishings.MODS));
		helper.assertTrue(got[0].equals(List.of("beautify", "handcrafted", "cozyhome")), "furniture mods in the game: " + got[0]);
		WorkplaceConfig blanks = WorkplaceConfig.parse(file("\"furnitureMods\": [null, \"\", \"  \"]"));
		helper.assertTrue(blanks.furnitureMods.isEmpty(), "a list of blanks read as " + blanks.furnitureMods);
		helper.succeed();
	}

	/**
	 * A file with every newer setting past its ends, read and written back the way a server start does, twice: the
	 * second start reads exactly what the first wrote (nothing drifts), the file is never set aside, and it holds the ends.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void extremesSettleOnTheFirstStartAndStay(GameTestHelper helper) throws Exception {
		StringBuilder body = new StringBuilder();
		int i = 0;
		for (String k : DOCUMENTED.keySet()) {
			body.append(body.length() == 0 ? "" : ", ").append('"').append(k).append("\": ").append(i++ % 2 == 0 ? "-4000000000" : "4000000000");
		}
		body.append(", \"retrainPrices\": [-1, 5000000000, null], \"wonderCaps\": [100, -100, 1, 2, 3], \"colonyRank\": \"Hamlet\",")
			.append(" \"leaderboardDay\": \"FRIDAY\", \"disabledWonders\": [\"great_cathedral\", null], \"furnitureMods\": [\"Cozyhome\", \"\"]");
		Path dir = Files.createTempDirectory("aw-qa-extremes");
		List<String> problems = new ArrayList<>();
		try {
			Path f = dir.resolve(WorkplaceConfig.FILE);
			Files.writeString(f, file(body.toString()));
			WorkplaceConfig.loadAndApply(dir);
			String first = Files.readString(f);
			WorkplaceConfig.loadAndApply(dir);
			String second = Files.readString(f);
			if (!first.equals(second)) {
				problems.add("the second start wrote a different file");
			}
			if (Files.exists(dir.resolve(WorkplaceConfig.BAD_FILE))) {
				problems.add("the file was set aside as unreadable");
			}
			WorkplaceConfig c = WorkplaceConfig.load(dir);
			i = 0;
			for (Map.Entry<String, int[]> e : DOCUMENTED.entrySet()) {
				int want = i++ % 2 == 0 ? e.getValue()[1] : e.getValue()[2];
				if (c.getInt(e.getKey()) != want) {
					problems.add(e.getKey() + " written as " + c.getInt(e.getKey()) + ", want " + want);
				}
			}
			if (!c.retrainPrices.equals(List.of(0, 1000, 20, 35, 50)) || !c.wonderCaps.equals(List.of(8, 0, 1, 2)) || !c.colonyRank.equals("hamlet")
				|| !c.leaderboardDay.equals("friday") || !c.furnitureMods.equals(List.of("cozyhome")) || !c.disabledWonders.contains("great_cathedral")) {
				problems.add("lists and words written as " + c.retrainPrices + " " + c.wonderCaps + " " + c.colonyRank + " " + c.leaderboardDay + " "
					+ c.furnitureMods + " " + c.disabledWonders);
			}
			if (Colonies.RANK != VillageRanks.Rank.HAMLET || Leaderboard.DAY != DayOfWeek.FRIDAY || Seasons.DAYS != 120 || Cups.EVERY != 1) {
				problems.add("the start didn't apply it: " + Colonies.RANK + " " + Leaderboard.DAY + " " + Seasons.DAYS + " " + Cups.EVERY);
			}
		} finally {
			new WorkplaceConfig().apply();
			deleteAll(dir);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The Cup calendar at both ends of cupEveryFestivals: 0 in the file is 1 (every festival a Cup, no divide by 0), and
	 * at 8 only every eighth festival is one: with festivals every 8 days from day 0, the next Cup from day 1 is day 64,
	 * and from day 65 it's day 128; festivals before the hall's offset (negative numbers) follow the same rule.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theCupCalendarAtBothEndsOfCupEveryFestivals(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		with(file("\"cupEveryFestivals\": 0"), () -> {
			if (Cups.EVERY != 1) {
				problems.add("0 reached the Cups as " + Cups.EVERY);
			}
			for (long n = -3; n <= 3; n++) {
				if (!Cups.isCup(n, Cups.EVERY)) {
					problems.add("festival " + n + " isn't a Cup at every-1");
				}
			}
			long day = Cups.cupDay(1, 0, 8, Cups.EVERY);
			if (day != 8) {
				problems.add("next Cup from day 1 at every-1: " + day);
			}
		});
		with(file("\"cupEveryFestivals\": 99"), () -> {
			if (Cups.EVERY != 8) {
				problems.add("99 reached the Cups as " + Cups.EVERY);
			}
			for (long n = -16; n <= 16; n++) {
				if (Cups.isCup(n, Cups.EVERY) != (Math.floorMod(n, 8) == 0)) {
					problems.add("festival " + n + " Cup=" + Cups.isCup(n, Cups.EVERY));
				}
			}
			long a = Cups.cupDay(1, 0, 8, Cups.EVERY);
			long b = Cups.cupDay(65, 0, 8, Cups.EVERY);
			long c = Cups.cupDay(64, 0, 8, Cups.EVERY);
			if (a != 64 || b != 128 || c != 64) {
				problems.add("next Cups at every-8: from 1 " + a + ", from 65 " + b + ", from 64 " + c);
			}
			long far = Cups.cupDay(4_000_000_000L, 3, 8, Cups.EVERY);
			if (far < 4_000_000_000L || Math.floorMod(far - 3, 64) != 0) {
				problems.add("a Cup far on (day 4e9): " + far);
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The seasons at seasonDays' clamps: 0 and -5 are 1-day seasons and 100000 is 120 days; on the first day, the last
	 * day of a year, the first of the next and a day billions of days on, the date is sane (day of season 1 to length,
	 * year from 1, the four seasons in turn) and the hall's season line has no negative number or "null" in it.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theCalendarAtBothEndsOfSeasonDays(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String raw : List.of("0", "-5", "100000")) {
			with(file("\"seasonDays\": " + raw), () -> {
				int len = Seasons.DAYS;
				int want = raw.equals("100000") ? 120 : 1;
				if (len != want) {
					problems.add("seasonDays " + raw + " reached the calendar as " + len);
					return;
				}
				for (long day : new long[] {0, 1, 4L * len, 4L * len + 1, 3_000_000_000L, Long.MAX_VALUE / 2}) {
					Seasons.Date d = Seasons.date(day, Seasons.DAYS);
					long index = Math.max(0, day - 1);
					Seasons.Season season = Seasons.Season.values()[(int) ((index / len) % 4)];
					if (d.dayOfSeason() < 1 || d.dayOfSeason() > len || d.year() < 1 || d.season() != season || d.length() != len
						|| d.festivalDay() < 1 || d.festivalDay() > len) {
						problems.add("seasonDays " + len + ", day " + day + ": " + d);
					}
					String line = Seasons.seasonLine(d).getString();
					if (line.contains("null") || line.contains("-") || line.contains("%")) {
						problems.add("season line on day " + day + ": " + line);
					}
				}
			});
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Money at the ends: dollarsPerEmerald 0 is 1 and a huge one 10000; a raid helper at the dearest price (64) and the
	 * dearest retraining (1000 emeralds) then come to 640,000 and 10,000,000 CobbleDollars, never a negative or wrapped
	 * number, and the emerald price a player reads says the plain count.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void pricesAtTheDearestEndsDoNotOverflow(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		with(file("\"dollarsPerEmerald\": 0, \"denHelperPrice\": 0"), () -> {
			if (Money.DOLLARS_PER_EMERALD != 1 || RaidHelpers.PRICE != 1 || RaidHelpers.dollars() != 1) {
				problems.add("at 0: rate " + Money.DOLLARS_PER_EMERALD + ", helper " + RaidHelpers.PRICE + " (" + RaidHelpers.dollars() + " dollars)");
			}
		});
		with(file("\"dollarsPerEmerald\": 99999999999, \"denHelperPrice\": 99999999999, \"retrainPrices\": [99999999999, 1000, 1000, 1000, 1000]"), () -> {
			if (Money.DOLLARS_PER_EMERALD != 10_000 || RaidHelpers.PRICE != 64 || RaidHelpers.dollars() != 640_000L) {
				problems.add("at the top: rate " + Money.DOLLARS_PER_EMERALD + ", helper " + RaidHelpers.PRICE + " (" + RaidHelpers.dollars() + " dollars)");
			}
			long retrain = (long) Retraining.PRICES[0] * Money.DOLLARS_PER_EMERALD;
			if (Retraining.PRICES[0] != 1000 || retrain != 10_000_000L) {
				problems.add("retraining at the top: " + Retraining.PRICES[0] + " emeralds, " + retrain + " dollars");
			}
			if (!Money.cobbleDollars()) {
				String text = Money.describe(retrain, Retraining.PRICES[0]).getString();
				if (!text.matches("1,?000 emeralds")) {
					problems.add("the price reads \"" + text + "\"");
				}
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** A whole file of nothing but nulls for every newer setting: each takes its default, and nothing throws on applying it. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aFileOfNullsGivesEveryNewerDefault(GameTestHelper helper) {
		StringBuilder body = new StringBuilder();
		for (String k : DOCUMENTED.keySet()) {
			body.append(body.length() == 0 ? "" : ", ").append('"').append(k).append("\": null");
		}
		for (String k : List.of("furnitureMods", "disabledWonders", "disabledArcs", "retrainPrices", "wonderCaps", "colonyRank", "leaderboardDay",
			"moddedStorageNetworks", "moddedLibrary")) {
			body.append(", \"").append(k).append("\": null");
		}
		Map<String, Integer> got = new LinkedHashMap<>();
		WorkplaceConfig[] c = new WorkplaceConfig[1];
		with(file(body.toString()), () -> {
			got.putAll(read());
			c[0] = WorkplaceConfig.parse(file(body.toString()));
		});
		List<String> problems = new ArrayList<>();
		DOCUMENTED.forEach((k, d) -> {
			if (got.get(k) != d[0]) {
				problems.add(k + " " + got.get(k));
			}
		});
		WorkplaceConfig fresh = new WorkplaceConfig();
		if (!c[0].furnitureMods.equals(fresh.furnitureMods) || !c[0].disabledWonders.isEmpty() || !c[0].disabledArcs.isEmpty()
			|| !c[0].retrainPrices.equals(fresh.retrainPrices) || !c[0].wonderCaps.equals(fresh.wonderCaps) || !c[0].colonyRank.equals("city")
			|| !c[0].leaderboardDay.equals("sunday") || c[0].moddedStorageNetworks != fresh.moddedStorageNetworks || c[0].moddedLibrary != fresh.moddedLibrary) {
			problems.add("lists, words and switches: " + c[0].furnitureMods + " " + c[0].disabledWonders + " " + c[0].disabledArcs + " "
				+ c[0].retrainPrices + " " + c[0].wonderCaps + " " + c[0].colonyRank + " " + c[0].leaderboardDay);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The two new storage switches written by hand as 0/1, a list or an object: that switch takes its default
	 * (on from 2.1) and the rest of the file stays; written "false" in quotes it is off, as meant.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theStorageSwitchesOfTheWrongKindTakeTheirDefault(GameTestHelper helper) {
		WorkplaceConfig fresh = new WorkplaceConfig();
		List<String> problems = new ArrayList<>();
		for (String name : List.of("moddedStorageNetworks", "moddedLibrary")) {
			for (String bad : List.of("0", "1", "[]", "{}", "{\"on\": true}")) {
				WorkplaceConfig c = WorkplaceConfig.parse(file("\"wonderCrew\": 7, \"" + name + "\": " + bad));
				if (c.getBoolean(name) != fresh.getBoolean(name) || c.wonderCrew != 7) {
					problems.add(name + "=" + bad + " -> " + c.getBoolean(name) + ", wonderCrew " + c.wonderCrew);
				}
			}
			WorkplaceConfig off = WorkplaceConfig.parse(file("\"" + name + "\": \"false\""));
			if (off.getBoolean(name)) {
				problems.add(name + "=\"false\" stays on");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	static void deleteAll(Path dir) {
		try (var walk = Files.walk(dir)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (java.io.IOException ignored) {
					// a temp folder left behind is harmless
				}
			});
		} catch (java.io.IOException ignored) {
			// nothing to clean
		}
	}
}
