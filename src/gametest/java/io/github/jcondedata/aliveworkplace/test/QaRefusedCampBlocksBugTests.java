package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-10-31), from B208's fix as the ROADMAP states it: "no camp without its captain". With
 * {@code forceThreatSpawns} off and another mod refusing hostile mobs (Cobbleverse's MobsBeGone lists pillagers and
 * vindicators), a bandit camp whose chief can't spawn isn't made. Then the land must be as it was: a camp that "isn't
 * made" must not leave its tents, fire and chests standing (no record of it means no cleanup ever takes it down, and
 * each later roll can stamp another).
 */
public class QaRefusedCampBlocksBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);

	//$ gametest_ticks_batch AREA '100' '"qaRefusedCampBlocks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRefusedCampBlocks")
	public void aCampNotMadeLeavesNoBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		HostileSpawns.forget();
		HostileSpawns.FORCE = false;
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof Enemy;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			BanditCamps.forget(level);
			VillageHalls.RADIUS = radius;
		});
		helper.runAfterDelay(2, () -> {
			BlockPos camp = helper.absolutePos(CAMP);
			Map<BlockPos, BlockState> before = new HashMap<>();
			for (BlockPos p : BlockPos.betweenClosed(camp.offset(-12, -4, -12), camp.offset(12, 10, 12))) {
				before.put(p.immutable(), level.getBlockState(p));
			}
			helper.assertTrue(BanditCamps.found(level, hall, camp) == null && Lairs.near(level, hall).isEmpty(), "setup: the camp was made without its chief");
			List<String> changed = new ArrayList<>();
			before.forEach((p, state) -> {
				BlockState now = level.getBlockState(p);
				if (!now.equals(state) && changed.size() < 8) {
					changed.add(p.subtract(camp).toShortString() + " " + state.getBlock().getName().getString() + " -> " + now.getBlock().getName().getString());
				}
			});
			long count = before.entrySet().stream().filter(e -> !level.getBlockState(e.getKey()).equals(e.getValue())).count();
			helper.assertTrue(count == 0, "the camp wasn't made (its chief couldn't spawn), yet " + count + " blocks round it changed, e.g. " + changed);
			helper.succeed();
		});
	}
}
