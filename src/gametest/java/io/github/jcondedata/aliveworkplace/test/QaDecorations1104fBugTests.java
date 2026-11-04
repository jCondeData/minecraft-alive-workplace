package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (2026-11-04, lane f) of ROADMAP 26.14's decorations: what fails because the mod is wrong. Setup and checks are
 * {@link QaDecorations1104fGameTests#takeDown}.
 */
public class QaDecorations1104fBugTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/**
	 * Failure: "aliveworkplace:lily_pond taken down still has 96 of its built blocks standing ({grass_block=17,
	 * cobblestone=6, stone=7, mossy_cobblestone=6, clay=15, water=42, stone_brick_slab=3}; the take-down planned 101
	 * steps, skipped 54)". Setup: a Builder's Bench with empty chests; exactly the Lily Pond's Material List (the held
	 * blueprint's) put in them; a builder builds it (it finishes as drawn); then its placed blueprint is sneak-handed to
	 * him (the guide's take-down). Likely cause (from BuilderWork.takeDown, not traced): the builder takes each water source away, but the sources beside it fill the hole
	 * again (two sources over the clay make a new one), so he gives the water up (skipped), then the bank and kerb under
	 * and round it too, and the take-down "finishes" with the pond still there and water spread onto the bank. A player
	 * sees the pond he asked to have taken down still standing (less its lily pads and plants). Expected: every block of
	 * it comes down, no water left in or round its footprint, and what it cost back in the chests. Also measured 100 ticks
	 * after: 337 water blocks in the area, 269 outside its footprint, and 16 water buckets + 14 empty buckets in the
	 * chests for the 15 water buckets paid (15 buckets made from nothing). Same numbers in 3 runs.
	 */
	//$ gametest_ticks_batch BIG '16000' '"qaDeco1104fDownPond"'
	@GameTest(template = BIG, timeoutTicks = 16000, batch = "qaDeco1104fDownPond")
	public void lilyPondTakenDownLeavesNoWaterAndGivesBackItsCost(GameTestHelper helper) {
		QaDecorations1104fGameTests.takeDown(helper, StarterBlueprints.LILY_POND);
	}

	/**
	 * Failure: "aliveworkplace:footbridge taken down still has 49 of its built blocks standing ({water=49}; the take-down
	 * planned 78 steps, skipped 0)"; 100 ticks after it, 176 water blocks in the area, 127 of them outside its footprint,
	 * and 20 water buckets + 10 empty buckets in the chests for the 15 water buckets paid. Setup as the Lily Pond's above,
	 * with the Footbridge (a 5 x 3 channel of water in a stone kerb). Every block but the water comes down; the water
	 * sources taken away likely fill again from their neighbors, so the channel's water stays and floods the ground once its
	 * kerb is gone, and each source taken hands the builder a water bucket (buckets made from nothing). A player sees a
	 * puddle spreading where the bridge stood and more buckets than he gave. Expected: no water left, 15 buckets
	 * (full or empty) back, every other item of the list back.
	 */
	//$ gametest_ticks_batch BIG '16000' '"qaDeco1104fDownBridge"'
	@GameTest(template = BIG, timeoutTicks = 16000, batch = "qaDeco1104fDownBridge")
	public void footbridgeTakenDownLeavesNoWaterAndGivesBackItsCost(GameTestHelper helper) {
		QaDecorations1104fGameTests.takeDown(helper, StarterBlueprints.FOOTBRIDGE);
	}
}
