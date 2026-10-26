package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (adversarial, from the spec of ROADMAP 27.6, not the code): a rule's {@code when} holds only when every condition
 * does (and explain still reports each one's number), and the conditions count only the hall's own village.
 */
public class QaStewardRulesDataGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static BlockPos hall(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		int old = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = old);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		return v;
	}

	private static StewardRules.Rule rule(String name, String... when) {
		return StewardRules.read(AliveWorkplace.id(name), JsonParser.parseString(
			"{\"when\": [" + String.join(",", when) + "], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}"));
	}

	/** Two villagers: "at least 1" and "at least 5" together don't hold, "at least 1" and "at least 2" do; each check keeps its own result and number. */
	//$ gametest_ticks_batch AREA '40' '"qaRuleAllHold"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRuleAllHold")
	public void aRuleHoldsOnlyWhenEveryConditionDoes(GameTestHelper helper) {
		BlockPos hall = hall(helper, 16);
		villager(helper, 5, 4);
		villager(helper, 7, 4);
		StewardConditions.Facts facts = StewardConditions.Facts.of(helper.getLevel(), hall);
		StewardRules.Rule mixed = rule("qa_mixed", "{\"villagers_at_least\": {\"n\": 1}}", "{\"villagers_at_least\": {\"n\": 5}}");
		StewardRules.Rule both = rule("qa_both", "{\"villagers_at_least\": {\"n\": 1}}", "{\"villagers_at_least\": {\"n\": 2}}");
		StewardRules.Rule negated = rule("qa_negated", "{\"villagers_at_least\": {\"n\": 1}}", "{\"not\": {\"villagers_at_least\": {\"n\": 2}}}");

		StewardWishes.Verdict m = StewardWishes.judge(mixed, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(m.status() == StewardWishes.Status.NOT_HELD, "one of two conditions failing still held: " + m.status());
		helper.assertTrue(m.checks().size() == 2 && m.checks().get(0).held() && !m.checks().get(1).held(),
			"the checks of a rule that didn't hold: " + m.checks());
		helper.assertTrue(m.numbers().equals(List.of(2L, 2L)), "the numbers explain shows: " + m.numbers());
		StewardWishes.Verdict b = StewardWishes.judge(both, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(b.status() == StewardWishes.Status.HELD, "both conditions held but the rule didn't: " + b.status() + " " + b.checks());
		StewardWishes.Verdict n = StewardWishes.judge(negated, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(n.status() == StewardWishes.Status.NOT_HELD, "a rule with a failing 'not' held: " + n.checks());

		List<StewardWishes.Wish> wishes = StewardWishes.rank(List.of(mixed, both, negated), facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(wishes.size() == 1 && wishes.get(0).rule().equals(both.id()), "the day's wishes: " + wishes);
		helper.succeed();
	}

	/** At a village radius of 6, a villager 3 blocks from the hall counts and one 10 blocks away doesn't, for every count condition. */
	//$ gametest_ticks_batch AREA '40' '"qaRuleOwnVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRuleOwnVillage")
	public void conditionsCountOnlyTheHallsOwnVillage(GameTestHelper helper) {
		BlockPos hall = hall(helper, 6);
		villager(helper, 14, 11); // 3 blocks east
		villager(helper, 11, 21); // 10 blocks south: outside
		StewardConditions.Facts facts = StewardConditions.Facts.of(helper.getLevel(), hall);
		StewardRules.Rule count = rule("qa_count", "{\"villagers_at_least\": {\"n\": 1}}");
		StewardRules.Rule two = rule("qa_two", "{\"villagers_at_least\": {\"n\": 2}}");
		StewardRules.Rule jobless = rule("qa_jobless", "{\"jobless\": {\"at_least\": 1}}");
		StewardRules.Rule beds = rule("qa_beds", "{\"beds_short\": {\"at_least\": 1}}");
		StewardWishes.Verdict c = StewardWishes.judge(count, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(c.status() == StewardWishes.Status.HELD && c.numbers().equals(List.of(1L)), "villagers in the village: " + c.checks());
		helper.assertTrue(StewardWishes.judge(two, facts, StewardWishes.State.EMPTY, 0).status() == StewardWishes.Status.NOT_HELD,
			"a villager outside the village was counted towards 2");
		StewardWishes.Verdict j = StewardWishes.judge(jobless, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(j.numbers().equals(List.of(1L)), "jobless counted " + j.numbers() + ", not just the one inside");
		StewardWishes.Verdict bd = StewardWishes.judge(beds, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(bd.numbers().equals(List.of(1L)), "beds short counted " + bd.numbers() + ", not just the one inside");
		helper.succeed();
	}
}
