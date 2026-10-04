package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/** ROADMAP 24.5: sentences with a number say "1 parcel" and "2 parcels", never "parcel(s)". */
public class WordsGameTests implements FabricGameTest {
	/** One takes the singular sentence, every other number (0 too) the plural one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneIsSingularEveryOtherNumberPlural(GameTestHelper helper) {
		String key = "message.aliveworkplace.mail.collected";
		helper.assertTrue(Words.counted(key, 1, 1, "Ana").getString().equals("You collected 1 parcel at the post office, from Ana."),
			"one: " + Words.counted(key, 1, 1, "Ana").getString());
		helper.assertTrue(Words.counted(key, 3, 3, "Ana, Bo").getString().equals("You collected 3 parcels at the post office, from Ana, Bo."),
			"three: " + Words.counted(key, 3, 3, "Ana, Bo").getString());
		helper.assertTrue(Words.counted("screen.aliveworkplace.hall.festival_in", 0, 0).getString().equals("In 0 days"), "zero is plural");
		helper.assertTrue(Words.counted("message.aliveworkplace.rally.raised", 1, 1).getString().equals("Banner raised: 1 guard follows you"),
			"rally: " + Words.counted("message.aliveworkplace.rally.raised", 1, 1).getString());
		helper.succeed();
	}

	/** No sentence the player reads has "(s)", and every singular sentence has its plural one (with the same %s count). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noSentenceSaysParenthesisS(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		JsonObject lang;
		try (InputStream in = Files.newInputStream(mod.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		List<String> problems = new ArrayList<>();
		for (String key : lang.keySet()) {
			String text = lang.get(key).getAsString();
			if (text.contains("(s)")) {
				problems.add(key + " says (s)");
			}
			if (key.endsWith(".one")) {
				String many = key.substring(0, key.length() - ".one".length());
				if (!lang.has(many)) {
					problems.add(key + " has no plural sentence");
				} else if (lang.get(many).getAsString().split("%s", -1).length != text.split("%s", -1).length) {
					problems.add(key + " and its plural take different numbers of values");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
