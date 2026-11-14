package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * QA (lane-c-1114-0053), from the spec of ROADMAP 33.1 (the M33 design note), for what the realm, config and edict
 * tests leave out: the note's config table (every default from an empty file, both edges of each range, the rank and
 * weekday words, a value that can't be read, every boolean a switch on the screen), and the six realm edicts as the
 * note lists them (scope, each one's values, its reform count, every sentence a player reads).
 */
public class QaRealm1114cGameTests implements FabricGameTest {
	private static final List<String> SWITCHES = List.of("villageEconomy", "visibleCaravans", "colonies", "realms",
		"realmRelief", "tradePacts", "alliances", "feuds", "feudWars", "weeklyLeaderboard");
	private static final List<String> NUMBERS = List.of("colonyCooldownDays", "coloniesPerVillage", "realmMaxMembers", "leaderboardHour");
	private static final Set<String> REALM_EDICTS = Set.of("royal_highway", "common_market", "levy_of_arms", "realm_festival",
		"crowns_peace", "royal_charter");

	/** An empty config file gives every M33 value the note's default. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anEmptyFileTakesTheNotesDefaults(GameTestHelper helper) {
		WorkplaceConfig c = WorkplaceConfig.parse("{}");
		helper.assertTrue("city".equals(c.colonyRank), "colonyRank default: " + c.colonyRank);
		helper.assertTrue(c.colonyCooldownDays == 7, "colonyCooldownDays default: " + c.colonyCooldownDays);
		helper.assertTrue(c.coloniesPerVillage == 3, "coloniesPerVillage default: " + c.coloniesPerVillage);
		helper.assertTrue(c.realmMaxMembers == 12, "realmMaxMembers default: " + c.realmMaxMembers);
		helper.assertTrue("sunday".equals(c.leaderboardDay), "leaderboardDay default: " + c.leaderboardDay);
		helper.assertTrue(c.leaderboardHour == 19, "leaderboardHour default: " + c.leaderboardHour);
		helper.succeed();
	}

	/** realmMaxMembers: 2 to 32, the edges kept and one past each clamped. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void realmMaxMembersEdgesFromBothSides(GameTestHelper helper) {
		helper.assertTrue(num("realmMaxMembers", 1) == 2, "1 member clamps to 2");
		helper.assertTrue(num("realmMaxMembers", 2) == 2, "2 kept");
		helper.assertTrue(num("realmMaxMembers", 32) == 32, "32 kept");
		helper.assertTrue(num("realmMaxMembers", 33) == 32, "33 clamps to 32");
		helper.assertTrue(num("realmMaxMembers", -500) == 2, "negative clamps to 2");
		helper.succeed();
	}

	/** colonyCooldownDays 0 to 60 and coloniesPerVillage 0 to 16, both edges from both sides. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void colonyNumbersEdgesFromBothSides(GameTestHelper helper) {
		helper.assertTrue(num("colonyCooldownDays", -1) == 0, "cooldown -1 -> 0");
		helper.assertTrue(num("colonyCooldownDays", 0) == 0, "cooldown 0 kept");
		helper.assertTrue(num("colonyCooldownDays", 60) == 60, "cooldown 60 kept");
		helper.assertTrue(num("colonyCooldownDays", 61) == 60, "cooldown 61 -> 60");
		helper.assertTrue(num("coloniesPerVillage", -1) == 0, "per village -1 -> 0");
		helper.assertTrue(num("coloniesPerVillage", 0) == 0, "per village 0 kept");
		helper.assertTrue(num("coloniesPerVillage", 16) == 16, "per village 16 kept");
		helper.assertTrue(num("coloniesPerVillage", 17) == 16, "per village 17 -> 16");
		helper.succeed();
	}

	/** leaderboardHour 0 to 23, both edges from both sides. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leaderboardHourEdgesFromBothSides(GameTestHelper helper) {
		helper.assertTrue(num("leaderboardHour", -1) == 0, "-1 -> 0");
		helper.assertTrue(num("leaderboardHour", 0) == 0, "0 kept");
		helper.assertTrue(num("leaderboardHour", 23) == 23, "23 kept");
		helper.assertTrue(num("leaderboardHour", 24) == 23, "24 -> 23");
		helper.succeed();
	}

	/** colonyRank takes hamlet, village, town or city in any case and spacing; anything else is a City. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void colonyRankWords(GameTestHelper helper) {
		helper.assertTrue("hamlet".equals(word("colonyRank", "hamlet")), "hamlet");
		helper.assertTrue("village".equals(word("colonyRank", "Village")), "Village");
		helper.assertTrue("town".equals(word("colonyRank", " TOWN ")), "' TOWN '");
		helper.assertTrue("city".equals(word("colonyRank", "city")), "city");
		helper.assertTrue("city".equals(word("colonyRank", "empire")), "an unknown rank is a City, was " + word("colonyRank", "empire"));
		helper.assertTrue("city".equals(word("colonyRank", "")), "an empty rank is a City");
		helper.assertTrue("city".equals(WorkplaceConfig.parse("{\"colonyRank\": null}").colonyRank), "a null rank is a City");
		helper.succeed();
	}

	/** leaderboardDay takes any weekday in any case; anything else is Sunday. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leaderboardDayWords(GameTestHelper helper) {
		helper.assertTrue("friday".equals(word("leaderboardDay", "FRIDAY")), "FRIDAY");
		helper.assertTrue("monday".equals(word("leaderboardDay", " Monday")), "' Monday'");
		helper.assertTrue("saturday".equals(word("leaderboardDay", "saturday")), "saturday");
		helper.assertTrue("sunday".equals(word("leaderboardDay", "funday")), "an unknown day is Sunday");
		helper.assertTrue("sunday".equals(word("leaderboardDay", "")), "an empty day is Sunday");
		helper.succeed();
	}

	/** A value that can't be read takes its default and costs no other value in the file. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUnreadableValueTakesItsDefaultOnly(GameTestHelper helper) {
		WorkplaceConfig c = WorkplaceConfig.parse("{\"realmMaxMembers\": \"lots\", \"leaderboardHour\": [1], \"colonyCooldownDays\": 20,"
			+ " \"coloniesPerVillage\": 5, \"colonyRank\": \"town\", \"leaderboardDay\": \"friday\", \"realms\": false, \"feudWars\": false}");
		helper.assertTrue(c.realmMaxMembers == 12, "unreadable realmMaxMembers takes 12, was " + c.realmMaxMembers);
		helper.assertTrue(c.leaderboardHour == 19, "unreadable leaderboardHour takes 19, was " + c.leaderboardHour);
		helper.assertTrue(c.colonyCooldownDays == 20 && c.coloniesPerVillage == 5, "the readable numbers are kept");
		helper.assertTrue("town".equals(c.colonyRank) && "friday".equals(c.leaderboardDay), "the readable words are kept");
		helper.assertTrue(!c.realms && !c.feudWars, "switches written off stay off");
		helper.succeed();
	}

	/** Every M33 switch in the note is an on/off switch and every number a number, so the Mod Menu screen can show them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyNoteSwitchIsASwitchAndEveryNumberANumber(GameTestHelper helper) {
		WorkplaceConfig off = WorkplaceConfig.parse("{}");
		for (String name : SWITCHES) {
			helper.assertTrue(WorkplaceConfig.isSwitch(name), name + " isn't an on/off switch");
			off.setBoolean(name, false);
			helper.assertTrue(!off.getBoolean(name), name + " can't be turned off");
			helper.assertTrue(!WorkplaceConfig.parse("{\"" + name + "\": false}").getBoolean(name), name + " written off reads on");
		}
		for (String name : NUMBERS) {
			helper.assertTrue(!WorkplaceConfig.isSwitch(name), name + " shouldn't be a switch");
			helper.assertTrue(WorkplaceConfig.RANGES.containsKey(name), name + " has no range");
		}
		helper.succeed();
	}

	/** Exactly the six realm edicts of the note are realm-scoped, each with its reform counting one of the six counts. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void exactlyTheSixRealmEdictsEachWithItsOwnCount(GameTestHelper helper) {
		Map<String, JsonObject> realm = realmEdicts(helper);
		helper.assertTrue(realm.keySet().equals(REALM_EDICTS), "realm-scoped edicts: " + realm.keySet());
		Set<ResourceLocation> counts = Set.of(RealmEdicts.CARAVANS_BETWEEN_MEMBERS, RealmEdicts.BOARD_TRADES, RealmEdicts.RAIDS_BEATEN_WITH_RELIEF,
			RealmEdicts.REALM_FESTIVALS, RealmEdicts.DAYS_WITHOUT_RAID, RealmEdicts.COLONIES_AT_VILLAGE_RANK);
		Set<ResourceLocation> used = new HashSet<>();
		realm.forEach((id, json) -> {
			JsonObject count = json.getAsJsonObject("reform").getAsJsonObject("count");
			ResourceLocation what = ResourceLocation.parse(count.get("what").getAsString());
			helper.assertTrue(counts.contains(what), id + " counts an unknown thing: " + what);
			helper.assertTrue(count.get("times").getAsInt() > 0, id + " needs a positive count");
			helper.assertTrue(json.get("enabled").getAsBoolean(), id + " ships disabled");
			used.add(what);
		});
		helper.assertTrue(used.equals(counts), "each count used once: " + used);
		helper.succeed();
	}

	/** Each realm edict carries the values the note gives it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachRealmEdictHasTheNotesValues(GameTestHelper helper) {
		Map<String, JsonObject> realm = realmEdicts(helper);
		JsonObject highway = realm.get("royal_highway");
		helper.assertTrue(effect(highway, "boost", "caravan_carry") != null && "members".equals(effect(highway, "boost", "caravan_carry").get("between").getAsString()),
			"Royal Highway carries between members");
		helper.assertTrue(effect(highway, "boost", "caravan_speed") != null, "Royal Highway speeds caravans");
		JsonObject upkeep = effect(highway, "cost", "realm_upkeep");
		helper.assertTrue(upkeep != null && upkeep.get("emeralds").getAsInt() == 1 && "route".equals(upkeep.get("per").getAsString()), "Royal Highway costs 1 a route");
		JsonObject market = realm.get("common_market");
		helper.assertTrue(effect(market, "boost", "common_board") != null, "Common Market shares the board");
		helper.assertTrue(effect(market, "boost", "member_spread") != null && effect(market, "boost", "member_spread").get("points").getAsInt() == 0,
			"Common Market's spread between members is 0");
		helper.assertTrue(effect(market, "cost", "levy") != null && effect(market, "cost", "levy").get("points").getAsInt() == 5, "Common Market's levy +5");
		JsonObject arms = realm.get("levy_of_arms");
		helper.assertTrue(effect(arms, "boost", "relief_reach") != null, "Levy of Arms reaches everywhere");
		helper.assertTrue(effect(arms, "boost", "relief_band") != null && effect(arms, "boost", "relief_band").get("factor").getAsInt() == 2, "riders x2");
		helper.assertTrue(effect(arms, "cost", "member_dues") != null && effect(arms, "cost", "member_dues").get("emeralds").getAsInt() == 2, "2 a member");
		JsonObject festival = realm.get("realm_festival");
		helper.assertTrue(effect(festival, "boost", "festival_together") != null, "festival together");
		helper.assertTrue(effect(festival, "boost", "festival_mood_days") != null && effect(festival, "boost", "festival_mood_days").get("days").getAsInt() == 3, "mood 3 days");
		helper.assertTrue(effect(festival, "cost", "realm_festival_cost") != null && effect(festival, "cost", "realm_festival_cost").get("emeralds").getAsInt() == 10,
			"festival costs 10 a member");
		JsonObject peace = realm.get("crowns_peace");
		helper.assertTrue(effect(peace, "boost", "no_feuds") != null, "Crown's Peace: no feuds");
		helper.assertTrue(effect(peace, "boost", "no_bandit_camps") != null && effect(peace, "boost", "no_bandit_camps").get("radius").getAsInt() == RealmEdicts.PEACE_RADIUS
			&& RealmEdicts.PEACE_RADIUS == 128, "no bandit camps within 128");
		helper.assertTrue(effect(peace, "cost", "takings") != null && effect(peace, "cost", "takings").get("percent").getAsInt() == -10, "takings -10%");
		JsonObject charter = realm.get("royal_charter");
		helper.assertTrue(effect(charter, "boost", "colony_rank") != null && "town".equals(effect(charter, "boost", "colony_rank").get("rank").getAsString()), "charter at Town");
		helper.assertTrue(effect(charter, "boost", "colony_cooldown") != null && effect(charter, "boost", "colony_cooldown").get("percent").getAsInt() == 50, "cooldown x0.5");
		helper.assertTrue(effect(charter, "cost", "colony_cost") != null && effect(charter, "cost", "colony_cost").get("emeralds").getAsInt() == 32, "+32 emeralds");
		helper.succeed();
	}

	/** Every sentence of a realm edict a player reads (name, description, reform name and line) has English text. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRealmEdictSentenceResolves(GameTestHelper helper) {
		Language lang = Language.getInstance();
		realmEdicts(helper).forEach((id, json) -> {
			List<String> keys = List.of(key(json.get("name")), key(json.get("description")),
				key(json.getAsJsonObject("reform").get("name")), key(json.getAsJsonObject("reform").get("line")));
			for (String key : keys) {
				helper.assertTrue(key != null && lang.has(key), id + ": no text for " + key);
				String text = lang.getOrDefault(key);
				helper.assertTrue(!text.isBlank() && !text.equals(key) && !text.contains("TODO"), id + ": bad text for " + key + ": " + text);
				helper.assertTrue(!text.contains("colour") && !text.contains("defence") && !text.contains("honour") && !text.contains("favour"),
					id + ": British spelling in " + key + ": " + text);
			}
		});
		helper.succeed();
	}

	// --- helpers ---

	private static int num(String name, int value) {
		return WorkplaceConfig.parse("{\"" + name + "\": " + value + "}").getInt(name);
	}

	private static String word(String name, String value) {
		WorkplaceConfig c = WorkplaceConfig.parse("{\"" + name + "\": \"" + value + "\"}");
		return "colonyRank".equals(name) ? c.colonyRank : c.leaderboardDay;
	}

	private static String key(JsonElement component) {
		return component != null && component.isJsonObject() && component.getAsJsonObject().has("translate")
			? component.getAsJsonObject().get("translate").getAsString() : null;
	}

	private static JsonObject effect(JsonObject edict, String list, String type) {
		for (JsonElement e : edict.getAsJsonArray(list)) {
			if (("aliveworkplace:" + type).equals(e.getAsJsonObject().get("type").getAsString())) {
				return e.getAsJsonObject();
			}
		}
		return null;
	}

	private static Map<String, JsonObject> realmEdicts(GameTestHelper helper) {
		Map<String, JsonObject> out = new HashMap<>();
		Map<ResourceLocation, Resource> found = helper.getLevel().getServer().getResourceManager()
			.listResources("edicts", p -> p.getNamespace().equals("aliveworkplace") && p.getPath().endsWith(".json"));
		helper.assertTrue(!found.isEmpty(), "no edicts found");
		found.forEach((loc, res) -> {
			try (Reader reader = res.openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.has("scope") && "realm".equals(json.get("scope").getAsString())) {
					String path = loc.getPath();
					out.put(path.substring("edicts/".length(), path.length() - ".json".length()), json);
				}
			} catch (Exception e) {
				throw new IllegalStateException("can't read " + loc, e);
			}
		});
		return out;
	}
}
