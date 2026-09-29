package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** config/aliveworkplace.json. */
public class ConfigGameTests implements FabricGameTest {
	/** Values are read, missing ones take their default, silly ones are clamped, and applying puts them into effect. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void configIsReadClampedAndApplied(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.parse("{\"supplyRadius\": 12, \"guardRadius\": 500, \"dollarsPerEmerald\": 0}");
		helper.assertTrue(config.supplyRadius == 12, "supplyRadius " + config.supplyRadius);
		helper.assertTrue(config.guardRadius == 64, "guardRadius should be clamped to 64, not " + config.guardRadius);
		helper.assertTrue(config.dollarsPerEmerald == 1, "dollarsPerEmerald should be at least 1, not " + config.dollarsPerEmerald);
		helper.assertTrue(config.maxSiteDistance == 48 && config.orchardRadius == 16, "missing values should take their defaults");
		helper.assertTrue(WorkplaceConfig.parse("").supplyRadius == 8, "an empty file means the defaults");

		// Applying (and putting the defaults back straight away: other tests run on the same server).
		config.apply();
		boolean applied = SupplyContainers.RADIUS == 12 && Guards.RADIUS == 64 && Money.DOLLARS_PER_EMERALD == 1;
		new WorkplaceConfig().apply();
		helper.assertTrue(applied, "apply() didn't change the values in use");
		helper.assertTrue(SupplyContainers.RADIUS == 8 && Guards.RADIUS == 24 && Money.DOLLARS_PER_EMERALD == 100, "defaults not restored");
		helper.succeed();
	}
}
