package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane D, 2026-10-31): two places where {@code /workplace threat} doesn't do what the player guide says
 * (command-threat.md, command-threat-disaster.md). Both fail on main at 19d15b2b9; see the ROADMAP bugs that name them.
 */
public class QaThreatOrdersBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The guide: "a raid starts whatever ... the difficulty say". On Peaceful hostile mobs can't exist (the game removes
	 * them), so an ordered raid can't really come; what the operator reads must then say why (Peaceful), not something
	 * untrue. Passes when the raid comes with raiders, or when the refusal names Peaceful.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaThreatOrderPeacefulBug"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaThreatOrderPeacefulBug")
	public void anOrderOnPeacefulComesOrSaysItsPeaceful(GameTestHelper helper) {
		BlockPos hall = QaThreatOrders1031GameTests.village(helper);
		ServerLevel level = helper.getLevel();
		net.minecraft.world.Difficulty before = level.getDifficulty();
		Leftovers.after(helper, () -> level.getServer().setDifficulty(before, true));
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			QaThreatOrders1031GameTests.at(level, 92, 15000);
			CommandSourceStack op = QaThreatOrders1031GameTests.op(level, hall, said);
			level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
			helper.assertTrue(level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL, "setup: not Peaceful");
			int sent = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			boolean came = sent > 0 && ThreatData.get(level).raid(hall).isPresent() && !VillageRaids.raiders(level, hall).isEmpty();
			boolean saysWhy = said.stream().anyMatch(s -> s.contains("Peaceful"));
			level.getServer().setDifficulty(before, true);
			helper.assertTrue(came || saysWhy, "on Peaceful '/workplace threat start bandits' returned " + sent + ", raid "
				+ ThreatData.get(level).raid(hall).isPresent() + ", and said " + said + " (expected the raid, or words naming Peaceful as the reason)");
			helper.succeed();
		});
	}

	/**
	 * The guide: "The command suggests the names as you type", and its examples are typed by path
	 * ({@code /workplace threat start pillager_warband}, {@code lair witch_coven}, {@code disaster drought}). Typing the
	 * first letters of a path must keep suggesting the names that fit, not go blank.
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaThreatOrderSuggestBug"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaThreatOrderSuggestBug")
	public void typingAPathsFirstLettersStillSuggestsIt(GameTestHelper helper) {
		CommandSourceStack op = QaThreatOrders1031GameTests.op(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), new ArrayList<>());
		List<String> wrong = new ArrayList<>();
		for (String[] typed : new String[][] {{"workplace threat start band", "aliveworkplace:bandits"}, {"workplace threat start pill", "aliveworkplace:pillager_warband"},
				{"workplace threat lair witch", "aliveworkplace:witch_coven"}, {"workplace threat disaster dro", "aliveworkplace:drought"}}) {
			Set<String> got = QaThreatOrders1031GameTests.suggestions(helper, op, typed[0]);
			if (!got.contains(typed[1])) {
				wrong.add("'" + typed[0] + "' suggests " + got);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "typed by path, the names aren't suggested: " + String.join("; ", wrong));
		helper.succeed();
	}
}
