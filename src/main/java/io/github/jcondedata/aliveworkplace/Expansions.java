package io.github.jcondedata.aliveworkplace;

/**
 * Which expansions are finished (owner, 2026-10-05, ROADMAP B76): an expansion's config switches stay off until every
 * item of its milestone is done, whatever a config file says, so a half-built expansion never reaches a player. The
 * release that completes a milestone flips its flag here to {@code true}; nothing else changes, and its switches then
 * default on and obey the file again.
 *
 * <p>A config written by 0.139.0 holds {@code true} for these switches: {@link WorkplaceConfig#apply()} ANDs each with
 * its milestone's flag, so those files leave the features off too. GameTests test the unfinished work, so the gates
 * are open while they run ({@link #openForTests}); a test of the gate itself closes them for a moment.
 */
public final class Expansions {
	/** Milestone 27, Villages that build themselves (1.1): {@code steward}, {@code stewardSelfRun}, {@code stewardMaxOpenBuilds}, {@code stewardRoads}, {@code caravanRoads}, {@code stewardWalls}, {@code stewardRenewal}. */
	public static final boolean M27 = false;
	/**
	 * Milestone 28, Pokémon and villagers, together (1.2): {@code partnerShows}, {@code nurseHealingMachine},
	 * {@code berryBreeders}, {@code campCooks}, {@code habitatKeepers}, {@code habitatSightings}, {@code daycareKeepers},
	 * {@code gemGrowers}, {@code villageHabitats}, {@code pokemonVillageHouses},
	 * {@code festivalCup}, {@code cupEveryFestivals}.
	 */
	public static final boolean M28 = false;
	/** Milestone 29, Legends (1.3): {@code legends}, {@code legendNeeds}, {@code legendSites}, {@code strangeMoods}, {@code giftedChance}. */
	public static final boolean M29 = false;
	/**
	 * Milestone 30, Edicts and civic items (1.4): {@code villageEdicts}, {@code edictMinDays}, {@code workHorns},
	 * {@code villageBanners}, {@code cradles}, {@code harvestIdols}, {@code tonics}.
	 */
	public static final boolean M30 = false;
	/**
	 * Milestone 31, Quests become stories (1.5). The daily quests ({@code villageQuests}) aren't behind it; the journal's
	 * tabs, Track and {@code /workplace quests} are (design note M31, "The expansion gate"), and so is {@code friendship}.
	 * tabs, Track and {@code /workplace quests} are (design note M31, "The expansion gate"), and {@code storyArcs},
	 * {@code arcCooldownDays}, {@code arcsAtOnce} (31.4).
	 */
	public static final boolean M31 = false;
	/** Milestone 34, Classes and luxuries (1.8): {@code villageClasses}, {@code classRiseDays}, {@code classFallDays}, {@code vintners}, {@code tailors}, {@code villagerAges}, {@code villagerElderDays}, {@code elderPassing}, {@code agelessElders}. */
	public static final boolean M34 = false;

	/**
	 * Open in GameTests (they test the unfinished expansions) and in the screenshot client (the nightly showcase films
	 * them for review); {@code ExpansionGateGameTests} closes it for a moment.
	 */
	public static boolean openForTests = System.getProperty("fabric-api.gametest") != null || Boolean.getBoolean("aliveworkplace.shots");

	private Expansions() {
	}

	/** Whether an expansion whose milestone flag is {@code complete} may run (its switches' default, and their gate). */
	public static boolean on(boolean complete) {
		return complete || openForTests;
	}

	/** The milestone flag of a config option that belongs to an unfinished-or-finished expansion, or null for one that doesn't. */
	static Boolean milestoneOf(String option) {
		return switch (option) {
			case "steward", "stewardSelfRun", "stewardMaxOpenBuilds", "stewardRoads", "caravanRoads", "caravanRoadReach",
				"stewardWalls", "stewardRenewal" -> M27;
			case "partnerShows", "nurseHealingMachine", "berryBreeders", "campCooks", "habitatKeepers", "habitatSightings",
				"daycareKeepers", "gemGrowers", "villageHabitats", "pokemonVillageHouses", "festivalCup", "cupEveryFestivals" -> M28;
			case "legends", "legendNeeds", "legendSites", "strangeMoods", "giftedChance" -> M29;
			case "villageEdicts", "edictMinDays", "workHorns", "villageBanners", "cradles", "harvestIdols", "tonics", "guilds",
				"guildsPerRank" -> M30;
			case "friendship" -> M31;
			case "storyArcs", "arcCooldownDays", "arcsAtOnce" -> M31;
			case "villageClasses", "classRiseDays", "classFallDays", "vintners", "tailors", "villagerAges", "villagerElderDays",
				"elderPassing", "agelessElders" -> M34;
			default -> null;
		};
	}

	/** Whether a config option is in effect now: false for one of an expansion that isn't finished (it's hidden from the screen). */
	public static boolean optionLive(String option) {
		Boolean milestone = milestoneOf(option);
		return milestone == null || on(milestone);
	}
}
