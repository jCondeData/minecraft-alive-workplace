package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Lane D QA (lane-d-1117-0408), B1324 on the threat side, from its spec and the config page: "one on/off switch per raider
 * culture ... a culture set to false never raids or makes camp", "one [disaster] set to false never starts", and a
 * switch written as a word (yes/on/no/off, any case, spaces round it) "is read as meant". These tests write the
 * raiderCultures and disasters maps as a person writes them by hand and check, through the config's own apply (the
 * same call the game makes at start), that the culture or disaster is really off or on, that the old villageRaids and
 * banditCamps switches still win when written as words, and that a file started twice and then listed by the world's
 * data load (listCultures, listDisasters) keeps every choice. Pure logic on temporary folders; each test puts the
 * defaults and the switched-off sets back within its tick.
 */
public class QaConfigThreatMaps1117dGameTests implements FabricGameTest {
	static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");
	static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	static final ResourceLocation DROUGHT = AliveWorkplace.id("drought");
	static final ResourceLocation PLAGUE = AliveWorkplace.id("plague");
	static final ResourceLocation FIRE = AliveWorkplace.id("village_fire");

	static String file(String body) {
		return "{\"configVersion\": 5, " + body + "}";
	}

	static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-threatmaps");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), json);
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	static String read(Path dir) {
		try {
			return Files.readString(dir.resolve(WorkplaceConfig.FILE));
		} catch (IOException e) {
			throw new GameTestAssertException("can't read the file back: " + e);
		}
	}

	/** Applies {@code config} as the game does at start, runs {@code check}, and puts the defaults back. */
	static void applied(WorkplaceConfig config, Runnable check) {
		Set<String> cultures = Threats.DISABLED;
		Set<String> disasters = Disasters.DISABLED;
		try {
			config.apply();
			check.run();
		} finally {
			new WorkplaceConfig().apply();
			Threats.DISABLED = cultures;
			Disasters.DISABLED = disasters;
		}
	}

	/**
	 * The happy path: cultures switched off and on with words in any case and with spaces round them. "OFF" and " no "
	 * are off (never on, so never picked), "Yes" and "oN" are on; a culture the map doesn't name stays on.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117d")
	public void culturesSwitchedByWordsAreOffAndOnAsWritten(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.parse(file("\"raiderCultures\": {\"bandits\": \"OFF\", \"pillager_warband\": \" no \","
			+ " \"monsters\": \"Yes\", \"witch_coven\": \"oN\"}"));
		helper.assertTrue(config.culturesOff().equals(Set.of("bandits", "pillager_warband")), "switched off: " + config.culturesOff());
		applied(config, () -> {
			helper.assertFalse(Threats.enabled(Threats.BANDITS), "bandits written \"OFF\" are still on");
			helper.assertFalse(Threats.enabled(WARBAND), "the warband written \" no \" is still on");
			helper.assertTrue(Threats.on(Threats.BANDITS).isEmpty() && Threats.on(WARBAND).isEmpty(), "a culture switched off is still offered");
			helper.assertTrue(Threats.enabled(Threats.MONSTERS) && Threats.enabled(COVEN), "monsters (\"Yes\") or the coven (\"oN\") are off");
			helper.assertTrue(Threats.enabled(AliveWorkplace.id("drowned_pirates")) && Threats.enabled(AliveWorkplace.id("desert_raiders")),
				"a culture the map doesn't name was switched off");
			helper.assertTrue(Threats.on(Threats.MONSTERS).isPresent() && Threats.on(COVEN).isPresent(), "a culture switched on isn't loaded or offered");
		});
		helper.succeed();
	}

	/**
	 * villageRaids and banditCamps "still switch monsters and bandits", also written as words: "no" in banditCamps wins
	 * over "yes" in the map, " OFF " in villageRaids switches the monsters off, and a word that says neither ("maybe")
	 * takes the default, on, never off.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117d")
	public void theOldRaidAndCampSwitchesStillWinWrittenAsWords(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Set<String> off = WorkplaceConfig.parse(file("\"banditCamps\": \"no\", \"raiderCultures\": {\"bandits\": \"yes\"}")).culturesOff();
		if (!off.equals(Set.of("bandits"))) {
			problems.add("banditCamps \"no\" with bandits \"yes\": off " + off);
		}
		off = WorkplaceConfig.parse(file("\"villageRaids\": \" OFF \"")).culturesOff();
		if (!off.equals(Set.of("monsters"))) {
			problems.add("villageRaids \" OFF \": off " + off);
		}
		off = WorkplaceConfig.parse(file("\"villageRaids\": \"maybe\", \"banditCamps\": \"On\"")).culturesOff();
		if (!off.isEmpty()) {
			problems.add("villageRaids \"maybe\", banditCamps \"On\": off " + off);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		applied(WorkplaceConfig.parse(file("\"banditCamps\": \"No\", \"villageRaids\": \"no\"")), () -> {
			helper.assertFalse(Threats.enabled(Threats.BANDITS) || Threats.enabled(Threats.MONSTERS), "bandits or monsters on with both switches \"no\"");
			helper.assertTrue(Threats.enabled(WARBAND), "the warband went off with the old switches");
		});
		helper.succeed();
	}

	/**
	 * Disasters switched by words: "Off" and "NO" never start (not on), "on" stays on; the whole id
	 * ("aliveworkplace:plague") works as the bare path does.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117d")
	public void disastersSwitchedByWordsAreOffAndOnAsWritten(GameTestHelper helper) {
		helper.assertTrue(Disasters.get(DROUGHT).isPresent() && Disasters.get(PLAGUE).isPresent() && Disasters.get(FIRE).isPresent(),
			"the three disasters aren't loaded: " + Disasters.all().stream().map(d -> d.id().toString()).toList());
		WorkplaceConfig config = WorkplaceConfig.parse(file("\"disasters\": {\"drought\": \"Off\", \"aliveworkplace:plague\": \"NO\","
			+ " \"village_fire\": \" on \"}"));
		helper.assertTrue(config.disastersOff().equals(Set.of("drought", "aliveworkplace:plague")), "switched off: " + config.disastersOff());
		applied(config, () -> {
			helper.assertTrue(Disasters.on(DROUGHT).isEmpty() && !Disasters.enabled(DROUGHT), "the drought written \"Off\" can still start");
			helper.assertTrue(Disasters.on(PLAGUE).isEmpty() && !Disasters.enabled(PLAGUE), "the plague written as its whole id \"NO\" can still start");
			helper.assertTrue(Disasters.on(FIRE).isPresent(), "the village fire written \" on \" is off");
		});
		helper.succeed();
	}

	/** An empty entry (null) in a map of words takes its default, on, and costs neither its neighbors nor the map. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117d")
	public void anEmptyEntryBesideWordsTakesItsDefaultAlone(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.parse(file("\"raiderCultures\": {\"bandits\": \"off\", \"monsters\": null},"
			+ " \"disasters\": {\"drought\": \"no\", \"plague\": null}"));
		helper.assertTrue(config.culturesOff().equals(Set.of("bandits")), "cultures off: " + config.culturesOff() + " in " + config.raiderCultures);
		helper.assertTrue(Boolean.TRUE.equals(config.raiderCultures.get("monsters")), "monsters: null isn't on: " + config.raiderCultures);
		helper.assertTrue(config.disastersOff().equals(Set.of("drought")) && Boolean.TRUE.equals(config.disasters.get("plague")),
			"disasters: " + config.disasters);
		helper.succeed();
	}

	/**
	 * Save and reload as the game does it: a hand-written file with words in both maps is started twice (read, written
	 * back), then the world's data load lists every loaded culture and disaster in it. Every choice the owner wrote is
	 * kept as plain false, each one the file didn't name is added on, and the last read applies the same switches.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117d")
	public void wordsInBothMapsSurviveTwoStartsAndTheWorldsListing(GameTestHelper helper) {
		Path dir = dirWith(file("\"guardRadius\": 30, \"raiderCultures\": {\"bandits\": \"OFF\", \"pillager_warband\": \" No \"},"
			+ " \"disasters\": {\"drought\": \"off\"}"));
		WorkplaceConfig.load(dir).save(dir);
		String first = read(dir);
		WorkplaceConfig.load(dir).save(dir);
		helper.assertTrue(first.equals(read(dir)), "the second start wrote a different file than the first");
		List<String> cultures = Threats.all().stream().map(c -> Threats.key(c.id())).toList();
		List<String> disasters = Disasters.all().stream().map(d -> Disasters.key(d.id())).toList();
		helper.assertTrue(cultures.contains("witch_coven") && disasters.contains("plague"), "loaded: " + cultures + ", " + disasters);
		WorkplaceConfig.listCultures(dir, cultures);
		WorkplaceConfig.listDisasters(dir, disasters);
		String text = read(dir);
		helper.assertTrue(text.contains("\"bandits\": false") && text.contains("\"pillager_warband\": false") && text.contains("\"drought\": false"),
			"the owner's choices weren't kept as plain false: " + text);
		helper.assertTrue(text.contains("\"witch_coven\": true") && text.contains("\"plague\": true") && !text.contains("\"OFF\""),
			"the file after the world's listing: " + text);
		WorkplaceConfig last = WorkplaceConfig.load(dir);
		helper.assertTrue(last.guardRadius == 30, "guardRadius lost: " + last.guardRadius);
		applied(last, () -> {
			helper.assertFalse(Threats.enabled(Threats.BANDITS) || Threats.enabled(WARBAND), "after two starts a culture written off is on");
			helper.assertFalse(Disasters.enabled(DROUGHT), "after two starts the drought written off is on");
			helper.assertTrue(Threats.enabled(COVEN) && Disasters.enabled(PLAGUE), "an unnamed culture or disaster is off after the listing");
		});
		helper.succeed();
	}
}
