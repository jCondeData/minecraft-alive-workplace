package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * B505: a sentence that puts "a" before a name says "an" when the name begins with a vowel sound ("Bram is now an
 * Armorer", "reviving an Omanyte", "Comes in an Ultra Ball"), through {@link Words#articled} and each key's {@code .an}
 * form. The tooltip test is QA's (lane E, branch tests/articles-1025).
 */
public class ArticleGameTests implements FabricGameTest {
	/** "a" before a word that starts with a vowel sound (a Unicorn, a Uniform, a Useful one are fine). */
	private static final Pattern A_BEFORE_VOWEL = Pattern.compile("\\b[aA] (?:[AEIOaeio]|U(?!ni|s[eu]))");

	/** Every workstation item's Shift lines, as a player reads them: "takes it as an Armorer" for the blast furnace. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStationsTooltipSaysAnBeforeAJobThatStartsWithAVowel(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int read = 0;
		boolean armorer = false;
		for (Item item : BuiltInRegistries.ITEM) {
			if (!(item instanceof BlockItem)) {
				continue;
			}
			for (Component line : Stations.tooltip(new ItemStack(item), true)) {
				read++;
				String text = line.getString();
				armorer |= text.equals("A villager without a job takes it as an Armorer");
				if (A_BEFORE_VOWEL.matcher(text).find()) {
					wrong.add(BuiltInRegistries.ITEM.getKey(item).getPath() + ": \"" + text + "\"");
				}
			}
		}
		helper.assertTrue(read > 20, "setup: only " + read + " workstation tooltip lines read");
		helper.assertTrue(armorer, "the blast furnace's tooltip doesn't say \"takes it as an Armorer\"");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " workstation tooltips say \"a\" before a vowel: " + wrong);
		helper.succeed();
	}

	/** Which words take "an": vowels, and a u or eu that isn't said "you". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anGoesBeforeAVowelSoundOnly(GameTestHelper helper) {
		for (String w : List.of("Armorer", "Eevee", "Innkeeper", "Omanyte", "Ultra Ball", "Umbreon", "apprentice", " Expert")) {
			helper.assertTrue(Words.startsWithVowelSound(w), "'" + w + "' doesn't take an");
		}
		for (String w : List.of("Porter", "Unicorn", "Useful one", "Utopia", "Eulogy", "Builder", "", "Hamlet")) {
			helper.assertTrue(!Words.startsWithVowelSound(w), "'" + w + "' takes an");
		}
		helper.succeed();
	}

	/** Each sentence of the bug, with a name that needs "an" and one that doesn't, reads as English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyArticledSentenceReadsAnBeforeAVowel(GameTestHelper helper) {
		Component armorer = Stations.name(VillagerProfession.ARMORER);
		Component porter = Component.literal("Porter");
		Component apprentice = BuilderLevels.levelName(2);
		String[][] cases = {
			{Words.articled("message.aliveworkplace.hall.assigned", 1, "Bram", armorer).getString(), "Bram is now an Armorer"},
			{Words.articled("message.aliveworkplace.hall.assigned", 1, "Jo", porter).getString(), "Jo is now a Porter"},
			{Words.articled("screen.aliveworkplace.hall.give_job", 1, "Bram", armorer).getString(), "Click: Bram becomes an Armorer"},
			{Words.articled("screen.aliveworkplace.hall_retrain.pick", 1, "Dara", armorer).getString(), "Click: see the price for Dara to become an Armorer"},
			{Words.articled("screen.aliveworkplace.hall_retrain.pay", 2, "20 emeralds", "Dara", armorer).getString(),
				"Click again to pay 20 emeralds: Dara becomes an Armorer"},
			{Words.articled("message.aliveworkplace.retrain.offer", 3, "Dara", "Librarian", "Novice", armorer, "5 emeralds").getString(),
				"Dara (Librarian, Novice) can be retrained as an Armorer for 5 emeralds. Their trades and level are lost: they start again as a Novice."},
			{Words.articled("message.aliveworkplace.retrain.done", 2, "Dara", "Librarian", armorer, "Blast Furnace", "5 emeralds").getString(),
				"Dara gave up the Librarian job and was retrained as an Armorer at the Blast Furnace, for 5 emeralds."},
			{Words.articled("message.aliveworkplace.retrain.done", 2, "Dara", "Librarian", porter, "Barrel", "5 emeralds").getString(),
				"Dara gave up the Librarian job and was retrained as a Porter at the Barrel, for 5 emeralds."},
			{Words.articled("message.aliveworkplace.guard.kind", 1, "Bram", Guards.Kind.ARCHER.title()).getString(), "Bram is an Archer now"},
			{Words.articled("message.aliveworkplace.guard.kind", 1, "Bram", Guards.Kind.KNIGHT.title()).getString(), "Bram is a Knight now"},
			{Words.articled("message.aliveworkplace.fossil.state.reviving", 0, "Omanyte", "Alex").getString(), "reviving an Omanyte for Alex"},
			{Words.articled("message.aliveworkplace.daycare_keeper.egg", 0, "Eevee").getString(), "an Eevee Egg"},
			{Words.articled("message.aliveworkplace.daycare_keeper.egg", 0, "Pichu").getString(), "a Pichu Egg"},
			{Words.articled("screen.aliveworkplace.pokemon_trader.ball", 0, "Ultra Ball").getString(), "Comes in an Ultra Ball"},
			{Words.articled("screen.aliveworkplace.pokemon_trader.cost_species", 0, "Eevee", 20).getString(), "For: an Eevee line, Lv. 20+"},
			{Words.articled("message.aliveworkplace.pokemon_trader.wants_species", 0, "Eevee", 20).getString(),
				"Special request: an Eevee (or one of its evolutions), Lv. 20 or higher"},
			{Words.articled("message.aliveworkplace.pokemon_trader.wrong_species", 0, "Eevee").getString(), "Not an Eevee or one of its evolutions"},
			{Words.articled("chronicle.aliveworkplace.joined", 0, apprentice, "Alex").getString(), "An Apprentice traveler joined the village, hired by Alex"},
			{Words.articled("chronicle.aliveworkplace.joined", 0, BuilderLevels.levelName(1), "Alex").getString(), "A Novice traveler joined the village, hired by Alex"},
			{Words.articled("message.aliveworkplace.inn.arrived", 0, apprentice).getString(),
				"A traveler has come to stay at the inn: an Apprentice. Right-click them to hire them."},
			{Words.articled("message.aliveworkplace.inn.hired", 0, BuilderLevels.levelName(4)).getString(),
				"The traveler stays! They'll take the first free job, as an Expert."},
			{Words.articled("screen.aliveworkplace.inn.starts_as", 0, apprentice).getString(), "Hired, they start their first job as an Apprentice"},
			{Words.articled("message.aliveworkplace.raid_helpers.cheer", 1, "Trainer Mira", "Oran Berry").getString(), "Trainer Mira cheers you on: an Oran Berry for the raid!"},
			{Words.articled("tooltip.aliveworkplace.station.by_itself", 0, armorer).getString(), "A villager without a job takes it as an Armorer"},
		};
		for (String[] c : cases) {
			helper.assertTrue(c[0].equals(c[1]), "read \"" + c[0] + "\", expected \"" + c[1] + "\"");
		}
		helper.succeed();
	}

	/** Each {@code .an} line is its plain line with that one "a" turned to "an", so the two never drift apart. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyAnLineMatchesItsPlainLine(GameTestHelper helper) {
		com.google.gson.JsonObject lang = QaAmericanSpellingGameTests.english();
		int seen = 0;
		for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
			if (!e.getKey().endsWith(".an")) {
				continue;
			}
			seen++;
			String base = e.getKey().substring(0, e.getKey().length() - 3);
			helper.assertTrue(lang.has(base), e.getKey() + " has no plain line");
			String plain = lang.get(base).getAsString();
			String an = e.getValue().getAsString();
			boolean one = false;
			for (String[] swap : new String[][] {{"a %", "an %"}, {"A %", "An %"}}) {
				int at = plain.indexOf(swap[0]);
				while (at >= 0) {
					one |= (plain.substring(0, at) + swap[1] + plain.substring(at + swap[0].length())).equals(an);
					at = plain.indexOf(swap[0], at + 1);
				}
			}
			helper.assertTrue(one, e.getKey() + " isn't its plain line with \"a\" made \"an\": \"" + plain + "\" / \"" + an + "\"");
		}
		helper.assertTrue(seen >= 19, "only " + seen + " .an lines");
		helper.succeed();
	}
}
