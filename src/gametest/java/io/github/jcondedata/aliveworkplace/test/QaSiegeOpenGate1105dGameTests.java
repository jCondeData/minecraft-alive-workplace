package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.FenceGateBlock;

/**
 * QA (lane D, 2026-11-05): sieges.md, "Players can still open the fence gates by hand." A player opens the Palisade
 * Gate's middle gate as the siege begins. The siege goes on as it would with the gate shut: the ram still breaks it
 * (nothing drops), and the pillager behind it gets in within the time the shut gate allows (600 ticks, as
 * {@code SiegeGameTests.aRamBreaksThePalisadeGate}); the open gate never leaves the raiders standing outside.
 */
public class QaSiegeOpenGate1105dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch AREA '900' '"qa1105dOpenGate"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qa1105dOpenGate")
	public void aGateAPlayerOpensMidSiegeStillLetsTheSiegeRunItsCourse(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = SiegeGameTests.raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), "no siege");
		level.setBlockAndUpdate(middle, level.getBlockState(middle).setValue(FenceGateBlock.OPEN, true)); // a player's hand
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (SiegeGameTests.through(helper, yard, pillager)) {
				helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "a gate dropped something");
				helper.succeed();
			} else if (tick > 600) {
				helper.fail("with the middle gate opened by hand the pillager isn't through within 600 ticks: pillager "
					+ SiegeGameTests.state(helper, pillager) + "; ram " + SiegeGameTests.state(helper, ram) + "; middle gate "
					+ (SiegeGameTests.isFenceGate(level, middle) ? (level.getBlockState(middle).getValue(FenceGateBlock.OPEN) ? "open" : "shut") + ", "
						+ Sieges.hitPointsLeft(level, yard.hall(), middle) + " hit points" : "gone"));
			}
		});
	}
}
