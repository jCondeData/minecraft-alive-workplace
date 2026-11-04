package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug tests for Conscription (30.10), lane d 2026-11-04. NOT registered in fabric.mod.json: each fails on the mod as it
 * is, and goes in with its fix.
 */
public class QaConscriptionBugs1104dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation CONSCRIPTION = AliveWorkplace.id("conscription");

	/**
	 * "Goes for the nearest raider within 24 blocks": a conscript up on a 10-block tower with a raider 6 blocks out from its
	 * foot (11.7 blocks away, in plain sight) is drawn into the fight. The search box is only 8 blocks tall each way
	 * ({@code MilitiaCombat#findFoe}), so a raider more than 8 blocks below or above is never seen, though the reformed
	 * work stop ({@code Conscription#raiderNear}) counts the same raider as near.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaConscriptBelow1104d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaConscriptBelow1104d")
	public void aConscriptOnATowerFightsARaiderTenBlocksDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageRaids.forget();
			VillageNeeds.forget();
			CivicEffects.forget();
			Conscription.forget();
			Moods.forget();
		});
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int y = 2; y <= 11; y++) {
			helper.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
		}
		AtomicBoolean fought = new AtomicBoolean();
		Villager[] watched = new Villager[1];
		// (registered at the start: onEachTick from inside a delayed step grows the map the test is iterating)
		helper.onEachTick(() -> fought.compareAndSet(false, watched[0] != null && MilitiaCombat.isFighting(watched[0])));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.setReforms(List.of());
			entity.setRaidWorkUntil(0);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			Leftovers.after(helper, () -> {
				entity.setEdicts(List.of());
				level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
			});
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 14000);
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 12, 4));
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			farmer.setVillagerXp(10);
			farmer.refreshBrain(level);
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(10, 2, 4));
			zombie.setNoAi(true);
			zombie.setPersistenceRequired();
			zombie.addTag(VillageRaids.TAG);
			VillageRaids.forget();
			VillageRaids.track(level, hall, 1);
			CivicEffects.forget();
			Conscription.forget();
			watched[0] = farmer;
			helper.runAfterDelay(150, () -> {
				double d = Math.sqrt(farmer.distanceToSqr(zombie));
				boolean sight = farmer.hasLineOfSight(zombie);
				boolean near = Conscription.raiderNear(level, farmer, 24);
				boolean armed = Conscription.isMilitiaSword(farmer.getMainHandItem());
				zombie.discard();
				VillageRaids.forget();
				helper.assertTrue(armed, "the conscript on the tower isn't armed: the raid wasn't staged");
				helper.assertTrue(fought.get(), String.format("a conscript on a tower never fought the raider %.1f blocks below him (in sight: %s; the reformed work stop counts it as near: %s)", d, sight, near));
				helper.succeed();
			});
		});
	}
}
