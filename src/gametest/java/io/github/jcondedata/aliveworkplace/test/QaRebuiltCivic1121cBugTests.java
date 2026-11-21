package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.pathfinder.Path;

/**
 * QA lane C (2026-11-21), bug candidates found by {@link QaRebuiltCivic1121cGameTests}: B1423, fixed by new grave slots
 * that leave a walk through each side of the yard (and a check that graves laid in the old slots on old saves still rest
 * in the Graveyard).
 *
 * <p>graveyard.md, "Each slot reachable on foot": "every slot has a cell a villager can stand on within 2 blocks, reached
 * on foot from the job block and from the street outside" (the Undertaker walks to within 2.5 blocks of a grave to revive
 * it; mourners to within 3). The grave slots ({@code Graveyards.YARD_SLOTS}: rows z = 1 and z = 6 at x 1-4 and 8-11, and
 * columns x 1, 4, 8, 11 for z 2-5) ring two 2 x 4 pockets (x 2-3 and 9-10, z 2-5) and the strips behind row 6, so once
 * the yard is full every grave's headstone walls in its neighbours: a villager in the street finds no path to the graves
 * along the walls (x 1, x 11, the corners of rows 1 and 6), only to the ones beside the way in.
 */
public class QaRebuiltCivic1121cBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** The Graveyard placed with a grave in each of its 32 slots: from the street, the game's pathfinder reaches every grave. */
	//$ gametest_ticks_batch HUGE '100' '"qaCivic1121cYardFull"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCivic1121cYardFull")
	public void qaEveryGraveInAFullGraveyardCanBeWalkedTo(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		BlockPos yard = new BlockPos(8, 2, 8);
		BlockPos origin = QaRebuiltCivic1121cGameTests.place(helper, StarterBlueprints.GRAVEYARD, yard);
		List<BlockPos> slots = QaRebuiltCivic1121cGameTests.yardSlots(helper);
		for (BlockPos local : slots) {
			level.setBlockAndUpdate(origin.offset(local), ModBlocks.GRAVE.defaultBlockState());
		}
		Villager walker = helper.spawn(EntityType.VILLAGER, yard.offset(6, 0, -3));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true); // (a villager without AI never lands by itself: the pathfinder wants it on the ground)
			List<String> cut = new ArrayList<>();
			for (BlockPos local : slots) {
				Path path = walker.getNavigation().createPath(origin.offset(local), 2);
				if (path == null || !path.canReach()) {
					cut.add(local.toShortString());
				}
			}
			walker.discard();
			helper.assertTrue(cut.isEmpty(), cut.size() + " of " + slots.size() + " graves in a full Graveyard have no path from the street to within reach: " + cut);
			helper.succeed();
		});
	}

	/**
	 * B1423, old saves: a grave the Undertaker laid in one of the slots of before the fix (the walk at (3, 0, 6) was one)
	 * still rests in its Graveyard: it is listed for revival from there and is not a stray to move; no new grave is sent
	 * to that cell.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAGraveLaidInAnOldSlotStillRestsInTheGraveyard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(0, 60, 0));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GRAVEYARD.id(), placement, UUID.randomUUID());
		BlockPos old = origin.offset(3, 0, 6);
		try {
			level.setBlockAndUpdate(old, ModBlocks.GRAVE.defaultBlockState());
			Graveyards.Resting resting = Graveyards.resting(level, origin, false);
			helper.assertFalse(resting.slots().contains(old), "the walk (3, 0, 6) is still a slot a grave is sent to");
			helper.assertTrue(Graveyards.resting(level, resting).contains(old), "a grave in an old slot isn't listed as resting in the Graveyard");
			helper.assertTrue(Graveyards.strays(level, List.of(old), resting).isEmpty(), "a grave in an old slot is a stray to move");
		} finally {
			level.setBlockAndUpdate(old, Blocks.AIR.defaultBlockState());
			BuildSiteManager.get(level).forgetFinished(placement);
		}
		helper.succeed();
	}
}
