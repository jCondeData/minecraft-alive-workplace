package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * QA (2026-10-26) bug tests for 34.6, failing on main: they travel with the fix (see QaClassHallRetirementGameTests).
 */
public class QaClassHallRetirementBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {

	/**
	 * 34.6: "chatter (Chatter), three lines each: wants wine, wants new clothes, proud of their rise, came down in the
	 * world, the Gazette's news, glad of the tavern". The rise and fall have theirs; wine, clothes, the Gazette and the
	 * tavern were waiting for 34.9+ goods, which have landed (Berry Wine, Fine Clothes, the Gazette, the Innkeeper's tavern).
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void classChatterHasThreeLinesForWineClothesGazetteAndTavern(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, String> topic : Map.of("wants wine", "wine", "wants new clothes", "cloth", "the Gazette's news", "gazette",
			"glad of the tavern", "tavern").entrySet()) {
			int lines = 0;
			for (String topicKey : chatterTopics()) {
				if (topicKey.contains(topic.getValue())) {
					for (int i = 0; i < 3; i++) {
						String key = "chatter.aliveworkplace." + topicKey + "." + i;
						if (Language.getInstance().has(key) && !Component.translatable(key).getString().isBlank()) {
							lines++;
						}
					}
				}
			}
			if (lines < 3) {
				missing.add(topic.getKey() + " (" + lines + " lines)");
			}
		}
		helper.assertTrue(missing.isEmpty(), "34.6 chatter missing: " + missing);
		helper.succeed();
	}

	/** Every chatter topic in en_us.json ("chatter.aliveworkplace.<topic>.<n>"). */
	private static List<String> chatterTopics() {
		List<String> out = new ArrayList<>();
		try (var in = QaClassHallRetirementBugTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			for (String key : lang.keySet()) {
				if (key.startsWith("chatter.aliveworkplace.")) {
					String rest = key.substring("chatter.aliveworkplace.".length());
					String topic = rest.contains(".") ? rest.substring(0, rest.lastIndexOf('.')) : rest;
					if (!out.contains(topic)) {
						out.add(topic);
					}
				}
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		return out;
	}
}
