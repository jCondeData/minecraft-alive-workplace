package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1107-0937), from docs/wiki/scaffolding.md and B1004's fix, for two survivors of a mutation spot-check on
 * that fix (BuilderWork's climb guard and its off-scaffolding spot filter):
 * <ul>
 * <li>"A builder going to stand on one of its site's columns climbs it (about 0.3 blocks a tick)": the lead is never put
 * on a column's top in one jump (a hop), whichever way he gets up there;</li>
 * <li>a crew's helper stays off the columns (B1004) but still lays his share of the pillar (builder.md: "Up to 3 idle
 * Builders help one site, each claiming steps ahead of the lead"; helpers must scale).</li>
 * </ul>
 * Same 14-high pillar with its 5 x 5 cap as {@link QaScaffoldingGameTests}, 64 scaffolding in the chest.
 */
public class QaScaffoldClimb1107GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	/**
	 * Every tick, if the lead moved more than 1.2 blocks since the last tick and now stands in or on scaffolding above
	 * the ground, he was put there in one jump, not climbed: a failure. Climbing is about 0.3 a tick, walking far less.
	 * The pillar is finished with nothing skipped, scaffolding went up, and he went up a column at least 4 high.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qaScafClimb1107Lead"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaScafClimb1107Lead")
	public void theLeadClimbsHisColumnsAndIsNeverPutOnATopInOneJump(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		int ground = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT).getY();
		Vec3[] last = {null};
		int[] most = {0};
		double[] highest = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			Villager b = s.builder();
			Vec3 now = b.position();
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			if (QaScaffolding1107GameTests.onScaffolding(level, b)) {
				highest[0] = Math.max(highest[0], now.y - ground);
			}
			if (last[0] != null && now.distanceTo(last[0]) > 1.2 && now.y > ground + 1.5 && QaScaffolding1107GameTests.onScaffolding(level, b)) {
				throw new GameTestAssertException("the lead was put on a scaffolding column in one jump at tick " + helper.getTick() + ": from "
					+ helper.relativeVec(last[0]) + " to " + helper.relativeVec(now) + " (" + s.site().scaffolds().size() + " up)");
			}
			last[0] = now;
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "no scaffolding went up");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped");
			helper.assertTrue(highest[0] >= 4, "the lead was never more than " + highest[0] + " up a column");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			LOG.info("[QA scafClimb1107 lead] pillar finished, {} scaffolds up at most, the lead {} blocks up at most, never hopped onto a top",
				most[0], highest[0]);
		});
	}

	/**
	 * The lead 4 or more up a column when night falls is put down at its foot (wiki: "When its shift ends (night, a
	 * panic) it is put down at the column's foot"); 300 ticks later it is day again and he goes back up to carry on. At
	 * every tick of the day he is never put on a column's top in one jump (more than 1.2 blocks in a tick, landing in or on
	 * scaffolding above the ground); after the night he is up a column again at least 2 high (he climbed back), and the
	 * pillar is finished with nothing skipped and no scaffolding left standing.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaScafClimb1107Night"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaScafClimb1107Night")
	public void afterTheNightTheLeadClimbsBackUpHisColumn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		int ground = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT).getY();
		Vec3[] last = {null};
		long[] nightAt = {-1};
		double[] upAfter = {0};
		double[] lowestAtNight = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			Villager b = s.builder();
			Vec3 now = b.position();
			boolean night = nightAt[0] >= 0 && helper.getTick() < nightAt[0] + 300;
			helper.setDayTime(night ? 18000 : 3000);
			boolean onCol = QaScaffolding1107GameTests.onScaffolding(level, b);
			if (night && helper.getTick() > nightAt[0] + 20) {
				lowestAtNight[0] = Math.min(lowestAtNight[0], now.y - ground);
			}
			if (nightAt[0] < 0 && onCol && now.y - ground >= 4) {
				nightAt[0] = helper.getTick();
				LOG.info("[QA scafClimb1107 night] night falls at tick {} with the lead {} up", nightAt[0], now.y - ground);
			}
			if (nightAt[0] >= 0 && helper.getTick() > nightAt[0] + 300 && onCol) {
				upAfter[0] = Math.max(upAfter[0], now.y - ground);
			}
			if (!night && last[0] != null && now.distanceTo(last[0]) > 1.2 && now.y > ground + 1.5 && onCol) {
				throw new GameTestAssertException("the lead was put on a scaffolding column in one jump at tick " + helper.getTick() + ": from "
					+ helper.relativeVec(last[0]) + " to " + helper.relativeVec(now) + " (night fell at " + nightAt[0] + ")");
			}
			last[0] = now;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(nightAt[0] >= 0, "setup: the lead was never 4 up a column");
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(lowestAtNight[0] <= 1, "at night the lead was never put down at the column's foot (lowest " + lowestAtNight[0] + " up)");
			helper.assertTrue(upAfter[0] >= 2, "after the night the lead was never back up a column (at most " + upAfter[0] + ")");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			LOG.info("[QA scafClimb1107 night] night at {}, lowest {} up that night, back up {} after it, finished with nothing skipped", nightAt[0],
				lowestAtNight[0], upAfter[0]);
		});
	}

	/**
	 * A crew of two on the pillar: the helper never stands in or on scaffolding (B1004) and still lays a share of the
	 * pillar's blocks (at least 5 of them, by the site's own count of blocks each builder placed). Finished, nothing
	 * skipped, no scaffolding left standing.
	 */
	//$ gametest_ticks_batch BIG '10000' '"qaScafClimb1107Helper"'
	@GameTest(template = BIG, timeoutTicks = 10000, batch = "qaScafClimb1107Helper")
	public void theHelperStaysOffTheColumnsButStillLaysHisShare(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.start(helper, QaScaffoldingGameTests.PILLAR, QaScaffoldingGameTests.PILLAR_AT, null);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer()));
		QaScaffoldingGameTests.fill(helper, QaScaffoldingGameTests.SCAFFOLDING);
		helper.setBlock(QaScaffoldingGameTests.BENCH2, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, QaScaffoldingGameTests.HELPER);
		Builders.employ(level, mate, helper.absolutePos(QaScaffoldingGameTests.BENCH2));
		int ground = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT).getY();
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			if (mate.getY() > ground + 1.5 && QaScaffolding1107GameTests.onScaffolding(level, mate)) {
				throw new GameTestAssertException("the helper is on a scaffolding column at " + helper.relativePos(mate.blockPosition()) + ", tick "
					+ helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			int lead = s.site().placedBy(s.builder().getUUID(), false);
			int share = s.site().placedBy(mate.getUUID(), false);
			helper.assertTrue(share >= 5, "the helper laid " + share + " block(s) of the pillar (the lead " + lead + ")");
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped with a crew");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			helper.assertTrue(!level.getBlockState(mate.blockPosition()).is(Blocks.SCAFFOLDING), "the helper ends in a scaffold");
			LOG.info("[QA scafClimb1107 helper] pillar finished; blocks laid: lead {}, helper {}", lead, share);
		});
	}
}
