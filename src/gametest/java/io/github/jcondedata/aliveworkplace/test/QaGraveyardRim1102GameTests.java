package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for B788 from its spec ("only Graveyards inside this village are its own"), at the edges its own tests leave out:
 * a Graveyard whose middle lies exactly on one village's rim, one block past it (and so in the neighbor's circle), and
 * two villages whose circles overlap, where a Graveyard in both belongs to the nearer hall. Radius 12 throughout.
 */
public class QaGraveyardRim1102GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Vec3i SIZE = new Vec3i(13, 11, 13);
	private static final BlockPos OAK_HALL = new BlockPos(2, 2, 8);

	/** Oak at {@link #OAK_HALL} and Elm at {@code elmHall}, radius 12, everything put back (halls removed) after. */
	private static void villages(GameTestHelper helper, BlockPos elmHall) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.graves(helper);
		int radius = VillageHalls.RADIUS;
		boolean was = Graveyards.ENABLED;
		VillageHalls.RADIUS = 12;
		Graveyards.ENABLED = true;
		helper.setBlock(OAK_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(elmHall, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos oak = helper.absolutePos(OAK_HALL);
		BlockPos elm = helper.absolutePos(elmHall);
		Leftovers.after(helper, () -> {
			helper.getLevel().removeBlock(oak, false);
			helper.getLevel().removeBlock(elm, false);
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			Graveyards.ENABLED = was;
		});
	}

	/** A finished Graveyard at the relative {@code at} (no rotation), forgotten when the test ends; its outline. */
	private static BoundingBox yard(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GRAVEYARD.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return BlueprintOutline.bounds(placement, SIZE);
	}

	private static List<BoundingBox> yardsOf(GameTestHelper helper, BlockPos hall) {
		return Graveyards.resting(helper.getLevel(), helper.absolutePos(hall), false).yards();
	}

	private static void assertIn(GameTestHelper helper, BoundingBox yard, BlockPos hall, String what) {
		Optional<BlockPos> village = VillageHalls.containing(helper.getLevel(), yard.getCenter());
		helper.assertTrue(village.equals(Optional.of(helper.absolutePos(hall))), "setup: " + what + "'s middle isn't in that village: " + village);
	}

	/** Elm 25 east of Oak. A Graveyard whose middle is 12 east of Oak's hall (on Oak's rim, 13 from Elm's) is Oak's only. */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveRimIn"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveRimIn")
	public void qaAGraveyardWhoseMiddleIsOnTheRimIsThatVillages(GameTestHelper helper) {
		BlockPos elmHall = new BlockPos(27, 2, 8);
		villages(helper, elmHall);
		BoundingBox yard = yard(helper, new BlockPos(8, 2, 2));
		assertIn(helper, yard, OAK_HALL, "the rim Graveyard");
		helper.assertTrue(yardsOf(helper, OAK_HALL).equals(List.of(yard)), "a Graveyard on Oak's rim isn't Oak's: " + yardsOf(helper, OAK_HALL));
		helper.assertTrue(yardsOf(helper, elmHall).isEmpty(), "a Graveyard on Oak's rim counts as Elm's: " + yardsOf(helper, elmHall));
		helper.succeed();
	}

	/** One block further east (13 from Oak, 12 from Elm) the Graveyard is Elm's only, though it reaches into Oak. */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveRimOut"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveRimOut")
	public void qaAGraveyardOneBlockPastTheRimIsTheNeighbors(GameTestHelper helper) {
		BlockPos elmHall = new BlockPos(27, 2, 8);
		villages(helper, elmHall);
		BoundingBox yard = yard(helper, new BlockPos(9, 2, 2));
		assertIn(helper, yard, elmHall, "the Graveyard past Oak's rim");
		helper.assertTrue(yardsOf(helper, OAK_HALL).isEmpty(), "a Graveyard past Oak's rim, in Elm, counts as Oak's: " + yardsOf(helper, OAK_HALL));
		helper.assertTrue(yardsOf(helper, elmHall).equals(List.of(yard)), "a Graveyard in Elm isn't Elm's: " + yardsOf(helper, elmHall));
		helper.assertTrue(!Graveyards.resting(helper.getLevel(), helper.absolutePos(OAK_HALL), false).holds(yard.getCenter()),
			"Oak holds a spot in Elm's Graveyard");
		helper.succeed();
	}

	/**
	 * Elm 18 east of Oak, so their circles overlap. A Graveyard whose middle is 8 from Oak and 10 from Elm is Oak's; one
	 * 10 from Oak and 8 from Elm is Elm's. Neither village takes the other's.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveOverlap"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveOverlap")
	public void qaWhereVillagesOverlapAGraveyardIsTheNearerHalls(GameTestHelper helper) {
		BlockPos elmHall = new BlockPos(20, 2, 8);
		villages(helper, elmHall);
		BoundingBox oakYard = yard(helper, new BlockPos(4, 2, 2));
		BoundingBox elmYard = yard(helper, new BlockPos(6, 2, 2));
		helper.assertTrue(VillageHalls.contains(helper.absolutePos(elmHall), oakYard.getCenter())
			&& VillageHalls.contains(helper.absolutePos(OAK_HALL), elmYard.getCenter()), "setup: the Graveyards aren't in both circles");
		assertIn(helper, oakYard, OAK_HALL, "the Graveyard nearer Oak");
		assertIn(helper, elmYard, elmHall, "the Graveyard nearer Elm");
		helper.assertTrue(yardsOf(helper, OAK_HALL).equals(List.of(oakYard)), "Oak's resting places aren't just the Graveyard nearer Oak: " + yardsOf(helper, OAK_HALL));
		helper.assertTrue(yardsOf(helper, elmHall).equals(List.of(elmYard)), "Elm's resting places aren't just the Graveyard nearer Elm: " + yardsOf(helper, elmHall));
		helper.succeed();
	}
}
