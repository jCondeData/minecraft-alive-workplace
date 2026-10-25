package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * QA (lane-e-1025-1314), the player-facing sentences (kin of B413/B444/B445): the Steward's reasons for three of the
 * mod's own rules have no singular, though their number is often exactly one. Each wish is the loaded rule's own (its
 * "why" key and effect) with the number its condition gives: households_want_grade counts households (the rule holds
 * from one), food_short counts the meals the store is short, research_idle counts the topics open.
 */
public class QaStewardReasonWordsBugTests {
	private static String reason(GameTestHelper helper, String rule, long... numbers) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", rule);
		StewardRules.Rule r = StewardRules.all().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
		helper.assertTrue(r != null, "no Steward rule " + id + " is loaded");
		List<Long> list = java.util.Arrays.stream(numbers).boxed().toList();
		return new StewardWishes.Wish(r.id(), r.effect(), r.priority(), r.why(), list).reason().getString();
	}

	/** One household wanting a grade II home (the rule holds at one): "1 household wants a grander home". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneHouseholdWantingAGranderHomeReadsInTheSingular(GameTestHelper helper) {
		String two = reason(helper, "homes_class", 2);
		helper.assertTrue(two.equals("2 households want a grander home"), "two households: " + two);
		String one = reason(helper, "homes_class", 1);
		helper.assertTrue(one.equals("1 household wants a grander home"), "one household: " + one);
		helper.succeed();
	}

	/** The store one meal short (the Berry Farm and Ranch rules): "The store is 1 meal short". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneMealShortReadsInTheSingular(GameTestHelper helper) {
		String two = reason(helper, "food_berry_farm", 2, 0);
		helper.assertTrue(two.equals("The store is 2 meals short"), "two meals: " + two);
		String one = reason(helper, "food_berry_farm", 1, 0);
		helper.assertTrue(one.equals("The store is 1 meal short"), "one meal (Berry Farm): " + one);
		String ranch = reason(helper, "food_ranch", 1, 0);
		helper.assertTrue(ranch.equals("The store is 1 meal short"), "one meal (Ranch): " + ranch);
		helper.succeed();
	}

	/** One research topic left open: "Nothing is being researched, and 1 topic is open". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneTopicOpenReadsInTheSingular(GameTestHelper helper) {
		String two = reason(helper, "research", 2);
		helper.assertTrue(two.equals("Nothing is being researched, and 2 topics are open"), "two topics: " + two);
		String one = reason(helper, "research", 1);
		helper.assertTrue(one.equals("Nothing is being researched, and 1 topic is open"), "one topic: " + one);
		helper.succeed();
	}
}
