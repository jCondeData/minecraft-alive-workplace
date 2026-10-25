package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1025-1838), B173 follow-up: failing on main (2026-10-25), so NOT registered in fabric.mod.json. Register
 * it once the blueprints are fixed.
 * <ul>
 * <li>The doorway sweep (QaDoorwayClearanceGameTests.tightDoorways) over every listed build finds doors with a full
 * block's step right in front of or behind them under a two-high lintel: the Healing Center III/IV garden door
 * (5,1,14: an upside-down stone brick stair at 5,1,15), the Pokemon Center lodge look II front door (6,1,10: an
 * upside-down spruce stair at 6,1,11), the Royal Hall's treasury door (11,1,19: the throne dais right behind it) and
 * Royal Hall II's east door (16,1,17: the hall floor a block higher at 15,1,17) and treasury door.</li>
 * <li>Real walks through three of them, every other door of the build walled up, by day, door shut as built, a nitwit walked by vanilla's walk target.</li>
 * </ul>
 */
public class QaDoorwayBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedBuildsDoorwaysLeaveAVillagerRoomOverTheStep(GameTestHelper helper) {
		int[] counts = {0, 0};
		List<String> problems = QaDoorwayClearanceGameTests.tightDoorways(helper, counts);
		String all = String.join("; ", problems);
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[QA B173] " + counts[0] + " builds, " + counts[1]
			+ " wooden doors checked, " + problems.size() + " builds with a doorway under 1.95: " + all));
		helper.assertTrue(counts[0] > 100 && counts[1] > 300, "only " + counts[0] + " builds and " + counts[1] + " doors checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " builds with a doorway a villager can't stand up in: "
			+ (all.length() > 800 ? all.substring(0, 800) + "..." : all));
		helper.succeed();
	}

	/** The Healing Center III: from its back garden in by the garden door (5,1,14) to the ward (5,1,13). */
	//$ gametest_ticks_batch AREA '500' '"qaB173HealingGarden"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaB173HealingGarden")
	public void aVillagerWalksInFromTheHealingCenterIIIsGarden(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "healing_center_3", Rotation.NONE, new BlockPos(9, 2, 4), new int[] {5, 1, 18}, new int[] {5, 1, 12}, new int[] {5, 1, 14});
	}

	/** Royal Hall II: from outside its east door (16,1,17) onto the hall floor right inside it (15,2,17). */
	//$ gametest_ticks_batch AREA '500' '"qaB173RoyalHallEast"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaB173RoyalHallEast")
	public void aVillagerWalksInByRoyalHallIIsEastDoor(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "realm/royal_hall_2", Rotation.NONE, new BlockPos(1, 2, 2), new int[] {19, 1, 17}, new int[] {15, 2, 17}, new int[] {16, 1, 17});
	}

	/** The Pokemon Center lodge look II: from the path in by the front door (6,1,10) to (6,1,8). */
	//$ gametest_ticks_batch AREA '500' '"qaB173LodgeFront"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaB173LodgeFront")
	public void aVillagerWalksInByThePokemonLodgesFrontDoor(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "looks/lodge/pokemon_center_2", Rotation.NONE, new BlockPos(4, 2, 4), new int[] {6, 1, 13}, new int[] {6, 1, 8}, new int[] {6, 1, 10});
	}
}
