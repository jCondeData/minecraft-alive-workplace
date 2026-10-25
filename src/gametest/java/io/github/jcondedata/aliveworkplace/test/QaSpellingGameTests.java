package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, the player-facing sentences), from the owner's change 22.10a (2026-10-12): American spelling everywhere,
 * "including page titles, nav and lang text players read": defense not defence, color, armor, favor, honor, neighbor,
 * center, gray. Every value in en_us.json is read for the British forms of exactly those words.
 */
public class QaSpellingGameTests implements FabricGameTest {
	private static final Pattern BRITISH = Pattern.compile(
		"\\b(?:colour\\w*|defence\\w*|armour\\w*|favour\\w*|honour\\w*|neighbour\\w*|centre[sd]?|grey(?:s|ed|ish)?)\\b", Pattern.CASE_INSENSITIVE);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLineAPlayerReadsIsInAmericanSpelling(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = QaSpellingGameTests.class.getClassLoader().getResourceAsStream("assets/aliveworkplace/lang/en_us.json")) {
			if (in == null) {
				throw new GameTestAssertException("setup: en_us.json not found");
			}
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("setup: en_us.json unreadable: " + e);
		}
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
			if (!e.getValue().isJsonPrimitive()) {
				continue;
			}
			Matcher m = BRITISH.matcher(e.getValue().getAsString());
			while (m.find()) {
				wrong.add(e.getKey() + ": '" + m.group() + "'");
			}
		}
		helper.assertTrue(lang.size() > 1000, "setup: only " + lang.size() + " lines read");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " lines a player reads in British spelling (22.10a): " + wrong);
		helper.succeed();
	}
}
