package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;

/**
 * QA (lane E, 2026-11-14, B1227) from {@code docs/wiki/config-switches.md}: every game rule the mod registers, not only
 * the ones the guide's table names, has its words on the Edit Game Rules screen ({@code gamerule.<name>} in en_us.json),
 * and those words read as a sentence in American English.
 */
public class QaGameRuleWords1114eGameTests implements FabricGameTest {

	private static List<String> ourRules() {
		List<String> names = new ArrayList<>();
		GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
			@Override
			public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				if (key.getId().startsWith("workplace")) {
					names.add(key.getId());
				}
			}
		});
		return names;
	}

	/** Every registered workplace rule has a gamerule.<name> line (so the screen never shows a raw key). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRegisteredRuleHasItsWords(GameTestHelper helper) {
		JsonObject lang = QaBuilderSwitches1114bGameTests.lang();
		List<String> rules = ourRules();
		helper.assertTrue(rules.size() >= 10, "expected the mod's 10+ game rules to be registered, found " + rules);
		List<String> missing = new ArrayList<>();
		for (String name : rules) {
			if (!lang.has("gamerule." + name)) {
				missing.add("gamerule." + name);
			}
		}
		helper.assertTrue(missing.isEmpty(), "the Edit Game Rules screen shows raw keys for: " + missing);
		helper.succeed();
	}

	/** workplaceVillageFarms (the rule B1227 was about) is registered and its words name what it switches. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void villageFarmsIsRegisteredAndSaysWhatItDoes(GameTestHelper helper) {
		helper.assertTrue(ourRules().contains("workplaceVillageFarms"), "workplaceVillageFarms is not a registered game rule");
		String words = QaBuilderSwitches1114bGameTests.lang().get("gamerule.workplaceVillageFarms").getAsString();
		helper.assertTrue(words.toLowerCase(Locale.ROOT).contains("farm"), "the words don't mention farms: " + words);
		helper.succeed();
	}

	/** Each rule's words are a readable line: not blank, not the key itself, prefixed like the others, American spelling. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRulesWordsReadLikeTheOthers(GameTestHelper helper) {
		JsonObject lang = QaBuilderSwitches1114bGameTests.lang();
		List<String> bad = new ArrayList<>();
		for (String name : ourRules()) {
			if (!lang.has("gamerule." + name)) {
				continue;
			}
			String words = lang.get("gamerule." + name).getAsString();
			String low = words.toLowerCase(Locale.ROOT);
			if (words.isBlank() || words.contains("gamerule.") || words.contains(name) || !words.startsWith("Alive Workplace: ")
				|| words.length() > 200) {
				bad.add(name + " -> '" + words + "'");
			}
			for (String british : new String[] {"colour", "defence", "armour", "cancelled", "grey", "behaviour", "neighbour", "centre"}) {
				if (low.contains(british)) {
					bad.add(name + " uses British '" + british + "'");
				}
			}
		}
		helper.assertTrue(bad.isEmpty(), "game rule words that don't read right: " + bad);
		helper.succeed();
	}
}
