package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane d, 2026-10-26) for B356 and 32.10: when the Piglin Warlord falls his band, at the outpost and raiding, "turns
 * zombified ... and stays", on a server whose mob blocklist refuses every hostile mob (Cobbleverse's MobsBeGone, with
 * {@code forceThreatSpawns} on). Staying means more than surviving the turn: each turned mob must not despawn once the
 * players walk away (persistent, as the band was), or the band would melt away the moment the player leaves to fetch
 * help. And a turned mob is no longer one of a lair's band (32.10: "it is no more one of a lair's band").
 */
public class QaWarlordBandStaysGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final long DAY = 24000;

	//$ gametest_ticks_batch HUGE '60' '"qaWarlordBandStays"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWarlordBandStays")
	public void theTurnedBandStaysWhenThePlayersWalkAway(GameTestHelper helper) {
		BlockPos hall = QaRefusedThreatsGameTests.village(helper, 20);
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			level.setDayTime(44 * DAY + 15000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null, "setup: no outpost");
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null, "setup: no Warlord");
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0, Double.NaN, Threats.get(PIGLINS).orElseThrow(), RandomSource.create(7L));
			helper.assertTrue(raid != null && raid.raiders() > 0, "setup: no raid");
			AABB around = helper.getBounds().inflate(32);
			int before = level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && !m.getUUID().equals(lair.captain())
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))).size();
			warlord.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			List<Mob> turned = level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && (m instanceof ZombifiedPiglin || m instanceof Zoglin));
			helper.assertTrue(turned.size() >= before && before > 0, "setup: " + turned.size() + " turned of " + before);
			StringBuilder fleeting = new StringBuilder();
			StringBuilder banded = new StringBuilder();
			for (Mob m : turned) {
				if (!m.isPersistenceRequired() && !m.requiresCustomPersistence()) {
					fleeting.append(m.getType().getDescriptionId()).append(m.getTags().contains(Threats.RAIDER_TAG) ? " (raider)" : " (band)").append("; ");
				}
				if (m.getTags().contains(Lairs.TAG)) {
					banded.append(m.getType().getDescriptionId()).append("; ");
				}
			}
			helper.assertTrue(fleeting.isEmpty(), "turned mobs that despawn once the players walk away (the band doesn't stay): " + fleeting);
			helper.assertTrue(banded.isEmpty(), "turned mobs still counted in a lair's band: " + banded);
			helper.succeed();
		});
	}
}
