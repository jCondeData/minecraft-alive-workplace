package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * QA lane D, 2026-11-17, the first mutation pass on threat/Culture (the raider-culture file reader, ROADMAP 32.2 and
 * 32.7): the limits a datapack author reads in the error messages hold at both ends. A captain's extra health may be 0 to
 * 1000 (Culture:274, {@code > 1000} to {@code >= 1000} refused a file with exactly 1000), and an extra's count by rank
 * 0 to 8 ("an extra's count must be 0 to 8"; Culture:333, {@code n < 0} to {@code n < 1} refused the warband-style
 * {@code {"village": 0, "town": 1}} that says none come below a town), and what a captain raises comes back at over 0
 * and at most 1 of its health (Culture:358: {@code > 2}, {@code && }, {@code < 0} each let a bad share through). Every one
 * of these mutants survived the 12 culture classes.
 */
public class QaCultureMutants1117dGameTests implements FabricGameTest {
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_culture_1117d");
	private static final String ROSTER = "\"roster\": [{\"entity\": \"minecraft:pillager\", \"share\": 1}]";

	private static Culture read(String body) {
		JsonObject json = JsonParser.parseString("{" + ROSTER + ", " + body + "}").getAsJsonObject();
		return Culture.read(ID, json);
	}

	/** The reader's refusal of {@code body}'s file, or null when it reads. */
	private static String refusal(String body) {
		try {
			read(body);
			return null;
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
	}

	private static String captain(String health) {
		return "\"captain\": {\"entity\": \"minecraft:vindicator\", \"health\": " + health + "}";
	}

	/** A captain's extra health is read from 0 to 1000, both ends included, and refused just past either. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCultureReader1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCultureReader1117d")
	public void aCaptainsExtraHealthIsReadFromNoneToAThousand(GameTestHelper helper) {
		for (String fine : new String[] {"0", "36", "999.5", "1000"}) {
			String refused = refusal(captain(fine));
			helper.assertTrue(refused == null, "a captain with " + fine + " extra health was refused: " + refused);
			double health = read(captain(fine)).captain().orElseThrow().health();
			helper.assertTrue(health == Double.parseDouble(fine), "a captain with " + fine + " extra health was read as " + health);
		}
		for (String bad : new String[] {"-0.5", "1000.5", "5000"}) {
			String refused = refusal(captain(bad));
			helper.assertTrue(refused != null && refused.contains("extra health"), "a captain with " + bad + " extra health wasn't refused for it: " + refused);
		}
		helper.succeed();
	}

	private static String raises(String health) {
		return "\"captain\": {\"entity\": \"minecraft:husk\", \"raises\": {\"entity\": \"minecraft:husk\"" + (health == null ? "" : ", \"health\": " + health) + "}}";
	}

	/**
	 * What a captain raises (32.9) rises at a share of its full health over 0 and at most 1: half when the file doesn't
	 * say, 1 and a little over 0 read; 0 (a mob that would rise dead), less, or more than its full health refused.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCultureReader1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCultureReader1117d")
	public void whatACaptainRaisesComesBackWithOverNoneAndAtMostAllItsHealth(GameTestHelper helper) {
		double share = read(raises(null)).captain().orElseThrow().raises().orElseThrow().health();
		helper.assertTrue(share == 0.5, "a raise with no health given comes back at " + share + " of its health, not half");
		for (String fine : new String[] {"0.01", "0.5", "1"}) {
			String refused = refusal(raises(fine));
			helper.assertTrue(refused == null, "a raise at " + fine + " of its health was refused: " + refused);
		}
		for (String bad : new String[] {"0", "-0.5", "1.01", "1.5", "3"}) {
			String refused = refusal(raises(bad));
			helper.assertTrue(refused != null && refused.contains("raises: health"), "a raise at " + bad + " of its health wasn't refused for it: " + refused);
		}
		helper.succeed();
	}

	/**
	 * An extra's count is 0 to 8 for a rank: 0 at a lower rank (none come yet) and 8 read; 9 or -1 refused. With
	 * {@code {"village": 0, "town": 2, "city": 8}} a hamlet and a village get none, a town 2 and a city 8.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCultureReader1117d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCultureReader1117d")
	public void anExtrasCountIsNoneToEightAndNoneAtALowerRankMeansNoneComeYet(GameTestHelper helper) {
		String extras = "\"extras\": [{\"entity\": \"minecraft:ravager\", \"role\": \"ram\", \"count\": {\"village\": 0, \"town\": 2, \"city\": 8}}]";
		String refused = refusal(extras);
		helper.assertTrue(refused == null, "extras with a count of 0 at village and 8 at city were refused: " + refused);
		Culture.Extra extra = read(extras).extras().get(0);
		int[] want = {0, 0, 2, 8};
		for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
			helper.assertTrue(extra.count(rank) == want[rank.ordinal()], "a " + rank + " gets " + extra.count(rank) + " ravagers, not " + want[rank.ordinal()]);
		}
		for (String bad : new String[] {"9", "-1"}) {
			String no = refusal("\"extras\": [{\"entity\": \"minecraft:ravager\", \"count\": {\"town\": " + bad + "}}]");
			helper.assertTrue(no != null && no.contains("count must be 0 to 8"), "an extra's count of " + bad + " wasn't refused for it: " + no);
		}
		helper.succeed();
	}
}
