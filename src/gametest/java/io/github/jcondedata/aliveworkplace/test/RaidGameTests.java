package io.github.jcondedata.aliveworkplace.test;

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
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void biggerVillagesAreRaidedMoreOften(GameTestHelper helper) {
		helper.assertTrue(VillageRaids.nightlyChance(5) == 0f, "a small village raided");
		helper.assertTrue(Math.abs(VillageRaids.nightlyChance(8) - 0.15f) < 1e-4, "8: " + VillageRaids.nightlyChance(8));
		helper.assertTrue(VillageRaids.nightlyChance(20) > VillageRaids.nightlyChance(8), "20");
		helper.assertTrue(VillageRaids.nightlyChance(100) <= 0.35f, "100: " + VillageRaids.nightlyChance(100));
		helper.succeed();
	}

	/** A raid: raiders gather at the edge of the village, it's in the chronicle, and once they're dead the village has won. */
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

	/** In a pillager raid a guard doesn't hide: their raid activity is a patrol. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void guardsDontHideFromPillagers(GameTestHelper helper) {
		var raid = net.minecraft.world.entity.ai.behavior.VillagerGoalPackages.getRaidPackage(ModVillagers.GUARD, 0.5f);
		helper.assertTrue(raid.stream().anyMatch(p -> p.getSecond() instanceof GuardPatrol), "a guard's raid package: " + raid);
		var farmer = net.minecraft.world.entity.ai.behavior.VillagerGoalPackages.getRaidPackage(net.minecraft.world.entity.npc.VillagerProfession.FARMER, 0.5f);
		helper.assertTrue(farmer.stream().noneMatch(p -> p.getSecond() instanceof GuardPatrol), "a farmer patrols");
		helper.succeed();
	}
}
