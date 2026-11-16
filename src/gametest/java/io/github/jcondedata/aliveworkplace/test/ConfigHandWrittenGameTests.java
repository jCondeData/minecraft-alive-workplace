package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * B1323 and B1324: the config file as a person writes it by hand. A switch written as a word ("yes", "ON", " off ") is
 * read as meant and any other word takes the switch's default (Gson read every word but "true" as false, switching the
 * feature off); ids in disabledArcs and disabledWonders are read trimmed and lower-cased (" bandit_king ",
 * "Great_Cathedral" matched nothing). A normal file reads exactly as before, and a file written back reads the same
 * again. Pure logic on temporary folders; a test that applies a config puts the defaults back within its tick.
 */
public class ConfigHandWrittenGameTests implements FabricGameTest {
	private static String file(String body) {
		return "{\"configVersion\": 5, " + body + "}";
	}

	private static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-handwritten");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), json);
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	private static String read(Path dir) {
		try {
			return Files.readString(dir.resolve(WorkplaceConfig.FILE));
		} catch (IOException e) {
			throw new GameTestAssertException("can't read the file back: " + e);
		}
	}

	/** Loads the way the game starts: read, then the file written back complete. */
	private static WorkplaceConfig start(Path dir) {
		WorkplaceConfig config = WorkplaceConfig.load(dir);
		config.save(dir);
		return config;
	}

	private static List<String> switches() {
		List<String> names = new ArrayList<>();
		for (String name : WorkplaceConfig.optionNames()) {
			if (WorkplaceConfig.isSwitch(name)) {
				names.add(name);
			}
		}
		return names;
	}

	/** B1324: every switch written as yes, on, no or off (any case, spaces round it) reads as meant. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySwitchWrittenAsAWordReadsAsMeant(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<String> names = switches();
		helper.assertTrue(names.contains("moddedLibrary") && names.contains("festivals"), "no switches found: " + names);
		for (String name : names) {
			for (String word : List.of("yes", "on", "ON", " Yes ", "True", "no", "off", "OFF", " No ", "False")) {
				boolean meant = List.of("yes", "on", "true").contains(word.trim().toLowerCase(java.util.Locale.ROOT));
				WorkplaceConfig c = WorkplaceConfig.parse(file("\"" + name + "\": \"" + word + "\", \"seasonDays\": 12"));
				if (c.getBoolean(name) != meant || c.seasonDays != 12) {
					problems.add(name + "=\"" + word + "\" read as " + c.getBoolean(name) + " (seasonDays " + c.seasonDays + ")");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " misread: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	/**
	 * B1324: a switch written as a word that says neither (maybe, enabled, an empty word), a number or a list takes that
	 * switch's default, never off, and the rest of the file is kept.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSwitchWrittenAsAnyOtherWordTakesItsDefault(GameTestHelper helper) {
		WorkplaceConfig fresh = new WorkplaceConfig();
		List<String> problems = new ArrayList<>();
		for (String name : List.of("moddedLibrary", "moddedStorageNetworks", "festivals", "questbookKeepEdits", "campRuins")) {
			for (String value : List.of("\"maybe\"", "\"enabled\"", "\"\"", "\"1\"", "0", "2", "[true]")) {
				WorkplaceConfig c = WorkplaceConfig.parse(file("\"" + name + "\": " + value + ", \"maxSiteDistance\": 100"));
				if (c.getBoolean(name) != fresh.getBoolean(name) || c.maxSiteDistance != 100) {
					problems.add(name + "=" + value + " read as " + c.getBoolean(name) + " (default " + fresh.getBoolean(name)
						+ "), maxSiteDistance " + c.maxSiteDistance);
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A file as the game writes it reads exactly as before: every switch true or false as written (also "true" and
	 * "false" in quotes), and the defaults written, read and written again give the same file.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNormalFileReadsAsBefore(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String name : switches()) {
			for (String value : List.of("true", "false", "\"true\"", "\"false\"")) {
				WorkplaceConfig c = WorkplaceConfig.parse(file("\"" + name + "\": " + value));
				if (c.getBoolean(name) != value.contains("true")) {
					problems.add(name + "=" + value + " read as " + c.getBoolean(name));
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " misread: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		try {
			Path first = Files.createTempDirectory("aliveworkplace-handwritten");
			new WorkplaceConfig().save(first);
			String written = read(first);
			Path second = dirWith(written);
			start(second);
			helper.assertTrue(written.equals(read(second)), "the defaults read back and written again changed the file");
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
		helper.succeed();
	}

	/**
	 * Save and reload: a file with words for switches and odd ids is written back as plain true/false and clean ids, and
	 * the second start reads exactly what the first wrote.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHandWrittenFileIsWrittenBackPlainAndReadsTheSameAgain(GameTestHelper helper) {
		Path dir = dirWith(file("\"moddedLibrary\": \"off\", \"festivals\": \"No\", \"campRuins\": \" yes \", \"villageRaids\": \"maybe\","
			+ " \"raiderCultures\": {\"bandits\": \"OFF\", \"monsters\": \"yes\"}, \"disasters\": {\"pack:flood\": \"no\"},"
			+ " \"disabledArcs\": [\" Bandit_King \", \"bandit_king\", \"\"], \"disabledWonders\": [\"LIGHTHOUSE\", \"Pack:Arch \"]"));
		WorkplaceConfig first = start(dir);
		String written = read(dir);
		WorkplaceConfig second = start(dir);
		helper.assertTrue(written.equals(read(dir)), "the second start wrote a different file than the first");
		WorkplaceConfig fresh = new WorkplaceConfig();
		for (WorkplaceConfig c : List.of(first, second)) {
			helper.assertTrue(!c.moddedLibrary && !c.festivals && c.campRuins && c.villageRaids == fresh.villageRaids,
				"switches: moddedLibrary " + c.moddedLibrary + ", festivals " + c.festivals + ", campRuins " + c.campRuins + ", villageRaids " + c.villageRaids);
			helper.assertTrue(Boolean.FALSE.equals(c.raiderCultures.get("bandits")) && Boolean.TRUE.equals(c.raiderCultures.get("monsters"))
				&& Boolean.FALSE.equals(c.disasters.get("pack:flood")), "map switches: " + c.raiderCultures + ", " + c.disasters);
			helper.assertTrue(c.disabledArcs.equals(List.of("bandit_king")) && c.disabledWonders.equals(List.of("lighthouse", "pack:arch")),
				"ids: " + c.disabledArcs + ", " + c.disabledWonders);
		}
		helper.assertTrue(written.contains("\"moddedLibrary\": false") && written.contains("\"campRuins\": true") && !written.contains("\"off\""),
			"switches weren't written back as plain true or false");
		helper.succeed();
	}

	/** B1323: an arc id with spaces round it or capitals in it is switched off, as is a Wonder id with capitals. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void idsWithSpacesOrCapitalsInTheDisabledListsAreOff(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		try {
			for (String arc : List.of("\" bandit_king \"", "\"Bandit_King\"", "\"ALIVEWORKPLACE:BANDIT_KING\"")) {
				WorkplaceConfig.parse(file("\"disabledArcs\": [" + arc + "]")).apply();
				if (!Arcs.disabled(AliveWorkplace.id("bandit_king"))) {
					problems.add("disabledArcs [" + arc + "] leaves the Bandit King on");
				}
			}
			WorkplaceConfig.parse(file("\"disabledWonders\": [\"Great_Cathedral\", \"LIGHTHOUSE\", \" Pack:Arch \"]")).apply();
			if (!Wonders.DISABLED.equals(java.util.Set.of(AliveWorkplace.id("great_cathedral"), AliveWorkplace.id("lighthouse"),
				ResourceLocation.parse("pack:arch")))) {
				problems.add("disabledWonders [Great_Cathedral, LIGHTHOUSE, \" Pack:Arch \"] switched off " + Wonders.DISABLED);
			}
			// Written as ids are, nothing changes: a pack's arc stays its own, and another arc stays on.
			WorkplaceConfig.parse(file("\"disabledArcs\": [\"pack:story\"], \"disabledWonders\": [\"great_cathedral\"]")).apply();
			if (!Arcs.disabled(ResourceLocation.parse("pack:story")) || Arcs.disabled(AliveWorkplace.id("bandit_king"))
				|| !Wonders.DISABLED.equals(java.util.Set.of(AliveWorkplace.id("great_cathedral")))) {
				problems.add("plain ids: pack:story off " + Arcs.disabled(ResourceLocation.parse("pack:story")) + ", bandit_king off "
					+ Arcs.disabled(AliveWorkplace.id("bandit_king")) + ", wonders off " + Wonders.DISABLED);
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
