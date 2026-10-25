package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane-d-1025-0408), failing on purpose until fixed: ROADMAP 32.20 gives Palisade II "a fighting step" (to stand on
 * and shoot over the low stakes) and says battle stations know the upgrades. The base Palisade's walkway of top slabs is
 * a row of battle stations; Palisade II's step (planks at y 2 under bottom slabs at y 3) is none, so upgrading a Palisade
 * takes every archer's station off it.
 */
public class QaDefenceUpgradeBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void palisadeIIsFightingStepIsABattleStation(GameTestHelper helper) {
		Blueprint base = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.PALISADE.id()).orElseThrow();
		Blueprint up = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.PALISADE_2.id()).orElseThrow();
		List<BlockPos> before = BattleStations.spots(base);
		helper.assertTrue(before.size() >= 4, "the Palisade's walkway has " + before.size() + " stations: " + before);
		int depth = StarterBlueprints.PALISADE.size().getZ();
		List<BlockPos> step = BattleStations.spots(up).stream().filter(p -> p.getZ() < depth).toList();
		helper.assertTrue(step.size() >= before.size(), "Palisade II's fighting step has " + step.size() + " battle stations (the Palisade had "
			+ before.size() + "): " + BattleStations.spots(up));
		helper.succeed();
	}
}
