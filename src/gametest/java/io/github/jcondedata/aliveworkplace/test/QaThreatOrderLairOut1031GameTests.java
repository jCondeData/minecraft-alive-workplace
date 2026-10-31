package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane E, 2026-10-31), B712's probe: an operator's {@code /workplace threat start bandits} by a village whose bandit
 * camp has every member out. The guide: the command's answers say why an order can't come. The camp with nobody home
 * has nobody to send, so "found nowhere to gather" names the wrong reason (the ground round the hall is open and flat).
 */
public class QaThreatOrderLairOut1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);

	//$ gametest_ticks_batch AREA '60' '"qaThreatOrderLairOut"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaThreatOrderLairOut")
	public void anOrderByACampWithNobodyHomeSaysSo(GameTestHelper helper) {
		BlockPos hall = QaThreatOrders1031GameTests.village(helper);
		ServerLevel level = helper.getLevel();
		boolean lairs = Lairs.ENABLED;
		Lairs.forget(level);
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			BanditCamps.forget(level);
			Lairs.ENABLED = lairs;
		});
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			QaThreatOrders1031GameTests.at(level, 95, 15000);
			new WorkplaceConfig().apply();
			CommandSourceStack op = QaThreatOrders1031GameTests.op(level, hall, said);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null && Lairs.of(level, hall, BanditCamps.CULTURE).isPresent(), "setup: no bandit camp");
			Lairs.sent(level, hall, 100);
			helper.assertTrue(Lairs.of(level, hall, BanditCamps.CULTURE).map(Lairs.Lair::home).orElse(-1) == 0, "setup: the camp still has bandits home");
			said.clear();
			int sent = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			boolean raid = ThreatData.get(level).raid(hall).isPresent();
			helper.assertTrue(raid || said.stream().noneMatch(s -> s.contains("found nowhere to gather")),
				"a camp with nobody home: the order (returned " + sent + ") was answered with the wrong reason: " + said);
			helper.succeed();
		});
	}
}
