package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.people.HomeGrades;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane-c-1107-0353), the home grade files (ROADMAP 34.15, 34.16) at their edges, from HomeGrades' contract: a file's
 * grade is 1 to 3 (both ends load, 0 and 4 skip the file); two files for one family keep the higher grade whichever
 * loads first; a blueprint's own tier above III stays what it is; a garden's tier is 1 or more (1 is the default, and a
 * tier-1 garden counts on the base home itself), its beauty 0 or more, and a bad garden skips the whole file. Found by a
 * mutation spot-check: a garden tier of 1 being refused survived every existing test. The shipped files are loaded back
 * at the end of each test.
 */
public class QaHomeGradeFilesLaneC1107GameTests implements FabricGameTest {

	/** Grades 1 and 3 load; 0 and 4 skip their file; the higher of two files for one family wins; tier IV stays IV. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaHomeGradeEdges1107"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaHomeGradeEdges1107")
	public void qaGradesOneToThreeLoadAndTheHigherFileWins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> HomeGrades.reload(level.getServer().getResourceManager()));
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("homes/a_stone_low.json"), json("{\"blueprint\": \"aliveworkplace:stone_house\", \"grade\": 1}"));
		files.put(ours("homes/z_stone_high.json"), json("{\"blueprint\": \"aliveworkplace:stone_house_2\", \"grade\": 3}"));
		files.put(ours("homes/a_terrace_high.json"), json("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 2}"));
		files.put(ours("homes/z_terrace_low.json"), json("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 1}"));
		files.put(ours("homes/cottage_zero.json"), json("{\"blueprint\": \"aliveworkplace:starter_cottage\", \"grade\": 0}"));
		files.put(ours("homes/townhouse_four.json"), json("{\"blueprint\": \"aliveworkplace:townhouse\", \"grade\": 4}"));
		HomeGrades.load(files);
		helper.assertTrue(HomeGrades.given(StarterBlueprints.STONE_HOUSE.id()) == 3,
			"grade 1 then grade 3 for the Stone House family: the higher, 3, should win, got " + HomeGrades.given(StarterBlueprints.STONE_HOUSE.id()));
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STONE_HOUSE.id()) == 3, "the Stone House given III: " + HomeGrades.grade(StarterBlueprints.STONE_HOUSE.id()));
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TERRACE.id()) == 2,
			"grade 2 then grade 1 for the Terrace: the higher, 2, should win, got " + HomeGrades.given(StarterBlueprints.TERRACE.id()));
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.TERRACE.id()) == 2 && HomeGrades.grade(StarterBlueprints.TERRACE_2.id()) == 3,
			"the Terrace given II is II, its upgrade III: " + HomeGrades.grade(StarterBlueprints.TERRACE.id()) + ", " + HomeGrades.grade(StarterBlueprints.TERRACE_2.id()));
		helper.assertTrue(HomeGrades.given(StarterBlueprints.STARTER_COTTAGE.id()) == 0, "a grade 0 file should be skipped");
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TOWNHOUSE.id()) == 0, "a grade 4 file should be skipped");
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.TOWNHOUSE.id()) == 1, "the Townhouse with its file skipped falls back to its tier, I");
		ResourceLocation fourth = AliveWorkplace.id("stone_house_4");
		helper.assertTrue(HomeGrades.grade(fourth) == 4, "a tier IV home stays IV (a file never lowers it): " + HomeGrades.grade(fourth));
		helper.succeed();
	}

	/**
	 * A garden of tier 1 (written, or left out) counts on the base home itself; a garden of beauty 0 loads its file with no
	 * beauty; a garden of tier 0 skips the whole file, grade and all.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaHomeGardenEdges1107"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaHomeGardenEdges1107")
	public void qaAGardenFromTierOneCountsOnTheBaseHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> HomeGrades.reload(level.getServer().getResourceManager()));
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("homes/stone.json"), json("{\"blueprint\": \"aliveworkplace:stone_house\", \"grade\": 1, \"garden\": {\"tier\": 1, \"beauty\": 2}}"));
		files.put(ours("homes/terrace.json"), json("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 1, \"garden\": {\"beauty\": 1}}"));
		files.put(ours("homes/townhouse.json"), json("{\"blueprint\": \"aliveworkplace:townhouse\", \"grade\": 2, \"garden\": {\"tier\": 1, \"beauty\": 0}}"));
		files.put(ours("homes/cottage.json"), json("{\"blueprint\": \"aliveworkplace:starter_cottage\", \"grade\": 2, \"garden\": {\"tier\": 0, \"beauty\": 2}}"));
		HomeGrades.load(files);
		helper.assertTrue(HomeGrades.given(StarterBlueprints.STONE_HOUSE.id()) == 1, "a tier-1 garden's file should load: given " + HomeGrades.given(StarterBlueprints.STONE_HOUSE.id()));
		helper.assertTrue(HomeGrades.gardenBeauty(StarterBlueprints.STONE_HOUSE.id()) == 2 && HomeGrades.gardenBeauty(StarterBlueprints.STONE_HOUSE_2.id()) == 2,
			"a tier-1 garden counts on the Stone House and its upgrade: " + HomeGrades.gardenBeauty(StarterBlueprints.STONE_HOUSE.id()) + ", "
				+ HomeGrades.gardenBeauty(StarterBlueprints.STONE_HOUSE_2.id()));
		helper.assertTrue(HomeGrades.gardenBeauty(StarterBlueprints.TERRACE.id()) == 1, "a garden with no tier is from tier 1: " + HomeGrades.gardenBeauty(StarterBlueprints.TERRACE.id()));
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TOWNHOUSE.id()) == 2 && HomeGrades.gardenBeauty(StarterBlueprints.TOWNHOUSE.id()) == 0,
			"a garden of beauty 0 loads its file (grade II) and counts nothing");
		helper.assertTrue(HomeGrades.given(StarterBlueprints.STARTER_COTTAGE.id()) == 0 && HomeGrades.gardenBeauty(StarterBlueprints.STARTER_COTTAGE.id()) == 0,
			"a garden of tier 0 should skip its whole file");
		helper.succeed();
	}

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static JsonElement json(String text) {
		return JsonParser.parseString(text);
	}
}
