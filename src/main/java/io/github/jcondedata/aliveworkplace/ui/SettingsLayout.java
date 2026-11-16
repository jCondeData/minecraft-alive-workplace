package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Where the Settings screen puts its parts in Copperline (ROADMAP 44.4, docs/design/copperline/boards/Settings.png): the
 * shell from {@link CopperLayout} (plaque, lintel with the close X, the stage, the whisper line); the rail of sections
 * (Work, Jobs, Builders, Village, Villagers, Defense, Realms, Pokémon; a two-column icon grid at 320x180); on the stage
 * the open section's settings in a scrolling list (the numbers under their heading, then the switches), each row a label
 * and its control at the right: a toggle with its On or Off word, a [-] value [+] field for a number of 20 steps or fewer,
 * else a slider and its value; and at the stage's foot "Reset this page" and Done (the copper button). Pure arithmetic in
 * GUI pixels, so the GameTests check it at GUI scale 2 and 3 and change settings through {@link SettingsEditor}. Every box
 * is {x, y, w, h}.
 */
public final class SettingsLayout {
	/** The sections in the rail's order; {@link #OTHER} holds any setting not sorted into one (shown only then). */
	public static final List<String> SECTIONS = List.of("work", "jobs", "builder", "village", "people", "defense", "realms", "pokemon", "other");
	public static final String OTHER = "other";

	private static final Map<String, String> SECTION_OF = sort(
		"work", "supplyRadius quarryReach fieldReach treeFarmReach orchardAreaReach berryPlotReach lumberjackRadius orchardRadius fisherRadius"
			+ " explorerRange postmanRange maxWorkersPerVillage randomStationJobs minerCraftsPickaxe quarryBuild quarryRegrowth fishermanCap"
			+ " fishermenPerVillage villagersPerFisherman workerPathRange keepVillagesWorking maxWorkPace workHorns",
		"jobs", "firewardens wellKeepers physicians cuisineDishes beanFarms furnicrafters mechanics machineWork vintners tailors printers"
			+ " campCooks jewellers",
		"builder", "maxSiteDistance movers moveBuildings builderPaths builderRepairs repairAsk blueprintUnlocks createBuilds createSchematics"
			+ " steward stewardMaxOpenBuilds stewardSelfRun stewardRoads caravanRoads caravanRoadReach stewardWalls stewardRenewal"
			+ " builderScaffolding furnishedBuilds moddedStorageNetworks moddedLibrary",
		"village", "villageWaystones villageRadius villageHallRadius villageGrowthCap marketDays villageTrains festivals villageTreasury"
			+ " treasuryPerWorker villageQuests questbookKeepEdits villageProtection villageEdicts edictMinDays villageBanners harvestIdols"
			+ " guilds guildsPerRank villageEconomy visibleCaravans dollarsPerEmerald seasonDays weeklyLeaderboard leaderboardHour wonders"
			+ " wonderLegends wonderServerLimit wonderCrew wonderStanding",
		"people", "villagerNames villagerTraits villagerSickness villagerMoods legends legendNeeds legendSites strangeMoods giftedChance"
			+ " villagerChatter villagerCouples friendship heartEvents personalRequests reputation titlesInChat cradles tonics villageClasses"
			+ " classRiseDays classFallDays classGraceDays villagerAges villagerElderDays villageMourning elderPassing agelessElders"
			+ " classOutfits elderRetirement familyNames",
		"defense", "guardRadius villageRaids banditCamps threatLevel forceThreatSpawns forceVillageSpawns gravesToGraveyard campRuins sieges"
			+ " siegeDamage storyArcs bountyBoard rogueBounties arcCooldownDays arcsAtOnce lostCaravanChance",
		"realms", "colonies colonyCooldownDays coloniesPerVillage realms realmMaxMembers realmRelief tradePacts alliances feuds realmMap feudWars",
		"pokemon", "partnerRadius partnerShows nurseHealingMachine denHelpers denHelperPrice villageMegas berryBreeders habitatKeepers"
			+ " daycareKeepers gemGrowers habitatSightings villageHabitats pokemonVillageHouses festivalCup cupEveryFestivals");

	public static final int BUTTON_H = 14;
	/** A list row (a setting or a heading). */
	public static final int ROW = 14;
	public static final int SCROLLBAR = 6;
	public static final int TOGGLE_W = 20;
	/** The number field's well, between its [-] and [+]. */
	public static final int FIELD_W = 30;
	/** A slider's value well (wide enough for "No Limit"). */
	public static final int VALUE_W = 50;
	public static final int SLIDER_W = 64;
	public static final int SLIDER_W_COMPACT = 40;
	/** A number of this many steps or fewer gets [-] and [+]; a bigger one a slider. */
	public static final int SMALL_SPAN = 20;
	/** The rail's widest label before it is cut. */
	public static final int RAIL_LABEL_MAX = 60;

	private static Map<String, String> sort(String... sectionNames) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < sectionNames.length; i += 2) {
			for (String name : sectionNames[i + 1].split(" ")) {
				map.put(name, sectionNames[i]);
			}
		}
		return Collections.unmodifiableMap(map);
	}

	/** The section a setting is shown in. */
	public static String sectionOf(String option) {
		return SECTION_OF.getOrDefault(option, OTHER);
	}

	/** The settings of {@code names} in a section, the numbers first, each group in the file's order. */
	public static List<String> optionsIn(String section, List<String> names, Predicate<String> isSwitch) {
		List<String> out = new ArrayList<>();
		for (boolean switches : new boolean[]{false, true}) {
			for (String name : names) {
				if (sectionOf(name).equals(section) && isSwitch.test(name) == switches) {
					out.add(name);
				}
			}
		}
		return out;
	}

	/** The sections that have a setting among {@code names}, in order. */
	public static List<String> shownSections(List<String> names) {
		return SECTIONS.stream().filter(s -> names.stream().anyMatch(n -> sectionOf(n).equals(s))).toList();
	}

	/** A list row: a setting, or a heading ("numbers" or "switches"). */
	public record Row(String option, String heading) {
		public boolean isHeading() {
			return heading != null;
		}
	}

	/** A section's rows: the numbers' heading and its numbers, the switches' heading and its switches (a heading only over some). */
	public static List<Row> rows(List<String> options, Predicate<String> isSwitch) {
		List<Row> rows = new ArrayList<>();
		for (boolean switches : new boolean[]{false, true}) {
			List<String> group = options.stream().filter(n -> isSwitch.test(n) == switches).toList();
			if (!group.isEmpty()) {
				rows.add(new Row(null, switches ? "switches" : "numbers"));
				group.forEach(n -> rows.add(new Row(n, null)));
			}
		}
		return rows;
	}

	/** The screen's boxes: the rail (its heading over {@code railBody}) and a {@code railEntries} box per section; the {@code list}; Reset and Done. */
	public record Screen(int[] win, boolean compact, int[] plaque, int[] close, int[] lintel, int[] rail, int[] railBody, int[][] railEntries,
		int[] stage, int[] list, int[] reset, int[] done, int[] whisper) {

		/** How many rows the list shows. */
		public int listRows() {
			return Math.max(1, list[3] / ROW);
		}
	}

	/** The screen {@code sw x sh}: a title {@code titleW} wide, the rail's widest label, {@code sections} rail entries, Reset's and Done's widths. */
	public static Screen screen(int sw, int sh, int titleW, int railLabelW, int sections, int resetW, int doneW) {
		int[] win = CopperLayout.window(sw, sh);
		boolean compact = CopperLayout.compact(sw, sh);
		int[] plaque = CopperLayout.plaque(win, titleW);
		int[] close = CopperLayout.close(win);
		int[] lintel = CopperLayout.lintel(win, plaque);
		int railW = CopperLayout.railWidth(win, Math.min(railLabelW, RAIL_LABEL_MAX), compact);
		int[] rail = CopperLayout.rail(win, railW);
		int[] railBody = compact ? rail : new int[]{rail[0], rail[1] + 12, rail[2], rail[3] - 12};
		int[][] entries = CopperLayout.railEntries(railBody, sections, compact);
		int[] stage = CopperLayout.stage(win, railW);
		int footY = stage[1] + stage[3] - BUTTON_H;
		int dw = Math.min(doneW, stage[2] / 3);
		int[] done = {stage[0] + stage[2] - dw, footY, dw, BUTTON_H};
		int[] reset = {stage[0], footY, Math.min(resetW, done[0] - 6 - stage[0]), BUTTON_H};
		int[] list = {stage[0], stage[1], stage[2], footY - 4 - stage[1]};
		return new Screen(win, compact, plaque, close, lintel, rail, railBody, entries, stage, list, reset, done, CopperLayout.whisper(win));
	}

	/** The {@code visible}th row shown (0 = the top), left of the scroll bar. */
	public static int[] row(Screen s, int visible) {
		int[] l = s.list();
		return new int[]{l[0], l[1] + visible * ROW, l[2] - SCROLLBAR, ROW};
	}

	/** The scroll bar's box at the list's right. */
	public static int[] scrollbar(Screen s) {
		int[] l = s.list();
		return new int[]{l[0] + l[2] - 4, l[1], 4, s.listRows() * ROW};
	}

	public static boolean small(WorkplaceConfig.Range range) {
		return range.max() - range.min() <= SMALL_SPAN;
	}

	/** A switch's toggle at the row's right; its On or Off word goes left of it. */
	public static int[] toggle(int[] row) {
		return new int[]{row[0] + row[2] - 2 - TOGGLE_W, row[1] + 2, TOGGLE_W, 10};
	}

	/** A small number's [-], value well and [+] (as {@code CopperStyle.number} draws them) at the row's right. */
	public static int[][] number(int[] row) {
		int x = row[0] + row[2] - 2 - (30 + FIELD_W);
		int y = row[1] + 1;
		return new int[][]{{x, y, 12, 12}, {x + 15, y, FIELD_W, 12}, {x + 18 + FIELD_W, y, 12, 12}};
	}

	/** A big number's value well at the row's right. */
	public static int[] value(int[] row) {
		return new int[]{row[0] + row[2] - 2 - VALUE_W, row[1] + 1, VALUE_W, 12};
	}

	/** A big number's slider, left of its value well. */
	public static int[] slider(int[] row, boolean compact) {
		int w = compact ? SLIDER_W_COMPACT : SLIDER_W;
		return new int[]{value(row)[0] - 4 - w, row[1] + 2, w, 10};
	}

	/** The leftmost pixel of a row's control (for a switch, its On or Off word {@code wordW} wide). */
	public static int controlLeft(int[] row, boolean isSwitch, boolean small, boolean compact, int wordW) {
		if (isSwitch) {
			return toggle(row)[0] - 4 - wordW;
		}
		return small ? number(row)[0][0] : slider(row, compact)[0];
	}

	/** Where a row's label starts. */
	public static int labelX(int[] row) {
		return row[0] + 5;
	}

	/** The room a row's label has before its control. */
	public static int labelRoom(int[] row, boolean isSwitch, boolean small, boolean compact, int wordW) {
		return controlLeft(row, isSwitch, small, compact, wordW) - 6 - labelX(row);
	}

	/** The value a slider gives at {@code mx}: the handle's middle under the mouse, clamped to the range. */
	public static int valueAt(WorkplaceConfig.Range range, int[] slider, double mx) {
		double f = (mx - slider[0] - 2) / Math.max(1, slider[2] - 4);
		f = Math.max(0, Math.min(1, f));
		return range.min() + (int) Math.round(f * (range.max() - range.min()));
	}

	/** Where a value puts the slider's handle, 0 to 1. */
	public static float fraction(WorkplaceConfig.Range range, int value) {
		return range.max() == range.min() ? 0f : (value - range.min()) / (float) (range.max() - range.min());
	}

	public static int maxScroll(Screen s, int rows) {
		return Math.max(0, rows - s.listRows());
	}

	/** The row (an index into the section's rows) under the mouse, given the rows scrolled past, or -1. */
	public static int rowAt(Screen s, int scroll, int count, double mx, double my) {
		int[] l = s.list();
		if (!CopperLayout.inside(new int[]{l[0], l[1], l[2] - SCROLLBAR, s.listRows() * ROW}, mx, my)) {
			return -1;
		}
		int i = scroll + (int) ((my - l[1]) / ROW);
		return i >= 0 && i < count ? i : -1;
	}

	private SettingsLayout() {
	}
}
