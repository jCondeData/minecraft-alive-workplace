package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Witch;

/**
 * QA (lane-b-1104-0938), bug: a builder struck by lightning mid-job becomes a witch (vanilla's Villager.thunderHit, any
 * difficulty but Peaceful). Unlike a death, nothing of the builder's is handled: the materials in his bag vanish with
 * him and his site stays his, a builder that no longer exists, so the build never goes on and no blueprint falls. See
 * {@link QaBuilderConverted1104GameTests} for the checks (the zombie bite there, a death first, passes).
 */
public class QaBuilderConverted1104BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Struck by lightning mid-STRUCTURE with materials in his bag: none of them is lost and the build isn't left to the witch. */
	//$ gametest_ticks_batch HUGE '6000' '"qaConvertedWitch"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaConvertedWitch")
	public void aBuilderStruckByLightningMidJobLosesNothingAndLeavesTheBuild(GameTestHelper helper) {
		QaBuilderConverted1104GameTests.convertedMidJob(helper, Difficulty.NORMAL, (level, builder) -> {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			bolt.moveTo(builder.position());
			bolt.setVisualOnly(true);
			builder.thunderHit(level, bolt);
		}, Witch.class);
	}
}
