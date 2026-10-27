package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.rules.Chance;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA lane E, 2026-10-27 ("an" before a vowel sound, B505/B523/B606): a fixed "a" before a number said with a vowel
 * sound, "eight", "eleven", "eighteen", "eighty": "a 8 × 8 strip mine", "Luck (a 80% chance)". English says "an 8 × 8"
 * and "an 80% chance"; "a 12 × 12" and "a 50% chance" stay as they are.
 */
public class QaArticlesNumbersBugTests implements FabricGameTest {
	/** "a" before 8, 11, 18 or 8x/8xx (eight, eleven, eighteen, eighty, eight hundred). */
	private static final Pattern A_BEFORE_VOWEL_NUMBER = Pattern.compile("\\b[aA] (?:8|11(?!\\d)|18(?!\\d)|11\\d\\d\\d(?!\\d)|18\\d\\d\\d(?!\\d))");

	private static void check(List<String> wrong, Component line) {
		String text = line.getString();
		if (A_BEFORE_VOWEL_NUMBER.matcher(text).find()) {
			wrong.add("\"" + text + "\"");
		}
	}

	/** The pattern itself: which numbers take "an". */
	private static boolean takesAn(String text) {
		return A_BEFORE_VOWEL_NUMBER.matcher(text).find();
	}

	/**
	 * A strip mine marked out 8 or 11 blocks wide: the miner's sentence when the player hands over the marker reads "a 8 ×
	 * 8 strip mine" (Miners passes the box's X and Z spans). A 12-wide one reads "a 12 × 12", as it should.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAStripMineEightOrElevenWideSaysAn(GameTestHelper helper) {
		helper.assertTrue(takesAn("a 8 × 8") && takesAn("a 11 × 11") && takesAn("a 80% chance") && takesAn("a 18 × 18")
			&& !takesAn("a 12 × 12") && !takesAn("a 1 × 1") && !takesAn("a 110 × 4") && !takesAn("a 50% chance"), "setup: the pattern is wrong");
		List<String> wrong = new ArrayList<>();
		check(wrong, Words.articled("message.aliveworkplace.quarry.started_shaft", 2, "Bram", -53, 12, 12, 70, 6));
		helper.assertTrue(wrong.isEmpty(), "setup: a 12 x 12 strip mine reads wrong: " + wrong);
		for (int size : new int[] {8, 11, 18}) {
			check(wrong, Words.articled("message.aliveworkplace.quarry.started_shaft", 2, "Bram", -53, size, size, 70, 6));
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " strip-mine sentences say \"a\" before eight, eleven or eighteen: " + wrong);
		helper.succeed();
	}

	/** A Steward or story rule with an 80% or 8% chance: its line reads "Luck (a 80% chance)" (Chance.progress). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaALuckLineOfEightyPercentSaysAn(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		Component fifty = new Chance(0.5f).progress(helper.getLevel(), hall).line();
		helper.assertTrue(fifty.getString().equals("Luck (a 50% chance)"), "setup: a 50% chance reads " + fifty.getString());
		for (float chance : new float[] {0.8f, 0.08f, 0.11f, 0.18f}) {
			check(wrong, new Chance(chance).progress(helper.getLevel(), hall).line());
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " luck lines say \"a\" before eight, eleven, eighteen or eighty: " + wrong);
		helper.succeed();
	}
}
