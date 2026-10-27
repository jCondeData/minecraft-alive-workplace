package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.city.RoadStyles;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA lane E, 2026-10-27 (B505/B523 follow-up, "an before a vowel sound" in every sentence a player reads): lines B505
 * and B523 didn't list still put a fixed "a" before a name that can start with a vowel. Each line is rendered here
 * from its lang key with the very argument the mod passes (the level name {@code BuilderLevels.levelName}, the job
 * name {@code Stations.name}, the road style's title), and must read as English.
 */
public class QaArticlesLeftBugTests implements FabricGameTest {
	/** "a" before a word that starts with a vowel sound (as ArticleGameTests checks it). */
	private static final Pattern A_BEFORE_VOWEL = Pattern.compile("\\b[aA] (?:[AEIOaeio]|U(?!ni|s[eu]))");

	private static void check(List<String> wrong, String what, Component line) {
		String text = line.getString();
		if (A_BEFORE_VOWEL.matcher(text).find()) {
			wrong.add(what + ": \"" + text + "\"");
		}
	}

	/**
	 * A trainer, tutor or Pokémon trader that goes up from Novice to Apprentice (or to Expert) is announced as "now a
	 * Apprentice trainer" (Trainers, Tutors and PokemonTraders pass {@code BuilderLevels.levelName(tier)}), and a Ball
	 * Smith's locked ball says "Needs a Apprentice smith" (BallSmiths passes {@code merchant.level.<tier>}).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaARankUpToApprenticeOrExpertSaysAn(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Component who = Component.literal("Ash");
		for (int tier : new int[] {2, 4}) {
			Component level = BuilderLevels.levelName(tier);
			helper.assertTrue(level.getString().equals(tier == 2 ? "Apprentice" : "Expert"), "setup: level " + tier + " reads " + level.getString());
			check(wrong, "trainer.ranked_up " + tier, Component.translatable("message.aliveworkplace.trainer.ranked_up", who, level));
			check(wrong, "tutor.ranked_up " + tier, Component.translatable("message.aliveworkplace.tutor.ranked_up", who, level));
			check(wrong, "pokemon_trader.ranked_up " + tier, Component.translatable("message.aliveworkplace.pokemon_trader.ranked_up", who, level));
			check(wrong, "ball_smith.needs_level " + tier, Component.translatable("screen.aliveworkplace.ball_smith.needs_level",
				Component.translatable("merchant.level." + tier)));
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " rank lines say \"a\" before a vowel: " + wrong);
		helper.succeed();
	}

	/**
	 * The People table's "give up the job" tip for an Armorer reads "see what Bram gives up as a Armorer" (HallJobs
	 * passes {@code Stations.name} of the villager's job).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheNoJobTipSaysAnBeforeAnArmorer(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Component armorer = Stations.name(VillagerProfession.ARMORER);
		helper.assertTrue(armorer.getString().equals("Armorer"), "setup: the armorer's job reads " + armorer.getString());
		check(wrong, "hall.jobs.no_job_pick", Component.translatable("screen.aliveworkplace.hall.jobs.no_job_pick", "Bram", armorer));
		helper.assertTrue(wrong.isEmpty(), "the no-job tip says \"a\" before a vowel: " + wrong);
		helper.succeed();
	}

	/**
	 * A road drawn without a style (built "As drawn") goes into the chronicle as "Bram finished a As drawn road, 40 blocks
	 * long" (Roads passes {@code RoadStyles.title} of the road's style; the Apricorn style with Cobblemon reads "a
	 * Apricorn road" the same way).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheRoadBuiltChronicleSaysAnBeforeAsDrawn(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Component style = RoadStyles.title(RoadStyles.forBlueprintStyle(""));
		helper.assertTrue(style.getString().equals("As drawn"), "setup: a road drawn without a style reads " + style.getString());
		check(wrong, "chronicle.road_built", Component.translatable("chronicle.aliveworkplace.road_built", "Bram", 40, style));
		helper.assertTrue(wrong.isEmpty(), "the road's chronicle line says \"a\" before a vowel: " + wrong);
		helper.succeed();
	}
}
