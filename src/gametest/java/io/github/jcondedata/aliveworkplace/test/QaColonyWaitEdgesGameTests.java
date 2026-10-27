package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane E, 2026-10-27), config extremes for B589's fix and 33.23 ("the Founder's village waits half as long between
 * colonies"): with the Founder, colonyCooldownDays 0 stays 0 (B589's own test), 1 stays 1 (half a day is still a day:
 * the floor holds), 7 becomes 4 and the largest the config allows, 60, becomes 30; without him each is its own.
 */
public class QaColonyWaitEdgesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	//$ gametest_ticks_batch AREA '100' '"qaColonyWaitEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaColonyWaitEdges")
	public void theFoundersHalfWaitAtOneSevenAndSixtyDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			RealmData.get(level.getServer()).clearColonies();
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				LegendRecord.get(level).forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setRank(VillageRanks.Rank.CITY);
		BlockPos hall = helper.absolutePos(HALL);
		RealmData data = RealmData.get(level.getServer());
		data.clearColonies();
		data.recordFounded(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(400, 0, 0)), Chronicle.day(level));
		int[][] cases = {{1, 1}, {7, 4}, {60, 30}};
		for (int[] c : cases) {
			Colonies.COOLDOWN_DAYS = c[0];
			helper.assertTrue(Colonies.cooldownDays(level, hall) == c[0] && Colonies.cooldownLeft(level, hall) == c[0],
				"without the Founder a " + c[0] + "-day wait is " + Colonies.cooldownDays(level, hall) + " (" + Colonies.cooldownLeft(level, hall) + " left)");
		}

		Legends.reload(level.getServer().getResourceManager());
		Legend founder = Legends.get(Founder.ID).orElseThrow();
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(Founder.ID, founder);
		Legends.setForTest(map);
		Villager him = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 6));
		him.setNoAi(true);
		Legends.make(level, him, founder, "test", Optional.of(hall));
		LegendPowers.forget();
		for (int[] c : cases) {
			Colonies.COOLDOWN_DAYS = c[0];
			helper.assertTrue(Colonies.cooldownDays(level, hall) == c[1] && Colonies.cooldownLeft(level, hall) == c[1],
				"with the Founder a " + c[0] + "-day wait is " + Colonies.cooldownDays(level, hall) + " (" + Colonies.cooldownLeft(level, hall)
					+ " left), not " + c[1]);
		}
		helper.succeed();
	}
}
