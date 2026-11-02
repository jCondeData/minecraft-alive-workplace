package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B788: a village's graves rest only in its own Graveyards. Two villages of radius 12 side by side: Oak (hall in one
 * corner) and Elm (hall in the far corner), whose Graveyard reaches toward Oak and stands within Oak's old reach (the
 * hall's RADIUS + 16) though it lies in Elm. Elm's Graveyard is Elm's, never Oak's; Oak's own Graveyard is still Oak's;
 * and once Elm's hall is gone its Graveyard lies in no village and is Oak's to use again (as a grave is, B779).
 */
public class B788GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Vec3i SIZE = new Vec3i(13, 11, 13);
	private static final BlockPos OAK_HALL = new BlockPos(2, 2, 2);
	private static final BlockPos OAK_YARD = new BlockPos(3, 2, 3);
	private static final BlockPos ELM_HALL = new BlockPos(26, 2, 26);
	private static final BlockPos ELM_YARD = new BlockPos(16, 2, 16);
	/** Where Oak's Undertaker's table stands (QaVillageReachGraves1101GameTests.undertaker), in Oak. */
	private static final BlockPos OAK_TABLE = new BlockPos(8, 2, 8);

	/** The two villages, radius 12, everything put back (and the halls taken away) after. */
	private static void villages(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.graves(helper);
		int radius = VillageHalls.RADIUS;
		boolean was = Graveyards.ENABLED;
		VillageHalls.RADIUS = 12;
		Graveyards.ENABLED = true;
		helper.setDayTime(2000);
		helper.setBlock(OAK_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(ELM_HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos oak = helper.absolutePos(OAK_HALL);
		BlockPos elm = helper.absolutePos(ELM_HALL);
		Leftovers.after(helper, () -> {
			// the halls go with the test: a hall left standing would claim the next batches' Graveyards near by
			helper.getLevel().removeBlock(oak, false);
			helper.getLevel().removeBlock(elm, false);
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			Graveyards.ENABLED = was;
		});
	}

	/** A finished Graveyard at the relative {@code at}, forgotten when the test ends; its outline. */
	private static BoundingBox yard(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GRAVEYARD.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return BlueprintOutline.bounds(placement, SIZE);
	}

	private static void setup(GameTestHelper helper, BoundingBox elmYard) {
		ServerLevel level = helper.getLevel();
		BlockPos elmMiddle = elmYard.getCenter();
		helper.assertTrue(VillageHalls.containing(level, elmMiddle).equals(Optional.of(helper.absolutePos(ELM_HALL))), "setup: Elm's Graveyard isn't in Elm");
		helper.assertTrue(ELM_YARD.distSqr(OAK_HALL) <= (double) (VillageHalls.RADIUS + 16) * (VillageHalls.RADIUS + 16),
			"setup: Elm's Graveyard is out of Oak's old reach");
	}

	/**
	 * Oak has no Graveyard; Elm's, near Oak, isn't Oak's: Oak's resting places hold none of it, a grave in Elm's yard
	 * doesn't rest in Oak's, and Oak's next slot isn't in Elm's yard. Elm's resting places are its own yard.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b788Neighbor"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b788Neighbor")
	public void aNeighborsGraveyardIsNotThisVillages(GameTestHelper helper) {
		villages(helper);
		BoundingBox elm = yard(helper, ELM_YARD);
		setup(helper, elm);
		ServerLevel level = helper.getLevel();
		BlockPos elmSlot = helper.absolutePos(ELM_YARD.offset(1, 0, 6));
		helper.setBlock(ELM_YARD.offset(1, 0, 6), ModBlocks.GRAVE);
		Graveyards.Resting oak = Graveyards.resting(level, helper.absolutePos(OAK_TABLE));
		helper.assertTrue(helper.absolutePos(OAK_HALL).equals(oak.hall()), "setup: Oak's table isn't in Oak");
		helper.assertTrue(oak.yards().isEmpty(), "Oak's resting places hold Elm's Graveyard: " + oak.yards());
		helper.assertTrue(!oak.holds(elmSlot), "a grave in Elm's Graveyard rests in Oak's");
		helper.assertTrue(oak.slots().stream().noneMatch(elm::isInside), "Oak's slots lie in Elm's Graveyard");
		helper.assertTrue(Graveyards.resting(level, oak).isEmpty(), "Oak counts Elm's grave as resting in Oak: " + Graveyards.resting(level, oak));
		BlockPos next = Graveyards.nextSlot(level, oak);
		helper.assertTrue(next == null || !elm.isInside(next), "Oak's next grave would be laid in Elm's Graveyard at " + next);
		Graveyards.Resting elmRest = Graveyards.resting(level, helper.absolutePos(ELM_HALL).offset(-2, 0, 0), false);
		helper.assertTrue(elmRest.yards().equals(List.of(elm)), "Elm's resting places aren't its own Graveyard: " + elmRest.yards());
		helper.assertTrue(Graveyards.resting(level, elmRest).equals(List.of(elmSlot)), "Elm's grave doesn't rest in Elm");
		helper.succeed();
	}

	/** With both villages having a Graveyard, each village's resting places are its own yard only, and slots fill there. */
	//$ gametest_ticks_batch HUGE '100' '"b788Own"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b788Own")
	public void eachVillageKeepsItsOwnGraveyard(GameTestHelper helper) {
		villages(helper);
		BoundingBox oakYard = yard(helper, OAK_YARD);
		BoundingBox elm = yard(helper, ELM_YARD);
		setup(helper, elm);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageHalls.containing(level, oakYard.getCenter()).equals(Optional.of(helper.absolutePos(OAK_HALL))), "setup: Oak's Graveyard isn't in Oak");
		Graveyards.Resting oak = Graveyards.resting(level, helper.absolutePos(OAK_TABLE));
		helper.assertTrue(oak.yards().equals(List.of(oakYard)), "Oak's resting places aren't just its own Graveyard: " + oak.yards());
		helper.assertTrue(helper.absolutePos(OAK_YARD.offset(1, 0, 6)).equals(Graveyards.nextSlot(level, oak)),
			"Oak's next grave isn't its Graveyard's first slot: " + Graveyards.nextSlot(level, oak));
		Graveyards.Resting elmRest = Graveyards.resting(level, helper.absolutePos(ELM_HALL), false);
		helper.assertTrue(elmRest.yards().equals(List.of(elm)), "Elm's resting places aren't just its own Graveyard: " + elmRest.yards());
		helper.succeed();
	}

	/** Once Elm's hall is gone, its Graveyard lies in no village and Oak's Undertaker may lay Oak's graves there. */
	//$ gametest_ticks_batch HUGE '100' '"b788HallGone"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b788HallGone")
	public void aGraveyardWhoseHallIsGoneIsTheNearVillages(GameTestHelper helper) {
		villages(helper);
		BoundingBox elm = yard(helper, ELM_YARD);
		setup(helper, elm);
		ServerLevel level = helper.getLevel();
		helper.setBlock(ELM_HALL, Blocks.AIR);
		VillageHalls.forgetNearest();
		helper.assertTrue(VillageHalls.containing(level, elm.getCenter()).isEmpty(), "setup: Elm's Graveyard is still in a village");
		Graveyards.Resting oak = Graveyards.resting(level, helper.absolutePos(OAK_TABLE));
		helper.assertTrue(oak.yards().equals(List.of(elm)), "Oak doesn't use the Graveyard left in no village: " + oak.yards());
		helper.succeed();
	}

	/**
	 * End to end: Oak (no Graveyard) has an Undertaker and a grave in its street. Before B788 he laid it in Elm's Graveyard
	 * near by; now it never goes there.
	 */
	//$ gametest_ticks_batch HUGE '700' '"b788Undertaker"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "b788Undertaker")
	public void anUndertakerNeverLaysAGraveInTheNeighborsGraveyard(GameTestHelper helper) {
		villages(helper);
		BoundingBox elm = yard(helper, ELM_YARD);
		setup(helper, elm);
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(5, 2, 11);
		helper.assertTrue(VillageHalls.containing(level, helper.absolutePos(at)).equals(Optional.of(helper.absolutePos(OAK_HALL))), "setup: Ivy's spot isn't in Oak");
		QaVillageReachGraves1101GameTests.dies(helper, at, "Ivy", 0);
		QaVillageReachGraves1101GameTests.undertaker(helper);
		helper.onEachTick(() -> {
			List<BlockPos> inElm = Graves.near(level, helper.absolutePos(ELM_HALL), 16).stream().filter(elm::isInside).toList();
			if (!inElm.isEmpty()) {
				helper.fail("Oak's grave was laid in Elm's Graveyard at " + inElm);
			}
		});
		helper.runAfterDelay(600, helper::succeed);
	}
}
