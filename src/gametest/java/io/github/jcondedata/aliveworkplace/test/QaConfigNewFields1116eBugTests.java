package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;

/**
 * QA (lane E, 2026-11-16), bugs found testing the newer config settings at their extremes (see
 * {@link QaConfigNewFields1116eGameTests}). Each test fails on main today, for the reason in its comment. Pure logic:
 * every test puts the default config back within its tick.
 */
public class QaConfigNewFields1116eBugTests implements FabricGameTest {
	private static String file(String body) {
		return "{\"configVersion\": 5, " + body + "}";
	}

	/**
	 * disabledArcs " bandit_king " (a space typed round the name, as easily done in a hand edit) should keep the Bandit
	 * King off, as disabledWonders " great_cathedral " keeps the Cathedral off and furnitureMods trims its names. Today
	 * the arc list is matched as written: the space makes the entry match nothing, and the story still starts, with no
	 * word in the log.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aDisabledArcWithSpacesRoundItsNameIsStillOff(GameTestHelper helper) {
		boolean wonderOff;
		boolean arcOff;
		try {
			WorkplaceConfig.parse(file("\"disabledWonders\": [\" great_cathedral \"], \"disabledArcs\": [\" bandit_king \"]")).apply();
			wonderOff = Wonders.DISABLED.contains(AliveWorkplace.id("great_cathedral"));
			arcOff = Arcs.disabled(AliveWorkplace.id("bandit_king"));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(wonderOff, "(control) disabledWonders \" great_cathedral \" didn't switch the Cathedral off");
		helper.assertTrue(arcOff, "disabledArcs [\" bandit_king \"] leaves the Bandit King on: the spaces make the entry match nothing,"
			+ " while disabledWonders [\" great_cathedral \"] does switch its Wonder off");
		helper.succeed();
	}

	/**
	 * An id written with capitals in disabledWonders ("Great_Cathedral") or disabledArcs ("Bandit_King") should switch
	 * that Wonder or story off, as colonyRank, leaderboardDay and furnitureMods read any case. Today both lists drop such
	 * an entry silently (a Wonder id with a capital isn't a valid id; an arc entry is compared as written), so the Wonder
	 * can still be started and the story still runs.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void anIdWrittenWithCapitalsInTheDisabledListsIsStillOff(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		try {
			WorkplaceConfig.parse(file("\"disabledWonders\": [\"Great_Cathedral\", \"LIGHTHOUSE\"], \"disabledArcs\": [\"Bandit_King\"]")).apply();
			if (!Wonders.DISABLED.contains(AliveWorkplace.id("great_cathedral")) || !Wonders.DISABLED.contains(AliveWorkplace.id("lighthouse"))) {
				problems.add("disabledWonders [\"Great_Cathedral\", \"LIGHTHOUSE\"] switched off " + Wonders.DISABLED);
			}
			if (!Arcs.disabled(AliveWorkplace.id("bandit_king"))) {
				problems.add("disabledArcs [\"Bandit_King\"] leaves the Bandit King on");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A switch written by hand as a word other than true or false ("moddedLibrary": "yes", "on") is a value of the wrong
	 * kind: like 0, a list or an object it should take its default (on from 2.1), as the config's class comment and
	 * config-switches.md promise for a value that can't be read. Today Gson reads any word but "true" as false, so "yes"
	 * switches the modded library off: the opposite of what the owner wrote, with no warning in the log.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aSwitchWrittenAsYesOrOnIsNotReadAsOff(GameTestHelper helper) {
		WorkplaceConfig fresh = new WorkplaceConfig();
		List<String> problems = new ArrayList<>();
		for (String name : List.of("moddedLibrary", "moddedStorageNetworks")) {
			for (String word : List.of("\"yes\"", "\"on\"", "\"ON\"")) {
				WorkplaceConfig c = WorkplaceConfig.parse(file("\"" + name + "\": " + word));
				if (c.getBoolean(name) != fresh.getBoolean(name)) {
					problems.add(name + "=" + word + " read as " + c.getBoolean(name) + " (default " + fresh.getBoolean(name) + ")");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A slider whose tooltip says "0: no limit" should read so at 0, as Workers per Village does ("No Limit", its
	 * {@code .zero} label). Villages per Wonder (wonderServerLimit) has none: at 0 the settings screen reads
	 * "Villages per Wonder: 0", which says no village may build a Wonder, the opposite of what 0 does.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aNoLimitZeroReadsNoLimitOnTheSlider(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (String name : WorkplaceConfig.RANGES.keySet()) {
			String key = "aliveworkplace.config." + name;
			String tip = lang.getOrDefault(key + ".tooltip", "");
			if (WorkplaceConfig.RANGES.get(name).min() == 0 && tip.toLowerCase(java.util.Locale.ROOT).contains("0: no limit") && !lang.has(key + ".zero")) {
				missing.add(name + " reads \"" + lang.getOrDefault(key, name) + ": 0\"");
			}
		}
		helper.assertTrue(missing.isEmpty(), "sliders whose 0 means no limit but read 0: " + missing);
		helper.succeed();
	}

	/**
	 * Lost Caravans' slider is "Lost Caravans: 1 in" a number, and its tooltip says 0 means none. At 0 it reads
	 * "Lost Caravans: 1 in: 0" (the screen's "label: value"), which reads as every caravan (or nonsense), not "none": it needs its own 0 label as
	 * Workers per Village has ("No Limit"), e.g. "Never".
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void lostCaravansAtZeroDoesNotReadOneInZero(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String key = "aliveworkplace.config.lostCaravanChance";
		helper.assertTrue(WorkplaceConfig.RANGES.get("lostCaravanChance").min() == 0, "(control) lostCaravanChance can't be 0 any more");
		helper.assertTrue(lang.has(key + ".zero"), "at 0 the slider reads \"" + lang.getOrDefault(key, key) + ": 0\" though 0 means no caravan goes missing");
		helper.succeed();
	}
}
