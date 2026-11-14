package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * QA for ROADMAP 35.1 (the Wonders design note, docs/design/M35.md), written from the note rather than the code: the
 * eight Wonders' table (requirements, power types and their numbers) against the shipped data files, their names and
 * descriptions in English, the new block and item ids, the config switches' ranges and defaults from both sides, a bare
 * name in {@code disabledWonders}, a finished Wonder keeping its powers while disabled but losing them while
 * {@code wonders} is off, the refusals' order, a site saved with only its first three fields, and the new rule lines.
 */
public class QaWonders1114cGameTests implements FabricGameTest {
	private static final String SMALL = "aliveworkplace_test:build_area";

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** Every translatable in {@code c} (arguments too) has an English line. */
	private static void allTranslated(GameTestHelper helper, Component c, String what) {
		if (c.getContents() instanceof TranslatableContents t) {
			helper.assertTrue(Language.getInstance().has(t.getKey()), what + ": no en_us line for " + t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component inner) {
					allTranslated(helper, inner, what);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			allTranslated(helper, sibling, what);
		}
	}

	private static void reads(GameTestHelper helper, Optional<Component> refusal, String english, String what) {
		helper.assertTrue(refusal.isPresent(), what + ": not refused (expected \"" + english + "\")");
		allTranslated(helper, refusal.get(), what);
		String text = refusal.get().getString();
		helper.assertTrue(text.equals(english), what + ": reads \"" + text + "\", expected \"" + english + "\"");
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, VillageRanks.Rank rank) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(at);
		entity.setRank(rank);
		return entity;
	}

	private static void forgetSites(ServerLevel level, BlockPos hall) {
		for (WonderManager.Located l : WonderManager.everywhere(level.getServer())) {
			if (l.level() == level && l.site().hall().equals(hall)) {
				WonderManager.get(l.level()).remove(l.site().id());
			}
		}
		Wonders.forgetPowers();
	}

	private static Wonder shipped(GameTestHelper helper, String path) {
		Optional<Wonder> w = Wonders.get(ours(path));
		helper.assertTrue(w.isPresent(), "the shipped Wonder " + path + " didn't load: " + Wonders.problems());
		return w.get();
	}

	private static JsonObject power(GameTestHelper helper, Wonder w, String type) {
		for (Wonder.Power p : w.powers()) {
			if (p.type().equals(type)) {
				return p.numbers();
			}
		}
		helper.fail(w.id() + " has no " + type + " power: " + w.powers().stream().map(Wonder.Power::type).toList());
		return new JsonObject();
	}

	private static void number(GameTestHelper helper, Wonder w, String type, String field, double expected) {
		JsonObject n = power(helper, w, type);
		helper.assertTrue(n.has(field) && Math.abs(n.get(field).getAsDouble() - expected) < 1e-9,
			w.id().getPath() + "'s " + type + "." + field + " should be " + expected + " (the note's table), is " + n.get(field));
	}

	// ---------------------------------------------------------------- the table

	/**
	 * The note's table of the eight Wonders, row by row: each loads, has exactly its power types, and a requirement
	 * list of its rank, its Legend, its other requirements and the Master Architect line every Wonder gets. The Stadium
	 * is there exactly when Cobblemon is.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonders1114c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonders1114c")
	public void theEightWondersMatchTheNotesTable(GameTestHelper helper) {
		Map<String, Set<String>> powers = new LinkedHashMap<>();
		powers.put("great_cathedral", Set.of("undertakers", "weddings", "beauty"));
		powers.put("lighthouse", Set.of("caravan_range", "caravan_speed", "ferry_fares", "early_warning"));
		powers.put("great_library", Set.of("research_tier"));
		powers.put("great_wall", Set.of("raid_barrier", "sentries"));
		powers.put("observatory", Set.of("sky_chart", "forecast"));
		powers.put("hanging_gardens", Set.of("harvest", "births"));
		powers.put("colossus", Set.of("mood", "travellers", "beauty"));
		// rank + Legend + other requirements + the Master Architect
		Map<String, Integer> requirements = Map.of("great_cathedral", 4, "lighthouse", 4, "great_library", 4, "great_wall", 4,
			"observatory", 3, "hanging_gardens", 3, "colossus", 3);
		for (Map.Entry<String, Set<String>> e : powers.entrySet()) {
			Wonder w = shipped(helper, e.getKey());
			Set<String> types = w.powers().stream().map(Wonder.Power::type).collect(Collectors.toSet());
			helper.assertTrue(types.equals(e.getValue()), e.getKey() + "'s powers should be " + e.getValue() + ", are " + types);
			int need = requirements.get(e.getKey());
			helper.assertTrue(w.requirements().size() == need, e.getKey() + " should have " + need + " requirements (its rank, its Legend, "
				+ "its others and the Master Architect), has " + w.requirements().size());
			helper.assertTrue(w.crew() >= 1 && w.crew() <= 32, e.getKey() + "'s crew out of 1-32: " + w.crew());
		}
		boolean cobblemon = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon");
		Optional<Wonder> stadium = Wonders.get(ours("stadium"));
		helper.assertTrue(stadium.isPresent() == cobblemon, "the Stadium is Cobblemon only: loaded " + stadium.isPresent() + ", Cobblemon " + cobblemon);
		if (stadium.isPresent()) {
			Set<String> types = stadium.get().powers().stream().map(Wonder.Power::type).collect(Collectors.toSet());
			helper.assertTrue(types.equals(Set.of("stadium_days", "tournament_host")), "the Stadium's powers: " + types);
		}
		helper.succeed();
	}

	/** The power numbers in the note's table, as the files give them. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonders1114c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonders1114c")
	public void thePowerNumbersAreTheNotes(GameTestHelper helper) {
		Wonder cathedral = shipped(helper, "great_cathedral");
		number(helper, cathedral, "weddings", "mood", 15);
		number(helper, cathedral, "weddings", "days", 3);
		number(helper, cathedral, "beauty", "points", 8);
		Wonder lighthouse = shipped(helper, "lighthouse");
		number(helper, lighthouse, "caravan_range", "factor", 2);
		number(helper, lighthouse, "caravan_speed", "factor", 0.5);
		number(helper, lighthouse, "ferry_fares", "factor", 0.5);
		number(helper, shipped(helper, "great_library"), "research_tier", "tier", 2);
		Wonder wall = shipped(helper, "great_wall");
		number(helper, wall, "raid_barrier", "margin_degrees", 10);
		number(helper, wall, "sentries", "count", 4);
		number(helper, wall, "sentries", "range", 16);
		number(helper, shipped(helper, "observatory"), "sky_chart", "size", 6144);
		Wonder gardens = shipped(helper, "hanging_gardens");
		number(helper, gardens, "harvest", "extra_every", 4);
		helper.assertTrue("realm".equals(power(helper, gardens, "harvest").get("scope").getAsString()), "the Gardens' harvest reaches the realm");
		number(helper, gardens, "births", "meals", 12);
		number(helper, gardens, "births", "per_day", 2);
		Wonder colossus = shipped(helper, "colossus");
		number(helper, colossus, "mood", "amount", 8);
		number(helper, colossus, "mood", "home_amount", 12);
		helper.assertTrue("realm".equals(power(helper, colossus, "mood").get("scope").getAsString()), "the Colossus' mood reaches the realm");
		number(helper, colossus, "travellers", "factor", 2);
		number(helper, colossus, "travellers", "expert_factor", 2);
		number(helper, colossus, "beauty", "points", 8);
		helper.succeed();
	}

	/** Each Wonder's name reads as the table's, its description is a real English line, and so is every power's name. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonders1114c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonders1114c")
	public void theirNamesAndPowersReadInEnglish(GameTestHelper helper) {
		Map<String, String> names = Map.of("great_cathedral", "Great Cathedral", "lighthouse", "Lighthouse", "great_library", "Great Library",
			"great_wall", "Great Wall", "observatory", "Observatory", "hanging_gardens", "Hanging Gardens", "colossus", "Colossus");
		for (Map.Entry<String, String> e : names.entrySet()) {
			Wonder w = shipped(helper, e.getKey());
			allTranslated(helper, w.nameText(), e.getKey() + "'s name");
			helper.assertTrue(w.nameText().getString().equals(e.getValue()), e.getKey() + " reads \"" + w.nameText().getString() + "\"");
			helper.assertTrue(Wonders.name(w.id()).getString().equals(e.getValue()), "Wonders.name(" + e.getKey() + ") reads " + Wonders.name(w.id()).getString());
			String desc = w.description();
			helper.assertTrue(Language.getInstance().has(desc), e.getKey() + "'s description has no English line: " + desc);
			String text = Language.getInstance().getOrDefault(desc);
			helper.assertTrue(text.length() > 40 && !text.contains("%") && !text.contains("aliveworkplace"), e.getKey() + "'s description reads oddly: " + text);
			for (Wonder.Power p : w.powers()) {
				Component line = p.power().describe();
				allTranslated(helper, line, e.getKey() + "'s power " + p.type());
				helper.assertTrue(!line.getString().isBlank() && !line.getString().contains("wonder.aliveworkplace"), e.getKey() + "'s power " + p.type()
					+ " reads \"" + line.getString() + "\"");
			}
		}
		helper.succeed();
	}

	/** The note's new block and item ids exist and read in English. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonders1114c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonders1114c")
	public void theStoneAndThePlanAreRegistered(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ours("foundation_stone")), "no aliveworkplace:foundation_stone block");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ours("foundation_stone")), "no aliveworkplace:foundation_stone item");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ours("wonder_plan")), "no aliveworkplace:wonder_plan item");
		String stone = BuiltInRegistries.BLOCK.get(ours("foundation_stone")).getName().getString();
		String plan = BuiltInRegistries.ITEM.get(ours("wonder_plan")).getDescription().getString();
		helper.assertTrue(stone.equals("Foundation Stone"), "the stone reads " + stone);
		helper.assertTrue(plan.equals("Wonder Plan"), "the plan reads " + plan);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the switches

	/**
	 * Each numeric switch at its range's edges from both sides (wonderServerLimit 0-64, wonderCrew 1-32, wonderStanding
	 * 50-100), the caps each 0-8, a short caps list filled from the defaults, and a missing value taking its default.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonders1114c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonders1114c")
	public void theSwitchesHoldTheirRanges(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{}").apply();
			helper.assertTrue(Wonders.SERVER_LIMIT == 0 && WonderCrews.LIMIT == 12 && WonderUpkeep.STANDING == 90, "the defaults (0, 12, 90): "
				+ Wonders.SERVER_LIMIT + ", " + WonderCrews.LIMIT + ", " + WonderUpkeep.STANDING);
			helper.assertTrue(Wonders.cap(VillageRanks.Rank.HAMLET) == 0 && Wonders.cap(VillageRanks.Rank.VILLAGE) == 0
				&& Wonders.cap(VillageRanks.Rank.TOWN) == 1 && Wonders.cap(VillageRanks.Rank.CITY) == 2, "the default caps aren't [0, 0, 1, 2]");
			WorkplaceConfig.parse("{\"wonderServerLimit\": 64, \"wonderCrew\": 1, \"wonderStanding\": 50}").apply();
			helper.assertTrue(Wonders.SERVER_LIMIT == 64 && WonderCrews.LIMIT == 1 && WonderUpkeep.STANDING == 50, "the edges inside (64, 1, 50): "
				+ Wonders.SERVER_LIMIT + ", " + WonderCrews.LIMIT + ", " + WonderUpkeep.STANDING);
			WorkplaceConfig.parse("{\"wonderServerLimit\": 65, \"wonderCrew\": 33, \"wonderStanding\": 101}").apply();
			helper.assertTrue(Wonders.SERVER_LIMIT == 64 && WonderCrews.LIMIT == 32 && WonderUpkeep.STANDING == 100, "over the edges (65, 33, 101) "
				+ "should hold at 64, 32, 100: " + Wonders.SERVER_LIMIT + ", " + WonderCrews.LIMIT + ", " + WonderUpkeep.STANDING);
			WorkplaceConfig.parse("{\"wonderServerLimit\": -1, \"wonderCrew\": 0, \"wonderStanding\": 49}").apply();
			helper.assertTrue(Wonders.SERVER_LIMIT == 0 && WonderCrews.LIMIT == 1 && WonderUpkeep.STANDING == 50, "under the edges (-1, 0, 49) "
				+ "should hold at 0, 1, 50: " + Wonders.SERVER_LIMIT + ", " + WonderCrews.LIMIT + ", " + WonderUpkeep.STANDING);
			WorkplaceConfig.parse("{\"wonderCaps\": [8, 9]}").apply();
			helper.assertTrue(Wonders.cap(VillageRanks.Rank.HAMLET) == 8 && Wonders.cap(VillageRanks.Rank.VILLAGE) == 8, "caps 8 and 9 should give 8, 8: "
				+ Wonders.cap(VillageRanks.Rank.HAMLET) + ", " + Wonders.cap(VillageRanks.Rank.VILLAGE));
			helper.assertTrue(Wonders.cap(VillageRanks.Rank.TOWN) == 1 && Wonders.cap(VillageRanks.Rank.CITY) == 2, "a short caps list should be filled "
				+ "from the defaults (Town 1, City 2): " + Wonders.cap(VillageRanks.Rank.TOWN) + ", " + Wonders.cap(VillageRanks.Rank.CITY));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(Wonders.SERVER_LIMIT == 0 && WonderCrews.LIMIT == 12 && WonderUpkeep.STANDING == 90, "the config wasn't reset");
		helper.succeed();
	}

	/**
	 * {@code disabledWonders}: a bare name is ours, a full id works too, and a bare name never reaches another pack's
	 * Wonder of that name. A disabled Wonder is off the page and refused as unknown; the others aren't touched.
	 */
	//$ gametest_batch SMALL '"qaWonders1114c"'
	@GameTest(template = SMALL, batch = "qaWonders1114c")
	public void aBareDisabledNameIsOurs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		forgetSites(level, hall);
		hall(helper, at, VillageRanks.Rank.CITY);
		ResourceLocation folly = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
		try {
			WorkplaceConfig.parse("{\"disabledWonders\": [\"observatory\", \"aliveworkplace:colossus\", \"test_folly\"]}").apply();
			helper.assertTrue(Wonders.DISABLED.contains(ours("observatory")) && Wonders.DISABLED.contains(ours("colossus")),
				"observatory / aliveworkplace:colossus not read as ours: " + Wonders.DISABLED);
			helper.assertTrue(!Wonders.DISABLED.contains(folly), "a bare test_folly should be aliveworkplace:test_folly, not the test pack's: " + Wonders.DISABLED);
			Set<ResourceLocation> page = Wonders.all().stream().map(Wonder::id).collect(Collectors.toSet());
			helper.assertTrue(!page.contains(ours("observatory")) && !page.contains(ours("colossus")), "disabled Wonders still on the page: " + page);
			helper.assertTrue(page.contains(ours("great_library")) && page.contains(folly), "the others left the page: " + page);
			reads(helper, Wonders.startRefusal(level, hall, ours("observatory")), "There's no Wonder aliveworkplace:observatory on this server.",
				"a disabled Observatory");
			Optional<Component> library = Wonders.capRefusal(level, hall, ours("great_library"));
			helper.assertTrue(library.isEmpty(), "the Library beside them is refused: " + library.map(Component::getString).orElse(""));
		} finally {
			new WorkplaceConfig().apply();
			forgetSites(level, hall);
		}
		helper.assertTrue(Wonders.DISABLED.isEmpty(), "the config wasn't reset");
		helper.succeed();
	}

	/**
	 * The refusals' order (35.2's as-built note): switched off before an unknown id and a missing hall; an unknown or
	 * disabled one before a missing hall; the same kind before the rank cap.
	 */
	//$ gametest_batch SMALL '"qaWonders1114c"'
	@GameTest(template = SMALL, batch = "qaWonders1114c")
	public void theRefusalsComeInTheNotesOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		BlockPos nowhere = helper.absolutePos(new BlockPos(6, 2, 6));
		forgetSites(level, hall);
		hall(helper, at, VillageRanks.Rank.TOWN);
		try {
			WorkplaceConfig.parse("{\"wonders\": false}").apply();
			reads(helper, Wonders.startRefusal(level, nowhere, ours("no_such_wonder")), "Wonders are switched off on this server.", "off, unknown, no hall");
			WorkplaceConfig.parse("{\"disabledWonders\": [\"lighthouse\"]}").apply();
			reads(helper, Wonders.startRefusal(level, nowhere, ours("lighthouse")), "There's no Wonder aliveworkplace:lighthouse on this server.",
				"disabled, no hall");
			new WorkplaceConfig().apply();
			WonderManager manager = WonderManager.get(level);
			WonderSite site = manager.begin(ours("great_library"), hall, null, "", 0);
			manager.finish(site.id(), 1);
			Optional<Component> again = Wonders.capRefusal(level, hall, ours("great_library"));
			helper.assertTrue(again.isPresent() && key(again.get()).equals("wonder.aliveworkplace.refused.same_kind"),
				"a second Library in a full Town should be refused as the same kind first: " + again.map(c -> key(c) + " " + c.getString()).orElse("allowed"));
			allTranslated(helper, again.get(), "the same kind");
			Optional<Component> other = Wonders.capRefusal(level, hall, ours("observatory"));
			helper.assertTrue(other.isPresent() && key(other.get()).equals("wonder.aliveworkplace.refused.cap"),
				"another kind in a full Town should meet the cap: " + other.map(c -> key(c) + " " + c.getString()).orElse("allowed"));
			reads(helper, other, "Wonders: 1 of 1 for a Town.", "the Town's cap");
		} finally {
			new WorkplaceConfig().apply();
			forgetSites(level, hall);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- powers and the switches

	/**
	 * A finished Wonder's powers: present when it's finished; still there while it's in {@code disabledWonders} ("one
	 * already finished keeps its blocks and powers"); gone while {@code wonders} is off; back when it's on again, with
	 * the site still saved.
	 */
	//$ gametest_batch SMALL '"qaWonders1114c"'
	@GameTest(template = SMALL, batch = "qaWonders1114c")
	public void aFinishedWonderKeepsItsPowersWhenDisabledNotWhenOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		forgetSites(level, hall);
		hall(helper, at, VillageRanks.Rank.CITY);
		try {
			new WorkplaceConfig().apply();
			WonderManager manager = WonderManager.get(level);
			WonderSite site = manager.begin(ours("colossus"), hall, null, "", 0);
			helper.assertTrue(Wonders.power(level, hall, "beauty").isEmpty(), "a Colossus still going up already gives its beauty");
			manager.finish(site.id(), 1);
			helper.assertTrue(Wonders.power(level, hall, "beauty").isPresent(), "a finished Colossus gives no beauty");
			helper.assertTrue(Wonders.power(level, hall, "mood").isPresent(), "a finished Colossus gives no mood");
			WorkplaceConfig.parse("{\"disabledWonders\": [\"colossus\"]}").apply();
			helper.assertTrue(Wonders.power(level, hall, "beauty").isPresent(), "a finished Colossus lost its beauty when disabled (the note: it keeps its powers)");
			WorkplaceConfig.parse("{\"wonders\": false}").apply();
			helper.assertTrue(Wonders.power(level, hall, "beauty").isEmpty(), "a finished Colossus kept its beauty while Wonders are off");
			helper.assertTrue(WonderManager.get(level).site(site.id()).isPresent(), "the site was forgotten while Wonders are off");
			new WorkplaceConfig().apply();
			helper.assertTrue(Wonders.power(level, hall, "beauty").isPresent(), "the Colossus' beauty didn't come back with Wonders on");
		} finally {
			new WorkplaceConfig().apply();
			forgetSites(level, hall);
		}
		helper.succeed();
	}

	/**
	 * A damaged Wonder (under {@code wonderStanding}) gives nothing, and a repaired one gives its powers again; the
	 * cache follows both (the note: "rebuilt when a Wonder is finished, damaged or repaired").
	 */
	//$ gametest_batch SMALL '"qaWonders1114c"'
	@GameTest(template = SMALL, batch = "qaWonders1114c")
	public void aDamagedWonderGivesNothingUntilRepaired(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		forgetSites(level, hall);
		hall(helper, at, VillageRanks.Rank.TOWN);
		try {
			WonderManager manager = WonderManager.get(level);
			WonderSite site = manager.begin(ours("great_library"), hall, null, "", 0);
			manager.finish(site.id(), 1);
			helper.assertTrue(Wonders.power(level, hall, "research_tier").isPresent(), "a finished Library gives no second tier");
			manager.setKept(site.id(), new WonderSite.Kept(70, 2L, true, 0L));
			helper.assertTrue(Wonders.power(level, hall, "research_tier").isEmpty(), "a damaged Library (70% standing) still gives its tier");
			manager.setKept(site.id(), new WonderSite.Kept(100, 3L, false, 0L));
			helper.assertTrue(Wonders.power(level, hall, "research_tier").isPresent(), "a repaired Library gives no tier");
			helper.assertTrue(Wonders.power(level, helper.absolutePos(new BlockPos(6, 2, 6)), "research_tier").isEmpty(),
				"a Library's tier reached a spot that isn't its village");
		} finally {
			forgetSites(level, hall);
		}
		helper.succeed();
	}

	/**
	 * {@code wonderLegends}: on, the Colossus asks for the Founder himself (not settled here, so unmet even in a City);
	 * off, the Founder's stand-in (City rank) is asked for instead: met in a City, unmet in a Town, and its line reads
	 * in English.
	 */
	//$ gametest_batch SMALL '"qaWonders1114c"'
	@GameTest(template = SMALL, batch = "qaWonders1114c")
	public void wonderLegendsOffAsksForTheStandIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		VillageHallBlockEntity entity = hall(helper, at, VillageRanks.Rank.CITY);
		Wonder colossus = shipped(helper, "colossus");
		Condition legend = colossus.requirements().get(1);
		try {
			new WorkplaceConfig().apply();
			helper.assertTrue(Wonders.LEGENDS, "wonderLegends should default to on (behind the opened gate)");
			Condition.Progress on = legend.progress(level, hall);
			helper.assertTrue(!on.met(), "with Legends on, a City with no Founder meets the Founder line: " + on.line().getString());
			WorkplaceConfig.parse("{\"wonderLegends\": false}").apply();
			Condition.Progress off = legend.progress(level, hall);
			helper.assertTrue(off.met(), "with wonderLegends off, a City should meet the Founder's stand-in (City rank): " + off.line().getString());
			allTranslated(helper, off.line(), "the stand-in line");
			helper.assertTrue(!off.line().getString().contains("%") && !off.line().getString().contains("rule.aliveworkplace"), "the stand-in reads \""
				+ off.line().getString() + "\"");
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(!legend.progress(level, hall).met(), "with wonderLegends off, a Town meets the Founder's stand-in (City rank)");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- save data and rules

	/**
	 * A site saved with only its first three fields (the note: every field optional with a default) reads with every
	 * later field at its default: stage 1, not dedicated, nothing delivered, no parts, no stone, standing 100 and not
	 * damaged; and a full site with every later field set reads back the same.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaWonders1114c"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaWonders1114c")
	public void anOldSiteReadsWithTheNotesDefaults(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		CompoundTag bare = new CompoundTag();
		CompoundTag one = new CompoundTag();
		one.putIntArray("id", net.minecraft.core.UUIDUtil.uuidToIntArray(UUID.randomUUID()));
		one.putString("wonder", ours("colossus").toString());
		one.putIntArray("hall", new int[] {1, 64, 2});
		ListTag list = new ListTag();
		list.add(one);
		bare.put("sites", list);
		WonderSite s = WonderManager.load(bare, registries).sites().get(0);
		helper.assertTrue(s.stage() == 1 && !s.dedicated() && s.delivered().isEmpty() && s.parts().isEmpty() && s.stone().isEmpty()
			&& s.placement().isEmpty() && s.builder().isEmpty() && s.finished().isEmpty(), "the defaults: " + s);
		helper.assertTrue(s.kept().standing() == 100 && !s.kept().damaged() && s.kept().counted() == 0L, "standing defaults: " + s.kept());
		helper.assertTrue(!s.empowered() && s.goingUp(), "an unfinished old site has powers or isn't going up");

		WonderManager manager = new WonderManager();
		WonderSite site = manager.begin(ours("great_wall"), new BlockPos(5, 70, -9), UUID.randomUUID(), "Jesse", 4);
		UUID part = UUID.randomUUID();
		manager.setParts(site.id(), List.of(part));
		manager.setDedicated(site.id(), true);
		manager.setDelivered(site.id(), List.of(new WonderSite.Handed(0, ResourceLocation.withDefaultNamespace("stone_bricks"), Optional.empty(), 64)));
		manager.finish(site.id(), 9);
		manager.setKept(site.id(), new WonderSite.Kept(85, 10L, true, 9L));
		WonderSite before = manager.site(site.id()).orElseThrow();
		CompoundTag saved = manager.save(new CompoundTag(), registries);
		WonderSite after = WonderManager.load(saved, registries).site(site.id()).orElse(null);
		helper.assertTrue(before.equals(after), "a full site didn't read back the same:\n" + before + "\n" + after);
		helper.succeed();
	}

	/**
	 * The note's new rule types each have their English line, and the lines a player reads for them have every
	 * argument filled: {@code coastal} names its 48 blocks, {@code mod_loaded} names the mod, a {@code legend} with a
	 * stand-in reads as one.
	 */
	//$ gametest_batch SMALL '"qaWonders1114c"'
	@GameTest(template = SMALL, batch = "qaWonders1114c")
	public void theNewRulesReadInEnglish(GameTestHelper helper) {
		for (String k : List.of("rule.aliveworkplace.coastal", "rule.aliveworkplace.mod_loaded", "rule.aliveworkplace.legend")) {
			helper.assertTrue(Language.getInstance().has(k), "no en_us line for " + k);
		}
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		hall(helper, at, VillageRanks.Rank.TOWN);
		ServerLevel level = helper.getLevel();
		Condition coastal = Conditions.parse(JsonParser.parseString("{\"type\": \"coastal\"}").getAsJsonObject());
		Component line = coastal.progress(level, hall).line();
		allTranslated(helper, line, "coastal");
		helper.assertTrue(line.getString().contains("48") && !line.getString().contains("%"), "coastal reads \"" + line.getString() + "\"");
		Condition mod = Conditions.parse(JsonParser.parseString("{\"type\": \"mod_loaded\", \"mod\": \"minecraft\"}").getAsJsonObject());
		Component modLine = mod.progress(level, hall).line();
		allTranslated(helper, modLine, "mod_loaded");
		helper.assertTrue(modLine.getString().endsWith("installed") && modLine.getString().toLowerCase().contains("minecraft") && !modLine.getString().contains("%"),
			"mod_loaded reads \"" + modLine.getString() + "\"");
		// every shipped Wonder's requirement lines, as the Wonders page shows them, read in English
		for (String id : List.of("great_cathedral", "lighthouse", "great_library", "great_wall", "observatory", "hanging_gardens", "colossus")) {
			for (Condition.Progress p : Wonders.requirements(level, hall, shipped(helper, id))) {
				allTranslated(helper, p.line(), id + "'s requirement");
				String text = p.line().getString();
				helper.assertTrue(!text.isBlank() && !text.contains("%") && !text.contains("rule.aliveworkplace"), id + "'s requirement reads \"" + text + "\"");
			}
		}
		helper.succeed();
	}
}
