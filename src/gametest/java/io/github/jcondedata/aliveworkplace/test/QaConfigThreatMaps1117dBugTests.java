package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Bug tests (lane D QA, lane-d-1117-0408), not registered on main: they fail until the mod is fixed.
 *
 * <p>1. B1324's spec says a switch written as a word that says neither yes nor no "takes that switch's default with a
 * warning in the log naming it, never off". In the raiderCultures and disasters maps each entry is a switch, but one
 * such word ("maybe", a typo like "flase") makes the whole map unreadable, so every other entry the owner wrote takes
 * its default too: a culture or disaster he set to false comes back on, and the file is written back without his
 * choice. Before B1324 the word read as false and the rest of the map was kept.
 *
 * <p>2. B1323 made disabledArcs and disabledWonders read ids in any case and with spaces round them; the maps' keys are
 * still taken as written, so "Bandits": false or " drought ": false switches nothing off, silently, and the file then
 * holds both "Bandits": false and "bandits": true.
 */
public class QaConfigThreatMaps1117dBugTests implements FabricGameTest {
	/** One bad word in each map costs only that entry: the owner's false beside it stays false and stays in the file. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117dBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117dBug")
	public void aTypoInOneMapEntryKeepsTheOwnersOtherSwitchesOff(GameTestHelper helper) {
		Path dir = QaConfigThreatMaps1117dGameTests.dirWith(QaConfigThreatMaps1117dGameTests.file("\"raiderCultures\": {\"bandits\": false,"
			+ " \"pillager_warband\": \"maybe\"}, \"disasters\": {\"drought\": false, \"plague\": \"flase\"}"));
		WorkplaceConfig config = WorkplaceConfig.load(dir);
		config.save(dir);
		String text = QaConfigThreatMaps1117dGameTests.read(dir);
		helper.assertTrue(config.culturesOff().contains("bandits"), "one unreadable entry (\"pillager_warband\": \"maybe\") switched the bandits, "
			+ "written false beside it, back on: raiderCultures read as " + config.raiderCultures);
		helper.assertTrue(config.disastersOff().contains("drought"), "one unreadable entry (\"plague\": \"flase\") switched the drought, written "
			+ "false beside it, back on: disasters read as " + config.disasters);
		helper.assertTrue(text.contains("\"bandits\": false") && text.contains("\"drought\": false"),
			"the file written back lost the owner's false switches: " + text);
		helper.succeed();
	}

	/** Keys written with capitals or spaces round them switch the culture or disaster off, as the disabled lists do. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigThreatMaps1117dBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigThreatMaps1117dBug")
	public void aCultureOrDisasterNamedWithCapitalsOrSpacesIsOff(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.parse(QaConfigThreatMaps1117dGameTests.file("\"raiderCultures\": {\"Bandits\": \"off\","
			+ " \" pillager_warband \": false}, \"disasters\": {\"Drought\": false, \" plague \": \"no\"}"));
		QaConfigThreatMaps1117dGameTests.applied(config, () -> {
			helper.assertFalse(Threats.enabled(Threats.BANDITS), "\"Bandits\": \"off\" leaves the bandits on (off: " + Threats.DISABLED + ")");
			helper.assertFalse(Threats.enabled(QaConfigThreatMaps1117dGameTests.WARBAND), "\" pillager_warband \": false leaves the warband on");
			helper.assertFalse(Disasters.enabled(QaConfigThreatMaps1117dGameTests.DROUGHT), "\"Drought\": false leaves the drought on (off: "
				+ Disasters.DISABLED + ")");
			helper.assertFalse(Disasters.enabled(QaConfigThreatMaps1117dGameTests.PLAGUE), "\" plague \": \"no\" leaves the plague on");
		});
		helper.assertTrue(Set.copyOf(config.raiderCultures.keySet()).stream().noneMatch(k -> !k.equals(k.trim().toLowerCase(java.util.Locale.ROOT))),
			"the file is written back with keys as written beside the plain ones: " + config.raiderCultures.keySet());
		helper.succeed();
	}
}
