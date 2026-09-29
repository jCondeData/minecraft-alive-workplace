package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.GuardPatrol;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Monster raids on villages with a Village Hall, and guards in raids. */
public class RaidGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** Small villages are left alone; bigger ones are likelier to be raided, up to a point. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void biggerVillagesAreRaidedMoreOften(GameTestHelper helper) {
		helper.assertTrue(VillageRaids.nightlyChance(5) == 0f, "a small village raided");
		helper.assertTrue(Math.abs(VillageRaids.nightlyChance(8) - 0.15f) < 1e-4, "8: " + VillageRaids.nightlyChance(8));
		helper.assertTrue(VillageRaids.nightlyChance(20) > VillageRaids.nightlyChance(8), "20");
		helper.assertTrue(VillageRaids.nightlyChance(100) <= 0.35f, "100: " + VillageRaids.nightlyChance(100));
		helper.succeed();
	}

	/** A raid: raiders gather at the edge of the village, it's in the chronicle, and once they're dead the village has won. */
	//$ gametest_ticks_batch AREA '100' '"aRaidIsFoughtOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aRaidIsFoughtOff")
	public void aRaidIsFoughtOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageRaids.forget();
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		for (int i = 0; i < 8; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(8 + i % 4, 2, 9 + i / 4));
		}
		helper.runAfterDelay(2, () -> {
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null && raid.raiders() == 5, "raid: " + raid);
			var raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 5, "raiders about: " + raiders.size());
			helper.assertTrue(raiders.stream().allMatch(Mob::isPersistenceRequired), "raiders would despawn");
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.RAID), "not in the chronicle");
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "no raid under way");
			// Guards anywhere in the village defend all of it while it's raided.
			helper.assertTrue(VillageRaids.raidArea(level, helper.absolutePos(new BlockPos(2, 2, 2))).map(a -> a.contains(helper.absoluteVec(
				new net.minecraft.world.phys.Vec3(20, 2, 20)))).orElse(false), "the raid area");
			raiders.forEach(m -> m.hurt(level.damageSources().fellOutOfWorld(), 1000f));
			VillageRaids.tick(level, hall, 8, 0, Chronicle.day(level), day -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end");
			helper.assertTrue(entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).count() == 2, "no victory in the chronicle");
			helper.succeed();
		});
	}

	/**
	 * Bandits make camp: the chief (in iron, tougher) and his men, a chest of loot; while the camp stands the village's raids
	 * are bandits; when the chief falls the band scatters and the camp is broken up.
	 */
	//$ gametest_ticks_batch AREA '100' '"banditCamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "banditCamp")
	public void aBanditCampIsBrokenUpWhenItsChiefFalls(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageRaids.forget();
			BanditCamps.forget(level);
		});
		BlockPos hallAt = new BlockPos(1, 2, 1);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		helper.runAfterDelay(2, () -> {
			BanditCamps.Camp camp = BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(12, 1, 12)));
			helper.assertTrue(camp != null && BanditCamps.near(level, hall).isPresent(), "no camp: " + camp);
			net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(camp.pos()).inflate(12);
			var band = level.getEntitiesOfClass(Mob.class, around, m -> m.getTags().contains(BanditCamps.TAG));
			helper.assertTrue(band.size() >= 4, "the band: " + band.size());
			Mob chief = (Mob) level.getEntity(camp.chief());
			helper.assertTrue(chief != null && chief.getTags().contains(BanditCamps.CHIEF_TAG) && chief.getMaxHealth() > 40, "the chief: " + chief);
			helper.assertTrue(band.stream().allMatch(Mob::isPersistenceRequired), "bandits would despawn");
			boolean loot = false;
			for (BlockPos p : BlockPos.betweenClosed(camp.pos().offset(-8, 0, -8), camp.pos().offset(8, 3, 8))) {
				if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
					loot = true;
				}
			}
			helper.assertTrue(loot, "no chest of loot in the camp");
			// A raid while the camp stands: bandits, not monsters.
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null, "no raid");
			var raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(!raiders.isEmpty() && raiders.stream().allMatch(m -> m instanceof net.minecraft.world.entity.monster.AbstractIllager),
				"raiders: " + raiders);
			raiders.forEach(m -> m.discard());
			chief.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(BanditCamps.near(level, hall).isEmpty(), "the camp still stands");
			helper.assertTrue(level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && m.getTags().contains(BanditCamps.TAG)).isEmpty(), "the band didn't scatter");
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).count() == 3, "the chronicle: " + entity.chronicle());
			helper.succeed();
		});
	}

	/** Warding: with it researched, an explosion in the village breaks nothing; without it, it does. */
	//$ gametest_ticks_batch AREA '100' '"warding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "warding")
	public void wardingSparesTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.VILLAGE_HALL);
		for (int x = 2; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 2, 6), net.minecraft.world.level.block.Blocks.STONE_BRICKS);
		}
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 3)));
			hall.setResearch(new io.github.jcondedata.aliveworkplace.research.Research.State(java.util.Map.of("warding", 1), java.util.Optional.empty(), 0, false));
			var at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 3, 7.5));
			level.explode(null, at.x, at.y, at.z, 3f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
			for (int x = 2; x <= 6; x++) {
				helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.STONE_BRICKS, new BlockPos(x, 2, 6));
			}
			hall.setResearch(io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY);
			level.explode(null, at.x, at.y, at.z, 3f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
			boolean broke = false;
			for (int x = 2; x <= 6; x++) {
				broke |= !level.getBlockState(helper.absolutePos(new BlockPos(x, 2, 6))).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
			}
			helper.assertTrue(broke, "without Warding the blast broke nothing");
			helper.succeed();
		});
	}

	/** In a pillager raid a guard doesn't hide: their raid activity is a patrol. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void guardsDontHideFromPillagers(GameTestHelper helper) {
		var raid = net.minecraft.world.entity.ai.behavior.VillagerGoalPackages.getRaidPackage(ModVillagers.GUARD, 0.5f);
		helper.assertTrue(raid.stream().anyMatch(p -> p.getSecond() instanceof GuardPatrol), "a guard's raid package: " + raid);
		var farmer = net.minecraft.world.entity.ai.behavior.VillagerGoalPackages.getRaidPackage(net.minecraft.world.entity.npc.VillagerProfession.FARMER, 0.5f);
		helper.assertTrue(farmer.stream().noneMatch(p -> p.getSecond() instanceof GuardPatrol), "a farmer patrols");
		helper.succeed();
	}

	/** In a village with a guard, a finished Gatehouse's gates are shut at night and opened in the morning. */
	//$ gametest_ticks_batch AREA '100' '"gatesAreShutAtNight"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "gatesAreShutAtNight")
	public void gatesAreShutAtNight(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		ServerLevel level = helper.getLevel();
		var template = level.getStructureManager().get(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.GATEHOUSE.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 12));
		template.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.getRandom(), 2);
		var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), origin,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		var sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		sites.recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.GATEHOUSE.id(), placement, java.util.UUID.randomUUID());
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos gate = origin.offset(5, 1, 2);
		helper.assertTrue(level.getBlockState(gate).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock, "no gate at " + gate);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Gates.round(level, hall, 0) == 0, "gates moved without a guard");
		helper.setDayTime(2000);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Gates.round(level, hall, 1) == 3, "morning: the gates didn't open");
		helper.assertTrue(level.getBlockState(gate).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN), "still shut");
		helper.setDayTime(14000);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Gates.round(level, hall, 1) == 3, "night: the gates weren't shut");
		helper.assertFalse(level.getBlockState(gate).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN), "still open");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Gates.round(level, hall, 1) == 0, "shut twice");
		helper.succeed();
	}
}
