package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-10-31, saves), from the Create page's "Saved data" (docs/wiki/create.md, 39.5), not the code: the
 * dimension's build sites keep {@code turn_checks}, "each with the build, its placement, owner, the builder's name, the
 * parts' positions, when it's due and how often it has looked", and a finished building's {@code not_turning} is "absent:
 * not checked; empty: everything turned". CreateBuildsGameTests reads back a check's tries and builder only; here every
 * field, a turned and mirrored placement, a check that comes due at the same game time after a server restart, the empty
 * list kept apart from "not checked", and a building taken down leaving neither behind in the save.
 */
public class QaTurnCheckSaves1031GameTests implements FabricGameTest {

	private static BuildSiteManager reload(BuildSiteManager manager, HolderLookup.Provider registries) {
		return BuildSiteManager.load(manager.save(new CompoundTag(), registries), registries);
	}

	private static BlueprintData.Placement at(GameTestHelper helper, int x, Rotation rotation, Mirror mirror) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(x, 70, -40), rotation, mirror);
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aTurningCheckComesBackWholeAndOnTime(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BuildSiteManager manager = BuildSiteManager.load(new CompoundTag(), registries);
		BlueprintData.Placement mill = at(helper, 1000, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
		BlueprintData.Placement wheel = at(helper, 2000, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		UUID owner = UUID.randomUUID();
		BuildSiteManager.TurnCheck first = new BuildSiteManager.TurnCheck(StarterBlueprints.WORKSHOP.id(), mill, owner, "Bramble",
			List.of(new BlockPos(1003, 71, -38), new BlockPos(1003, 72, -38), new BlockPos(-5, -60, 7)), 123_456_789L, 2);
		BuildSiteManager.TurnCheck second = new BuildSiteManager.TurnCheck(StarterBlueprints.WELL.id(), wheel, UUID.randomUUID(), "Ivy",
			List.of(new BlockPos(2001, 70, -41)), 123_456_900L, 0);
		manager.scheduleTurnCheck(first);
		manager.scheduleTurnCheck(second);

		BuildSiteManager back = reload(manager, registries);
		helper.assertTrue(back.turnChecks().size() == 2, "after a restart the checks are " + back.turnChecks());
		BuildSiteManager.TurnCheck loaded = back.turnChecks().stream().filter(c -> c.placement().equals(mill)).findFirst().orElse(null);
		helper.assertTrue(loaded != null, "the mill's check (turned and mirrored) lost its placement: " + back.turnChecks());
		helper.assertTrue(loaded.equals(first), "the mill's check came back as " + loaded + ", saved as " + first);
		helper.assertTrue(back.turnChecks().contains(second), "the wheel's check came back as " + back.turnChecks());

		// it looks when it was due before the restart, not sooner nor later
		helper.assertTrue(back.dueTurnChecks(123_456_788L).isEmpty(), "a check came due early after a restart");
		helper.assertTrue(back.dueTurnChecks(123_456_789L).equals(List.of(first)), "the mill's check didn't come due on time after a restart");
		helper.assertTrue(back.turnChecks().equals(List.of(second)), "taking the due check off left " + back.turnChecks());
		// and once taken off, a second save doesn't bring it back
		helper.assertTrue(reload(back, registries).turnChecks().equals(List.of(second)), "a check already looked at came back after the next save");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everythingTurnedIsKeptApartFromNotChecked(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BuildSiteManager manager = BuildSiteManager.load(new CompoundTag(), registries);
		BlueprintData.Placement turned = at(helper, 3000, Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement stuck = at(helper, 3100, Rotation.CLOCKWISE_180, Mirror.LEFT_RIGHT);
		BlueprintData.Placement unchecked = at(helper, 3200, Rotation.NONE, Mirror.NONE);
		UUID owner = UUID.randomUUID();
		manager.recordFinished(StarterBlueprints.WORKSHOP.id(), turned, owner);
		manager.recordFinished(StarterBlueprints.WORKSHOP.id(), stuck, owner);
		manager.recordFinished(StarterBlueprints.WELL.id(), unchecked, owner);
		manager.setNotTurning(turned, List.of());
		List<BlockPos> parts = List.of(new BlockPos(3101, 71, -40), new BlockPos(3102, 71, -40));
		manager.setNotTurning(stuck, parts);

		BuildSiteManager back = reload(manager, registries);
		helper.assertTrue(back.notTurning(turned).equals(Optional.of(List.of())),
			"a mill whose check found everything turning loads as " + back.notTurning(turned) + " (the page: empty, everything turned)");
		helper.assertTrue(back.notTurning(stuck).equals(Optional.of(parts)), "the parts that didn't turn came back as " + back.notTurning(stuck));
		helper.assertTrue(back.notTurning(unchecked).isEmpty(), "a building never checked loads as checked: " + back.notTurning(unchecked));

		// a contraption taken down takes its check and its result with it, in the save too
		back.scheduleTurnCheck(new BuildSiteManager.TurnCheck(StarterBlueprints.WORKSHOP.id(), stuck, owner, "Bramble", parts, 500L, 1));
		back.forgetFinished(stuck);
		BuildSiteManager again = reload(back, registries);
		helper.assertTrue(again.notTurning(stuck).isEmpty() && again.turnChecks().isEmpty(),
			"a building taken down left its turning record in the save: " + again.notTurning(stuck) + ", " + again.turnChecks());
		helper.assertTrue(again.notTurning(turned).equals(Optional.of(List.of())), "the other mill's record went with it: " + again.notTurning(turned));

		// built again on the same spot, it's a new building: not checked yet
		again.recordFinished(StarterBlueprints.WORKSHOP.id(), turned, owner);
		helper.assertTrue(reload(again, registries).notTurning(turned).isEmpty(), "a building rebuilt on the spot kept the old one's check");
		helper.succeed();
	}
}
