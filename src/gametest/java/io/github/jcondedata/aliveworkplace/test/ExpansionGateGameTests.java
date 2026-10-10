package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.habitat.VillageHabitats;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.people.Tonics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * ROADMAP B76: the expansions still being built (M27-M30) stay off for players until the release that finishes them,
 * even with a config 0.139.0 wrote with their switches on. GameTests open the gates; these tests close them for a
 * moment (synchronously, so no other test sees it) and put the defaults back after.
 */
public class ExpansionGateGameTests implements FabricGameTest {
	/** Every switch of an unfinished expansion (M27 steward; M28 Pokémon jobs and shows; M29 Legends; M30 civic items; M31 friendship; M33 the economy; M34 classes). */
	static final List<String> GATED_SWITCHES = List.of("steward", "stewardSelfRun", "stewardRoads", "caravanRoads", "stewardWalls", "stewardRenewal",
		"partnerShows", "nurseHealingMachine", "berryBreeders", "campCooks", "habitatKeepers", "habitatSightings",
		"daycareKeepers", "gemGrowers", "jewellers", "villageHabitats", "pokemonVillageHouses", "festivalCup",
		"legends", "legendNeeds", "legendSites", "strangeMoods",
		"villageEdicts", "workHorns", "villageBanners", "cradles", "harvestIdols", "tonics", "guilds",
		"villageEconomy", "visibleCaravans", "villageClasses", "friendship", "heartEvents", "storyArcs", "vintners", "tailors", "printers", "villagerAges", "elderPassing", "agelessElders");
	/** The numbers that belong to them (hidden from the screen with them). */
	static final List<String> GATED_NUMBERS = List.of("stewardMaxOpenBuilds", "caravanRoadReach", "giftedChance", "edictMinDays", "guildsPerRank",
		"classRiseDays", "classFallDays", "cupEveryFestivals", "arcCooldownDays", "arcsAtOnce", "villagerElderDays");

	/** A config file as 0.139.0 wrote it: every switch on. */
	static String oldConfig() {
		StringBuilder json = new StringBuilder("{\"configVersion\": 2, \"villageProtection\": true, \"giftedChance\": 30");
		for (String name : GATED_SWITCHES) {
			json.append(", \"").append(name).append("\": true");
		}
		return json.append('}').toString();
	}

	/** What each gated switch turns on in the game, read after {@link WorkplaceConfig#apply()}. */
	static Map<String, BooleanSupplier> effects() {
		Map<String, BooleanSupplier> map = new LinkedHashMap<>();
		map.put("Stewards", () -> Stewards.ENABLED);
		map.put("StewardDesk.SELF_RUN", () -> StewardDesk.SELF_RUN);
		// Roads and Walls are also off in every GameTest (their own tests turn them on), so only the caravan roads show here.
		map.put("CaravanRoads", () -> io.github.jcondedata.aliveworkplace.city.CaravanRoads.ENABLED);
		map.put("Nurses.HEALING_MACHINE", () -> Nurses.HEALING_MACHINE);
		map.put("BerryBreeders", () -> BerryBreeders.ENABLED);
		map.put("CampCooks", () -> CampCooks.ENABLED);
		map.put("HabitatKeepers", () -> HabitatKeepers.ENABLED);
		map.put("HabitatKeepers.SIGHTINGS", () -> HabitatKeepers.SIGHTINGS);
		map.put("DaycareKeepers", () -> DaycareKeepers.ENABLED);
		map.put("GemGrowers", () -> GemGrowers.ENABLED);
		map.put("Jewellers", () -> io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED);
		map.put("VillageHabitats", () -> VillageHabitats.ENABLED);
		map.put("Cups", () -> io.github.jcondedata.aliveworkplace.cup.Cups.ENABLED);
		map.put("VillageHouses.POKEMON_JOBS", () -> io.github.jcondedata.aliveworkplace.world.VillageHouses.POKEMON_JOBS);
		map.put("Legends", () -> Legends.ENABLED);
		map.put("LegendSites", () -> LegendSites.ENABLED);
		map.put("Gifted.CHANCE", () -> Gifted.CHANCE > 0);
		map.put("Edicts", () -> Edicts.ENABLED);
		map.put("WorkHorn", () -> WorkHorn.ENABLED);
		map.put("VillageBanners", () -> VillageBanners.ENABLED);
		map.put("Cradles", () -> Cradles.ENABLED);
		map.put("HarvestIdols", () -> HarvestIdols.ENABLED);
		map.put("Tonics", () -> Tonics.ENABLED);
		map.put("Guilds", () -> io.github.jcondedata.aliveworkplace.hall.Guilds.ENABLED);
		map.put("Economy", () -> io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED);
		map.put("Vintners", () -> io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED);
		map.put("Tailors", () -> io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED);
		map.put("Printers", () -> io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED);
		map.put("LifeStages.AGES", () -> io.github.jcondedata.aliveworkplace.people.LifeStages.AGES);
		map.put("LifeStages.PASSING", () -> io.github.jcondedata.aliveworkplace.people.LifeStages.PASSING);
		map.put("LifeStages.AGELESS", () -> io.github.jcondedata.aliveworkplace.people.LifeStages.AGELESS);
		map.put("Friendship", () -> io.github.jcondedata.aliveworkplace.story.Friendship.ENABLED);
		map.put("HeartEvents", () -> io.github.jcondedata.aliveworkplace.story.HeartEvents.ENABLED);
		map.put("Arcs", () -> io.github.jcondedata.aliveworkplace.story.Arcs.ENABLED);
		return map;
	}

	/** Runs {@code check} as a player's game would: gates closed. Then the gates reopen and the defaults are applied. */
	static void asPlayer(Runnable check) {
		boolean open = Expansions.openForTests;
		Expansions.openForTests = false;
		try {
			check.run();
		} finally {
			Expansions.openForTests = open;
			new WorkplaceConfig().apply();
		}
	}

	/** The owner's server: a 0.139.0 file with every switch on still leaves the unfinished expansions off. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void oldConfigWithSwitchesOnLeavesThemOff(GameTestHelper helper) {
		List<String> on = new ArrayList<>();
		boolean[] protection = new boolean[1];
		asPlayer(() -> {
			WorkplaceConfig config = WorkplaceConfig.parse(oldConfig());
			config.apply();
			effects().forEach((name, effect) -> {
				if (effect.getAsBoolean()) {
					on.add(name);
				}
			});
			protection[0] = VillageProtection.ENABLED;
		});
		helper.assertTrue(!Expansions.M27 && !Expansions.M28 && !Expansions.M29 && !Expansions.M30 && !Expansions.M31 && !Expansions.M33 && !Expansions.M34,
			"a milestone was marked complete: move its switches out of this test");
		helper.assertTrue(on.isEmpty(), "a 0.139.0 config turned on unfinished expansions: " + on);
		helper.assertTrue(protection[0], "a finished feature's switch (villageProtection) should still work");
		helper.succeed();
	}

	/** New configs (no file, or a file without the switch) have them off, and the settings screen doesn't list them. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void unfinishedSwitchesDefaultOffAndHidden(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		asPlayer(() -> {
			WorkplaceConfig fresh = new WorkplaceConfig();
			WorkplaceConfig empty = WorkplaceConfig.parse("{}");
			for (String name : GATED_SWITCHES) {
				if (fresh.getBoolean(name) || empty.getBoolean(name)) {
					problems.add(name + " defaults on");
				}
			}
			for (String name : List.of("villageProtection", "festivals", "builderRepairs", "villagerMoods", "builderPaths")) {
				if (!fresh.getBoolean(name)) {
					problems.add("finished " + name + " defaults off");
				}
			}
			List<String> screen = WorkplaceConfig.optionNames();
			for (String name : GATED_SWITCHES) {
				if (screen.contains(name)) {
					problems.add(name + " is on the settings screen");
				}
			}
			for (String name : GATED_NUMBERS) {
				if (screen.contains(name)) {
					problems.add(name + " is on the settings screen");
				}
			}
			if (!screen.contains("villageProtection") || !screen.contains("maxWorkPace") || !screen.contains("seasonDays")) {
				problems.add("a finished option left the screen: " + screen);
			}
			if (screen.size() != 86 - GATED_SWITCHES.size() - GATED_NUMBERS.size()) {
				problems.add("expected " + (86 - GATED_SWITCHES.size() - GATED_NUMBERS.size()) + " options on the screen, found "
					+ screen.size());
			}
			empty.setBoolean("tonics", true);
			empty.apply();
			if (Tonics.ENABLED) {
				problems.add("a player turning tonics on before 1.4 turned them on");
			}
		});
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** GameTests still turn the unfinished expansions on (they test them): defaults on, and the file's switches obeyed. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void testsCanTurnThemOn(GameTestHelper helper) {
		helper.assertTrue(Expansions.openForTests, "the gates should be open in GameTests");
		List<String> off = new ArrayList<>();
		List<String> stillOn = new ArrayList<>();
		try {
			WorkplaceConfig.parse(oldConfig()).apply();
			effects().forEach((name, effect) -> {
				if (!effect.getAsBoolean()) {
					off.add(name);
				}
			});
			StringBuilder json = new StringBuilder("{\"giftedChance\": 0");
			for (String name : GATED_SWITCHES) {
				json.append(", \"").append(name).append("\": false");
			}
			WorkplaceConfig.parse(json.append('}').toString()).apply();
			effects().forEach((name, effect) -> {
				if (effect.getAsBoolean()) {
					stillOn.add(name);
				}
			});
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(off.isEmpty(), "switched on in a GameTest, still off: " + off);
		helper.assertTrue(stillOn.isEmpty(), "switched off in a GameTest, still on: " + stillOn);
		for (String name : GATED_SWITCHES) {
			helper.assertTrue(new WorkplaceConfig().getBoolean(name), name + " should default on in GameTests");
		}
		helper.assertTrue(WorkplaceConfig.optionNames().size() == 86, "every option on the screen in GameTests");
		helper.succeed();
	}
}
