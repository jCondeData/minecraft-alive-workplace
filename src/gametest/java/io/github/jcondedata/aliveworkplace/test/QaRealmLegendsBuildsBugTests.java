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
 * QA bug tests for ROADMAP 33.23 (NOT registered: they fail on the current code; the builder registers each with its fix).
 *
 * Bug: the Founder makes a village wait LONGER when the server's colony wait is 0 days. The config allows
 * {@code colonyCooldownDays} 0 (range 0 to 60). Spec: "waits half as long between colonies". Half of 0 is 0, but
 * {@code Colonies.cooldownDays} floors the Founder's wait at 1 ({@code Math.max(cooldown > 0 ? 1 : 0, ...)}), so a
 * village with the Founder must wait a day after each colony while every other village may send the next at once.
 * Expected: 0 days with and without him. Seen: 1 day with him.
 */
public class QaRealmLegendsBuildsBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	//$ gametest_ticks_batch AREA '100' '"qaFounderZeroWait"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderZeroWait")
	public void theFounderNeverLengthensANoWaitServer(GameTestHelper helper) {
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
		Colonies.COOLDOWN_DAYS = 0;
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
		helper.assertTrue(Colonies.cooldownDays(level, hall) == 0 && Colonies.cooldownLeft(level, hall) == 0, "a 0-day server without the Founder waits " + Colonies.cooldownDays(level, hall));

		Legends.reload(level.getServer().getResourceManager());
		Legend founder = Legends.get(Founder.ID).orElseThrow();
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(Founder.ID, founder);
		Legends.setForTest(map);
		Villager him = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 6));
		him.setNoAi(true);
		Legends.make(level, him, founder, "test", Optional.of(hall));
		LegendPowers.forget();
		helper.assertTrue(Colonies.cooldownDays(level, hall) == 0 && Colonies.cooldownLeft(level, hall) == 0,
			"with the Founder a 0-day wait became " + Colonies.cooldownDays(level, hall) + " (half of 0 is 0)");
		helper.succeed();
	}
}
